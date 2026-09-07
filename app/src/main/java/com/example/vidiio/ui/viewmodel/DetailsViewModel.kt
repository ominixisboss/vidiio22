package com.example.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vidiio.data.api.SubdlService
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.Episode
import com.example.vidiio.data.model.StreamSource
import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.data.model.subtitles.SubdlSubtitle
import com.example.vidiio.data.model.toFavoriteMovie
import com.example.vidiio.data.repository.FavoriteRepository
import com.example.vidiio.data.repository.MovieRepository
import com.example.vidiio.data.repository.SettingsRepository
import com.example.vidiio.data.repository.DownloadRepository
import com.example.vidiio.data.repository.WatchProgressRepository
import com.example.vidiio.download.DownloadManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(
        val movie: Movie,
        val streamSources: List<StreamSource>,
        val subtitles: List<SubdlSubtitle> = emptyList(),
        val isFavorite: Boolean = false,
        val selectedEpisode: Episode? = null,
        val isSearchingSources: Boolean = true
    ) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}

class DetailsViewModel(
    private val movieRepository: MovieRepository,
    private val favoriteRepository: FavoriteRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val subdlService: SubdlService,
    private val settingsRepository: SettingsRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val movie: Movie,
    private val initialEpisodeId: String? = null
) : ViewModel() {

    /** Position (ms) to resume playback from, resolved from saved Continue Watching progress. */
    private val _resumePositionMs = MutableStateFlow(0L)
    val resumePositionMs: StateFlow<Long> = _resumePositionMs.asStateFlow()

    private val _movie = MutableStateFlow<Movie?>(null)
    private val _allFoundSources = MutableStateFlow<List<StreamSource>>(emptyList())
    private val _isSearchingSources = MutableStateFlow(true)
    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    private val _isFavorite = MutableStateFlow(false)
    private val _subtitles = MutableStateFlow<List<SubdlSubtitle>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)

    val enabledSources: StateFlow<Set<String>> = settingsRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var scrapingJob: Job? = null

    private companion object {
        /** Overall wall-clock budget for one source search across all scrapers + Stremio. */
        const val SCRAPE_TIMEOUT_MS = 45_000L
    }

    val uiState: StateFlow<DetailsUiState> = combine(
        combine(_movie, _isFavorite, _subtitles, _error) { movie, favorite, subs, error ->
            @Suppress("UNCHECKED_CAST")
            Triple(movie, favorite, subs) to error
        },
        combine(_allFoundSources, _isSearchingSources, _selectedEpisode, settingsRepository.sourcesFlow) { sources, searching, episode, enabled ->
            @Suppress("UNCHECKED_CAST")
            Triple(sources, searching, episode) to enabled
        }
    ) { meta, sourcesData ->
        val (movie, favorite, subs) = meta.first
        val error = meta.second
        val (sources, searching, episode) = sourcesData.first
        val enabled = sourcesData.second

        when {
            error != null -> DetailsUiState.Error(error)
            movie == null -> DetailsUiState.Loading
            else -> {
                val filteredSources = sources.filter { it.sourceId in enabled || it.sourceId == "stremio" }
                    .distinctBy { it.url }
                    .sortedWith(compareByDescending<StreamSource> { it.seeders ?: 0 }
                        .thenBy { it.quality != "4K" }
                        .thenBy { it.quality != "1080p" })
                
                DetailsUiState.Success(
                    movie = movie,
                    streamSources = filteredSources,
                    subtitles = subs,
                    isFavorite = favorite,
                    selectedEpisode = episode,
                    isSearchingSources = searching
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailsUiState.Loading)

    init {
        loadDetails()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            try {
                val fullMovie = movieRepository.getMovieDetails(movie)
                _movie.value = fullMovie
                
                val allEpisodes = fullMovie.seasons.flatMap { it.episodes }
                val selectedEpisode = if (initialEpisodeId != null) {
                    allEpisodes.find { it.id == initialEpisodeId } ?: fullMovie.seasons.firstOrNull()?.episodes?.firstOrNull()
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

                val subtitles = getSubtitles(fullMovie)
                _subtitles.value = subtitles

                launch {
                    favoriteRepository.isFavorite(fullMovie.id).collect { isFav ->
                        _isFavorite.value = isFav
                    }
                }
                // Scraping is kicked off by observeSettings() once _movie is set.
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    private fun startScraping(movie: Movie, episode: Episode?) {
        scrapingJob?.cancel()
        scrapingJob = viewModelScope.launch {
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

    fun toggleFavorite() {
        val currentMovie = _movie.value
        val currentFavorite = _isFavorite.value
        if (currentMovie != null) {
            viewModelScope.launch {
                if (currentFavorite) {
                    favoriteRepository.removeFavorite(currentMovie.toFavoriteMovie())
                } else {
                    favoriteRepository.addFavorite(currentMovie.toFavoriteMovie())
                }
            }
        }
    }

    fun toggleSource(sourceId: String) {
        viewModelScope.launch {
            val currentSources = settingsRepository.sourcesFlow.first().toMutableSet()
            if (currentSources.contains(sourceId)) {
                currentSources.remove(sourceId)
            } else {
                currentSources.add(sourceId)
            }
            settingsRepository.setSources(currentSources)
        }
    }

    fun downloadSource(source: StreamSource) {
        val movie = _movie.value ?: return
        val selectedEpisode = _selectedEpisode.value
        val title = if (selectedEpisode != null) {
            "${movie.title} - S${selectedEpisode.seasonNumber}E${selectedEpisode.episodeNumber}"
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
        viewModelScope.launch {
            runCatching {
                watchProgressRepository.save(current, _selectedEpisode.value, positionMs, durationMs)
            }
        }
    }

    fun getDownloadStatus(url: String): Flow<DownloadStatus?> {
        return downloadRepository.getDownloadByUrlFlow(url).map { it?.status }
    }

    private suspend fun getSubtitles(movie: Movie): List<SubdlSubtitle> {
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
            emptyList()
        }
    }
}
