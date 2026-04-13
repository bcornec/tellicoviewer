package org.fdroid.tellicoviewer.data.parser

import android.util.Log
import org.fdroid.tellicoviewer.data.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Parseur de fichiers Tellico (.tc) — version finale validée sur fichiers réels.
 *
 * RÈGLE UNIVERSELLE découverte par analyse de 4 fichiers réels (Livres, BD, DVD, Disques) :
 *
 * Tellico génère TOUJOURS wrapper = field_name + "s" (jamais de pluriel grammatical).
 * - "title"       → <titles><title>…</title></titles>
 * - "series"      → <seriess><series>…</series></seriess>
 * - "nationality" → <nationalitys><nationality>…</nationality></nationalitys>
 * - "compositeur" → <compositeurs><compositeur>…</compositeur></compositeurs>
 *
 * EXCEPTION : les champs de type DATE (type=12) ont le même tag que field_name
 * mais contiennent des sous-éléments year/month/day :
 *   <date-achat><year>2007</year><month>07</month><day>02</day></date-achat>
 *
 * ALGORITHME :
 * On construit un index wrapperIndex = { field_name+"s" → field_name } avant le parsing.
 * Pour chaque tag XML dans une entry :
 *   - Si tag dans wrapperIndex → c'est un wrapper, lire les TEXT des enfants
 *   - Si tag est un champ date → lire year/month/day → formater en YYYY-MM-DD
 *   - Sinon → lire le TEXT direct
 *
 * NAMESPACE : le XML a xmlns="http://periapsis.org/tellico/". On désactive
 * la gestion des namespaces (isNamespaceAware=false) pour lire les tags sans préfixe.
 * La méthode localName() strip les accolades si jamais elles apparaissent quand même.
 *
 * DOCTYPE : on désactive le traitement du DOCTYPE pour éviter les accès réseau.
 */
class TellicoParser {

    companion object {
        private const val TAG = "TellicoParser"
        private const val TELLICO_XML = "tellico.xml"
    }

    data class ParseResult(
        val collection: TellicoCollection,
        val images: Map<String, ByteArray>
    )

    @Throws(TellicoParseException::class)
    suspend fun parse(
        inputStream: InputStream,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): ParseResult {
        val images = mutableMapOf<String, ByteArray>()
        var xmlContent: ByteArray? = null

        onProgress(5, "Décompression de l'archive...")
        try {
            ZipInputStream(inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == TELLICO_XML -> xmlContent = zip.readBytes()
                        entry.name.startsWith("images/") && !entry.isDirectory ->
                            images[entry.name.removePrefix("images/")] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: IOException) {
            throw TellicoParseException("Erreur ZIP : ${e.message}", e)
        }

        val xml = xmlContent ?: throw TellicoParseException("tellico.xml absent de l'archive")
        onProgress(20, "Parsing XML...")
        val collection = parseXml(xml, onProgress)
        onProgress(100, "Import terminé")
        return ParseResult(collection, images)
    }

    // -------------------------------------------------------------------------
    // Parser XML — machine à états explicite, namespace-agnostique
    // -------------------------------------------------------------------------

    private suspend fun parseXml(
        xmlBytes: ByteArray,
        onProgress: suspend (Int, String) -> Unit
    ): TellicoCollection {

        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }.newPullParser().apply {
            try { setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
            catch (_: Exception) {}
            setInput(xmlBytes.inputStream(), "UTF-8")
        }

        var collId    = 0
        var collTitle = ""
        var collType  = CollectionType.CUSTOM

        val fields  = mutableListOf<TellicoField>()
        val entries = mutableListOf<TellicoEntry>()

        // Index construits après parsing de <fields>
        // wrapperIndex : "titles" → "title", "authors" → "author", etc.
        // Règle : wrapper = field_name + "s" (TOUJOURS, pas de pluriel grammatical)
        val wrapperIndex = mutableMapOf<String, String>()
        val dateFields   = mutableSetOf<String>()   // champs type DATE (type=12)

        var inFields = false
        var inEntry  = false
        var entryCount = 0
        var curId    = 0
        var curFields = mutableMapOf<String, String>()
        var curImages = mutableListOf<String>()

        try {
            var evt = parser.eventType
            mainLoop@ while (evt != XmlPullParser.END_DOCUMENT) {

                if (evt == XmlPullParser.START_TAG) {
                    val tag = parser.name.localName()

                    when {
                        tag == "collection" -> {
                            collId    = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: 0
                            collTitle = parser.getAttributeValue(null, "title") ?: ""
                            collType  = CollectionType.fromXmlValue(
                                parser.getAttributeValue(null, "type") ?: "1")
                        }

                        tag == "fields" -> inFields = true

                        tag == "field" && inFields -> {
                            val fieldName = parser.getAttributeValue(null, "name") ?: ""
                            val fieldType = parser.getAttributeValue(null, "type") ?: "1"
                            if (fieldName.isNotEmpty()) {
                                fields.add(TellicoField(
                                    name         = fieldName,
                                    title        = parser.getAttributeValue(null, "title") ?: "",
                                    type         = FieldType.fromXmlValue(fieldType),
                                    category     = parser.getAttributeValue(null, "category") ?: "General",
                                    flags        = parser.getAttributeValue(null, "flags")?.toIntOrNull() ?: 0,
                                    allowed      = parser.getAttributeValue(null, "allowed")
                                                       ?.split(";")?.filter { it.isNotBlank() } ?: emptyList(),
                                    defaultValue = parser.getAttributeValue(null, "default") ?: "",
                                    description  = parser.getAttributeValue(null, "description") ?: ""
                                ))
                                // INDEX WRAPPER : field_name + "s" → field_name
                                wrapperIndex[fieldName + "s"] = fieldName
                                // Mapping direct aussi (tag = field_name pour les champs directs)
                                wrapperIndex[fieldName]       = fieldName
                                if (fieldType == "12") dateFields.add(fieldName)
                            }
                            // Ignorer les sous-éléments <prop> du field
                            skipElement(parser)
                        }

                        tag == "entry" && !inFields -> {
                            inEntry   = true
                            curId     = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: 0
                            curFields = mutableMapOf()
                            curImages = mutableListOf()
                        }

                        inEntry -> {
                            // Lire le champ et avancer le parser jusqu'au END_TAG inclus
                            readEntryChild(parser, tag, wrapperIndex, dateFields, curFields, curImages)
                            // Le curseur est maintenant SUR le END_TAG du tag courant.
                            // La boucle va faire parser.next() → avance correctement.
                        }
                    }
                }

                if (evt == XmlPullParser.END_TAG) {
                    when (parser.name.localName()) {
                        "fields" -> {
                            inFields = false
                            Log.d(TAG, "Schéma: ${fields.size} champs, index=${wrapperIndex.keys.take(10)}")
                        }
                        "entry" -> if (inEntry) {
                            entries.add(TellicoEntry(curId, curFields.toMap(), curImages.toList()))
                            inEntry = false
                            entryCount++
                            if (entryCount % 200 == 0) {
                                onProgress((20 + entryCount / 100).coerceAtMost(90),
                                    "Import : $entryCount articles...")
                            }
                        }
                    }
                }

                evt = parser.next()
            }
        } catch (e: XmlPullParserException) {
            throw TellicoParseException("Erreur XML L${e.lineNumber}: ${e.message}", e)
        }

        Log.i(TAG, "Parsé: ${fields.size} champs, ${entries.size} entrées")
        return TellicoCollection(collId, collTitle, collType, fields, entries)
    }

    // -------------------------------------------------------------------------
    // Lecture d'un tag enfant d'une entry
    // Précondition  : parser est sur START_TAG du tag
    // Postcondition : parser est sur END_TAG du tag (la boucle appelante fera next())
    // -------------------------------------------------------------------------

    private fun readEntryChild(
        parser: XmlPullParser,
        tag: String,
        wrapperIndex: Map<String, String>,
        dateFields: Set<String>,
        curFields: MutableMap<String, String>,
        curImages: MutableList<String>
    ) {
        // Résoudre le field_name cible
        val fieldName = wrapperIndex[tag] ?: tag   // fallback = tag lui-même

        // Cas spécial : image
        if (tag == "image") {
            parser.getAttributeValue(null, "id")?.takeIf { it.isNotEmpty() }
                ?.let { curImages.add(it) }
            skipElement(parser)
            return
        }

        // Lire TOUT le contenu de ce tag (enfants + texte)
        // On construit : directText et la liste des enfants (childTag, childText)
        var directText    = ""
        val childTexts    = mutableListOf<String>()      // textes des enfants
        val childDateParts = mutableMapOf<String, String>()  // "year","month","day" → valeur
        var depth = 1

        var ev = parser.next()
        while (depth > 0) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val childTag = parser.name.localName()
                    // Lire le texte de cet enfant (peut lui-même avoir des enfants, ex: tracks)
                    val childSb = StringBuilder()
                    var cev = parser.next()
                    while (cev != XmlPullParser.END_TAG && cev != XmlPullParser.END_DOCUMENT) {
                        if (cev == XmlPullParser.TEXT) childSb.append(parser.text?.trim() ?: "")
                        // Ignorer les sous-sous-éléments (ex: colonnes d'une piste)
                        if (cev == XmlPullParser.START_TAG) { skipElement(parser); continue }
                        cev = parser.next()
                    }
                    // cev == END_TAG du childTag → profondeur descend
                    depth--
                    val ct = childSb.toString().trim()
                    // Accumuler selon le type de contenu
                    when (childTag) {
                        "year", "month", "day" -> childDateParts[childTag] = ct
                        else -> if (ct.isNotEmpty()) childTexts.add(ct)
                    }
                }
                XmlPullParser.TEXT -> directText += parser.text?.trim() ?: ""
                XmlPullParser.END_TAG -> depth--
                else -> {}
            }
            if (depth > 0) ev = parser.next()
        }
        // Ici : parser est sur END_TAG du tag de départ ✓

        // Choisir la valeur à stocker
        val value: String = when {
            // Date avec year/month/day
            fieldName in dateFields && childDateParts.isNotEmpty() -> {
                val y = childDateParts["year"] ?: return
                val m = (childDateParts["month"] ?: "01").padStart(2, '0')
                val d = (childDateParts["day"]   ?: "01").padStart(2, '0')
                "$y-$m-$d"
            }
            // Wrapper pluriel ou champ multi-valeurs
            childTexts.isNotEmpty() -> childTexts.joinToString(TellicoEntry.LIST_SEPARATOR)
            // Texte direct
            directText.isNotEmpty() -> directText
            else -> return  // vide → ne rien stocker
        }

        // Stocker (concaténer si déjà présent)
        val existing = curFields[fieldName]
        curFields[fieldName] = if (existing != null)
            "$existing${TellicoEntry.LIST_SEPARATOR}$value" else value
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    /**
     * Saute tous les tokens jusqu'au END_TAG correspondant au START_TAG courant.
     * Appeler quand le curseur est SUR le START_TAG à ignorer.
     * Postcondition : curseur sur le END_TAG correspondant.
     */
    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG   -> depth--
            }
        }
    }

    /** Enlève le namespace du tag si présent (ex: "{http://...}title" → "title") */
    private fun String.localName() = if (contains('}')) substringAfterLast('}') else this
}

class TellicoParseException(message: String, cause: Throwable? = null) :
    IOException(message, cause)
