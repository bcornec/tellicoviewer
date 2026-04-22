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
 * Application class: process entry point.
 *
 * Implements [ImageLoaderFactory] to register the custom Coil ImageLoader
 * (with the tellico:// and tellicofile:// fetchers) as the global loader.
 * Coil calls newImageLoader() automatically to configure its singleton.
 */
@HiltAndroidApp
class TellicoViewerApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Our custom ImageLoader is injected by Hilt.
     * It holds the TellicoImageFetcher that handles tellico:// and tellicofile:// URIs.
     * Hilt cannot inject into Application before onCreate(), so we use
     * lateinit and defer access via newImageLoader().
     */
    @Inject lateinit var tellicoImageLoader: TellicoImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * Called by Coil to obtain the global singleton ImageLoader.
     * All AsyncImage() calls in the app will automatically use this loader.
     */
    override fun newImageLoader(): ImageLoader = tellicoImageLoader.imageLoader
}
