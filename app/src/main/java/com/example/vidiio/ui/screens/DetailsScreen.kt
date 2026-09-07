package com.example.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.data.model.StreamSource
import com.example.vidiio.ui.components.GlassCard
import com.example.vidiio.ui.components.RiveLoader
import com.example.vidiio.ui.viewmodel.DetailsUiState
import com.example.vidiio.ui.viewmodel.DetailsViewModel

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (Movie, Episode?, StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSourceSelector by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                        permissions = com.example.vidiio.utils.PermissionUtils.getRequiredPermissions()
                    )
                    MovieDetailContent(
                        movie = state.movie,
                        streamSources = state.streamSources,
                        selectedEpisode = state.selectedEpisode,
                        isSearchingSources = state.isSearchingSources,
                        onEpisodeSelected = { viewModel.selectEpisode(it) },
                        onPlayClick = { source -> onPlay(state.movie, state.selectedEpisode, source) },
                        onDownloadClick = { source ->
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                            viewModel.downloadSource(source)
                        },
                        onToggleSourceSelector = { showSourceSelector = true }
                    )
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
    selectedEpisode: Episode?,
    isSearchingSources: Boolean,
    onEpisodeSelected: (Episode) -> Unit,
    onPlayClick: (StreamSource) -> Unit,
    onDownloadClick: (StreamSource) -> Unit,
    onToggleSourceSelector: () -> Unit
) {
    var selectedSeasonIndex by remember(movie.id) { 
        mutableIntStateOf(movie.seasons.indexOfFirst { it.seasonNumber == selectedEpisode?.seasonNumber }.coerceAtLeast(0)) 
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
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
                        modifier = Modifier.fillMaxWidth().height(56.dp),
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
                    onClick = { onPlayClick(source) },
                    onDownload = { onDownloadClick(source) }
                )
            }
        }

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
                                    "Season ${season.seasonNumber}",
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 130.dp, height = 74.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray)
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
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
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(16.dp))
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
