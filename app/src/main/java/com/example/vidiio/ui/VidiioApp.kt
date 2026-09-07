package com.example.vidiio.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.example.vidiio.VidiioApplication
import com.example.vidiio.navigation.Navigator
import com.example.vidiio.navigation.VidiioRoute
import com.example.vidiio.navigation.rememberNavigationState
import com.example.vidiio.navigation.toEntries
import com.example.vidiio.data.model.toMovie
import com.example.vidiio.ui.screens.*
import com.example.vidiio.ui.viewmodel.VidiioViewModelFactory

@Composable
fun VidiioApp() {
    val navigationState = rememberNavigationState(
        startRoute = VidiioRoute.Splash,
        topLevelRoutes = setOf(VidiioRoute.Home, VidiioRoute.Search, VidiioRoute.Favorites, VidiioRoute.Downloads, VidiioRoute.Settings, VidiioRoute.Splash)
    )

    val navigator = remember { Navigator(navigationState) }
    val context = LocalContext.current
    val application = context.applicationContext as VidiioApplication
    
    // Core dependencies
    val repository = application.movieRepository
    val settingsRepository = application.settingsRepository
    val favoriteRepository = application.favoriteRepository
    val downloadRepository = application.downloadRepository
    val downloadManager = application.downloadManager
    val subdlService = application.subdlService
    val watchProgressRepository = application.watchProgressRepository

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
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
                    fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
                }
            }
        ) {
            val viewModel: com.example.vidiio.ui.viewmodel.HomeViewModel = viewModel(
                factory = VidiioViewModelFactory(
                    movieRepository = repository,
                    watchProgressRepository = watchProgressRepository
                )
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
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
                    fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
                }
            }
        ) {
            val viewModel: com.example.vidiio.ui.viewmodel.SearchViewModel = viewModel(
                factory = VidiioViewModelFactory(movieRepository = repository)
            )
            SearchScreen(
                viewModel = viewModel,
                onNavigateToDetails = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Favorites>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
                    fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
                }
            }
        ) {
            val viewModel: com.example.vidiio.ui.viewmodel.FavoritesViewModel = viewModel(
                factory = VidiioViewModelFactory(favoriteRepository = favoriteRepository)
            )
            FavoritesScreen(
                viewModel = viewModel,
                onMovieClick = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Downloads>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
                    fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
                }
            }
        ) {
            val viewModel: com.example.vidiio.ui.viewmodel.DownloadsViewModel = viewModel(
                factory = VidiioViewModelFactory(
                    downloadRepository = downloadRepository,
                    downloadManager = downloadManager,
                    context = context
                )
            )
            DownloadsScreen(
                viewModel = viewModel,
                onPlayLocal = { movie, path ->
                    val uri = android.net.Uri.fromFile(java.io.File(path)).toString()
                    navigator.navigate(VidiioRoute.Player(movie, null, com.example.vidiio.data.model.StreamSource(serverName = "Local", url = uri, quality = "Local", isM3u8 = false)))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Settings>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
                    fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
                }
            }
        ) {
            val viewModel: com.example.vidiio.ui.viewmodel.SettingsViewModel = viewModel(
                factory = VidiioViewModelFactory(
                    settingsRepository = settingsRepository,
                    context = context
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Details>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    slideInHorizontally(tween(400)) { it } + fadeIn(tween(400)) togetherWith
                    slideOutHorizontally(tween(400)) { -it } + fadeOut(tween(400))
                }
            }
        ) { key ->
            val viewModel: com.example.vidiio.ui.viewmodel.DetailsViewModel = viewModel(
                key = key.movie.id,
                factory = VidiioViewModelFactory(
                    movieRepository = repository,
                    favoriteRepository = favoriteRepository,
                    downloadRepository = downloadRepository,
                    downloadManager = downloadManager,
                    subdlService = subdlService,
                    watchProgressRepository = watchProgressRepository,
                    settingsRepository = settingsRepository,
                    movie = key.movie
                )
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
            val viewModel: com.example.vidiio.ui.viewmodel.DetailsViewModel = viewModel(
                key = "player_${key.movie.id}_${key.episodeId ?: ""}",
                factory = VidiioViewModelFactory(
                    movieRepository = repository,
                    favoriteRepository = favoriteRepository,
                    downloadRepository = downloadRepository,
                    downloadManager = downloadManager,
                    subdlService = subdlService,
                    watchProgressRepository = watchProgressRepository,
                    settingsRepository = settingsRepository,
                    movie = key.movie,
                    initialEpisodeId = key.episodeId
                )
            )
            PlayerScreen(
                viewModel = viewModel,
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
    com.example.vidiio.ui.theme.VidiioTheme {
        VidiioApp()
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun VidiioAppTabletPreview() {
    com.example.vidiio.ui.theme.VidiioTheme {
        VidiioApp()
    }
}
