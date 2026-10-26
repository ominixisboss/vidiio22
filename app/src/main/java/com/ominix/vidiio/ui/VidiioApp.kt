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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.ominix.vidiio.ui.pointer.VirtualPointerOverlay
import com.ominix.vidiio.ui.tv.LocalIsTv
import com.ominix.vidiio.ui.tv.ProvideTvScrolling
import com.ominix.vidiio.ui.tv.TvNavItem
import com.ominix.vidiio.ui.tv.TvNavScaffold
import com.ominix.vidiio.ui.tv.isTvDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VidiioApp() {
    val navigationState = rememberNavigationState(
        startRoute = VidiioRoute.Splash,
        topLevelRoutes = setOf(VidiioRoute.Home, VidiioRoute.Search(), VidiioRoute.Favorites, VidiioRoute.Downloads, VidiioRoute.Settings, VidiioRoute.Splash)
    )

    val navigator = remember { Navigator(navigationState) }
    val context = LocalContext.current
    val isTv = remember { isTvDevice(context) }
    val application = context.applicationContext as VidiioApplication
    
    // Screens get their dependencies from VidiioViewModelFactories, which reads them off
    // `application` directly - only what this composable itself uses is hoisted here.
    val settingsRepository = application.settingsRepository

    val homeStyle by settingsRepository.homeStyleFlow
        .collectAsState(initial = com.ominix.vidiio.data.repository.HomeStyle.VIDIIO)

    val mouseToggle by settingsRepository.mouseToggleFlow.collectAsState(initial = false)

    val themeAccent = MaterialTheme.colorScheme.primary
    val spec = remember(homeStyle, themeAccent) { homeStyle.spec(themeAccent) }

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
                onSearchQuery = { query -> navigator.navigate(VidiioRoute.Search(query)) },
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
        ) { key ->
            val viewModel: com.ominix.vidiio.ui.viewmodel.SearchViewModel = viewModel(
                key = "search_${key.query}",
                factory = VidiioViewModelFactories.search(application)
            )
            LaunchedEffect(key.query) {
                if (key.query.isNotBlank()) {
                    viewModel.onQueryChange(key.query)
                    viewModel.search()
                }
            }
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
            val playerViewModel: com.ominix.vidiio.ui.player.PlayerViewModel = viewModel(
                key = "player_${key.movie.id}_${key.episodeId ?: ""}",
                factory = VidiioViewModelFactories.player(application, key.movie, key.episodeId)
            )
            PlayerScreen(
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
    val isSearchScreen = currentRoute is VidiioRoute.Search
    val pointerEnabled = mouseToggle && !isPlayerScreen && !isSplashScreen && !isSearchScreen

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

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = spec.accent,
            indicatorColor = spec.accent.copy(alpha = 0.35f),
            unselectedIconColor = Color.White.copy(alpha = 0.6f),
            unselectedTextColor = Color.White.copy(alpha = 0.6f)
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = spec.accent,
            indicatorColor = spec.accent.copy(alpha = 0.35f),
            unselectedIconColor = Color.White.copy(alpha = 0.6f),
            unselectedTextColor = Color.White.copy(alpha = 0.6f)
        )
    )

    // One list of sections, shared by the standard scaffold and the TV one.
    val navItems = listOf(
        TvNavItem(
            selected = navigationState.topLevelRoute == VidiioRoute.Home,
            icon = Icons.Rounded.Home, label = "Home",
            onClick = { navigator.navigate(VidiioRoute.Home) }
        ),
        TvNavItem(
            selected = navigationState.topLevelRoute is VidiioRoute.Search,
            icon = Icons.Rounded.Search, label = "Search",
            onClick = { navigator.navigate(VidiioRoute.Search()) }
        ),
        TvNavItem(
            selected = navigationState.topLevelRoute == VidiioRoute.Favorites,
            icon = Icons.Rounded.Favorite, label = "My List",
            onClick = { navigator.navigate(VidiioRoute.Favorites) }
        ),
        TvNavItem(
            selected = navigationState.topLevelRoute == VidiioRoute.Downloads,
            icon = Icons.Rounded.Download, label = "Downloads",
            onClick = { navigator.navigate(VidiioRoute.Downloads) }
        ),
        TvNavItem(
            selected = navigationState.topLevelRoute == VidiioRoute.Settings,
            icon = Icons.Rounded.Settings, label = "Settings",
            onClick = { navigator.navigate(VidiioRoute.Settings) }
        ),
    )

    val pageContent: @Composable () -> Unit = {
        VirtualPointerOverlay(enabled = pointerEnabled) {
            NavDisplay(
                entries = navigationState.toEntries(entryProvider),
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    CompositionLocalProvider(LocalIsTv provides isTv) {
        ProvideTvScrolling(enabled = isTv) {
            if (isTv) {
                // A remote-driven TV gets a side menu that hides itself while you browse.
                TvNavScaffold(
                    items = navItems,
                    railEnabled = !(isPlayerScreen || isSplashScreen),
                    railColors = navItemColors.navigationRailItemColors,
                    pageKey = currentRoute ?: "none",
                    autoFocus = !isSearchScreen,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        pageContent()
                    }
                }
            } else {
                NavigationSuiteScaffold(
                    layoutType = layoutType,
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationSuiteItems = {
                        navItems.forEach { nav ->
                            item(
                                selected = nav.selected,
                                onClick = nav.onClick,
                                icon = { Icon(nav.icon, contentDescription = nav.label) },
                                label = { Text(nav.label, fontWeight = FontWeight.Bold) },
                                colors = navItemColors
                            )
                        }
                    }
                ) {
                    pageContent()
                }
            }
        }
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
