package org.hyper_linux.tellicoviewer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.Buffer
import okio.buffer
import okio.source
import org.hyper_linux.tellicoviewer.data.db.ImageDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetcher Coil gérant deux sources d'images Tellico.
 *
 * IMPORTANT: Coil automatically converts Strings to android.net.Uri
 * when the model looks like a URI. Use Fetcher.Factory<Uri>
 * (not Factory<String>) to intercept our custom URIs.
 *
 * Supportés schemas:
 *   tellico://collectionId/imageId      → images embedded in Room
 *   tellicofile:///absolute/path/img    → external images on the file system
 */
class TellicoImageFetcher private constructor(
    private val uri: Uri,
    private val options: Options,
    private val imageDao: ImageDao
) : Fetcher {

    override suspend fun fetch(): FetchResult? = when (uri.scheme) {
        "tellico"     -> fetchFromRoom()
        "tellicofile" -> fetchFromFile()
        else          -> null
    }

    // -----------------------------------------------------------------------
    // Images embedded in Room.
    // tellico://collectionId/imageId
    // -----------------------------------------------------------------------
    private suspend fun fetchFromRoom(): FetchResult? {
        val collectionId = uri.host?.toLongOrNull() ?: return null
        val imageId      = uri.path?.removePrefix("/") ?: return null
        if (imageId.isBlank()) return null

        Log.d("TellicoFetcher", "Room: collectionId=$collectionId imageId=$imageId")
        val imageEntity = imageDao.getImage(collectionId, imageId) ?: run {
            Log.w("TellicoFetcher", "Image non trouvée en base : $imageId")
            return null
        }
        val buffer = Buffer().write(imageEntity.data)
        return SourceResult(
            source     = ImageSource(buffer, options.context),
            mimeType   = imageEntity.mimeType,
            dataSource = DataSource.DISK
        )
    }

    // -----------------------------------------------------------------------
    // External images on the file system.
    // tellicofile:///storage/emulated/0/Download/BD_files/abc.jpeg
    // -----------------------------------------------------------------------
    private fun fetchFromFile(): FetchResult? {
        // Uri.path on "tellicofile:///storage/emulated/0/Download/BD_files/abc.jpeg"
        // returns "/storage/emulated/0/Download/BD_files/abc.jpeg"
        val filePath = uri.path ?: run {
            Log.w("TellicoFetcher", "URI sans chemin : $uri")
            return null
        }
        val file = File(filePath)
        Log.d("TellicoFetcher", "File: path=$filePath exists=${file.exists()} canRead=${file.canRead()}")

        if (!file.exists()) {
            Log.w("TellicoFetcher", "Fichier introuvable : $filePath")
            return null
        }
        if (!file.canRead()) {
            Log.w("TellicoFetcher", "Fichier illisible (permission?) : $filePath")
            return null
        }

        return SourceResult(
            source     = ImageSource(file.source().buffer(), options.context),
            mimeType   = mimeTypeForFile(file.name),
            dataSource = DataSource.DISK
        )
    }

    private fun mimeTypeForFile(name: String): String = when {
        name.endsWith(".jpg",  true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png",  true)  -> "image/png"
        name.endsWith(".webp", true)  -> "image/webp"
        else -> "image/*"
    }

    // Factory<Uri> — Coil converts String URIs to Uri before looking up a fetcher.
    class Factory @Inject constructor(
        private val imageDao: ImageDao
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme ?: return null
            if (scheme != "tellico" && scheme != "tellicofile") return null
            Log.d("TellicoFetcher", "Factory.create: scheme=$scheme uri=$data")
            return TellicoImageFetcher(data, options, imageDao)
        }
    }
}

@Singleton
class TellicoImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcherFactory: TellicoImageFetcher.Factory
) {
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(fetcherFactory) }
            .crossfade(true)
            .build()
    }
}
