package org.hyper_linux.tellicoviewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import dagger.hilt.android.AndroidEntryPoint
import org.hyper_linux.tellicoviewer.ui.TellicoViewerNavHost
import org.hyper_linux.tellicoviewer.ui.theme.TellicoViewerTheme
import org.hyper_linux.tellicoviewer.ui.screens.list.CollectionListViewModel

/**
 * MainActivity: the application's single Activity (Single Activity Architecture).
 *
 * Modern Android architecture favours a single Activity with multiple
 * Compose "screens" (navigated destinations), similar to a web
 * SPA (Single Page App) where the router swaps views without page reloads.
 *
 * @AndroidEntryPoint: marks this class for Hilt injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Shared ViewModel at the activity level to handle file-open intents.
    private val collectionViewModel: CollectionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge display (full screen, insets handled).
        enableEdgeToEdge()

        // Request full storage access to load external cover images (_files/).
        requestStoragePermission()

        // Handle a .tc file open intent at startup.
        handleIntent(intent)

        setContent {
            TellicoViewerTheme {
                // NavHost: navigation router between Compose screens.
                TellicoViewerNavHost()
            }
        }
    }

    /**
     * Handles incoming intents when the app is already running.
     * Example: the user opens a .tc file from the file manager
     * while TellicoViewer is already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Requests external storage permission to load cover images.
     * Android 11+: MANAGE_EXTERNAL_STORAGE via system Settings.
     * Android <11: READ_EXTERNAL_STORAGE via the standard dialog.
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ : vérifier si on a déjà l'accès complet
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback : ouvrir la page générale MANAGE_ALL_FILES
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 : demander READ_EXTERNAL_STORAGE
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1001
                )
            }
        }
    }

    /**
     * Extracts the file URI from the intent and forwards it to the ViewModel.
     * The ViewModel will trigger parsing and database import.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                collectionViewModel.importFromUri(uri)
            }
        }
    }
}
