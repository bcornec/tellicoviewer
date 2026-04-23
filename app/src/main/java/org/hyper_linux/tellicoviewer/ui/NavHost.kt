package org.hyper_linux.tellicoviewer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.hyper_linux.tellicoviewer.ui.screens.config.CollectionPrefsScreen
import org.hyper_linux.tellicoviewer.ui.screens.detail.EntryDetailScreen
import org.hyper_linux.tellicoviewer.ui.screens.list.CollectionListScreen
import org.hyper_linux.tellicoviewer.ui.screens.sync.SyncScreen

/**
 * Application navigation graph.
 *
 * Routes:
 *   home                             – main collection list + entry grid
 *   detail/{collectionId}/{entryId}  – entry detail view
 *   prefs/{collectionId}             – unified collection preferences
 *   sync                             – Wi-Fi sync server
 */
@Composable
fun TellicoViewerNavHost() {
    val navController = rememberNavController()

    // Shared ViewModel at NavHost level so CollectionListScreen and the
    // preferences screen can communicate (e.g. refresh after prefs change).
    val collectionViewModel: org.hyper_linux.tellicoviewer.ui.screens.list.CollectionListViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            CollectionListScreen(
                viewModel     = collectionViewModel,
                onEntryClick  = { collectionId, entryId ->
                    navController.navigate(Routes.detail(collectionId, entryId))
                },
                onPrefsClick  = { collectionId ->
                    navController.navigate(Routes.prefs(collectionId))
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
            route     = Routes.PREFS,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.LongType }
            )
        ) {
            CollectionPrefsScreen(
                onBack = {
                    // Trigger a field-preference refresh so the grid updates
                    // immediately without requiring a re-import.
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
    const val PREFS  = "prefs/{collectionId}"
    const val SYNC   = "sync"

    fun detail(collectionId: Long, entryId: Long) = "detail/$collectionId/$entryId"
    fun prefs(collectionId: Long)                  = "prefs/$collectionId"
}
