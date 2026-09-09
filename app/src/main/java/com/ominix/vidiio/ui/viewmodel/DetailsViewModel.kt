package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.api.SubdlService
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.subtitles.SubdlSubtitle
import com.ominix.vidiio.data.model.toFavoriteMovie
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.FavoriteRepository
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.WatchProgressRepository
import com.ominix.vidiio.download.DownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

/**
 * The details screen.
 *
 * Everything about the title itself - details, episodes, sources, subtitles, watch
 * progress - lives in [MediaSession]. What is left here is what only the details screen
 * does: favourites, and enabling or disabling source providers.
 *
 * The player used to borrow this whole ViewModel; it owns its own [MediaSession] now.
 */
class DetailsViewModel(
    movieRepository: MovieRepository,
    private val favoriteRepository: FavoriteRepository,
    downloadRepository: DownloadRepository,
    downloadManager: DownloadManager,
    subdlService: SubdlService,
    private val settingsRepository: SettingsRepository,
    watchProgressRepository: WatchProgressRepository,
    movie: Movie,
    initialEpisodeId: String? = null
) : ViewModel() {

    private val session = MediaSession(
        scope = viewModelScope,
        movieRepository = movieRepository,
        downloadRepository = downloadRepository,
        downloadManager = downloadManager,
        subdlService = subdlService,
        settingsRepository = settingsRepository,
        watchProgressRepository = watchProgressRepository,
        initialMovie = movie,
        initialEpisodeId = initialEpisodeId
    )

    private val _isFavorite = MutableStateFlow(false)

    val enabledSources: StateFlow<Set<String>> = settingsRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val uiState: StateFlow<DetailsUiState> = combine(
        session.movie,
        session.streamSources,
        session.subtitles,
        session.selectedEpisode,
        combine(session.isSearchingSources, _isFavorite, session.error) { searching, favorite, error ->
            Triple(searching, favorite, error)
        }
    ) { resolvedMovie, sources, subtitles, episode, (searching, favorite, error) ->
        when {
            error != null -> DetailsUiState.Error(error)
            resolvedMovie == null -> DetailsUiState.Loading
            else -> DetailsUiState.Success(
                movie = resolvedMovie,
                streamSources = sources,
                subtitles = subtitles,
                isFavorite = favorite,
                selectedEpisode = episode,
                isSearchingSources = searching
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailsUiState.Loading)

    init {
        // Favourite state follows whichever movie the session resolved.
        session.movie
            .onEach { resolved ->
                if (resolved != null) {
                    favoriteRepository.isFavorite(resolved.id)
                        .onEach { _isFavorite.value = it }
                        .launchIn(viewModelScope)
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectEpisode(episode: Episode) = session.selectEpisode(episode)

    fun downloadSource(source: StreamSource) = session.downloadSource(source)

    fun toggleFavorite() {
        val currentMovie = session.movie.value ?: return
        val currentFavorite = _isFavorite.value
        viewModelScope.launch {
            if (currentFavorite) {
                favoriteRepository.removeFavorite(currentMovie.toFavoriteMovie())
            } else {
                favoriteRepository.addFavorite(currentMovie.toFavoriteMovie())
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
}
