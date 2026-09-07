package com.example.vidiio.ui.screens

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.IBinder
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.vidiio.MainActivity
import com.example.vidiio.VidiioApplication
import com.example.vidiio.data.model.StreamSource
import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.torrent.TorrentService
import com.example.vidiio.torrent.TorrentFileInfo
import com.example.vidiio.ui.viewmodel.DetailsUiState
import com.example.vidiio.ui.viewmodel.DetailsViewModel
import com.example.vidiio.ui.player.AudioTrackInfo
import com.example.vidiio.ui.player.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

@UnstableApi
@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun PlayerScreen(
    viewModel: DetailsViewModel,
    initialSource: StreamSource? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val permissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState(
        permissions = com.example.vidiio.utils.PermissionUtils.getRequiredPermissions()
    )
    
    // Core Playback State
    var selectedSource by remember { mutableStateOf(initialSource) }
    var isBuffering by remember { mutableStateOf(false) }
    var isTorrentLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    
    // UI State
    var showControls by remember { mutableStateOf(true) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAspectMenu by remember { mutableStateOf(false) }
    var showEpisodesSidebar by remember { mutableStateOf(false) }
    
    // HUDs & Gestures
    var showVolumeHud by remember { mutableStateOf(false) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1.0f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var isMuted by remember { mutableStateOf(false) }
    var audioHudText by remember { mutableStateOf("") }
    var showAudioHud by remember { mutableStateOf(false) }
    
    // Settings
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var selectedAudioTrack by remember { mutableStateOf<AudioTrackInfo?>(null) }
    var availableAudioTracks by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }

    // Auto-Next & Skip
    var showSkipIntro by remember { mutableStateOf(false) }
    var isIntroDismissed by remember { mutableStateOf(false) }
    var showSkipOutro by remember { mutableStateOf(false) }
    var showAutoNextOverlay by remember { mutableStateOf(false) }
    var autoNextSeconds by remember { mutableIntStateOf(5) }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompact = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
    
    // Torrent Logic
    var torrentService by remember { mutableStateOf<TorrentService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                torrentService = (service as TorrentService.LocalBinder).getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                torrentService = null
            }
        }
    }

    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    LaunchedEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // Seed brightness from the window override if set, else from the system setting.
        val winB = activity?.window?.attributes?.screenBrightness ?: -1f
        brightness = if (winB in 0f..1f) winB else runCatching {
            android.provider.Settings.System.getInt(
                context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        }.getOrDefault(0.5f)
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        context.bindService(Intent(context, TorrentService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        (activity as? MainActivity)?.acquirePlayerLocks()

        onDispose {
            (activity as? MainActivity)?.releasePlayerLocks()
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            torrentService?.stopStreaming()
            context.unbindService(serviceConnection)
        }
    }

    val torrentStatus by (torrentService?.torrentManager?.status?.collectAsState() ?: remember { mutableStateOf(null) })
    var torrentFiles by remember { mutableStateOf<List<TorrentFileInfo>?>(null) }
    var showTorrentFileSheet by remember { mutableStateOf(false) }

    val downloadStatus by remember(selectedSource) {
        selectedSource?.let { viewModel.getDownloadStatus(it.url) } ?: flowOf(null)
    }.collectAsState(null)
    val isDownloading = downloadStatus != null && downloadStatus != DownloadStatus.COMPLETED && downloadStatus != DownloadStatus.FAILED && downloadStatus != DownloadStatus.CANCELLED

    // Media3 Player
    val dataSourceFactory = remember {
        OkHttpDataSource.Factory((context.applicationContext as VidiioApplication).playbackHttpClient)
    }
    val exoPlayer = remember {

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60000, // minBufferMs
                120000, // maxBufferMs
                10000, // bufferForPlaybackMs
                15000 // bufferForPlaybackAfterRebufferMs
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "Playback Error: ${error.localizedMessage}"
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        val tracksList = mutableListOf<AudioTrackInfo>()
                        tracks.groups.forEachIndexed { groupIndex, group ->
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    tracksList.add(AudioTrackInfo(
                                        name = format.label ?: format.language ?: "Track ${tracksList.size + 1}",
                                        groupIndex = groupIndex,
                                        trackIndex = i,
                                        format = format
                                    ))
                                }
                            }
                        }
                        availableAudioTracks = tracksList
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                    }
                })
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Position Polling
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            bufferedPosition = exoPlayer.bufferedPosition
            
            // Skip & Auto-Next Logic
            if (duration > 0) {
                val inIntroRange = currentPosition in 5000L..90000L
                showSkipIntro = inIntroRange && !isIntroDismissed
                
                if (!inIntroRange) {
                    isIntroDismissed = false // Reset for later if needed
                }

                showSkipOutro = duration - currentPosition < 120000L && duration > 120000L
                
                val remaining = duration - currentPosition
                if (remaining in 1L..5000L && isPlaying) {
                    showAutoNextOverlay = true
                    autoNextSeconds = (remaining / 1000).toInt() + 1
                } else if (remaining > 5000L) {
                    showAutoNextOverlay = false
                }
            }
            delay(500)
        }
    }

    // Auto-dismiss Skip Intro
    LaunchedEffect(showSkipIntro) {
        if (showSkipIntro) {
            delay(7000)
            showSkipIntro = false
            isIntroDismissed = true
        }
    }

    // Source Selection Effect
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is DetailsUiState.Success && selectedSource == null) {
            selectedSource = state.streamSources.firstOrNull()
        }
    }

    var retryTrigger by remember { mutableIntStateOf(0) }

    // Playback Logic
    fun startPlayback(source: StreamSource) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        // Many streaming hosts 403 without the scraper's Referer/Origin/UA headers.
        // ExoPlayer carries these on the HTTP data source, not the MediaItem.
        val requestHeaders = buildMap {
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            source.headers?.let { putAll(it) }
        }
        dataSourceFactory.setDefaultRequestProperties(requestHeaders)

        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(if (source.isM3u8) MimeTypes.APPLICATION_M3U8 else null)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(selectedSource, torrentService, retryTrigger) {
        val service = torrentService ?: return@LaunchedEffect
        selectedSource?.let { source ->
            if (source.url.startsWith("magnet:")) {
                isTorrentLoading = true
                val timeoutJob = scope.launch {
                    delay(95000)
                    if (isTorrentLoading) {
                        isTorrentLoading = false
                        errorMessage = "Torrent timed out (no peers found after 95 seconds)."
                    }
                }
                service.getMetadata(source.url) { files ->
                    timeoutJob.cancel()
                    if (files != null) {
                        if (files.size > 1) {
                            torrentFiles = files
                            showTorrentFileSheet = true
                            isTorrentLoading = false
                        } else {
                            val selectedEpisode = (uiState as? DetailsUiState.Success)?.selectedEpisode
                            service.startStreaming(-1, selectedEpisode?.seasonNumber, selectedEpisode?.episodeNumber) { streamUrl ->
                                isTorrentLoading = false
                                if (streamUrl.isNotEmpty()) {
                                    startPlayback(StreamSource(serverName = source.serverName, url = streamUrl, isM3u8 = false))
                                } else {
                                    errorMessage = "Failed to load torrent. No peers found or timeout."
                                }
                            }
                        }
                    } else {
                        isTorrentLoading = false
                        errorMessage = "Failed to fetch torrent metadata."
                    }
                }
            } else if (!source.url.contains("embed")) {
                startPlayback(source)
            }
        }
    }

    // Controls visibility and immersive mode
    LaunchedEffect(showControls) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (showControls) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Controls timeout
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .pointerInput(Unit) {
            var dragSide = 0 // 0: None, 1: Left (Brightness), 2: Right (Volume)
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    dragSide = if (offset.x < size.width / 2) 1 else 2
                },
                onVerticalDrag = { _, dragAmount ->
                    // Drag up = increase. A full swipe over ~65% of the screen covers the whole range,
                    // and we accumulate in a float so tiny drags still register.
                    val deltaFraction = -dragAmount / (size.height * 0.65f)
                    if (dragSide == 2) {
                        volume = (volume + deltaFraction).coerceIn(0f, 1f)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            kotlin.math.round(volume * maxVol).toInt(),
                            0
                        )
                        isMuted = volume <= 0f
                        showVolumeHud = true
                        showBrightnessHud = false
                    } else {
                        brightness = (brightness + deltaFraction).coerceIn(0.02f, 1f)
                        activity?.window?.let { w ->
                            w.attributes = w.attributes.apply { screenBrightness = brightness }
                        }
                        showBrightnessHud = true
                        showVolumeHud = false
                    }
                },
                onDragEnd = {
                    scope.launch {
                        delay(1200)
                        showVolumeHud = false
                        showBrightnessHud = false
                    }
                }
            )
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { showControls = !showControls },
                onDoubleTap = { offset ->
                    val width = size.width
                    if (offset.x < width / 2) {
                        exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10000))
                    } else {
                        exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10000))
                    }
                }
            )
        }
    ) {
        val streamUrl = selectedSource?.url ?: ""
        val isEmbed = streamUrl.contains("embed") || streamUrl.contains("vidsrc")

        if (isEmbed) {
            EmbedPlayer(
                url = streamUrl,
                onBack = onBack,
                onError = { errorMessage = it }
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // UI Layer
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val movie = (uiState as? DetailsUiState.Success)?.movie
                val currentEpisode = (uiState as? DetailsUiState.Success)?.selectedEpisode
                
                PlayerTopBar(
                    title = movie?.title ?: "Loading...",
                    subtitle = currentEpisode?.let { "S${it.seasonNumber}:E${it.episodeNumber} - ${it.name}" },
                    quality = selectedSource?.quality,
                    isDownloading = isDownloading,
                    isEpisodesActive = showEpisodesSidebar,
                    onBack = onBack,
                    onDownload = {
                        selectedSource?.let { source ->
                            // Downloads go to app-scoped storage - no permission needed.
                            // Still ask for POST_NOTIFICATIONS so progress shows, but don't block on it.
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                            viewModel.downloadSource(source)
                        }
                    },
                    onToggleEpisodes = { showEpisodesSidebar = !showEpisodesSidebar }
                )

                PlayerTransport(
                    isPlaying = isPlaying,
                    position = currentPosition,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    isSubtitlesActive = selectedSubtitleUrl != null, 
                    isAudioActive = selectedAudioTrack != null,
                    isSpeedActive = playbackSpeed != 1.0f,
                    isAspectActive = resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    isFullscreen = true, 
                    playbackSpeed = playbackSpeed,
                    onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    onSeek = { exoPlayer.seekTo(it) },
                    onRewind = { exoPlayer.seekTo(maxOf(0, currentPosition - 10000)) },
                    onForward = { exoPlayer.seekTo(minOf(duration, currentPosition + 10000)) },
                    onToggleSubtitles = { showSubtitleMenu = true },
                    onToggleAudio = { showAudioMenu = true },
                    onToggleSpeed = { showSpeedMenu = true },
                    onToggleAspect = { showAspectMenu = true },
                    onToggleFullscreen = {
                        // The player is always immersive; use this as a quick fill/fit toggle.
                        resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Overlays
        if (isTorrentLoading || isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                val currentStatus = torrentStatus
                if (selectedSource?.url?.startsWith("magnet:") == true && currentStatus != null) {
                    val status = currentStatus
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { (status.bufferProgress / 100f).coerceIn(0f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = status.statusMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (status.statusMessage.contains("Buffering") || status.statusMessage.contains("Peers")) {
                            Text(
                                text = "Buffer: ${status.bufferProgress.toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${status.numSeeders} seeders • ${status.numPeers} peers",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format("%.1f KB/s", status.downloadRate),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        }

        errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Playback Error") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = {
                        errorMessage = null
                        isTorrentLoading = false
                        retryTrigger++
                    }) {
                        Text("Retry")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        errorMessage = null
                        isTorrentLoading = false
                        onBack()
                    }) {
                        Text("Choose Another Source")
                    }
                }
            )
        }


        Box(modifier = Modifier.align(Alignment.Center)) {
            VolumeHud(volume = volume, isMuted = isMuted, visible = showVolumeHud)
        }
        Box(modifier = Modifier.align(Alignment.Center)) {
            BrightnessHud(brightness = brightness, visible = showBrightnessHud)
        }

        if (showAudioHud) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                AudioHud(text = audioHudText, visible = showAudioHud)
            }
        }

        if (showSkipIntro) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 120.dp, end = 24.dp)) {
                SkipButton(
                    label = "Skip Intro", 
                    onSkip = { 
                        exoPlayer.seekTo(90000L)
                        showSkipIntro = false
                        isIntroDismissed = true
                    }, 
                    onDismiss = { 
                        showSkipIntro = false
                        isIntroDismissed = true
                    }
                )
            }
        }

        if (showAutoNextOverlay && uiState is DetailsUiState.Success) {
            val success = uiState as DetailsUiState.Success
            val allEpisodes = success.movie.seasons.flatMap { it.episodes }
            val currentIndex = allEpisodes.indexOfFirst { it.id == success.selectedEpisode?.id }
            if (currentIndex != -1 && currentIndex < allEpisodes.size - 1) {
                AutoNextOverlay(
                    nextEpisodeName = allEpisodes[currentIndex + 1].name,
                    secondsLeft = autoNextSeconds,
                    onCancel = { showAutoNextOverlay = false },
                    onPlayNow = { viewModel.selectEpisode(allEpisodes[currentIndex + 1]) }
                )
            }
        }

        // Side Panels & Menus
        if (showEpisodesSidebar && uiState is DetailsUiState.Success) {
            val success = uiState as DetailsUiState.Success
            EpisodeSidebar(
                movie = success.movie,
                currentEpisode = success.selectedEpisode,
                onEpisodeSelect = { 
                    viewModel.selectEpisode(it)
                    showEpisodesSidebar = false
                },
                onClose = { showEpisodesSidebar = false },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        if (showSubtitleMenu && uiState is DetailsUiState.Success) {
            SubtitleMenu(
                subtitles = (uiState as DetailsUiState.Success).subtitles,
                selectedUrl = selectedSubtitleUrl, 
                offsetMs = subtitleOffsetMs,
                onOffsetChange = { subtitleOffsetMs = it },
                onSubtitleSelect = { sub ->
                    selectedSubtitleUrl = sub.url
                    sub.url?.let { url ->
                        val subConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(sub.language)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        
                        val currentMediaItem = exoPlayer.currentMediaItem
                        if (currentMediaItem != null) {
                            val updatedItem = currentMediaItem.buildUpon()
                                .setSubtitleConfigurations(listOf(subConfig))
                                .build()
                            exoPlayer.setMediaItem(updatedItem, exoPlayer.currentPosition)
                        }
                    }
                },
                onDisable = { 
                    selectedSubtitleUrl = null
                    val currentMediaItem = exoPlayer.currentMediaItem
                    if (currentMediaItem != null) {
                        val updatedItem = currentMediaItem.buildUpon()
                            .setSubtitleConfigurations(emptyList())
                            .build()
                        exoPlayer.setMediaItem(updatedItem, exoPlayer.currentPosition)
                    }
                },
                onDismiss = { showSubtitleMenu = false }
            )
        }

        if (showAudioMenu) {
            AudioMenu(
                tracks = availableAudioTracks,
                selectedTrack = selectedAudioTrack,
                onTrackSelect = { track ->
                    selectedAudioTrack = track
                    audioHudText = track.name
                    showAudioHud = true
                    
                    val parameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .addOverride(TrackSelectionOverride(exoPlayer.currentTracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                        .build()
                    exoPlayer.trackSelectionParameters = parameters

                    scope.launch { delay(2000); showAudioHud = false }
                },
                onDismiss = { showAudioMenu = false }
            )
        }

        if (showSpeedMenu) {
            SpeedMenu(
                currentSpeed = playbackSpeed,
                onSpeedSelect = { speed ->
                    playbackSpeed = speed
                    exoPlayer.setPlaybackSpeed(speed)
                },
                onDismiss = { showSpeedMenu = false }
            )
        }

        if (showAspectMenu) {
            AspectMenu(
                currentMode = resizeMode,
                onModeSelect = { resizeMode = it },
                onDismiss = { showAspectMenu = false }
            )
        }

        if (showTorrentFileSheet && torrentFiles != null) {
            TorrentFileSheet(
                onDismiss = { showTorrentFileSheet = false },
                files = torrentFiles!!,
                onFileSelect = { file ->
                    isTorrentLoading = true
                    torrentService?.startStreaming(file.index, null, null) { streamUrl ->
                        isTorrentLoading = false
                        if (streamUrl.isNotEmpty()) {
                            startPlayback(StreamSource(serverName = file.name, url = streamUrl, isM3u8 = false))
                        } else {
                            errorMessage = "Failed to load torrent. No peers found or timeout."
                        }
                    }
                    showTorrentFileSheet = false
                }
            )
        }
    }
}

@Composable
fun EmbedPlayer(url: String, onBack: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onError("Failed to load embed page (${error?.description ?: "Network error"})")
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        onError("Failed to load embed page (HTTP error ${errorResponse?.statusCode})")
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        webView.loadUrl(url)
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}
