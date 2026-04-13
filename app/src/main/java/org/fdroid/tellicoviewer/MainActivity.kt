package org.fdroid.tellicoviewer

import android.content.Intent
import android.os.Bundle
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
