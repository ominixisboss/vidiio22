package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import com.ominix.vidiio.data.api.SubdlService
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.subtitles.SubtitleTrack
import com.ominix.vidiio.data.model.subtitles.toSubtitleTracks
import com.ominix.vidiio.data.stremio.AddonManager
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.WatchProgressRepository
import com.ominix.vidiio.download.DownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
    private val addonManager: AddonManager,
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

    private val _subtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitles: StateFlow<List<SubtitleTrack>> = _subtitles.asStateFlow()

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
        observeSubtitles()
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
                val details = movieRepository.getMovieDetails(initialMovie)

                // Resolve the IMDb id onto the Movie itself. MovieRepository resolved it
                // for the scraper fan-out but only onto a local copy, so the Movie the
                // rest of the app holds kept imdbId = null. Subtitle addons key on IMDb
                // ids, so without this every TMDB-sourced title - which is nearly all of
                // them - produced no id to query with, and no subtitles at all.
                val fullMovie = if (details.imdbId == null) {
                    details.copy(imdbId = movieRepository.getImdbId(details))
                } else {
                    details
                }
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

                // Scraping is kicked off by observeSettings() once _movie is set.
                // Subtitles follow the selected episode, so they are collected separately.
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

    /**
     * Subtitles for whatever is currently selected, refreshed when the episode changes.
     *
     * Providers run concurrently and independently: one failing or timing out must not
     * cost the user the results from the others, which is why each is wrapped rather than
     * letting a failure propagate out of the awaitAll.
     */
    private fun observeSubtitles() {
        scope.launch {
            combine(
                _movie.filterNotNull(),
                _selectedEpisode,
                settingsRepository.subtitleLanguagesFlow,
            ) { movie, episode, languages -> Triple(movie, episode, languages) }
                .distinctUntilChanged()
                .collectLatest { (movie, episode, languages) ->
                    _subtitles.value = emptyList()
                    val fromSubdl = scope.async { fetchSubdlSubtitles(movie, episode, languages) }
                    val fromAddons = scope.async { fetchAddonSubtitles(movie, episode) }
                    val all = (fromSubdl.await() + fromAddons.await()).distinctBy { it.url }

                    // Preferred languages first, in the order the user listed them, then
                    // everything else - so a French-first user does not have to scroll
                    // past forty English entries.
                    _subtitles.value = all.sortedWith(
                        compareBy(
                            { languagePriority(it.language, languages) },
                            { it.source },
                            { it.label },
                        )
                    )
                }
        }
    }

    /** Index in the preferred list, or a value past the end for anything unlisted. */
    private fun languagePriority(language: String, preferred: List<String>): Int {
        val code = language.trim().lowercase()
        val idx = preferred.indexOfFirst { p -> code == p || code.startsWith(p) || p.startsWith(code) }
        return if (idx >= 0) idx else preferred.size
    }

    private suspend fun fetchAddonSubtitles(movie: Movie, episode: Episode?): List<SubtitleTrack> {
        val stremioType = if (movie.type == MovieType.MOVIE) "movie" else "series"
        // Same id scheme the stream lookup uses: the addon-native id where the item came
        // from an addon, plus the IMDb-keyed id the large public addons expect.
        val nativeId = when {
            movie.source != "stremio" -> null
            movie.type == MovieType.TV_SHOW && episode != null -> episode.id
            else -> movie.id
        }
        val imdb = movie.imdbId ?: movie.id.takeIf { it.startsWith("tt") }
        val imdbId = imdb?.let { tt ->
            if (movie.type == MovieType.TV_SHOW && episode != null) {
                "$tt:${episode.seasonNumber}:${episode.episodeNumber}"
            } else {
                tt
            }
        }

        return listOfNotNull(nativeId, imdbId).distinct().flatMap { id ->
            try {
                withTimeoutOrNull(SUBTITLE_TIMEOUT_MS) {
                    addonManager.getSubtitles(stremioType, id)
                }.orEmpty()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "MediaSession.fetchAddonSubtitles() failed for $id", e)
                emptyList()
            }
        }
    }

    private suspend fun fetchSubdlSubtitles(
        movie: Movie,
        episode: Episode?,
        languages: List<String>,
    ): List<SubtitleTrack> {
        val apiKey = settingsRepository.subdlApiKeyFlow.first()?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return try {
            val imdbId = if (movie.id.startsWith("tt")) movie.id else movie.imdbId
            val tmdbId = movie.id.toIntOrNull()
            val isSeries = movie.type == MovieType.TV_SHOW

            val response = withTimeoutOrNull(SUBTITLE_TIMEOUT_MS) {
                subdlService.searchSubtitles(
                    apiKey = apiKey,
                    tmdbId = if (imdbId == null) tmdbId else null,
                    imdbId = imdbId,
                    // SubDL expects upper-case codes ("EN,FR").
                    languages = languages.joinToString(",") { it.uppercase() },
                    type = if (isSeries) "tv" else "movie",
                    // Without these a series query returns results for the whole show,
                    // so every episode offered the same season-agnostic subtitles.
                    seasonNumber = episode?.seasonNumber.takeIf { isSeries },
                    episodeNumber = episode?.episodeNumber.takeIf { isSeries },
                )
            }

            val tracks = response?.subtitles.orEmpty().flatMap { it.toSubtitleTracks() }
            if (response != null && response.subtitles.orEmpty().isNotEmpty() && tracks.isEmpty()) {
                Log.w(TAG, "SubDL returned results but none were playable (packed .zip only)")
            }
            tracks
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "MediaSession.fetchSubdlSubtitles() failed", e)
            emptyList()
        }
    }

    private companion object {
        /** Overall wall-clock budget for one source search across all scrapers + Stremio. */
        const val SCRAPE_TIMEOUT_MS = 45_000L

        /** Per-provider budget for a subtitle lookup. */
        const val SUBTITLE_TIMEOUT_MS = 15_000L
        const val TAG = "MediaSession"
    }
}
