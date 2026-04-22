package org.fdroid.tellicoviewer.data.db

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAOs (Data Access Objects).
 *
 * Room generates SQL implementations at compile time via KSP.
 * Each @Query is validated statically — SQL errors are build errors.
 *
 * NOTE FTS: Room + FTS4 with contentEntity has two key constraints:
 *  1. `rank` is NOT available in FTS4 (FTS5 only).
 *     → Use ORDER BY rowid DESC (insertion order) or cachedTitle.
 *  2. The FTS↔entries join must use fts.rowid = e.id (Room generates
 *     the FTS table with rowid = id of the source table).
 *  3. To return a PagingSource from an FTS query with JOIN,
 *     the query must return EntryEntity via e.* with the main table
 *     (entries) in the FROM clause.
 */

// ---------------------------------------------------------------------------
// Collections DAO.
// ---------------------------------------------------------------------------

@Dao
interface CollectionDao {

    @Query("""
        SELECT c.id, c.title, c.type, c.entryCount,
               COUNT(f.id) AS fieldCount, c.importedAt, c.imageBasePath
        FROM collections c
        LEFT JOIN fields f ON f.collectionId = c.id
        GROUP BY c.id
        ORDER BY c.importedAt DESC
    """)
    fun observeCollections(): Flow<List<CollectionWithFieldCount>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): CollectionEntity?

    @Query("SELECT imageBasePath FROM collections WHERE id = :id")
    fun observeImageBasePath(id: Long): kotlinx.coroutines.flow.Flow<String?>

    @Query("UPDATE collections SET imageBasePath = :path WHERE id = :id")
    suspend fun updateImageBasePath(id: Long, path: String?)

    @Query("SELECT * FROM collections WHERE sourceFile = :path")
    suspend fun getBySourceFile(path: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionEntity): Long

    @Update
    suspend fun update(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE collections SET entryCount = :count WHERE id = :id")
    suspend fun updateEntryCount(id: Long, count: Int)
}

// ---------------------------------------------------------------------------
// Fields (schema) DAO.
// ---------------------------------------------------------------------------

@Dao
interface FieldDao {

    @Query("SELECT * FROM fields WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    suspend fun getFieldsForCollection(collectionId: Long): List<FieldEntity>

    @Query("SELECT * FROM fields WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    fun observeFieldsForCollection(collectionId: Long): Flow<List<FieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fields: List<FieldEntity>)

    @Query("DELETE FROM fields WHERE collectionId = :collectionId")
    suspend fun deleteForCollection(collectionId: Long)
}

// ---------------------------------------------------------------------------
// Entries DAO.
// ---------------------------------------------------------------------------

@Dao
interface EntryDao {

    // PagingSources for paginated list display.

    @Query("SELECT * FROM entries WHERE collectionId = :collectionId ORDER BY cachedTitle ASC")
    fun pagingSourceByTitle(collectionId: Long): PagingSource<Int, EntryEntity>

    @Query("SELECT * FROM entries WHERE collectionId = :collectionId ORDER BY id ASC")
    fun pagingSourceById(collectionId: Long): PagingSource<Int, EntryEntity>

    // ---- Recherche FTS ----
    //
    // WHY THIS APPROACH:
    // FTS4 with contentEntity does not support ORDER BY rank (that is FTS5).
    // Correct solution for Room + FTS4:
    //   1. FTS query to get matching rowids.
    //   2. Main query on entries with WHERE id IN (...)
    //
    // For a PagingSource, use a SQL query that performs the join
    // correctly without using `rank`.
    //
    // The `entries_fts.rowid` column maps to `entries.id` (Room FTS contentEntity).
    //
    // MATCH support : "mot*" (préfix), "mot1 mot2" (ET), "mot1 OR mot2"

    @Query("""
        SELECT e.* FROM entries e
        WHERE e.collectionId = :collectionId
          AND e.id IN (
              SELECT rowid FROM entries_fts
              WHERE entries_fts MATCH :query
          )
        ORDER BY e.cachedTitle ASC
    """)
    fun searchFtsPaged(collectionId: Long, query: String): PagingSource<Int, EntryEntity>

    // ---- Field filter (json_extract) ----
    //
    // json_extract(json, '$.key') extracts the value for a key from JSON.
    // Available in SQLite 3.38+ (Android 12+), partially supported before.
    // For Android 8+ (API 26) compatibility, fall back to LIKE on the full fieldValues
    // — less precise but universal.

    @Query("""
        SELECT * FROM entries
        WHERE collectionId = :collectionId
          AND (
              json_extract(fieldValues, '$.' || :fieldName) LIKE '%' || :value || '%'
              OR fieldValues LIKE '%' || :value || '%'
          )
        ORDER BY cachedTitle ASC
    """)
    fun filterByField(
        collectionId: Long,
        fieldName: String,
        value: String
    ): PagingSource<Int, EntryEntity>

    // ---- Accès individuel ----

    @Query("SELECT * FROM entries WHERE collectionId = :collectionId AND tellicoId = :tellicoId")
    suspend fun getByTellicoId(collectionId: Long, tellicoId: Int): EntryEntity?

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE collectionId = :collectionId")
    suspend fun countForCollection(collectionId: Long): Int

    // ---- Insertion en batch ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE collectionId = :collectionId")
    suspend fun deleteForCollection(collectionId: Long)

    // ---- Distinct values for filters ----
    //
    // Returns distinct values for a field to populate filter menus.
    // json_extract() with '$.' || fieldName extracts the key dynamically.
    // LIMIT 200: avoids overwhelming the UI with too many choices.

    @Query("""
        SELECT DISTINCT json_extract(fieldValues, '$.' || :fieldName) AS val
        FROM entries
        WHERE collectionId = :collectionId
          AND val IS NOT NULL AND val != ''
        ORDER BY val ASC
        LIMIT 200
    """)
    suspend fun getDistinctValues(collectionId: Long, fieldName: String): List<String>
}

// ---------------------------------------------------------------------------
// Images DAO.
// ---------------------------------------------------------------------------

@Dao
interface ImageDao {

    @Query("""
        SELECT * FROM images
        WHERE collectionId = :collectionId AND tellicoImageId = :imageId
    """)
    suspend fun getImage(collectionId: Long, imageId: String): ImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ImageEntity>)

    @Query("DELETE FROM images WHERE collectionId = :collectionId")
    suspend fun deleteForCollection(collectionId: Long)

    @Query("""
        SELECT id, collectionId, tellicoImageId, mimeType, sizeBytes
        FROM images WHERE collectionId = :collectionId
    """)
    suspend fun listImagesForCollection(collectionId: Long): List<ImageMetadata>
}

/** Image metadata without the binary BLOB (list without loading data into RAM). */
data class ImageMetadata(
    val id: Long,
    val collectionId: Long,
    val tellicoImageId: String,
    val mimeType: String,
    val sizeBytes: Int
)
