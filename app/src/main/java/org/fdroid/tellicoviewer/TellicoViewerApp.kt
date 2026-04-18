package org.fdroid.tellicoviewer

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import org.fdroid.tellicoviewer.util.TellicoImageLoader
import javax.inject.Inject

/**
 * Classe Application : point d'entrée du processus Android.
 *
 * Implémente [ImageLoaderFactory] pour enregistrer notre ImageLoader Coil custom
 * (avec le fetcher tellico:// et tellicofile://) comme loader global.
 * Coil appelle newImageLoader() automatiquement pour configurer son singleton.
 */
@HiltAndroidApp
class TellicoViewerApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Notre ImageLoader custom est injecté par Hilt.
     * Il contient le TellicoImageFetcher qui gère les URIs tellico:// et tellicofile://.
     * Hilt ne peut pas injecter dans Application avant onCreate(), donc on utilise
     * lateinit et on retarde l'accès via newImageLoader().
     */
    @Inject lateinit var tellicoImageLoader: TellicoImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * Appelé par Coil pour obtenir l'ImageLoader singleton global.
     * Toutes les AsyncImage() de l'app utiliseront automatiquement ce loader.
     */
    override fun newImageLoader(): ImageLoader = tellicoImageLoader.imageLoader
}
