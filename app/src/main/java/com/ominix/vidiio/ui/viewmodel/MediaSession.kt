package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import com.ominix.vidiio.data.api.SubdlService
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.subtitles.SubdlSubtitle
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.WatchProgressRepository
import com.ominix.vidiio.download.DownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One title being watched: its details, which episode is selected, the stream sources
 * found for it, its subtitles, and its watch progress.
 *
 * This is deliberately NOT a ViewModel. Both the details screen and the player need all
 * of it, and previously that was expressed by the player borrowing DetailsViewModel -
 * which dragged in favourites and the source-enable toggles it has no use for, and meant
 * PlayerScreen took two ViewModels that had to be keyed in lockstep. Now each ViewModel
 * owns a MediaSession scoped to its own viewModelScope.
 *
 * Note that details and player are separate nav entries, so they already had separate
 * instances and scraped independently; that behaviour is unchanged.
 */
class MediaSession(
    private val scope: CoroutineScope,
    private val movieRepository: MovieRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val subdlService: SubdlService,
    private val settingsRepository: SettingsRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val initialMovie: Movie,
    private val initialEpisodeId: String? = null,
) {

    private val _movie = MutableStateFlow<Movie?>(null)
    val movie: StateFlow<Movie?> = _movie.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    val selectedEpisode: StateFlow<Episode?> = _selectedEpisode.asStateFlow()

    private val _allFoundSources = MutableStateFlow<List<StreamSource>>(emptyList())

    private val _isSearchingSources = MutableStateFlow(true)
    val isSearchingSources: StateFlow<Boolean> = _isSearchingSources.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubdlSubtitle>>(emptyList())
    val subtitles: StateFlow<List<SubdlSubtitle>> = _subtitles.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Position (ms) to resume playback from, from saved Continue Watching progress. */
    private val _resumePositionMs = MutableStateFlow(0L)
    val resumePositionMs: StateFlow<Long> = _resumePositionMs.asStateFlow()

    /**
     * Found sources, filtered to the enabled providers and ordered best-first.
     *
     * This filtering and sorting used to sit inside DetailsViewModel's uiState combine,
     * where the player could not reach it - so the player showed whatever order its own
     * copy happened to produce.
     */
    val streamSources: StateFlow<List<StreamSource>> =
        combine(_allFoundSources, settingsRepository.sourcesFlow) { sources, enabled ->
            sources
                .filter { it.sourceId in enabled || it.sourceId == "stremio" }
                .distinctBy { it.url }
                .sortedWith(
                    compareByDescending<StreamSource> { it.seeders ?: 0 }
                        .thenBy { it.quality != "4K" }
                        .thenBy { it.quality != "1080p" }
                )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var scrapingJob: Job? = null

    init {
        loadDetails()
        observeSettings()
    }

    private fun observeSettings() {
        scope.launch {
            // Single scrape trigger: (re)scrape when the movie first loads, the selected
            // episode changes, or the enabled-source set changes - and nothing else.
            // DataStore re-emits the same value on startup, so distinctUntilChanged is what
            // stops startScraping from cancelling itself in a loop.
            combine(
                _movie.filterNotNull(),
                _selectedEpisode,
                settingsRepository.sourcesFlow,
            ) { movie, episode, enabled -> Triple(movie, episode, enabled) }
                .distinctUntilChanged()
                .collectLatest { (movie, episode, _) ->
                    startScraping(movie, episode)
                }
        }
    }

    private fun loadDetails() {
        scope.launch {
            try {
                val fullMovie = movieRepository.getMovieDetails(initialMovie)
                _movie.value = fullMovie

                val allEpisodes = fullMovie.seasons.flatMap { it.episodes }
                val selectedEpisode = if (initialEpisodeId != null) {
                    allEpisodes.find { it.id == initialEpisodeId }
                        ?: fullMovie.seasons.firstOrNull()?.episodes?.firstOrNull()
                } else {
                    fullMovie.seasons.firstOrNull()?.episodes?.firstOrNull()
                }
                _selectedEpisode.value = selectedEpisode

                // Resume point for Continue Watching: only when the saved episode matches
                // (or it's a movie).
                runCatching {
                    val saved = watchProgressRepository.get(fullMovie.id)
                    if (saved != null && (saved.episodeId == null || saved.episodeId == selectedEpisode?.id)) {
                        _resumePositionMs.value = saved.positionMs
                    }
                }

                _subtitles.value = fetchSubtitles(fullMovie)
                // Scraping is kicked off by observeSettings() once _movie is set.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    private fun startScraping(movie: Movie, episode: Episode?) {
        scrapingJob?.cancel()
        scrapingJob = scope.launch {
            _isSearchingSources.value = true
            try {
                // Collect until every scraper has finished or the overall budget is hit.
                // The per-scraper timeout lives in MovieRepository; don't cut them off early
                // here — the crypto-heavy providers (Cinejoy, VidSrc, Stremio) need >12s.
                withTimeoutOrNull(SCRAPE_TIMEOUT_MS) {
                    movieRepository.getStreamSources(movie, episode)
                        .collect { source ->
                            _allFoundSources.update { (it + source).distinctBy { s -> s.url } }
                        }
                }
            } finally {
                _isSearchingSources.value = false
            }
        }
    }

    fun selectEpisode(episode: Episode) {
        _allFoundSources.value = emptyList()
        _selectedEpisode.value = episode
        // observeSettings() re-scrapes on the episode change.
    }

    fun downloadSource(source: StreamSource) {
        val movie = _movie.value ?: return
        val episode = _selectedEpisode.value
        val title = if (episode != null) {
            "${movie.title} - S${episode.seasonNumber}E${episode.episodeNumber}"
        } else {
            movie.title
        }
        downloadManager.enqueue(
            title = title,
            url = source.url,
            headers = source.headers,
            torrentFileIndex = source.fileIndex,
            torrentFileName = source.fileName
        )
    }

    /** Called periodically by the player to persist the Continue Watching position. */
    fun saveWatchProgress(positionMs: Long, durationMs: Long) {
        val current = _movie.value ?: return
        scope.launch {
            runCatching {
                watchProgressRepository.save(current, _selectedEpisode.value, positionMs, durationMs)
            }
        }
    }

    fun getDownloadStatus(url: String): Flow<DownloadStatus?> =
        downloadRepository.getDownloadByUrlFlow(url).map { it?.status }

    private suspend fun fetchSubtitles(movie: Movie): List<SubdlSubtitle> {
        val apiKey = settingsRepository.subdlApiKeyFlow.first() ?: return emptyList()
        return try {
            val imdbId = if (movie.id.startsWith("tt")) movie.id else null
            val tmdbId = movie.id.toIntOrNull()

            val response = subdlService.searchSubtitles(
                apiKey = apiKey,
                tmdbId = if (imdbId == null) tmdbId else null,
                imdbId = imdbId,
                languages = "en"
            )
            response.subtitles ?: emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "MediaSession.fetchSubtitles() failed", e)
            emptyList()
        }
    }

    private companion object {
        /** Overall wall-clock budget for one source search across all scrapers + Stremio. */
        const val SCRAPE_TIMEOUT_MS = 45_000L
        const val TAG = "MediaSession"
    }
}
