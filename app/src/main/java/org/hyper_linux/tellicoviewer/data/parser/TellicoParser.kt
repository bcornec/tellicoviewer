package org.hyper_linux.tellicoviewer.data.parser

import android.util.Log
import org.hyper_linux.tellicoviewer.data.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Tellico (.tc) file parser — final version validated on real files.
 *
 * UNIVERSAL RULE discovered by analysing 4 real files (Books, Comics, DVD, Records):
 *
 * Tellico génère TOUJOURS wrapper = field_name + "s" (jamais de pluriel grammatical).
 * - "title"       → <titles><title>…</title></titles>
 * - "series"      → <seriess><series>…</series></seriess>
 * - "nationality" → <nationalitys><nationality>…</nationality></nationalitys>
 * - "compositeur" → <compositeurs><compositeur>…</compositeur></compositeurs>
 *
 * EXCEPTION: DATE fields (type=12) share the tag name with field_name
 * but contain year/month/day child elements:
 *   <date-achat><year>2007</year><month>07</month><day>02</day></date-achat>
 *
 * ALGORITHME :
 * We build a wrapperIndex = { field_name+"s" → field_name } before parsing.
 * For each XML tag in an entry:
 *   - If tag is in wrapperIndex → it is a wrapper, read child TEXT nodes
 *   - If tag is a date field → read year/month/day → format as YYYY-MM-DD
 *   - Otherwise → read TEXT content directly
 *
 * NAMESPACE : le XML a xmlns="http://periapsis.org/tellico/". On désactive
 * namespace handling (isNamespaceAware=false) to read tags without prefix.
 * localName() strips braces in case they appear anyway.
 *
 * DOCTYPE: we disable DOCTYPE processing to avoid network access.
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

        // Indexes built after parsing <fields>.
        // wrapperIndex : "titles" → "title", "authors" → "author", etc.
        // Rule: wrapper = field_name + "s" (ALWAYS, no grammatical pluralisation).
        val wrapperIndex = mutableMapOf<String, String>()
        val dateFields   = mutableSetOf<String>()   // fields of type DATE (type=12)

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
                                // WRAPPER INDEX: field_name + "s" → field_name
                                wrapperIndex[fieldName + "s"] = fieldName
                                // Direct mapping too (tag == field_name for simple fields).
                                wrapperIndex[fieldName]       = fieldName
                                if (fieldType == "12") dateFields.add(fieldName)
                            }
                            // Skip <prop> child elements inside <field>.
                            skipElement(parser)
                        }

                        tag == "entry" && !inFields -> {
                            inEntry   = true
                            curId     = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: 0
                            curFields = mutableMapOf()
                            curImages = mutableListOf()
                        }

                        inEntry -> {
                            // Read the field and advance the parser to END_TAG inclusive.
                            readEntryChild(parser, tag, wrapperIndex, dateFields, curFields, curImages)
                            // Cursor is now ON the END_TAG of the current tag.
                            // The loop will call parser.next() → advances correctly.
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
    // Read a child tag of an entry.
    // Precondition : parser is on START_TAG of the tag.
    // Postcondition: parser is on END_TAG of the tag (caller will call next()).
    // -------------------------------------------------------------------------

    /**
     * Reads a child tag of an entry and stores its value in curFields.
     *
     * Handles 4 Tellico XML patterns:
     *
     * 1. DIRECT TEXT   : <binding>Paperback</binding>
     * 2. WRAPPER SIMPLE : <authors><author>Herbert</author></authors>
     *    → childTexts = ["Herbert"], value = "Herbert"
     * 3. TABLE 2 levels : <casts><cast><column>A</column><column>B</column></cast></casts>
     *    → each <cast> produces "A	B", joined by LIST_SEPARATOR
     * 4. DATE           : <date-achat><year>Y</year><month>M</month><day>D</day></date-achat>
     *    → value = "Y-MM-DD"
     *
     * Postcondition: parser cursor is on the END_TAG of the opening tag.
     */
    private fun readEntryChild(
        parser: XmlPullParser,
        tag: String,
        wrapperIndex: Map<String, String>,
        dateFields: Set<String>,
        curFields: MutableMap<String, String>,
        curImages: MutableList<String>
    ) {
        val fieldName = wrapperIndex[tag] ?: tag

        if (tag == "image") {
            parser.getAttributeValue(null, "id")?.takeIf { it.isNotEmpty() }
                ?.let { curImages.add(it) }
            skipElement(parser)
            return
        }

        var directText          = ""
        val rowValues           = mutableListOf<String>()   // row values (wrapper or table)
        val childDateParts      = mutableMapOf<String, String>()
        var depth               = 1

        var ev = parser.next()
        while (depth > 0) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val childTag = parser.name.localName()

                    when (childTag) {
                        // Date field: accumulate year/month/day directly.
                        "year", "month", "day" -> {
                            val txt = readTextContent(parser)  // reads to END_TAG of year/month/day
                            depth--
                            if (txt.isNotEmpty()) childDateParts[childTag] = txt
                        }
                        else -> {
                            // Lire le contenu de cet enfant.
                            // Il peut s'agir :
                            //   - direct text (simple wrapper: <author>Herbert</author>)
                            //   - <column> sub-elements (table: <cast><column>A</column><column>B</column></cast>)
                            val row = readChildWithColumns(parser)
                            depth--
                            if (row.isNotEmpty()) rowValues.add(row)
                        }
                    }
                }
                XmlPullParser.TEXT -> directText += parser.text?.trim() ?: ""
                XmlPullParser.END_TAG -> depth--
                else -> {}
            }
            if (depth > 0) ev = parser.next()
        }
        // Cursor is on END_TAG of the opening tag ✓

        val value: String = when {
            fieldName in dateFields && childDateParts.isNotEmpty() -> {
                val y = childDateParts["year"] ?: return
                val m = (childDateParts["month"] ?: "01").padStart(2, '0')
                val d = (childDateParts["day"]   ?: "01").padStart(2, '0')
                "$y-$m-$d"
            }
            rowValues.isNotEmpty() -> rowValues.joinToString(TellicoEntry.LIST_SEPARATOR)
            directText.isNotEmpty() -> directText
            else -> return
        }

        val existing = curFields[fieldName]
        curFields[fieldName] = if (existing != null)
            "$existing${TellicoEntry.LIST_SEPARATOR}$value" else value
    }

    /**
     * Reads the simple text content of an element up to its END_TAG.
     * Precondition: cursor just AFTER the START_TAG (called after seeing START_TAG and
     * incrementing depth, so the caller must decrement depth after).
     * Postcondition: cursor ON the END_TAG of the element.
     */
    private fun readTextContent(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var ev = parser.next()
        while (ev != XmlPullParser.END_TAG && ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.TEXT) sb.append(parser.text?.trim() ?: "")
            else if (ev == XmlPullParser.START_TAG) skipElement(parser)
            ev = parser.next()
        }
        return sb.toString().trim()
    }

    /**
     * Reads a child that may contain direct text or <column> sub-elements.
     *
     * Case 1 — direct text (simple wrapper):
     *   <author>Herbert</author>  →  "Herbert"
     *
     * Cas 2 — colonnes (table) :
     *   <cast><column>Tom Cruise</column><column>Ethan Hunt</column></cast>  →  "Tom Cruise	Ethan Hunt"
     *
     * Precondition: cursor just AFTER the START_TAG of the child.
     * Postcondition: cursor ON the END_TAG of the child.
     * Le caller doit décrémenter depth.
     */
    private fun readChildWithColumns(parser: XmlPullParser): String {
        val columns = mutableListOf<String>()
        var directText = ""
        var ev = parser.next()

        while (ev != XmlPullParser.END_TAG && ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.TEXT -> directText += parser.text?.trim() ?: ""
                XmlPullParser.START_TAG -> {
                    // Sub-element: read its text (typically <column>).
                    val colText = readTextContent(parser)
                    if (colText.isNotEmpty()) columns.add(colText)
                }
            }
            ev = parser.next()
        }
        // ev == END_TAG de l'enfant ✓

        return when {
            columns.isNotEmpty() -> columns.joinToString("	")  // "Acteur	Rôle"
            directText.isNotEmpty() -> directText
            else -> ""
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    /**
     * Skips all tokens up to the END_TAG matching the current START_TAG.
     * Call when cursor is ON the START_TAG to skip.
     * Postcondition: cursor on the corresponding END_TAG.
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
