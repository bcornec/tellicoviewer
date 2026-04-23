package org.hyper_linux.tellicoviewer.data.model

/**
 * Domain model for a Tellico collection.
 *
 * ARCHITECTURE: This package contains Plain Old Kotlin Objects (POKO),
 * independent of Android and Room. In Clean Architecture these are the
 * "Domain Layer" entities — they know nothing about the database,
 * ni l'interface utilisateur.
 *
 * C analogy: plain structs with no library dependencies.
 */

// ---------------------------------------------------------------------------
// Field types supported by Tellico.
// Matches the "type" attribute in Tellico XML.
// ---------------------------------------------------------------------------

/**
 * Enumeration of Tellico field types.
 * Each type determines how values are displayed and filtered.
 */
enum class FieldType(val xmlValue: String) {
    LINE("1"),         // Single-line text (title, author…)
    PARA("2"),         // Paragraphe (synopsis, description)
    CHOICE("3"),       // Predefined choice list (genre, format…)
    CHECKBOX("4"),     // Booléen (lu/non lu, possédé...)
    NUMBER("6"),       // Integer (year, pages, track…)
    URL("7"),          // Lien URL
    TABLE("8"),        // Multi-value table (actors, tags…)
    IMAGE("10"),       // Embedded image
    DATE("12"),        // Date
    RATING("14"),      // Note (1-5 étoiles)
    TABLE2("15");      // Two-column table

    companion object {
        fun fromXmlValue(v: String): FieldType =
            entries.firstOrNull { it.xmlValue == v } ?: LINE
    }
}

// ---------------------------------------------------------------------------
// Collection field model (schema metadata)
// ---------------------------------------------------------------------------

/**
 * Represents a field (column) defined in the Tellico file.
 *
 * Exemple XML Tellico :
 * <field name="title" title="Title" type="1" flags="8" category="General" />
 *
 * @param name      Identifiant interne (ex: "title", "author", "year")
 * @param title     Label shown to the user (localised in the TC file)
 * @param type      Data type
 * @param category  Group for organising the UI
 * @param flags     Bitmask Tellico (requis=1, multiple=2, groupable=4, searchable=8...)
 * @param allowed   For CHOICE: list of permitted values
 * @param defaultValue Default value
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
// Model for a single collection entry.
// ---------------------------------------------------------------------------

/**
 * Represents a collection entry (a book, CD, film…).
 *
 * Field values are stored in a Map<String, String>
 * because the schema is dynamic (unknown at compile time).
 * Equivalent to a SQL row with variable columns,
 * or a Perl hash: %entry = (title => "...", author => "...", year => "2020").
 *
 * @param id        Identifiant unique Tellico (attribut id= de la balise <entry>)
 * @param fields    Map field_name -> serialised value(s) in JSON
 * @param imageIds  List of image identifiers associated with this entry
 */
data class TellicoEntry(
    val id: Int,
    val fields: Map<String, String>,   // key = TellicoField.name
    val imageIds: List<String> = emptyList()
) {
    /** Returns the value of a field, empty string if absent. */
    fun getValue(fieldName: String): String = fields[fieldName] ?: ""

    /** Returns a multi-value field (TABLE) as a list. */
    fun getList(fieldName: String): List<String> =
        fields[fieldName]
            ?.split(LIST_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    companion object {
        const val LIST_SEPARATOR = "::"  // separator used by Tellico XML for multi-value fields
    }
}

// ---------------------------------------------------------------------------
// Full collection model (metadata + data)
// ---------------------------------------------------------------------------

/**
 * Complete Tellico collection as read from a .tc file.
 *
 * @param id            Numeric collection identifier in the file
 * @param title         Collection name (e.g. "My Film Library")
 * @param type          Tellico type (2=Books, 3=Videos, 4=Music, etc.)
 * @param fields        Schema: ordered list of fields
 * @param entries       Data: list of entries
 * @param images        Images embarquées : id -> bytes PNG/JPEG
 * @param sourceFile    Local path of the imported .tc file
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
    /** Fields visible by default in the list (searchable or first 3). */
    val primaryFields: List<TellicoField>
        get() = fields.filter { it.isSearchable }.take(5)
            .ifEmpty { fields.take(3) }
}

// ---------------------------------------------------------------------------
// Collection types predefined by Tellico.
// ---------------------------------------------------------------------------

/**
 * Types de collections connus de Tellico.
 * Tellico XML has a "type" attribute on the <collection> tag.
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
// Search result with relevance score.
// ---------------------------------------------------------------------------

/**
 * Entry enriched with a relevance score for search.
 * The score enables sorting by relevance (fuzzy search ranking).
 *
 * @param entry     The original Tellico entry
 * @param score     Score de pertinence (0.0 = aucune correspondance, 1.0 = parfait)
 * @param matches   Map field -> highlighted fragment
 */
data class ScoredEntry(
    val entry: TellicoEntry,
    val score: Float,
    val matches: Map<String, String> = emptyMap()
)
