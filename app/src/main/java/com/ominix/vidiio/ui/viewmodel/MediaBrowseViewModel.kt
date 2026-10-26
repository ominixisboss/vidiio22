package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseList
import com.ominix.vidiio.data.repository.MovieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a Movies or TV page has so far. Each shelf loads on its own and appears as soon as it is
 * ready; `null` means still loading.
 */
data class BrowseState(
    val trending: List<Movie>? = null,
    val popular: List<Movie>? = null,
    val fresh: List<Movie>? = null,
    val soon: List<Movie>? = null,
    val topRated: List<Movie>? = null,
    /** The selected genre and its results (null while loading). */
    val genre: Genre? = null,
    val genreResults: List<Movie>? = null,
    val isRefreshing: Boolean = false,
    val failures: Int = 0,
) {
    val isLoadingEverything: Boolean get() = trending == null && popular == null && failures == 0
    val hasNothing: Boolean get() = failures > 0 && trending.isNullOrEmpty() && popular.isNullOrEmpty()
}

/** A TMDB genre. The ids differ between movies and TV, so each page has its own list. */
data class Genre(val id: Int, val name: String)

/** Drives the Movies page or the TV page; [kind] picks which. */
class MediaBrowseViewModel(
    val kind: BrowseKind,
    private val repository: MovieRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var genreJob: Job? = null

    /** In-memory cache, so coming back to the page does not refetch it. */
    private val cache = HashMap<String, List<Movie>>()

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        loadJob?.cancel()
        if (force) cache.clear()
        _state.update { it.copy(isRefreshing = force, failures = 0) }
        loadJob = viewModelScope.launch {
            coroutineScope {
                launch { shelf(BrowseList.TRENDING) { s, v -> s.copy(trending = v) } }
                launch { shelf(BrowseList.POPULAR) { s, v -> s.copy(popular = v) } }
                launch { shelf(BrowseList.FRESH) { s, v -> s.copy(fresh = v) } }
                launch { shelf(BrowseList.SOON) { s, v -> s.copy(soon = v) } }
                launch { shelf(BrowseList.TOP_RATED) { s, v -> s.copy(topRated = v) } }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
        _state.value.genre?.let { selectGenre(it, force = true) }
    }

    /** Loads one shelf; a failure is counted, not thrown, so the other shelves still arrive. */
    private suspend fun shelf(list: BrowseList, apply: (BrowseState, List<Movie>) -> BrowseState) {
        try {
            val movies = cache.getOrPut("list-$list") { repository.browse(kind, list) }
            _state.update { apply(it, movies) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Browse shelf $list failed", e)
            _state.update { it.copy(failures = it.failures + 1) }
        }
    }

    fun selectGenre(genre: Genre?, force: Boolean = false) {
        genreJob?.cancel()
        if (genre == null || (genre == _state.value.genre && !force && _state.value.genreResults != null)) {
            _state.update { it.copy(genre = null, genreResults = null) }
            return
        }
        _state.update { it.copy(genre = genre, genreResults = null) }
        genreJob = viewModelScope.launch {
            try {
                val results = cache.getOrPut("genre-${genre.id}") { repository.browseGenre(kind, genre.id) }
                _state.update { if (it.genre == genre) it.copy(genreResults = results) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Genre load failed", e)
                _state.update { if (it.genre == genre) it.copy(genreResults = emptyList()) else it }
            }
        }
    }

    private companion object {
        const val TAG = "MediaBrowseViewModel"
    }
}
