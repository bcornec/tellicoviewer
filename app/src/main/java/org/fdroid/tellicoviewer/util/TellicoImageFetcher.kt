package org.fdroid.tellicoviewer.util

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.Buffer
import org.fdroid.tellicoviewer.data.db.ImageDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetcher Coil personnalisé pour charger des images depuis la base Room/SQLite.
 *
 * SCHÉMA D'URI : "tellico://<collectionId>/<imageId>"
 * Exemple      : "tellico://3/cover.jpg"
 *
 * Quand AsyncImage() reçoit une URI de ce format, Coil cherche un Fetcher.Factory
 * capable de la gérer. Notre factory répond si l'URI commence par "tellico://".
 * Le Fetcher lit alors les bytes depuis Room et les retourne à Coil en Buffer okio.
 *
 * Coil gère ensuite le décodage PNG/JPEG et le cache disque/mémoire automatiquement.
 *
 * Analogie : c'est comme un VFS handler pour un scheme URI custom.
 * Compatible Coil 2.6.x.
 */
class TellicoImageFetcher private constructor(
    private val data: String,
    private val options: Options,
    private val imageDao: ImageDao
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Parser l'URI "tellico://collectionId/imageId"
        val withoutScheme = data.removePrefix("tellico://")
        val slashIdx = withoutScheme.indexOf('/')
        if (slashIdx < 0) return null

        val collectionId = withoutScheme.substring(0, slashIdx).toLongOrNull() ?: return null
        val imageId      = withoutScheme.substring(slashIdx + 1)
        if (imageId.isBlank()) return null

        // Récupérer les bytes depuis Room (Dispatchers.IO via le ViewModel/coroutine Coil)
        val imageEntity = imageDao.getImage(collectionId, imageId) ?: return null

        // Envelopper les bytes dans un Buffer okio (format attendu par Coil 2.x)
        val buffer = Buffer().write(imageEntity.data)

        return SourceResult(
            source     = ImageSource(buffer, options.context),
            mimeType   = imageEntity.mimeType,
            dataSource = DataSource.DISK   // DISK = persisté localement (Room)
        )
    }

    /**
     * Factory : instancie un TellicoImageFetcher si l'URI correspond à "tellico://".
     * Retourne null pour toutes les autres URI (http://, file://, etc.) → Coil
     * essaie les autres factories enregistrées.
     */
    class Factory @Inject constructor(
        private val imageDao: ImageDao
    ) : Fetcher.Factory<String> {

        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.startsWith("tellico://")) return null
            return TellicoImageFetcher(data, options, imageDao)
        }
    }
}

/**
 * Fournit un ImageLoader Coil configuré avec le fetcher Tellico.
 * Injecté comme singleton par Hilt dans l'Application.
 *
 * Pour l'utiliser globalement, configurer dans TellicoViewerApp :
 *   LocalImageLoader.provides(tellicoImageLoader.imageLoader)
 * ou passer directement à AsyncImage(imageLoader = ...).
 */
@Singleton
class TellicoImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcherFactory: TellicoImageFetcher.Factory
) {
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(fetcherFactory)   // Enregistre notre handler "tellico://"
            }
            .crossfade(true)          // Transition douce entre placeholder et image
            .build()
    }
}
