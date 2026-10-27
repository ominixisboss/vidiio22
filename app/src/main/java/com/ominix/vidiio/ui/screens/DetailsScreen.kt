package com.ominix.vidiio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.repository.SourceHealth
import com.ominix.vidiio.ui.components.SourceHealthBadge
import com.ominix.vidiio.ui.components.tvFocusRing
import com.ominix.vidiio.ui.components.GlassCard
import com.ominix.vidiio.ui.components.RiveLoader
import com.ominix.vidiio.ui.viewmodel.DetailsUiState
import com.ominix.vidiio.ui.viewmodel.DetailsViewModel

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (Movie, Episode?, StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceHealth by viewModel.sourceHealth.collectAsState()
    val unavailableCount by viewModel.unavailableCount.collectAsState()
    val showUnavailable by viewModel.showUnavailable.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    // Up from Play has nothing reliably above it to land on (the trailer sits there), so give
    // the remote a direct route to the top bar.
    val topFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var showSourceSelector by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.focusRequester(topFocus)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is DetailsUiState.Success) {
                        val isFavorite = (uiState as DetailsUiState.Success).isFavorite
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is DetailsUiState.Loading -> {
                    RiveLoader(modifier = Modifier.align(Alignment.Center))
                }
                is DetailsUiState.Success -> {
                    val permissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState(
                        permissions = com.ominix.vidiio.utils.PermissionUtils.getRequiredPermissions()
                    )
                    // Picking an episode starts the best stream that works and is in English, rather than just
                    // listing links: the episode that was tapped, once its sources are found and checked.
                    var autoPlayEpisodeId by remember { mutableStateOf<String?>(null) }
                    var giveUp by remember { mutableStateOf(false) }
                    var sawSearching by remember { mutableStateOf(false) }
                    val toastContext = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(autoPlayEpisodeId) {
                        giveUp = false
                        sawSearching = false
                        if (autoPlayEpisodeId != null) {
                            kotlinx.coroutines.delay(15_000)
                            giveUp = true
                        }
                    }
                    LaunchedEffect(autoPlayEpisodeId, state.selectedEpisode?.id, state.streamSources, sourceHealth, state.isSearchingSources, giveUp) {
                        val pending = autoPlayEpisodeId ?: return@LaunchedEffect
                        // Wait until the tap has taken effect, and the new search has actually begun - before
                        // that the list is empty and "not searching" only because it is between episodes.
                        if (state.selectedEpisode?.id != pending) return@LaunchedEffect
                        if (state.isSearchingSources) sawSearching = true
                        val best = com.ominix.vidiio.data.repository.pickStreamToPlay(
                            state.streamSources, sourceHealth, state.isSearchingSources, giveUp
                        )
                        when {
                            best != null -> {
                                autoPlayEpisodeId = null
                                onPlay(state.movie, state.selectedEpisode, best)
                            }
                            (sawSearching && !state.isSearchingSources) || giveUp -> {
                                autoPlayEpisodeId = null
                                android.widget.Toast.makeText(toastContext, "No working stream found for this episode", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    androidx.activity.compose.BackHandler(enabled = autoPlayEpisodeId != null) { autoPlayEpisodeId = null }

                    MovieDetailContent(
                        movie = state.movie,
                        streamSources = state.streamSources,
                        sourceHealth = sourceHealth,
                        unavailableCount = unavailableCount,
                        showUnavailable = showUnavailable,
                        onShowUnavailableChange = viewModel::setShowUnavailable,
                        selectedEpisode = state.selectedEpisode,
                        isSearchingSources = state.isSearchingSources,
                        onEpisodeSelected = { episode ->
                            viewModel.selectEpisode(episode)
                            autoPlayEpisodeId = episode.id
                        },
                        onPlayClick = { source -> onPlay(state.movie, state.selectedEpisode, source) },
                        onDownloadClick = { source ->
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                            viewModel.downloadSource(source)
                        },
                        onToggleSourceSelector = { showSourceSelector = true },
                        onFocusTop = { runCatching { topFocus.requestFocus() } }
                    )
                    if (autoPlayEpisodeId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .pointerInput(Unit) { },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(16.dp))
                                Text("Finding the best stream...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("Back to cancel", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                is DetailsUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    if (showSourceSelector && uiState is DetailsUiState.Success) {
        SourceSelectorBottomSheet(
            onDismiss = { showSourceSelector = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun MovieDetailContent(
    movie: Movie,
    streamSources: List<StreamSource>,
    sourceHealth: Map<String, SourceHealth>,
    unavailableCount: Int,
    showUnavailable: Boolean,
    onShowUnavailableChange: (Boolean) -> Unit,
    selectedEpisode: Episode?,
    isSearchingSources: Boolean,
    onEpisodeSelected: (Episode) -> Unit,
    onPlayClick: (StreamSource) -> Unit,
    onDownloadClick: (StreamSource) -> Unit,
    onToggleSourceSelector: () -> Unit,
    onFocusTop: () -> Unit = {}
) {
    var selectedSeasonIndex by remember(movie.id) {
        mutableIntStateOf(movie.seasons.indexOfFirst { it.seasonNumber == selectedEpisode?.seasonNumber }.coerceAtLeast(0))
    }
    var trailerMuted by remember(movie.id) { mutableStateOf(true) }
    // The inline trailer is a WebView, the heaviest thing on this page. On low-memory devices (1 GB TV
    // sticks) the system kills it and the viewer sees a browser crash page, so offer the external
    // Trailer button there instead of autoplaying.
    val lowRamDevice = androidx.compose.ui.platform.LocalContext.current.let { c ->
        (c.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.let { it.isLowRamDevice || it.memoryClass <= 192 } == true
    }
    var trailerFailed by remember(movie.id) { mutableStateOf(lowRamDevice) }
    var trailerFullscreen by remember(movie.id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val trailerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Pause the inline trailer once the header scrolls (mostly) out of view.
    val headerVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 700
        }
    }

    val trailerKey = movie.trailerKey
    if (trailerFullscreen && trailerKey != null) {
        Dialog(
            onDismissRequest = { trailerFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { trailerFullscreen = false }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                com.ominix.vidiio.ui.components.TrailerPlayer(
                    videoId = trailerKey,
                    playing = true,
                    muted = false,
                    controls = true,
                    interactive = true,
                    startMuted = false,
                    fit = com.ominix.vidiio.ui.components.TrailerFit.CONTAIN,
                    modifier = Modifier.fillMaxSize(),
                    onUnavailable = { trailerFailed = true; trailerFullscreen = false }
                )
                IconButton(
                    onClick = { trailerFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                val trailerActive = trailerKey != null && !trailerFailed
                if (trailerActive) {
                    com.ominix.vidiio.ui.components.TrailerPlayer(
                        videoId = trailerKey!!,
                        playing = headerVisible && !trailerFullscreen,
                        muted = trailerMuted,
                        modifier = Modifier.fillMaxSize(),
                        onUnavailable = { trailerFailed = true }
                    )
                } else {
                    AsyncImage(
                        model = movie.backdropUrl ?: movie.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 300f
                            )
                        )
                )
                if (trailerActive) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { trailerMuted = !trailerMuted },
                            modifier = Modifier.focusRequester(trailerFocus).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = if (trailerMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (trailerMuted) "Unmute trailer" else "Mute trailer",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { trailerFullscreen = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen trailer", tint = Color.White)
                        }
                    }
                } else if (trailerKey != null) {
                    // Trailer can't be embedded — offer to open it externally.
                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Trailer")
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        movie.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        ) {
                            Text(
                                text = "HD",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                if (streamSources.isNotEmpty()) {
                    Button(
                        onClick = { onPlayClick(streamSources.first()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .tvFocusRing(RoundedCornerShape(24.dp))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    // Scroll back up to the trailer and put focus on its controls; with no
                                    // trailer there, fall back to the top bar.
                                    scope.launch {
                                        listState.scrollToItem(0)
                                        kotlinx.coroutines.delay(150)
                                        runCatching { trailerFocus.requestFocus() }.onFailure { onFocusTop() }
                                    }
                                    true
                                } else false
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (movie.type == MovieType.TV_SHOW && selectedEpisode != null) "Play S${selectedEpisode.seasonNumber}:E${selectedEpisode.episodeNumber}" else "Play",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                movie.synopsis?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Seasons and episodes come first: the links below are for the episode picked here.
        if (movie.type == MovieType.TV_SHOW && movie.seasons.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedSeasonIndex,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    movie.seasons.forEachIndexed { index, season ->
                        Tab(
                            selected = selectedSeasonIndex == index,
                            onClick = { selectedSeasonIndex = index },
                            text = {
                                Text(
                                    season.name ?: "Season ${season.seasonNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            val currentSeason = movie.seasons.getOrNull(selectedSeasonIndex)
            if (currentSeason != null) {
                items(currentSeason.episodes.size) { index ->
                    val episode = currentSeason.episodes[index]
                    EpisodeListItem(
                        episode = episode,
                        isSelected = episode.id == selectedEpisode?.id,
                        onClick = { onEpisodeSelected(episode) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (isSearchingSources) {
                    RiveLoader(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (unavailableCount > 0) {
                    TextButton(onClick = { onShowUnavailableChange(!showUnavailable) }) {
                        Text(
                            text = if (showUnavailable) "Hide not working" else "Show not working ($unavailableCount)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                IconButton(onClick = onToggleSourceSelector) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Select Sources", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        if (streamSources.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (isSearchingSources) {
                        RiveLoader(modifier = Modifier.size(100.dp))
                    } else {
                        Text(text = "No sources found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(streamSources.size) { index ->
                val source = streamSources[index]
                SourceItem(
                    source = source,
                    health = sourceHealth[source.url],
                    onClick = { onPlayClick(source) },
                    onDownload = { onDownloadClick(source) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectorBottomSheet(
    onDismiss: () -> Unit,
    viewModel: DetailsViewModel
) {
    val sheetState = rememberModalBottomSheetState()
    val enabledSources by viewModel.enabledSources.collectAsState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Select Sources",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            val sources = listOf(
                "vidsrc" to "VidSrc",
                "videasy" to "VidEasy",
                "vadapav" to "Vadapav",
                "vuflix" to "Vuflix",
                "cinejoy" to "Cinejoy",
                "movy" to "Movy",
                "a111477" to "A111477",
                "knaben" to "Knaben",
                "tg" to "TorrentGalaxy"
            )
            
            LazyColumn {
                items(sources.size) { index ->
                    val (id, name) = sources[index]
                    val isEnabled = enabledSources.contains(id)
                    
                    GlassCard(
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { viewModel.toggleSource(id) },
                        alpha = 0.05f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = { viewModel.toggleSource(id) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SourceItem(
    source: StreamSource,
    health: SourceHealth?,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        alpha = 0.1f,
        borderAlpha = 0.15f,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                source.url.startsWith("magnet:") -> Icons.Rounded.CloudDownload
                else -> Icons.Rounded.PlayCircle
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.serverName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.sourceName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    source.quality?.let {
                        Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    source.size?.let {
                        Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    source.seeders?.let {
                        Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "👥 $it", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
                SourceHealthBadge(health = health, modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: Episode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        alpha = if (isSelected) 0.15f else 0.05f,
        borderAlpha = if (isSelected) 0.3f else 0.1f,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray)
            ) {
                if (episode.stillPath != null) {
                    AsyncImage(
                        model = episode.stillPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f
                            )
                        )
                )
                Text(
                    text = "${episode.episodeNumber}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                episode.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
