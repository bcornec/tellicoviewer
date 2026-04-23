package org.hyper_linux.tellicoviewer.data.db

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room entities (SQLite tables).
 * NOTE: @Entity classes are NOT annotated @Serializable (KSP processor conflict).
 * JSON serialisation is done manually in Converters and the Repository.
 */

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun fromStringList(v: List<String>): String = json.encodeToString(v)
    @TypeConverter fun toStringList(v: String): List<String> =
        try { json.decodeFromString(v) } catch (e: Exception) { emptyList() }

    @TypeConverter fun fromStringMap(v: Map<String, String>): String = json.encodeToString(v)
    @TypeConverter fun toStringMap(v: String): Map<String, String> =
        try { json.decodeFromString(v) } catch (e: Exception) { emptyMap() }
}

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tellicoId: Int,
    val title: String,
    val type: String,
    val sourceFile: String,
    val importedAt: Long = System.currentTimeMillis(),
    val sourceModifiedAt: Long = 0L,
    val entryCount: Int = 0,
    val fileHash: String = "",
    /**
     * Absolute path to the directory containing external images.
     * Ex: /storage/emulated/0/Download/Livres_files
     * Null if images are embedded in the .tc file or absent.
     */
    val imageBasePath: String? = null
)

@Entity(
    tableName = "fields",
    foreignKeys = [ForeignKey(
        entity = CollectionEntity::class,
        parentColumns = ["id"], childColumns = ["collectionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("collectionId"), Index("name")]
)
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val name: String,
    val title: String,
    val type: String,
    val category: String = "General",
    val flags: Int = 0,
    val allowed: String = "[]",
    val defaultValue: String = "",
    val sortOrder: Int = 0
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = CollectionEntity::class,
        parentColumns = ["id"], childColumns = ["collectionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("collectionId"),
        Index("tellicoId"),
        Index(value = ["collectionId", "tellicoId"], unique = true)
    ]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val tellicoId: Int,
    /** JSON object: {"title":"Dune","author":"Herbert",...} */
    val fieldValues: String = "{}",
    /** JSON array: ["cover.jpg"] */
    val imageIds: String = "[]",
    val cachedTitle: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** FTS4 index for full-text search in O(log n). */
@Fts4(contentEntity = EntryEntity::class)
@Entity(tableName = "entries_fts")
data class EntryFtsEntity(
    val cachedTitle: String,
    val fieldValues: String
)

@Entity(
    tableName = "images",
    foreignKeys = [ForeignKey(
        entity = CollectionEntity::class,
        parentColumns = ["id"], childColumns = ["collectionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("collectionId"), Index("tellicoImageId")]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val tellicoImageId: String,
    val mimeType: String = "image/jpeg",
    val data: ByteArray,
    val sizeBytes: Int = data.size
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageEntity) return false
        return id == other.id && tellicoImageId == other.tellicoImageId
    }
    override fun hashCode(): Int = 31 * id.hashCode() + tellicoImageId.hashCode()
}

data class CollectionWithFieldCount(
    val id: Long, val title: String, val type: String,
    val entryCount: Int, val fieldCount: Int, val importedAt: Long,
    val imageBasePath: String? = null
)
