package com.ominix.vidiio.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.ominix.vidiio.VidiioApplication
import com.ominix.vidiio.navigation.Navigator
import com.ominix.vidiio.navigation.VidiioRoute
import com.ominix.vidiio.navigation.rememberNavigationState
import com.ominix.vidiio.navigation.toEntries
import com.ominix.vidiio.data.model.toMovie
import com.ominix.vidiio.ui.screens.*
import com.ominix.vidiio.ui.viewmodel.VidiioViewModelFactories

@Composable
fun VidiioApp() {
    val navigationState = rememberNavigationState(
        startRoute = VidiioRoute.Splash,
        topLevelRoutes = setOf(VidiioRoute.Home, VidiioRoute.Search, VidiioRoute.Favorites, VidiioRoute.Downloads, VidiioRoute.Settings, VidiioRoute.Splash)
    )

    val navigator = remember { Navigator(navigationState) }
    val context = LocalContext.current
    val application = context.applicationContext as VidiioApplication
    
    // Screens get their dependencies from VidiioViewModelFactories, which reads them off
    // `application` directly - only what this composable itself uses is hoisted here.
    val settingsRepository = application.settingsRepository

    val homeStyle by settingsRepository.homeStyleFlow
        .collectAsState(initial = com.ominix.vidiio.data.repository.HomeStyle.VIDIIO)

    val entryProvider = entryProvider<NavKey> {
        entry<VidiioRoute.Splash> {
            SplashScreen(
                onSplashFinished = {
                    navigator.navigate(VidiioRoute.Home)
                }
            )
        }
        entry<VidiioRoute.Home>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.HomeViewModel = viewModel(
                factory = VidiioViewModelFactories.home(application)
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigateToDetails = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                onResumeWatching = { wp ->
                    navigator.navigate(VidiioRoute.Player(wp.toMovie(), wp.episodeId, null))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Search>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.SearchViewModel = viewModel(
                factory = VidiioViewModelFactories.search(application)
            )
            SearchScreen(
                viewModel = viewModel,
                onNavigateToDetails = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Favorites>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.FavoritesViewModel = viewModel(
                factory = VidiioViewModelFactories.favorites(application)
            )
            FavoritesScreen(
                viewModel = viewModel,
                onMovieClick = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Downloads>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.DownloadsViewModel = viewModel(
                factory = VidiioViewModelFactories.downloads(application)
            )
            DownloadsScreen(
                viewModel = viewModel,
                onPlayLocal = { movie, path ->
                    val uri = android.net.Uri.fromFile(java.io.File(path)).toString()
                    navigator.navigate(VidiioRoute.Player(movie, null, com.ominix.vidiio.data.model.StreamSource(serverName = "Local", url = uri, quality = "Local", isM3u8 = false)))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Settings>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.SettingsViewModel = viewModel(
                factory = VidiioViewModelFactories.settings(application)
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Details>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.detailTransition() }
            }
        ) { key ->
            val viewModel: com.ominix.vidiio.ui.viewmodel.DetailsViewModel = viewModel(
                key = key.movie.id,
                factory = VidiioViewModelFactories.details(application, key.movie)
            )
            DetailsScreen(
                viewModel = viewModel,
                onBack = { navigator.goBack() },
                onPlay = { movie, episode, source -> 
                    navigator.navigate(VidiioRoute.Player(movie, episode?.id, source)) 
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        @androidx.media3.common.util.UnstableApi
        entry<VidiioRoute.Player>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                }
            }
        ) { key ->
            val playerKey = "player_${key.movie.id}_${key.episodeId ?: ""}"
            val viewModel: com.ominix.vidiio.ui.viewmodel.DetailsViewModel = viewModel(
                key = playerKey,
                factory = VidiioViewModelFactories.details(application, key.movie, key.episodeId)
            )
            // Distinct key: ViewModelStore is keyed by string alone, so reusing playerKey
            // for a second ViewModel class would hand back the DetailsViewModel.
            val playerViewModel: com.ominix.vidiio.ui.player.PlayerViewModel = viewModel(
                key = "$playerKey#player",
                factory = VidiioViewModelFactories.player(application)
            )
            PlayerScreen(
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                initialSource = key.source,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val currentRoute = navigationState.backStacks[navigationState.topLevelRoute]?.lastOrNull()
    val isPlayerScreen = currentRoute is VidiioRoute.Player
    val isSplashScreen = currentRoute is VidiioRoute.Splash

    val layoutType = if (isPlayerScreen || isSplashScreen) {
        NavigationSuiteType.None
    } else {
        val windowSizeClass = adaptiveInfo.windowSizeClass
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> NavigationSuiteType.NavigationRail
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> NavigationSuiteType.NavigationRail
            else -> NavigationSuiteType.NavigationBar
        }
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            item(
                selected = navigationState.topLevelRoute == VidiioRoute.Home,
                onClick = { navigator.navigate(VidiioRoute.Home) },
                icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                label = { Text("Home") }
            )
            item(
                selected = navigationState.topLevelRoute == VidiioRoute.Search,
                onClick = { navigator.navigate(VidiioRoute.Search) },
                icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                label = { Text("Search") }
            )
            item(
                selected = navigationState.topLevelRoute == VidiioRoute.Favorites,
                onClick = { navigator.navigate(VidiioRoute.Favorites) },
                icon = { Icon(Icons.Rounded.Favorite, contentDescription = "Favorites") },
                label = { Text("My List") }
            )
            item(
                selected = navigationState.topLevelRoute == VidiioRoute.Downloads,
                onClick = { navigator.navigate(VidiioRoute.Downloads) },
                icon = { Icon(Icons.Rounded.Download, contentDescription = "Downloads") },
                label = { Text("Downloads") }
            )
            item(
                selected = navigationState.topLevelRoute == VidiioRoute.Settings,
                onClick = { navigator.navigate(VidiioRoute.Settings) },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
    ) {
        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun VidiioAppPhonePreview() {
    com.ominix.vidiio.ui.theme.VidiioTheme {
        VidiioApp()
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun VidiioAppTabletPreview() {
    com.ominix.vidiio.ui.theme.VidiioTheme {
        VidiioApp()
    }
}
