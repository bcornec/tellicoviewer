package org.fdroid.tellicoviewer.data.repository

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
import org.fdroid.tellicoviewer.data.db.*
import org.fdroid.tellicoviewer.data.model.*
import org.fdroid.tellicoviewer.data.parser.TellicoParser
import org.fdroid.tellicoviewer.util.SearchEngine
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository : couche d'accès aux données unifiée.
 *
 * RÔLE DU REPOSITORY (Clean Architecture) :
 * - Abstrait la source des données (BDD locale, réseau, fichier)
 * - Les ViewModels ne savent pas si les données viennent de SQLite ou du réseau
 * - Transforme les entités DB en modèles de domaine
 *
 * Analogie Unix : le Repository est comme un VFS (Virtual File System).
 * Les appelants font open("fichier") sans savoir si c'est ext4, NFS ou tmpfs.
 *
 * PIPELINE DE DONNÉES (import) :
 * URI fichier .tc
 *   → ContentResolver.openInputStream()
 *   → TellicoParser.parse()        (décompression ZIP + parsing XML)
 *   → insertCollectionToDb()       (sérialisation JSON + batch insert Room)
 *   → PagingSource                 (lecture paginée depuis Room)
 *   → ViewModel                    (état UI)
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
        private const val PAGE_SIZE = 30          // articles par page Paging3
        private const val PREFETCH_DISTANCE = 60  // articles préchargés en avance
        private const val BATCH_SIZE = 500        // articles insérés par transaction SQL
    }

    // ---------------------------------------------------------------------------
    // Observation des collections
    // ---------------------------------------------------------------------------

    /** Flow observable de toutes les collections (mise à jour automatique) */
    fun observeCollections(): Flow<List<CollectionWithFieldCount>> =
        db.collectionDao().observeCollections()

    /** Flow du schéma (champs) d'une collection */
    suspend fun getImageBasePath(collectionId: Long): String? =
        db.collectionDao().getById(collectionId)?.imageBasePath

    /**
     * Flow réactif sur imageBasePath d'une collection.
     * Émet une nouvelle valeur dès que la collection est mise à jour en base
     * (par exemple après un import qui recalcule imageBasePath).
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
    // Import d'un fichier .tc
    // ---------------------------------------------------------------------------

    /**
     * Importe un fichier .tc depuis une URI Android (SAF - Storage Access Framework).
     *
     * SAF = mécanisme Android pour accéder aux fichiers de manière sécurisée,
     * qu'ils soient sur le stockage local, une carte SD ou Google Drive.
     * Équivalent : un fd ouvert par le kernel, passé à notre processus.
     *
     * @param uri URI du fichier .tc (obtenue via Intent ou file picker)
     * @param onProgress Callback de progression pour l'UI
     * @return ID de la collection en base, ou null en cas d'erreur
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

            // Passe 1 : calculer le hash SHA-256 du fichier ZIP (sans décompresser)
            // On lit le fichier une première fois en streaming pour le hash.
            val hash = context.contentResolver.openInputStream(uri)
                ?.use { sha256Stream(it) }
                ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier : $uri")

            val existingByPath = db.collectionDao().getBySourceFile(uri.toString())
            if (existingByPath != null) {
                Log.i(TAG, "Collection déjà importée, mise à jour...")
                db.collectionDao().deleteById(existingByPath.id)
            }

            onProgress(8, "Parsing du fichier...")

            // Passe 2 : parser le fichier en streaming (sans tout charger en RAM)
            val result = context.contentResolver.openInputStream(uri)
                ?.use { parser.parse(it, onProgress) }
                ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier : $uri")

            val collection = result.collection

            // Résoudre le répertoire d'images externes (ex: Livres_files/)
            val imageBasePath = resolveImageBasePath(uri, collection.title)

            // Insérer en base Room
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
     * Insère une collection complète dans Room en plusieurs transactions.
     *
     * On découpe en lots de BATCH_SIZE pour :
     * 1. Éviter les transactions trop longues (lock SQLite)
     * 2. Permettre la mise à jour de progression
     * 3. Limiter la consommation mémoire (sérialisation JSON des 500 articles à la fois)
     */
    private suspend fun insertCollectionToDb(
        collection: TellicoCollection,
        images: Map<String, ByteArray>,
        sourceUri: String,
        fileHash: String,
        imageBasePath: String? = null,
        onProgress: suspend (Int, String) -> Unit
    ): Long {
        // 1. Insérer les métadonnées de la collection
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

        // 3. Insérer les articles par lots
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

        // 4. Insérer les images par lots de 20 (les BLOB peuvent être volumineux)
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
    // Lecture paginée (Paging 3)
    // ---------------------------------------------------------------------------

    /**
     * Retourne un Flow<PagingData<TellicoEntry>> pour l'affichage en liste.
     *
     * PagingData est le conteneur de données paginées de Paging 3.
     * Il se connecte directement à LazyColumn/LazyVerticalGrid dans Compose.
     *
     * @param collectionId ID de la collection à afficher
     * @param sortField    Nom du champ pour le tri (null = ordre d'import)
     * @param searchQuery  Requête de recherche (null = tout afficher)
     * @param filterField  Champ sur lequel filtrer
     * @param filterValue  Valeur du filtre
     */
    fun getEntriesPaged(
        collectionId: Long,
        sortField: String? = null,
        searchQuery: String? = null,
        filterField: String? = null,
        filterValue: String? = null
    ): Flow<PagingData<TellicoEntry>> {
        val pagingConfig = PagingConfig(
            pageSize         = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = true   // permet de montrer des "skeleton" en attendant
        )

        return Pager(config = pagingConfig) {
            when {
                // Recherche FTS
                !searchQuery.isNullOrBlank() -> {
                    val ftsQuery = "${searchQuery.trim()}*"  // prefix match FTS
                    db.entryDao().searchFtsPaged(collectionId, ftsQuery)
                }
                // Filtre par champ
                !filterField.isNullOrBlank() && !filterValue.isNullOrBlank() ->
                    db.entryDao().filterByField(collectionId, filterField, filterValue)
                // Tri par titre (défaut)
                else ->
                    db.entryDao().pagingSourceByTitle(collectionId)
            }
        }.flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
    }

    // ---------------------------------------------------------------------------
    // Récupération d'un article et de ses images
    // ---------------------------------------------------------------------------

    /**
     * Récupère un article par son tellicoId (l'id dans le fichier XML Tellico).
     * C'est cet identifiant qui est passé dans la navigation.
     */
    suspend fun getEntry(collectionId: Long, tellicoId: Long): TellicoEntry? =
        db.entryDao().getByTellicoId(collectionId, tellicoId.toInt())?.toDomain()

    suspend fun getFields(collectionId: Long): List<TellicoField> =
        db.fieldDao().getFieldsForCollection(collectionId).map { it.toDomain() }

    /**
     * Récupère une image depuis la BDD Room.
     * Les données sont retournées en ByteArray pour être décodées par Coil.
     */
    suspend fun getImage(collectionId: Long, imageId: String): ByteArray? =
        db.imageDao().getImage(collectionId, imageId)?.data

    /**
     * Retourne les valeurs distinctes d'un champ (pour les filtres).
     * Ex: toutes les années, tous les genres...
     */
    suspend fun getDistinctValues(collectionId: Long, fieldName: String): List<String> =
        db.entryDao().getDistinctValues(collectionId, fieldName)

    // ---------------------------------------------------------------------------
    // Suppression
    // ---------------------------------------------------------------------------

    suspend fun deleteCollection(collectionId: Long) {
        db.collectionDao().deleteById(collectionId)
        // Les champs, entrées et images sont supprimés en cascade (ForeignKey.CASCADE)
    }

    // ---------------------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------------------

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Calcule le SHA-256 d'un InputStream en streaming (sans charger tout en RAM).
     * Adapté aux gros fichiers comme Disques.tc (37 Mo décompressé).
     */
    /**
     * Résout le chemin absolu du répertoire d'images externes.
     *
     * Convention Tellico : répertoire = nom_fichier_sans_extension + "_files"
     * au même niveau que le fichier .tc.
     * Ex : Livres.tc → Livres_files/
     *
     * Stratégie (compatible scoped storage Android 10+) :
     * 1. Obtenir le nom du fichier .tc via ContentResolver DISPLAY_NAME
     * 2. Retirer l'extension → "Livres"
     * 3. Chercher "Livres_files" dans les répertoires courants
     */
    private fun resolveImageBasePath(sourceUri: Uri, collectionTitle: String): String? {
        return try {
            // Étape 1 : obtenir le nom du fichier depuis l'URI
            // DISPLAY_NAME fonctionne même avec scoped storage (Android 10+)
            val displayName = getDisplayName(sourceUri)
            Log.d(TAG, "displayName=$displayName, uri=$sourceUri")

            // Construire les noms de répertoires candidats
            val candidates = mutableListOf<String>()

            if (displayName != null) {
                val baseName = displayName.substringBeforeLast('.')  // "Livres.tc" → "Livres"
                candidates += baseName + "_files"
            }
            // Fallback avec le titre Tellico (en cas où displayName indisponible)
            candidates += collectionTitle + "_files"
            candidates += collectionTitle.replace(" ", "_") + "_files"

            // Étape 2 : chercher dans les répertoires courants
            val searchDirs = mutableListOf<java.io.File>()

            // Essayer d'obtenir le chemin réel du parent via MediaStore
            val realPath = tryGetRealPath(sourceUri)
            if (realPath != null) {
                val parent = java.io.File(realPath).parentFile
                if (parent != null) searchDirs += parent
                Log.d(TAG, "realPath=$realPath, parent=$parent")
            }

            // Toujours chercher aussi dans Downloads (le cas le plus courant)
            searchDirs += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // Et dans le stockage externe racine
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
     * Obtient le nom d'affichage du fichier depuis son URI content://.
     * Fonctionne même avec scoped storage (Android 10+).
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
     * Tente d'obtenir le chemin absolu depuis un URI.
     * Peut retourner null sur Android 10+ avec scoped storage.
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
                    else null  // msf: IDs ne donnent pas de chemin réel sur Android 10+
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
        val buffer = ByteArray(65536)  // 64 Ko par chunk
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
// Extensions de mapping Entité DB <-> Modèle de domaine
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
