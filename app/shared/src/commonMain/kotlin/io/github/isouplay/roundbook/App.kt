package io.github.isouplay.roundbook

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.isouplay.roundbook.domain.EntryId
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.ui.MeScreen
import io.github.isouplay.roundbook.ui.MenuScreen
import io.github.isouplay.roundbook.ui.PlaceScreen
import io.github.isouplay.roundbook.ui.PlacesScreen
import io.github.isouplay.roundbook.ui.today.EditEntriesScreen
import io.github.isouplay.roundbook.ui.today.TodayScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.tab_me
import roundbook.app.shared.generated.resources.tab_today

// Routes of the navigation. The bar at the bottom has two tabs: Heute (today) and Ich (me).

@Serializable
data object TodayTab

@Serializable
data object TodayRoute

/** Change entries of the running evening; [entryIds] joined by ",". */
@Serializable
data class EditEntriesRoute(val entryIds: String)

@Serializable
data object MeTab

@Serializable
data object MeRoute

@Serializable
data object PlacesRoute

@Serializable
data class PlaceRoute(val placeId: String)

@Serializable
data class MenuRoute(val placeId: String)

/** Root of the UI. Plain screens, no styling yet (that comes with the design round). */
@Composable
fun App(roundbook: Roundbook) {
    MaterialTheme {
        val navController = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val notify: (String) -> Unit = { text ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }

        // The favourite row orders itself anew after at least an hour in the background.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            scope.launch { roundbook.inBackground { favorites.movedToBackground() } }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            scope.launch { roundbook.inBackground { favorites.cameToForeground() } }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomBar(navController) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = TodayTab,
                modifier = Modifier.padding(padding),
            ) {
                navigation<TodayTab>(startDestination = TodayRoute) {
                    composable<TodayRoute> {
                        TodayScreen(
                            roundbook = roundbook,
                            notify = notify,
                            onEdit = { ids -> navController.navigate(EditEntriesRoute(ids.joinToString(",") { it.value })) },
                        )
                    }
                    composable<EditEntriesRoute> { entry ->
                        val route = entry.toRoute<EditEntriesRoute>()
                        EditEntriesScreen(
                            roundbook = roundbook,
                            ids = route.entryIds.split(',').filter { it.isNotEmpty() }.map(::EntryId),
                            notify = notify,
                            onDone = { navController.popBackStack() },
                        )
                    }
                }
                navigation<MeTab>(startDestination = MeRoute) {
                    composable<MeRoute> {
                        MeScreen(onPlaces = { navController.navigate(PlacesRoute) })
                    }
                    composable<PlacesRoute> {
                        PlacesScreen(
                            roundbook = roundbook,
                            onBack = { navController.popBackStack() },
                            onOpenPlace = { navController.navigate(PlaceRoute(it.value)) },
                        )
                    }
                    composable<PlaceRoute> { entry ->
                        val placeId = PlaceId(entry.toRoute<PlaceRoute>().placeId)
                        PlaceScreen(
                            roundbook = roundbook,
                            placeId = placeId,
                            onBack = { navController.popBackStack() },
                            onShowMenu = { navController.navigate(MenuRoute(placeId.value)) },
                        )
                    }
                    composable<MenuRoute> { entry ->
                        MenuScreen(
                            roundbook = roundbook,
                            placeId = PlaceId(entry.toRoute<MenuRoute>().placeId),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    NavigationBar {
        Tab(navController, destination?.hierarchy?.any { it.hasRoute(TodayTab::class) } == true, TodayTab) {
            Text(stringResource(Res.string.tab_today))
        }
        Tab(navController, destination?.hierarchy?.any { it.hasRoute(MeTab::class) } == true, MeTab) {
            Text(stringResource(Res.string.tab_me))
        }
    }
}

@Composable
private fun RowScope.Tab(
    navController: NavHostController,
    selected: Boolean,
    tab: Any,
    label: @Composable () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = {
            navController.navigate(tab) {
                // One stack per tab: switching keeps where each tab was.
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = {},
        label = label,
    )
}
