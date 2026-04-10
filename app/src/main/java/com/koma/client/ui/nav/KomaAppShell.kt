package com.koma.client.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koma.client.R
import com.koma.client.domain.model.MediaType
import com.koma.client.ui.dashboard.DashboardScreen
import com.koma.client.ui.offline.OfflineScreen
import com.koma.client.ui.series.SeriesDetailScreen
import com.koma.client.ui.series.SeriesListScreen
import com.koma.client.ui.server.ServerScreen
import com.koma.client.ui.settings.SettingsScreen

enum class KomaTab(val label: String, val route: String, val iconResId: Int?) {
    HOME("Home", "tab_home", null),
    OFFLINE("Downloads", "tab_offline", R.drawable.ic_download),
    SERVER("Server", "tab_server", R.drawable.ic_server),
    SETTINGS("Settings", "tab_settings", null),
}

@Composable
fun KomaAppShell(
    onOpenReader: (bookId: String, mediaType: MediaType) -> Unit,
    onAddServer: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(KomaTab.HOME) }
    val tabNavControllers = KomaTab.entries.associateWith { rememberNavController() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar {
                KomaTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            when (tab) {
                                KomaTab.HOME -> Icon(Icons.Filled.Home, contentDescription = tab.label)
                                KomaTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = tab.label)
                                else -> tab.iconResId?.let {
                                    Icon(
                                        ImageVector.vectorResource(id = it),
                                        contentDescription = tab.label,
                                    )
                                }
                            }
                        },
                        label = { Text(tab.label) },
                        selected = selectedTab == tab,
                        onClick = {
                            if (selectedTab == tab) {
                                // Pop to start of this tab's nav stack
                                tabNavControllers[tab]?.popBackStack(
                                    tabNavControllers[tab]?.graph?.startDestinationRoute ?: return@NavigationBarItem,
                                    inclusive = false,
                                )
                            } else {
                                selectedTab = tab
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        KomaTab.entries.forEach { tab ->
            if (tab == selectedTab) {
                val navController = tabNavControllers[tab]!!
                when (tab) {
                    KomaTab.HOME -> HomeTabNav(
                        navController = navController,
                        onOpenReader = onOpenReader,
                        modifier = Modifier.padding(padding),
                    )
                    KomaTab.OFFLINE -> OfflineTabNav(
                        onOpenReader = onOpenReader,
                        modifier = Modifier.padding(padding),
                    )
                    KomaTab.SERVER -> ServerTabNav(
                        onAddServer = onAddServer,
                        modifier = Modifier.padding(padding),
                    )
                    KomaTab.SETTINGS -> SettingsTabNav(
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTabNav(
    navController: NavHostController,
    onOpenReader: (bookId: String, mediaType: MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = "home_dashboard",
        modifier = modifier,
    ) {
        composable("home_dashboard") {
            DashboardScreen(
                onBookClick = { bookId, mediaType -> onOpenReader(bookId, mediaType) },
                onSeriesClick = { seriesId -> navController.navigate("home_series_detail/$seriesId") },
                onSeeAllLibraries = { /* no-op in tab nav */ },
                onSeeAll = { libraryId -> navController.navigate("home_series_list/$libraryId") },
            )
        }
        composable("home_series_detail/{seriesId}") {
            SeriesDetailScreen(
                onBookClick = { bookId, mediaType -> onOpenReader(bookId, mediaType) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("home_series_list/{libraryId}") {
            SeriesListScreen(
                onSeriesClick = { seriesId -> navController.navigate("home_series_detail/$seriesId") },
            )
        }
    }
}

@Composable
private fun OfflineTabNav(
    onOpenReader: (bookId: String, mediaType: MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    OfflineScreen(
        modifier = modifier,
        onOpenReader = { bookId, mediaType ->
            onOpenReader(bookId, MediaType.valueOf(mediaType))
        },
    )
}


@Composable
private fun ServerTabNav(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ServerScreen(
        onSwitchServer = onAddServer,
        modifier = modifier,
    )
}

@Composable
private fun SettingsTabNav(
    modifier: Modifier = Modifier,
) {
    SettingsScreen(modifier = modifier)
}
