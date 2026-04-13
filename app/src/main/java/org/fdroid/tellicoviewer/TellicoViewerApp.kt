package org.fdroid.tellicoviewer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Classe Application : point d'entrée du processus Android.
 * Analogie : équivalent du main() en C, mais pour le cycle de vie global de l'app.
 *
 * @HiltAndroidApp déclenche la génération du code d'injection de dépendances par Hilt.
 * C'est comme générer automatiquement tous les constructeurs et l'initialisation
 * des singletons au démarrage.
 *
 * Implémente [Configuration.Provider] pour intégrer Hilt avec WorkManager
 * (les Workers ont besoin d'injection de dépendances).
 */
@HiltAndroidApp
class TellicoViewerApp : Application(), Configuration.Provider {

    /**
     * HiltWorkerFactory est injecté automatiquement par Hilt.
     * Il permet aux Workers (tâches de fond) de recevoir des dépendances injectées.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Configuration de WorkManager avec la factory Hilt.
     * WorkManager est l'équivalent de systemd/cron pour les tâches Android.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
