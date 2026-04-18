package org.fdroid.tellicoviewer

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
import org.fdroid.tellicoviewer.ui.TellicoViewerNavHost
import org.fdroid.tellicoviewer.ui.theme.TellicoViewerTheme
import org.fdroid.tellicoviewer.ui.screens.list.CollectionListViewModel

/**
 * MainActivity : unique activité de l'application (Single Activity Architecture).
 *
 * En architecture Android moderne, on préfère une seule Activity avec plusieurs
 * "fragments" Compose (écrans navigués). C'est comparable à une application web
 * SPA (Single Page App) où le routeur gère les vues sans rechargement de page.
 *
 * @AndroidEntryPoint : marque cette classe pour l'injection Hilt.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ViewModel partagé au niveau de l'activité pour gérer l'intent d'ouverture de fichier
    private val collectionViewModel: CollectionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Affichage bord à bord (utilise tout l'écran, gère les insets)
        enableEdgeToEdge()

        // Demander l'accès complet au stockage pour charger les images externes (_files/)
        requestStoragePermission()

        // Traite un éventuel intent d'ouverture de fichier .tc au démarrage
        handleIntent(intent)

        setContent {
            TellicoViewerTheme {
                // NavHost : routeur de navigation entre les écrans Compose
                TellicoViewerNavHost()
            }
        }
    }

    /**
     * Gère les intents entrants quand l'app est déjà en cours d'exécution.
     * Exemple : l'utilisateur ouvre un .tc depuis le gestionnaire de fichiers
     * alors que TellicoViewer est déjà ouvert.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Demande la permission d'accès au stockage externe pour charger les images.
     * Sur Android 11+ : MANAGE_EXTERNAL_STORAGE via les Settings système.
     * Sur Android <11 : READ_EXTERNAL_STORAGE via le dialog standard.
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
     * Extrait l'URI du fichier depuis l'intent et le transmet au ViewModel.
     * Le ViewModel déclenchera le parsing et l'import en base de données.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                collectionViewModel.importFromUri(uri)
            }
        }
    }
}
