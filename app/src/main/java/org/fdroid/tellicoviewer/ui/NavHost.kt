package org.fdroid.tellicoviewer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.fdroid.tellicoviewer.ui.screens.config.FieldConfigScreen
import org.fdroid.tellicoviewer.ui.screens.detail.EntryDetailScreen
import org.fdroid.tellicoviewer.ui.screens.list.CollectionListScreen
import org.fdroid.tellicoviewer.ui.screens.sync.SyncScreen

/**
 * Graphe de navigation de l'application.
 * Routes :
 *   home                              → liste collections + grille articles
 *   detail/{collectionId}/{entryId}   → fiche détail article
 *   config/{collectionId}             → configuration des champs visibles
 *   sync                              → synchronisation Wi-Fi
 */
@Composable
fun TellicoViewerNavHost() {
    val navController = rememberNavController()
    // ViewModel partagé au niveau du NavHost pour permettre la communication entre écrans
    val collectionViewModel: org.fdroid.tellicoviewer.ui.screens.list.CollectionListViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            CollectionListScreen(
                viewModel     = collectionViewModel,
                onEntryClick  = { collectionId, entryId ->
                    navController.navigate(Routes.detail(collectionId, entryId))
                },
                onConfigClick = { collectionId ->
                    navController.navigate(Routes.config(collectionId))
                },
                onSyncClick   = { navController.navigate(Routes.SYNC) }
            )
        }

        composable(
            route     = Routes.DETAIL,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.LongType },
                navArgument("entryId")      { type = NavType.LongType }
            )
        ) {
            EntryDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route     = Routes.CONFIG,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.LongType }
            )
        ) {
            FieldConfigScreen(
                onBack = {
                    // Notifier le ViewModel principal que les prefs ont changé
                    collectionViewModel.refreshFieldPreferences()
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SYNC) {
            SyncScreen(onBack = { navController.popBackStack() })
        }
    }
}

object Routes {
    const val HOME   = "home"
    const val DETAIL = "detail/{collectionId}/{entryId}"
    const val CONFIG = "config/{collectionId}"
    const val SYNC   = "sync"

    fun detail(collectionId: Long, entryId: Long) = "detail/$collectionId/$entryId"
    fun config(collectionId: Long)                 = "config/$collectionId"
}
