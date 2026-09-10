package com.ominix.vidiio.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.ominix.vidiio.VidiioApplication
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.subtitles.SubtitleTrack
import com.ominix.vidiio.data.stremio.AddonManager
import com.ominix.vidiio.data.api.SubdlService
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.SubtitleEdge
import com.ominix.vidiio.data.repository.SubtitleStyle
import com.ominix.vidiio.data.repository.WatchProgressRepository
import com.ominix.vidiio.download.DownloadManager
import com.ominix.vidiio.torrent.TorrentFileInfo
import com.ominix.vidiio.ui.viewmodel.MediaSession
import kotlinx.coroutines.flow.Flow
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything PlayerScreen renders. One object so the screen has a single subscription. */
data class PlayerUiState(
    val selectedSource: StreamSource? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val errorMessage: String? = null,

    val subtitlesEnabled: Boolean = true,
    val selectedSubtitleUrl: String? = null,
    val subtitleOffsetMs: Long = 0L,

    val availableAudioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioTrack: AudioTrackInfo? = null,

    val playbackSpeed: Float = 1.0f,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val notchSafe: Boolean = false,

    val showSkipIntro: Boolean = false,
    val showSkipOutro: Boolean = false,
    val showAutoNext: Boolean = false,
    val autoNextSeconds: Int = 5,

    val isTorrentStream: Boolean = false,
    val isTorrentLoading: Boolean = false,
    val torrentFiles: List<TorrentFileInfo>? = null,
    val showTorrentFileSheet: Boolean = false,
    val pickedTorrentFileIndex: Int? = null,
) {
    val isEmbed: Boolean
        get() = selectedSource?.url?.let { it.contains("embed") || it.contains("vidsrc") } == true

    val isMagnet: Boolean
        get() = selectedSource?.url?.startsWith("magnet:") == true
}

/**
 * Owns the ExoPlayer instance and the whole playback session.
 *
 * This used to live in `PlayerScreen` as ~30 `remember`ed variables around a `remember`ed
 * ExoPlayer, which meant the player and everything about the session were destroyed and
 * rebuilt on any configuration change the Activity does not declare in `configChanges` -
 * a full re-buffer, and for a torrent stream that is 10-30 seconds of finding peers
 * again. Held here, the session survives.
 *
 * The screen keeps what is genuinely view state (which menu is open, whether the controls
 * are showing) and the Android Service binding, which is tied to its context.
 */
@UnstableApi
class PlayerViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    movieRepository: MovieRepository,
    downloadRepository: DownloadRepository,
    downloadManager: DownloadManager,
    subdlService: SubdlService,
    watchProgressRepository: WatchProgressRepository,
    addonManager: AddonManager,
    movie: Movie,
    initialEpisodeId: String? = null,
) : AndroidViewModel(application) {

    /**
     * The title being played: details, episodes, sources, subtitles, watch progress.
     *
     * The player used to reach into DetailsViewModel for all of this, which meant
     * PlayerScreen took two ViewModels that had to be keyed in lockstep and carried
     * favourites and source-toggle state it never used.
     */
    private val session = MediaSession(
        scope = viewModelScope,
        movieRepository = movieRepository,
        downloadRepository = downloadRepository,
        downloadManager = downloadManager,
        subdlService = subdlService,
        settingsRepository = settingsRepository,
        watchProgressRepository = watchProgressRepository,
        addonManager = addonManager,
        initialMovie = movie,
        initialEpisodeId = initialEpisodeId
    )

    val movie: StateFlow<Movie?> = session.movie
    val selectedEpisode: StateFlow<Episode?> = session.selectedEpisode
    val subtitles: StateFlow<List<SubtitleTrack>> = session.subtitles

    /** User's subtitle appearance, applied to the player's SubtitleView by the screen. */
    val subtitleStyle: StateFlow<SubtitleStyle> = settingsRepository.subtitleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleStyle())

    fun selectEpisode(episode: Episode) = session.selectEpisode(episode)
    fun downloadSource(source: StreamSource) = session.downloadSource(source)
    fun getDownloadStatus(url: String): Flow<DownloadStatus?> = session.getDownloadStatus(url)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Resume point from Continue Watching. Applied once, when the media is ready. */
    private var resumePositionMs: Long = 0L
    private var didResume = false

    /** Counts the silent re-kicks described in [Player.Listener.onPlayerError]. */
    private var torrentPrepareRetries = 0

    /** Bumped to re-run the screen's source-preparation effect. */
    private val _retryTrigger = MutableStateFlow(0)
    val retryTrigger: StateFlow<Int> = _retryTrigger.asStateFlow()

    private var pollJob: Job? = null
    private var skipIntroDismissJob: Job? = null
    private var isIntroDismissed = false
    private var audioHudJob: Job? = null

    private val _audioHudText = MutableStateFlow<String?>(null)
    val audioHudText: StateFlow<String?> = _audioHudText.asStateFlow()

    // OkHttp for http(s); DefaultDataSource delegates file:// / content:// (offline
    // downloads) to FileDataSource / ContentDataSource.
    private val httpDataSourceFactory = OkHttpDataSource.Factory(
        (application as VidiioApplication).playbackHttpClient
    )

    private val dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // A fresh torrent often 'Source error's for the first 10-30s while TorrServer
            // finds peers and buffers. Silently re-kick the stream a couple of times (the
            // picked file is remembered) before giving up.
            if (_state.value.isTorrentStream && torrentPrepareRetries < 3) {
                torrentPrepareRetries++
                _state.update { it.copy(isTorrentLoading = true) }
                viewModelScope.launch {
                    delay(4000)
                    _retryTrigger.update { n -> n + 1 }
                }
            } else {
                _state.update { it.copy(errorMessage = "Playback Error: ${error.localizedMessage}") }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val tracksList = mutableListOf<AudioTrackInfo>()
            tracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        tracksList.add(
                            AudioTrackInfo(
                                name = format.label ?: format.language ?: "Track ${tracksList.size + 1}",
                                groupIndex = groupIndex,
                                trackIndex = i,
                                format = format
                            )
                        )
                    }
                }
            }
            _state.update { it.copy(availableAudioTracks = tracksList) }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _state.update { it.copy(isPlaying = playing) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        }
    }

    /**
     * Built eagerly, not `by lazy`: the initialiser has to start the polling coroutine, and
     * on Dispatchers.Main.immediate that body can run before the lazy field is assigned,
     * re-entering a half-initialised delegate.
     */
    val player: ExoPlayer = run {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60000, // minBufferMs
                120000, // maxBufferMs
                10000, // bufferForPlaybackMs
                15000 // bufferForPlaybackAfterRebufferMs
            )
            .build()

        // buildWithAssSupport installs a libass-backed ASS/SSA subtitle renderer
        // (styled positioning/fonts/colours). CUES mode pre-renders to bitmap cues the
        // existing SubtitleView draws — no extra overlay view to wire up.
        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .buildWithAssSupport(
                context = application,
                renderType = AssRenderType.CUES,
                dataSourceFactory = dataSourceFactory
            )
    }

    private var notchSafeSeeded = false

    init {
        player.addListener(playerListener)
        startPolling()

        viewModelScope.launch {
            session.resumePositionMs.collect { resumePositionMs = it }
        }
        viewModelScope.launch {
            session.streamSources.collect { sources ->
                selectSourceIfNone(sources.firstOrNull())
            }
        }

        // Seed the cutout preference once; the Aspect menu can then flip it live for the
        // session without writing back until the user asks.
        viewModelScope.launch {
            val avoid = settingsRepository.avoidCameraCutoutFlow.first()
            if (!notchSafeSeeded) {
                notchSafeSeeded = true
                _state.update { it.copy(notchSafe = avoid) }
            }
        }
    }

    // ── Position polling, resume, skip/auto-next ─────────────────────────────

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                val position = player.currentPosition
                val duration = if (player.duration > 0) player.duration else 0L

                // Resume from saved Continue Watching position, once, when ready.
                if (!didResume && resumePositionMs > 3000L && duration > 0L &&
                    player.playbackState == Player.STATE_READY
                ) {
                    player.seekTo(resumePositionMs)
                    didResume = true
                }

                val playing = player.isPlaying

                // Persist progress every ~10s of playback.
                if (playing && duration > 0L && ++tick % 20 == 0) {
                    session.saveWatchProgress(position, duration)
                }

                _state.update { s ->
                    var next = s.copy(
                        positionMs = position,
                        durationMs = duration,
                        bufferedPositionMs = player.bufferedPosition
                    )
                    if (duration > 0) {
                        val inIntroRange = position in 5000L..90000L
                        if (!inIntroRange) isIntroDismissed = false

                        val remaining = duration - position
                        next = next.copy(
                            showSkipIntro = inIntroRange && !isIntroDismissed,
                            showSkipOutro = remaining < 120000L && duration > 120000L,
                            showAutoNext = when {
                                remaining in 1L..5000L && playing -> true
                                remaining > 5000L -> false
                                else -> next.showAutoNext
                            },
                            autoNextSeconds = if (remaining in 1L..5000L && playing) {
                                (remaining / 1000).toInt() + 1
                            } else {
                                next.autoNextSeconds
                            }
                        )
                    }
                    next
                }

                // Auto-dismiss the Skip Intro prompt after 7s of being visible.
                if (_state.value.showSkipIntro && skipIntroDismissJob?.isActive != true) {
                    skipIntroDismissJob = viewModelScope.launch {
                        delay(7000)
                        dismissSkipIntro()
                    }
                }

                delay(500)
            }
        }
    }

    // ── Source selection and playback ────────────────────────────────────────

    /** Picks the first source once the details load, if the caller gave us none. */
    private fun selectSourceIfNone(source: StreamSource?) {
        if (_state.value.selectedSource == null && source != null) selectSource(source)
    }

    fun selectSource(source: StreamSource) {
        // A new source is a new torrent session: forget the picked file and retry count.
        _state.update {
            it.copy(selectedSource = source, pickedTorrentFileIndex = null)
        }
        torrentPrepareRetries = 0
    }

    /** Starts playback of a directly playable URL. */
    fun startPlayback(source: StreamSource) {
        player.stop()
        player.clearMediaItems()

        // Many streaming hosts 403 without the scraper's Referer/Origin/UA headers.
        // ExoPlayer carries these on the HTTP data source, not the MediaItem.
        val requestHeaders = buildMap {
            put(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            source.headers?.let { putAll(it) }
        }
        httpDataSourceFactory.setDefaultRequestProperties(requestHeaders)

        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(if (source.isM3u8) MimeTypes.APPLICATION_M3U8 else null)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    // ── Transport ────────────────────────────────────────────────────────────

    fun togglePlayPause() = if (player.isPlaying) player.pause() else player.play()
    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun seekBy(deltaMs: Long) {
        val target = player.currentPosition + deltaMs
        val max = player.duration.coerceAtLeast(0L)
        player.seekTo(target.coerceIn(0L, if (max > 0) max else target.coerceAtLeast(0L)))
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setResizeMode(mode: Int) = _state.update { it.copy(resizeMode = mode) }

    /** The player is always immersive; the fullscreen button is a quick fill/fit toggle. */
    fun toggleFillMode() = _state.update {
        it.copy(
            resizeMode = if (it.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        )
    }

    fun setNotchSafe(enabled: Boolean, persist: Boolean = true) {
        _state.update { it.copy(notchSafe = enabled) }
        if (persist) viewModelScope.launch { settingsRepository.setAvoidCameraCutout(enabled) }
    }

    // ── Subtitles ────────────────────────────────────────────────────────────

    private fun setTextTrackDisabled(disabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled).build()
    }

    /** Re-enable the text renderer only - no setMediaItem, so no re-buffer. */
    fun useEmbeddedSubtitles() {
        _state.update { it.copy(selectedSubtitleUrl = null, subtitlesEnabled = true) }
        setTextTrackDisabled(false)
    }

    fun selectSubtitle(subtitle: SubtitleTrack) {
        _state.update { it.copy(selectedSubtitleUrl = subtitle.url, subtitlesEnabled = true) }
        setTextTrackDisabled(false)

        val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setMimeType(subtitleMimeType(subtitle.url))
            .setLanguage(subtitle.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val currentMediaItem = player.currentMediaItem ?: return
        val updatedItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subConfig))
            .build()
        player.setMediaItem(updatedItem, player.currentPosition)
    }

    /**
     * Turn off every subtitle track by disabling the text renderer. No setMediaItem here -
     * that would re-prepare and re-buffer the stream.
     */
    fun disableSubtitles() {
        _state.update { it.copy(selectedSubtitleUrl = null, subtitlesEnabled = false) }
        setTextTrackDisabled(true)
    }

    fun setSubtitleOffset(offsetMs: Long) = _state.update { it.copy(subtitleOffsetMs = offsetMs) }

    /**
     * Guesses the subtitle format from the URL, defaulting to SubRip.
     *
     * This used to be hardcoded to TEXT_VTT, which silently broke every provider that
     * serves anything else - OpenSubtitles v3, for one, returns application/x-subrip from
     * URLs with no file extension at all, so nothing it offered would ever have rendered.
     * SubRip is the right default: it is what the subtitle addons overwhelmingly serve,
     * and it is what an extensionless URL almost always turns out to be.
     */
    private fun subtitleMimeType(url: String): String {
        // Only look at the last path segment - hosts have dots in them too, so taking the
        // extension off the whole URL finds ".io" in "subs5.strem.io".
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        val extension = if (lastSegment.contains('.')) {
            lastSegment.substringAfterLast('.').lowercase()
        } else {
            ""
        }
        return when (extension) {
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    // ── Audio ────────────────────────────────────────────────────────────────

    fun selectAudioTrack(track: AudioTrackInfo) {
        _state.update { it.copy(selectedAudioTrack = track) }

        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()

        _audioHudText.value = track.name
        audioHudJob?.cancel()
        audioHudJob = viewModelScope.launch {
            delay(2000)
            _audioHudText.value = null
        }
    }

    // ── Skip / auto-next ─────────────────────────────────────────────────────

    fun skipIntro() {
        player.seekTo(90000L)
        dismissSkipIntro()
    }

    fun dismissSkipIntro() {
        isIntroDismissed = true
        _state.update { it.copy(showSkipIntro = false) }
    }

    fun cancelAutoNext() = _state.update { it.copy(showAutoNext = false) }

    // ── Torrent session ──────────────────────────────────────────────────────

    fun setTorrentLoading(loading: Boolean) = _state.update { it.copy(isTorrentLoading = loading) }
    fun setTorrentStream(isTorrent: Boolean) = _state.update { it.copy(isTorrentStream = isTorrent) }

    fun showTorrentFilePicker(files: List<TorrentFileInfo>) = _state.update {
        it.copy(torrentFiles = files, showTorrentFileSheet = true, isTorrentLoading = false)
    }

    fun dismissTorrentFilePicker() = _state.update { it.copy(showTorrentFileSheet = false) }

    fun onTorrentFilePicked(index: Int) = _state.update {
        it.copy(pickedTorrentFileIndex = index, showTorrentFileSheet = false,
            isTorrentLoading = true, isTorrentStream = true)
    }

    // ── Errors ───────────────────────────────────────────────────────────────

    fun showError(message: String) = _state.update { it.copy(errorMessage = message, isTorrentLoading = false) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun retry() {
        _state.update { it.copy(errorMessage = null, isTorrentLoading = false) }
        _retryTrigger.update { it + 1 }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { session.saveWatchProgress(player.currentPosition, player.duration) }
        pollJob?.cancel()
        player.removeListener(playerListener)
        player.stop()
        player.release()
    }
}
