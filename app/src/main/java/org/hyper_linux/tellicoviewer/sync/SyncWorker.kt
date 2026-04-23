package org.hyper_linux.tellicoviewer.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.hyper_linux.tellicoviewer.R
import org.hyper_linux.tellicoviewer.data.repository.TellicoRepository
import java.util.concurrent.TimeUnit

/**
 * Periodic synchronisation worker using WorkManager.
 *
 * WorkManager is the Android equivalent of cron/systemd.
 * It schedules tasks that must run even when the app is closed,
 * with constraint management (Wi-Fi required, battery not critical…).
 *
 * @HiltWorker: enables dependency injection in the Worker.
 * @Assisted: parameters supplied by WorkManager (Context and WorkerParameters).
 *
 * CONTRAINTES :
 * - Network required (Wi-Fi preferred)
 * - Ne pas s'exécuter si batterie faible
 * - Periodicity: every 6 hours
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: TellicoRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME       = "tellico_sync_periodic"
        const val CHANNEL_ID      = "tellico_sync"
        const val NOTIFICATION_ID = 1001

        /**
         * Planifie la synchronisation périodique.
         * Call from Settings or at startup if enabled.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // keep existing work if already scheduled
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        // Show a progress notification (foreground service required).
        setForeground(createForegroundInfo("Synchronisation en cours..."))

        return try {
            // In a real scenario, check files on a remote server.
            // Here: simple state check (no server configured = no-op).
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("TellicoViewer")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Synchronisation Tellico",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

/**
 * Foreground service for manual synchronisation.
 * Declared in the Manifest.
 */
class SyncForegroundService : android.app.Service() {
    override fun onBind(intent: android.content.Intent?) = null
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
