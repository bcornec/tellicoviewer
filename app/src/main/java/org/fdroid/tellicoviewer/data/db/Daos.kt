package org.fdroid.tellicoviewer.data.db

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAOs (Data Access Objects) Room.
 *
 * Room génère l'implémentation SQL à la compilation via KSP.
 * Chaque @Query est validée statiquement — les erreurs SQL sont des erreurs de build.
 *
 * NOTE FTS : Room + FTS4 avec contentEntity a deux contraintes importantes :
 *  1. `rank` n'est PAS disponible dans FTS4 (c'est FTS5 seulement).
 *     → On utilise ORDER BY rowid DESC (ordre d'insertion) ou cachedTitle.
 *  2. La jointure FTS↔entries doit utiliser fts.rowid = e.id (Room génère
 *     la table FTS avec rowid = id de la table source).
 *  3. Pour retourner un PagingSource depuis une requête FTS avec JOIN,
 *     il faut que la requête retourne EntryEntity via e.* et que la table
 *     principale (entries) soit celle dans FROM.
 */

// ---------------------------------------------------------------------------
// DAO des collections
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
// DAO des champs (schéma)
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
// DAO des entrées (articles)
// ---------------------------------------------------------------------------

@Dao
interface EntryDao {

    // ---- PagingSources pour l'affichage en liste ----

    @Query("SELECT * FROM entries WHERE collectionId = :collectionId ORDER BY cachedTitle ASC")
    fun pagingSourceByTitle(collectionId: Long): PagingSource<Int, EntryEntity>

    @Query("SELECT * FROM entries WHERE collectionId = :collectionId ORDER BY id ASC")
    fun pagingSourceById(collectionId: Long): PagingSource<Int, EntryEntity>

    // ---- Recherche FTS ----
    //
    // POURQUOI CETTE APPROCHE :
    // FTS4 avec contentEntity ne supporte pas ORDER BY rank (c'est FTS5).
    // La solution correcte pour Room + FTS4 :
    //   1. Requête FTS pour obtenir les rowids correspondants
    //   2. Requête principale sur entries avec WHERE id IN (...)
    //
    // Pour un PagingSource, on utilise une requête SQL qui fait la jointure
    // correctement sans utiliser `rank`.
    //
    // La colonne `entries_fts.rowid` correspond à `entries.id` (Room FTS contentEntity).
    //
    // MATCH supporte : "mot*" (préfixe), "mot1 mot2" (ET), "mot1 OR mot2"

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

    // ---- Filtre par champ (json_extract) ----
    //
    // json_extract(json, '$.key') extrait la valeur de la clé dans le JSON.
    // Disponible dans SQLite 3.38+ (Android 12+) et partiellement avant.
    // Pour compatibilité Android 8+ (API 26), on utilise LIKE sur fieldValues entier
    // en fallback — moins précis mais universel.

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

    // ---- Valeurs distinctes pour les filtres ----
    //
    // Retourne les valeurs distinctes d'un champ pour alimenter les menus de filtre.
    // json_extract() avec '$.' || fieldName extrait la clé dynamiquement.
    // LIMIT 200 : évite de surcharger l'UI avec trop de choix.

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
// DAO des images
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

/** Métadonnées d'image sans le BLOB binaire (pour lister sans tout charger en RAM) */
data class ImageMetadata(
    val id: Long,
    val collectionId: Long,
    val tellicoImageId: String,
    val mimeType: String,
    val sizeBytes: Int
)
