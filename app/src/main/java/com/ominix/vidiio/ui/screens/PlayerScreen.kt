package com.ominix.vidiio.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.torrent.TorrentService
import com.ominix.vidiio.ui.player.PlayerViewModel
import com.ominix.vidiio.ui.player.PlayerWindowEffects
import com.ominix.vidiio.ui.player.components.*
import com.ominix.vidiio.ui.player.playerRemoteControls
import com.ominix.vidiio.ui.player.playerTouchGestures
import com.ominix.vidiio.ui.player.rememberPlayerDeviceControls
import com.ominix.vidiio.ui.viewmodel.DetailsUiState
import com.ominix.vidiio.ui.viewmodel.DetailsViewModel
import com.ominix.vidiio.utils.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The player.
 *
 * Playback state and the ExoPlayer itself live in [PlayerViewModel]; the window-level
 * effects in PlayerWindowEffects; the input handling in Modifier.playerRemoteControls /
 * playerTouchGestures. What is left here is composition, the menu-visibility flags (view
 * state, nothing else needs them) and the TorrentService binding, which is tied to this
 * composable's context.
 */
@UnstableApi
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlayerScreen(
    viewModel: DetailsViewModel,
    playerViewModel: PlayerViewModel,
    initialSource: StreamSource? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    val state by playerViewModel.state.collectAsState()
    val audioHudText by playerViewModel.audioHudText.collectAsState()
    val resumePositionMs by viewModel.resumePositionMs.collectAsState()
    val retryTrigger by playerViewModel.retryTrigger.collectAsState()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionUtils.getRequiredPermissions()
    )

    // ── View state: which chrome is showing. Nothing outside this screen needs it. ──
    var showControls by remember { mutableStateOf(true) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAspectMenu by remember { mutableStateOf(false) }
    var showEpisodesSidebar by remember { mutableStateOf(false) }

    val deviceControls = rememberPlayerDeviceControls()

    // Watch progress is saved by the ViewModel's polling loop and on clear, but only
    // DetailsViewModel knows which movie/episode the position belongs to.
    LaunchedEffect(viewModel) {
        playerViewModel.onSaveProgress = { position, duration ->
            viewModel.saveWatchProgress(position, duration)
        }
    }
    LaunchedEffect(resumePositionMs) { playerViewModel.setResumePosition(resumePositionMs) }
    LaunchedEffect(initialSource) { initialSource?.let(playerViewModel::selectSource) }

    PlayerWindowEffects(notchSafe = state.notchSafe, showControls = showControls)

    // ── Torrent service binding ──────────────────────────────────────────────
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

    DisposableEffect(Unit) {
        context.bindService(
            Intent(context, TorrentService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        onDispose {
            torrentService?.stopStreaming()
            context.unbindService(serviceConnection)
        }
    }

    val torrentStatus by (
        torrentService?.torrentManager?.status?.collectAsState()
            ?: remember { mutableStateOf(null) }
        )

    val downloadStatus by remember(state.selectedSource) {
        state.selectedSource?.let { viewModel.getDownloadStatus(it.url) } ?: flowOf(null)
    }.collectAsState(null)
    val isDownloading = downloadStatus != null &&
        downloadStatus != DownloadStatus.COMPLETED &&
        downloadStatus != DownloadStatus.FAILED &&
        downloadStatus != DownloadStatus.CANCELLED

    // Pick the first source once details land, if the caller gave us none.
    LaunchedEffect(uiState) {
        (uiState as? DetailsUiState.Success)?.let {
            playerViewModel.selectSourceIfNone(it.streamSources.firstOrNull())
        }
    }

    // ── Source preparation ───────────────────────────────────────────────────
    LaunchedEffect(state.selectedSource, torrentService, retryTrigger) {
        val service = torrentService ?: return@LaunchedEffect
        val source = state.selectedSource ?: return@LaunchedEffect

        if (source.url.startsWith("magnet:")) {
            playerViewModel.setTorrentLoading(true)
            val timeoutJob = scope.launch {
                delay(95000)
                if (playerViewModel.state.value.isTorrentLoading) {
                    playerViewModel.showError("Torrent timed out (no peers found after 95 seconds).")
                }
            }
            service.getMetadata(source.url) { files ->
                timeoutJob.cancel()
                if (files == null) {
                    playerViewModel.showError("Failed to fetch torrent metadata.")
                    return@getMetadata
                }
                val selectedEpisode = (uiState as? DetailsUiState.Success)?.selectedEpisode
                // Show the picker only for a genuinely ambiguous multi-file torrent that
                // nobody has resolved yet: not an addon pick, not an already-picked file,
                // and more than one *video* file (subtitles/samples don't count).
                val addonPicked = source.fileName != null || source.fileIndex != null
                val videoFiles = files.filter { it.isMedia }
                val needsPicker = playerViewModel.state.value.pickedTorrentFileIndex == null &&
                    !addonPicked && selectedEpisode == null && videoFiles.size > 1
                if (needsPicker) {
                    playerViewModel.showTorrentFilePicker(
                        (videoFiles.ifEmpty { files }).sortedByDescending { it.size }
                    )
                } else {
                    playerViewModel.setTorrentStream(true)
                    service.startStreaming(
                        fileIndex = playerViewModel.state.value.pickedTorrentFileIndex ?: -1,
                        season = selectedEpisode?.seasonNumber,
                        episode = selectedEpisode?.episodeNumber,
                        fileName = source.fileName
                    ) { streamUrl ->
                        playerViewModel.setTorrentLoading(false)
                        if (streamUrl.isNotEmpty()) {
                            playerViewModel.startPlayback(
                                StreamSource(serverName = source.serverName, url = streamUrl, isM3u8 = false)
                            )
                        } else {
                            playerViewModel.showError("Failed to load torrent. No peers found or timeout.")
                        }
                    }
                }
            }
        } else if (!source.url.contains("embed")) {
            playerViewModel.setTorrentStream(false)
            playerViewModel.startPlayback(source)
        }
    }

    // Controls auto-hide during playback.
    LaunchedEffect(showControls, state.isPlaying) {
        if (showControls && state.isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    val playerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playerFocus.requestFocus() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .focusable()
            .playerRemoteControls(
                viewModel = playerViewModel,
                controlsVisible = showControls,
                onShowControls = { showControls = true }
            )
            .playerTouchGestures(
                viewModel = playerViewModel,
                deviceControls = deviceControls,
                scope = scope,
                onToggleControls = { showControls = !showControls }
            )
    ) {
        if (state.isEmbed) {
            EmbedPlayer(
                url = state.selectedSource?.url ?: "",
                onBack = onBack,
                onError = playerViewModel::showError
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playerViewModel.player
                        useController = false
                        resizeMode = state.resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view -> view.resizeMode = state.resizeMode },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (state.notchSafe) Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                        else Modifier
                    )
            )
        }

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val success = uiState as? DetailsUiState.Success
                val movie = success?.movie
                val currentEpisode = success?.selectedEpisode

                PlayerTopBar(
                    title = movie?.title ?: "Loading...",
                    subtitle = currentEpisode?.let { "S${it.seasonNumber}:E${it.episodeNumber} - ${it.name}" },
                    quality = state.selectedSource?.quality,
                    isDownloading = isDownloading,
                    isEpisodesActive = showEpisodesSidebar,
                    onBack = onBack,
                    onDownload = {
                        state.selectedSource?.let { source ->
                            // Downloads go to app-scoped storage - no permission needed.
                            // Still ask for POST_NOTIFICATIONS so progress shows, but don't
                            // block on it.
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                            viewModel.downloadSource(source)
                        }
                    },
                    onToggleEpisodes = { showEpisodesSidebar = !showEpisodesSidebar }
                )

                PlayerTransport(
                    isPlaying = state.isPlaying,
                    position = state.positionMs,
                    duration = state.durationMs,
                    bufferedPosition = state.bufferedPositionMs,
                    isSubtitlesActive = state.subtitlesEnabled,
                    isAudioActive = state.selectedAudioTrack != null,
                    isSpeedActive = state.playbackSpeed != 1.0f,
                    isAspectActive = state.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    isFullscreen = true,
                    playbackSpeed = state.playbackSpeed,
                    onPlayPause = playerViewModel::togglePlayPause,
                    onSeek = playerViewModel::seekTo,
                    onRewind = { playerViewModel.seekBy(-10_000L) },
                    onForward = { playerViewModel.seekBy(10_000L) },
                    onToggleSubtitles = { showSubtitleMenu = true },
                    onToggleAudio = { showAudioMenu = true },
                    onToggleSpeed = { showSpeedMenu = true },
                    onToggleAspect = { showAspectMenu = true },
                    onToggleFullscreen = playerViewModel::toggleFillMode,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        if (state.isTorrentLoading || state.isBuffering) {
            PlayerLoadingOverlay(
                isMagnet = state.isMagnet,
                status = torrentStatus,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        state.errorMessage?.let { message ->
            PlaybackErrorDialog(
                message = message,
                onRetry = playerViewModel::retry,
                onChooseAnotherSource = {
                    playerViewModel.clearError()
                    onBack()
                },
                onDismiss = playerViewModel::clearError
            )
        }

        Box(modifier = Modifier.align(Alignment.Center)) {
            VolumeHud(
                volume = deviceControls.volume,
                isMuted = deviceControls.isMuted,
                visible = deviceControls.showVolumeHud
            )
        }
        Box(modifier = Modifier.align(Alignment.Center)) {
            BrightnessHud(
                brightness = deviceControls.brightness,
                visible = deviceControls.showBrightnessHud
            )
        }
        audioHudText?.let { text ->
            Box(modifier = Modifier.align(Alignment.Center)) {
                AudioHud(text = text, visible = true)
            }
        }

        if (state.showSkipIntro) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 120.dp, end = 24.dp)) {
                SkipButton(
                    label = "Skip Intro",
                    onSkip = playerViewModel::skipIntro,
                    onDismiss = playerViewModel::dismissSkipIntro
                )
            }
        }

        if (state.showAutoNext) {
            val success = uiState as? DetailsUiState.Success
            val allEpisodes = success?.movie?.seasons?.flatMap { it.episodes }.orEmpty()
            val currentIndex = allEpisodes.indexOfFirst { it.id == success?.selectedEpisode?.id }
            if (currentIndex != -1 && currentIndex < allEpisodes.size - 1) {
                AutoNextOverlay(
                    nextEpisodeName = allEpisodes[currentIndex + 1].name,
                    secondsLeft = state.autoNextSeconds,
                    onCancel = playerViewModel::cancelAutoNext,
                    onPlayNow = { viewModel.selectEpisode(allEpisodes[currentIndex + 1]) }
                )
            }
        }

        if (showEpisodesSidebar) {
            (uiState as? DetailsUiState.Success)?.let { success ->
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
        }

        if (showSubtitleMenu) {
            (uiState as? DetailsUiState.Success)?.let { success ->
                SubtitleMenu(
                    subtitles = success.subtitles,
                    selectedUrl = state.selectedSubtitleUrl,
                    offsetMs = state.subtitleOffsetMs,
                    onOffsetChange = playerViewModel::setSubtitleOffset,
                    subtitlesEnabled = state.subtitlesEnabled,
                    onUseEmbedded = playerViewModel::useEmbeddedSubtitles,
                    onSubtitleSelect = playerViewModel::selectSubtitle,
                    onDisable = playerViewModel::disableSubtitles,
                    onDismiss = { showSubtitleMenu = false }
                )
            }
        }

        if (showAudioMenu) {
            AudioMenu(
                tracks = state.availableAudioTracks,
                selectedTrack = state.selectedAudioTrack,
                onTrackSelect = playerViewModel::selectAudioTrack,
                onDismiss = { showAudioMenu = false }
            )
        }

        if (showSpeedMenu) {
            SpeedMenu(
                currentSpeed = state.playbackSpeed,
                onSpeedSelect = playerViewModel::setSpeed,
                onDismiss = { showSpeedMenu = false }
            )
        }

        if (showAspectMenu) {
            AspectMenu(
                currentMode = state.resizeMode,
                onModeSelect = playerViewModel::setResizeMode,
                avoidCutout = state.notchSafe,
                onToggleAvoidCutout = { playerViewModel.setNotchSafe(it) },
                onDismiss = { showAspectMenu = false }
            )
        }

        val torrentFiles = state.torrentFiles
        if (state.showTorrentFileSheet && torrentFiles != null) {
            TorrentFileSheet(
                onDismiss = playerViewModel::dismissTorrentFilePicker,
                files = torrentFiles,
                onFileSelect = { file ->
                    playerViewModel.onTorrentFilePicked(file.index)
                    torrentService?.startStreaming(fileIndex = file.index) { streamUrl ->
                        playerViewModel.setTorrentLoading(false)
                        if (streamUrl.isNotEmpty()) {
                            playerViewModel.startPlayback(
                                StreamSource(serverName = file.name, url = streamUrl, isM3u8 = false)
                            )
                        } else {
                            playerViewModel.showError("Failed to load torrent. No peers found or timeout.")
                        }
                    }
                }
            )
        }
    }
}
