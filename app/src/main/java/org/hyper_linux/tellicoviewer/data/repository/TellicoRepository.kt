package org.hyper_linux.tellicoviewer.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.paging.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyper_linux.tellicoviewer.data.db.*
import org.hyper_linux.tellicoviewer.data.model.*
import org.hyper_linux.tellicoviewer.data.parser.TellicoParser
import org.hyper_linux.tellicoviewer.util.SearchEngine
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository : couche d'accès aux données unifiée.
 *
 * RÔLE DU REPOSITORY (Clean Architecture) :
 * - Abstracts the data source (local DB, network, file)
 * - ViewModels do not know whether data comes from SQLite or the network
 * - Transforms DB entities into domain models
 *
 * Unix analogy: the Repository is like a VFS (Virtual File System).
 * Callers call open("file") without knowing whether it is ext4, NFS or tmpfs.
 *
 * DATA PIPELINE (import):
 *   .tc file URI
 *   → ContentResolver.openInputStream()
 *   → TellicoParser.parse()        (ZIP decompression + XML parsing)
 *   → insertCollectionToDb()       (JSON serialisation + Room batch insert)
 *   → PagingSource                 (paginated read from Room)
 *   → ViewModel                    (UI state)
 *   → Compose LazyColumn/Grid      (affichage)
 */
@Singleton
class TellicoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TellicoDatabase,
    private val parser: TellicoParser,
    private val searchEngine: SearchEngine
) {
    companion object {
        private const val TAG = "TellicoRepository"
        private const val PAGE_SIZE = 30          // entries per Paging3 page
        private const val PREFETCH_DISTANCE = 60  // articles préchargés en avance
        private const val BATCH_SIZE = 500        // articles insérés par transaction SQL
    }

    // ---------------------------------------------------------------------------
    // Observe collections.
    // ---------------------------------------------------------------------------

    /** Observable Flow of all collections (auto-updates on DB change). */
    fun observeCollections(): Flow<List<CollectionWithFieldCount>> =
        db.collectionDao().observeCollections()

    /** Flow of the schema (fields) for a collection. */
    suspend fun getImageBasePath(collectionId: Long): String? =
        db.collectionDao().getById(collectionId)?.imageBasePath

    /**
     * Reactive Flow on a collection's imageBasePath.
     * Emits a new value whenever the collection is updated in the database
     * (e.g. after an import that recomputes imageBasePath).
     */
    fun observeImageBasePath(collectionId: Long): kotlinx.coroutines.flow.Flow<String?> =
        db.collectionDao().observeImageBasePath(collectionId)

    suspend fun updateImageBasePath(collectionId: Long, path: String?) {
        db.collectionDao().updateImageBasePath(collectionId, path)
    }

    fun observeFields(collectionId: Long): Flow<List<TellicoField>> =
        db.fieldDao().observeFieldsForCollection(collectionId)
            .map { entities -> entities.map { it.toDomain() } }

    // ---------------------------------------------------------------------------
    // .tc file import.
    // ---------------------------------------------------------------------------

    /**
     * Imports a .tc file from an Android URI (SAF — Storage Access Framework).
     *
     * SAF = Android mechanism for secure file access,
     * whether on local storage, an SD card or Google Drive.
     * Equivalent: an fd opened by the kernel and passed to our process.
     *
     * @param uri URI of the .tc file (obtained via Intent or file picker)
     * @param onProgress Progress callback for the UI
     * @return Collection ID in the database, or null on error
     */
    suspend fun importFromUri(
        uri: Uri,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): Result<Long> = runCatching {
        Log.i(TAG, "Import depuis URI : $uri")

        // Tout le travail lourd (I/O fichier, ZIP, parsing XML, SHA-256, inserts Room)
        // est exécuté sur Dispatchers.IO pour ne jamais bloquer le thread UI.
        // onProgress émet via StateFlow thread-safe → pas besoin de revenir sur Main.
        withContext(Dispatchers.IO) {
            onProgress(5, "Vérification du fichier...")

            // Pass 1: compute SHA-256 hash of the ZIP file (no decompression).
            // Stream the raw bytes once for the hash.
            val hash = context.contentResolver.openInputStream(uri)
                ?.use { sha256Stream(it) }
                ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier : $uri")

            val existingByPath = db.collectionDao().getBySourceFile(uri.toString())
            if (existingByPath != null) {
                Log.i(TAG, "Collection déjà importée, mise à jour...")
                db.collectionDao().deleteById(existingByPath.id)
            }

            onProgress(8, "Parsing du fichier...")

            // Pass 2: parse the file via streaming (no full in-memory load).
            val result = context.contentResolver.openInputStream(uri)
                ?.use { parser.parse(it, onProgress) }
                ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier : $uri")

            val collection = result.collection

            // Resolve the external images directory (e.g. Livres_files/).
            val imageBasePath = resolveImageBasePath(uri, collection.title)

            // Insert into Room database.
            val collectionId = insertCollectionToDb(
                collection    = collection,
                images        = result.images,
                sourceUri     = uri.toString(),
                fileHash      = hash,
                imageBasePath = imageBasePath,
                onProgress    = onProgress
            )

            Log.i(TAG, "Import réussi : ${collection.entries.size} articles, ID=$collectionId")
            collectionId
        }
    }

    // ---------------------------------------------------------------------------
    // Persistance en base
    // ---------------------------------------------------------------------------

    /**
     * Inserts a complete collection into Room across multiple transactions.
     *
     * Batching into BATCH_SIZE chunks to:
     * 1. Avoid overly long transactions (SQLite lock)
     * 2. Permettre la mise à jour de progression
     * 3. Limit memory usage (JSON serialisation of 500 entries at a time)
     */
    private suspend fun insertCollectionToDb(
        collection: TellicoCollection,
        images: Map<String, ByteArray>,
        sourceUri: String,
        fileHash: String,
        imageBasePath: String? = null,
        onProgress: suspend (Int, String) -> Unit
    ): Long {
        // 1. Insert collection metadata.
        val collectionEntity = CollectionEntity(
            tellicoId       = collection.id,
            title           = collection.title,
            type            = collection.type.xmlValue,
            sourceFile      = sourceUri,
            entryCount      = collection.entries.size,
            fileHash      = fileHash,
            imageBasePath = imageBasePath
        )
        val collectionId = db.collectionDao().insert(collectionEntity)

        // 2. Insérer le schéma (champs)
        val fieldEntities = collection.fields.mapIndexed { index, field ->
            field.toEntity(collectionId, index)
        }
        db.fieldDao().insertAll(fieldEntities)

        onProgress(40, "Sauvegarde de ${collection.entries.size} articles...")

        // 3. Insert entries in batches.
        val json = Json { ignoreUnknownKeys = true }
        collection.entries.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val entryEntities = batch.map { entry ->
                EntryEntity(
                    collectionId = collectionId,
                    tellicoId    = entry.id,
                    fieldValues  = json.encodeToString(entry.fields),
                    imageIds     = json.encodeToString(entry.imageIds),
                    cachedTitle  = entry.getValue("title").ifEmpty { "Article ${entry.id}" },
                    updatedAt    = System.currentTimeMillis()
                )
            }
            db.entryDao().insertAll(entryEntities)

            val progress = 40 + (batchIndex + 1) * BATCH_SIZE * 45 / collection.entries.size.coerceAtLeast(1)
            onProgress(progress.coerceAtMost(85), "Lot ${batchIndex + 1} sauvegardé...")
        }

        onProgress(87, "Sauvegarde des images...")

        // 4. Insert images in batches of 20 (BLOBs can be large).
        images.entries.chunked(20).forEach { chunk ->
            val imageEntities = chunk.map { (imageId, bytes) ->
                ImageEntity(
                    collectionId    = collectionId,
                    tellicoImageId  = imageId,
                    mimeType        = guessMimeType(imageId),
                    data            = bytes
                )
            }
            db.imageDao().insertAll(imageEntities)
        }

        return collectionId
    }

    // ---------------------------------------------------------------------------
    // Paginated read (Paging 3).
    // ---------------------------------------------------------------------------

    /**
     * Returns a Flow<PagingData<TellicoEntry>> for list display.
     *
     * PagingData is the Paging 3 paginated data container.
     * It connects directly to LazyColumn/LazyVerticalGrid in Compose.
     *
     * @param collectionId ID of the collection to display
     * @param sortField    Field name for sorting (null = import order)
     * @param searchQuery  Search query (null = show all)
     * @param filterField  Field to filter on
     * @param filterValue  Filter value
     */
    /** Returns true if the given field uses numeric ordering (NUMBER or RATING type). */
    suspend fun isFieldNumeric(collectionId: Long, fieldName: String): Boolean {
        val field = db.fieldDao().getFieldByName(collectionId, fieldName)
        return field?.type in listOf("6", "14")  // NUMBER, RATING
    }

    fun getEntriesPaged(
        collectionId: Long,
        sortField: String? = null,
        sortAscending: Boolean = true,
        sortNumeric: Boolean = false,
        searchQuery: String? = null,
        filterField: String? = null,
        filterValue: String? = null
    ): Flow<PagingData<TellicoEntry>> {
        val pagingConfig = PagingConfig(
            pageSize           = PAGE_SIZE,
            prefetchDistance   = PREFETCH_DISTANCE,
            enablePlaceholders = true
        )

        return Pager(config = pagingConfig) {
            when {
                // FTS search overrides sorting.
                !searchQuery.isNullOrBlank() -> {
                    val ftsQuery = "${searchQuery.trim()}*"
                    db.entryDao().searchFtsPaged(collectionId, ftsQuery)
                }
                // Field filter.
                !filterField.isNullOrBlank() && !filterValue.isNullOrBlank() ->
                    db.entryDao().filterByField(collectionId, filterField, filterValue)
                // Sort on a specific field — type already resolved outside Pager lambda.
                !sortField.isNullOrBlank() -> when {
                    sortNumeric && sortAscending  ->
                        db.entryDao().pagingSourceByFieldNumericAsc(collectionId, sortField)
                    sortNumeric && !sortAscending ->
                        db.entryDao().pagingSourceByFieldNumericDesc(collectionId, sortField)
                    sortAscending                 ->
                        db.entryDao().pagingSourceByFieldAsc(collectionId, sortField)
                    else                          ->
                        db.entryDao().pagingSourceByFieldDesc(collectionId, sortField)
                }
                // Default: sort by cached title.
                else -> db.entryDao().pagingSourceByTitle(collectionId)
            }
        }.flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
    }

    // ---------------------------------------------------------------------------
    // Fetch a single entry and its images.
    // ---------------------------------------------------------------------------

    /**
     * Fetches an entry by its tellicoId (the id from the Tellico XML file).
     * This identifier is passed through navigation.
     */
    suspend fun getEntry(collectionId: Long, tellicoId: Long): TellicoEntry? =
        db.entryDao().getByTellicoId(collectionId, tellicoId.toInt())?.toDomain()

    suspend fun getFields(collectionId: Long): List<TellicoField> =
        db.fieldDao().getFieldsForCollection(collectionId).map { it.toDomain() }

    /**
     * Fetches an image from the Room database.
     * Data is returned as ByteArray to be decoded by Coil.
     */
    suspend fun getImage(collectionId: Long, imageId: String): ByteArray? =
        db.imageDao().getImage(collectionId, imageId)?.data

    /**
     * Returns distinct values for a field (used by filters).
     * E.g. all years, all genres…
     */
    suspend fun getDistinctValues(collectionId: Long, fieldName: String): List<String> =
        db.entryDao().getDistinctValues(collectionId, fieldName)

    // ---------------------------------------------------------------------------
    // Suppression
    // ---------------------------------------------------------------------------

    suspend fun deleteCollection(collectionId: Long) {
        db.collectionDao().deleteById(collectionId)
        // Fields, entries and images are deleted in cascade (ForeignKey.CASCADE).
    }

    // ---------------------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------------------

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes SHA-256 of an InputStream via streaming (no full RAM load).
     * Suitable for large files like Disques.tc (37 MB decompressed).
     */
    /**
     * Resolves the absolute path of the external images directory.
     *
     * Convention Tellico : répertoire = nom_fichier_sans_extension + "_files"
     * at the same level as the .tc file.
     * Ex : Livres.tc → Livres_files/
     *
     * Stratégie (compatible scoped storage Android 10+) :
     * 1. Get the .tc filename via ContentResolver DISPLAY_NAME
     * 2. Remove the extension → "Livres"
     * 3. Search for "Livres_files" in common directories
     */
    private fun resolveImageBasePath(sourceUri: Uri, collectionTitle: String): String? {
        return try {
            // Step 1: get the filename from the URI.
            // DISPLAY_NAME works even with scoped storage (Android 10+).
            val displayName = getDisplayName(sourceUri)
            Log.d(TAG, "displayName=$displayName, uri=$sourceUri")

            // Build candidate directory names.
            val candidates = mutableListOf<String>()

            if (displayName != null) {
                val baseName = displayName.substringBeforeLast('.')  // "Livres.tc" → "Livres"
                candidates += baseName + "_files"
            }
            // Fallback using the Tellico title (if displayName is unavailable).
            candidates += collectionTitle + "_files"
            candidates += collectionTitle.replace(" ", "_") + "_files"

            // Step 2: search in common directories.
            val searchDirs = mutableListOf<java.io.File>()

            // Try to get the real parent path via MediaStore.
            val realPath = tryGetRealPath(sourceUri)
            if (realPath != null) {
                val parent = java.io.File(realPath).parentFile
                if (parent != null) searchDirs += parent
                Log.d(TAG, "realPath=$realPath, parent=$parent")
            }

            // Always also search in Downloads (the most common location).
            searchDirs += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // And in the external storage root.
            searchDirs += Environment.getExternalStorageDirectory()

            for (dir in searchDirs) {
                for (candidate in candidates) {
                    val imagesDir = java.io.File(dir, candidate)
                    Log.d(TAG, "Cherche images dans : ${imagesDir.absolutePath} → exists=${imagesDir.exists()}")
                    if (imagesDir.exists() && imagesDir.isDirectory) {
                        Log.i(TAG, "Répertoire images trouvé : ${imagesDir.absolutePath}")
                        return imagesDir.absolutePath
                    }
                }
            }

            Log.w(TAG, "Aucun répertoire _files trouvé (candidats=$candidates)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "resolveImageBasePath error: ${e.message}")
            null
        }
    }

    /**
     * Gets the display name of the file from its content:// URI.
     * Works even with scoped storage (Android 10+).
     */
    private fun getDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return java.io.File(uri.path ?: return null).name
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "getDisplayName: ${e.message}")
            null
        }
    }

    /**
     * Attempts to get the absolute path from a URI.
     * May return null on Android 10+ with scoped storage.
     */
    private fun tryGetRealPath(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        val authority = uri.authority ?: return null
        return try {
            when {
                authority.contains("externalstorage") -> {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2 && split[0].equals("primary", true))
                        "${Environment.getExternalStorageDirectory()}/${split[1]}"
                    else null
                }
                authority.contains("downloads") -> {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    if (docId.startsWith("raw:")) docId.removePrefix("raw:")
                    else null  // msf: IDs do not provide a real path on Android 10+
                }
                else -> {
                    context.contentResolver.query(
                        uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotEmpty() } else null
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryGetRealPath: ${e.message}")
            null
        }
    }

        private fun sha256Stream(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)  // 64 KB per chunk
        var n = input.read(buffer)
        while (n > 0) {
            digest.update(buffer, 0, n)
            n = input.read(buffer)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun guessMimeType(filename: String): String = when {
        filename.endsWith(".png", ignoreCase = true)  -> "image/png"
        filename.endsWith(".gif", ignoreCase = true)  -> "image/gif"
        filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }
}

// ---------------------------------------------------------------------------
// DB Entity <-> Domain Model mapping extensions.
// ---------------------------------------------------------------------------

fun EntryEntity.toDomain(): TellicoEntry {
    val json = Json { ignoreUnknownKeys = true }
    val fields: Map<String, String> = try {
        json.decodeFromString(fieldValues)
    } catch (e: Exception) { emptyMap() }
    val images: List<String> = try {
        json.decodeFromString(imageIds)
    } catch (e: Exception) { emptyList() }
    return TellicoEntry(id = tellicoId, fields = fields, imageIds = images)
}

fun FieldEntity.toDomain(): TellicoField {
    val json = Json { ignoreUnknownKeys = true }
    val allowedList: List<String> = try {
        json.decodeFromString(allowed)
    } catch (e: Exception) { emptyList() }
    return TellicoField(
        name         = name,
        title        = title,
        type         = FieldType.fromXmlValue(type),
        category     = category,
        flags        = flags,
        allowed      = allowedList,
        defaultValue = defaultValue
    )
}

fun TellicoField.toEntity(collectionId: Long, order: Int): FieldEntity {
    val json = Json { ignoreUnknownKeys = true }
    return FieldEntity(
        collectionId = collectionId,
        name         = name,
        title        = title,
        type         = type.xmlValue,
        category     = category,
        flags        = flags,
        allowed      = json.encodeToString(allowed),
        defaultValue = defaultValue,
        sortOrder    = order
    )
}
