package org.fdroid.tellicoviewer.data.model

/**
 * Modèle de domaine pour une collection Tellico.
 *
 * ARCHITECTURE : Ce package contient les "Plain Old Kotlin Objects" (POKO),
 * indépendants d'Android et de Room. En Clean Architecture, ce sont les
 * entités du "Domain Layer" — elles ne connaissent ni la base de données,
 * ni l'interface utilisateur.
 *
 * Analogie C : ce sont les structs de base, sans dépendance à aucune lib.
 */

// ---------------------------------------------------------------------------
// Types de champs supportés par Tellico
// Correspond aux attributs "type" dans le XML Tellico
// ---------------------------------------------------------------------------

/**
 * Énumération des types de champs Tellico.
 * Chaque type détermine comment la valeur est affichée et filtrée.
 */
enum class FieldType(val xmlValue: String) {
    LINE("1"),         // Texte simple (titre, auteur...)
    PARA("2"),         // Paragraphe (synopsis, description)
    CHOICE("3"),       // Liste de choix prédéfinis (genre, format...)
    CHECKBOX("4"),     // Booléen (lu/non lu, possédé...)
    NUMBER("6"),       // Entier (année, pages, piste...)
    URL("7"),          // Lien URL
    TABLE("8"),        // Tableau multi-valeurs (acteurs, tags...)
    IMAGE("10"),       // Image embarquée
    DATE("12"),        // Date
    RATING("14"),      // Note (1-5 étoiles)
    TABLE2("15");      // Tableau à 2 colonnes

    companion object {
        fun fromXmlValue(v: String): FieldType =
            entries.firstOrNull { it.xmlValue == v } ?: LINE
    }
}

// ---------------------------------------------------------------------------
// Modèle d'un champ de collection (métadonnée de schéma)
// ---------------------------------------------------------------------------

/**
 * Représente un champ (colonne) défini dans le fichier Tellico.
 *
 * Exemple XML Tellico :
 * <field name="title" title="Title" type="1" flags="8" category="General" />
 *
 * @param name      Identifiant interne (ex: "title", "author", "year")
 * @param title     Label affiché à l'utilisateur (localisé dans le fichier TC)
 * @param type      Type de données
 * @param category  Groupe d'appartenance pour organiser l'interface
 * @param flags     Bitmask Tellico (requis=1, multiple=2, groupable=4, searchable=8...)
 * @param allowed   Pour CHOICE : liste des valeurs autorisées
 * @param defaultValue Valeur par défaut
 */
data class TellicoField(
    val name: String,
    val title: String,
    val type: FieldType,
    val category: String = "General",
    val flags: Int = 0,
    val allowed: List<String> = emptyList(),
    val defaultValue: String = "",
    val description: String = ""
) {
    // Flags Tellico (bitmask)
    val isRequired: Boolean    get() = flags and 0x01 != 0
    val isMultiple: Boolean    get() = flags and 0x02 != 0
    val isGroupable: Boolean   get() = flags and 0x04 != 0
    val isSearchable: Boolean  get() = flags and 0x08 != 0
}

// ---------------------------------------------------------------------------
// Modèle d'une entrée (un article de la collection)
// ---------------------------------------------------------------------------

/**
 * Représente un article de la collection (un livre, un CD, un film...).
 *
 * Les valeurs des champs sont stockées dans une Map<String, String>
 * car le schéma est dynamique (inconnu à la compilation).
 * C'est l'équivalent d'une row dans une table SQL avec des colonnes variables,
 * ou d'un hash Perl : %entry = (title => "...", author => "...", year => "2020").
 *
 * @param id        Identifiant unique Tellico (attribut id= de la balise <entry>)
 * @param fields    Map nom_champ -> valeur(s) sérialisée(s) en JSON
 * @param imageIds  Liste des identifiants d'images associées à cet article
 */
data class TellicoEntry(
    val id: Int,
    val fields: Map<String, String>,   // clé = TellicoField.name
    val imageIds: List<String> = emptyList()
) {
    /** Récupère la valeur d'un champ, chaîne vide si absent */
    fun getValue(fieldName: String): String = fields[fieldName] ?: ""

    /** Récupère un champ multi-valeurs (TABLE) comme liste */
    fun getList(fieldName: String): List<String> =
        fields[fieldName]
            ?.split(LIST_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    companion object {
        const val LIST_SEPARATOR = "::"  // séparateur utilisé par Tellico dans son XML
    }
}

// ---------------------------------------------------------------------------
// Modèle de la collection complète (métadonnées + données)
// ---------------------------------------------------------------------------

/**
 * Collection Tellico complète, telle que lue depuis un fichier .tc.
 *
 * @param id            Identifiant numérique de la collection dans le fichier
 * @param title         Nom de la collection (ex: "Ma cinémathèque")
 * @param type          Type Tellico (2=Livres, 3=Vidéos, 4=Musique, etc.)
 * @param fields        Schéma : liste ordonnée des champs
 * @param entries       Données : liste des articles
 * @param images        Images embarquées : id -> bytes PNG/JPEG
 * @param sourceFile    Chemin local du fichier .tc importé
 */
data class TellicoCollection(
    val id: Int = 0,
    val title: String = "",
    val type: CollectionType = CollectionType.CUSTOM,
    val fields: List<TellicoField> = emptyList(),
    val entries: List<TellicoEntry> = emptyList(),
    val images: Map<String, ByteArray> = emptyMap(),
    val sourceFile: String = ""
) {
    /** Champs visibles par défaut dans la liste (searchable ou les 3 premiers) */
    val primaryFields: List<TellicoField>
        get() = fields.filter { it.isSearchable }.take(5)
            .ifEmpty { fields.take(3) }
}

// ---------------------------------------------------------------------------
// Types de collections prédéfinis par Tellico
// ---------------------------------------------------------------------------

/**
 * Types de collections connus de Tellico.
 * Le XML Tellico contient un attribut "type" sur la balise <collection>.
 */
enum class CollectionType(val xmlValue: String, val labelKey: String) {
    BOOKS("2",      "collection_type_books"),
    VIDEO("3",      "collection_type_video"),
    MUSIC("4",      "collection_type_music"),
    COINS("6",      "collection_type_coins"),
    STAMPS("7",     "collection_type_stamps"),
    WINES("8",      "collection_type_wines"),
    GAMES("11",     "collection_type_games"),
    BOARDGAMES("13","collection_type_boardgames"),
    CUSTOM("1",     "collection_type_custom");

    companion object {
        fun fromXmlValue(v: String): CollectionType =
            entries.firstOrNull { it.xmlValue == v } ?: CUSTOM
    }
}

// ---------------------------------------------------------------------------
// Résultat de recherche avec score de pertinence
// ---------------------------------------------------------------------------

/**
 * Article enrichi d'un score de pertinence pour la recherche.
 * Le score permet le tri par pertinence (fuzzy search ranking).
 *
 * @param entry     L'article Tellico original
 * @param score     Score de pertinence (0.0 = aucune correspondance, 1.0 = parfait)
 * @param matches   Map champ -> fragment mis en évidence
 */
data class ScoredEntry(
    val entry: TellicoEntry,
    val score: Float,
    val matches: Map<String, String> = emptyMap()
)
