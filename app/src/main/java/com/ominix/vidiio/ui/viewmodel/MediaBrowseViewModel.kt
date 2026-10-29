package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.BrowseFilter
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseList
import com.ominix.vidiio.data.repository.BrowseSort
import com.ominix.vidiio.data.repository.Decade
import com.ominix.vidiio.data.repository.Mood
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.Provider
import com.ominix.vidiio.data.repository.StreamingService
import com.ominix.vidiio.data.repository.BrowseOptions
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
    /** Movies page only: the 100 best Halloween films, once loaded. */
    val halloween: List<Movie>? = null,
    /** What the viewer has narrowed the page to, and the matching titles (null while loading). */
    val filter: BrowseFilter = BrowseFilter(),
    val filterResults: List<Movie>? = null,
    /** Which ready-made list the page shows while no filter is set. */
    val list: BrowseList = BrowseList.TRENDING,
    /** A streaming service picked from the tile row, and its trending titles (null while loading). */
    val service: StreamingService? = null,
    val serviceResults: List<Movie>? = null,
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
    private var filterJob: Job? = null

    /** In-memory cache, so coming back to the page does not refetch it. */
    private val cache = HashMap<String, List<Movie>>()

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        loadJob?.cancel()
        if (force) {
            cache.clear()
            repository.invalidateCache()
        }
        _state.update { it.copy(isRefreshing = force, failures = 0) }
        loadJob = viewModelScope.launch {
            coroutineScope {
                launch { shelf(BrowseList.TRENDING) { s, v -> s.copy(trending = v) } }
                launch { shelf(BrowseList.POPULAR) { s, v -> s.copy(popular = v) } }
                launch { shelf(BrowseList.FRESH) { s, v -> s.copy(fresh = v) } }
                launch { shelf(BrowseList.SOON) { s, v -> s.copy(soon = v) } }
                launch { shelf(BrowseList.TOP_RATED) { s, v -> s.copy(topRated = v) } }
                if (kind == BrowseKind.MOVIES) launch { loadHalloween() }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
        if (!_state.value.filter.isEmpty) applyFilter(_state.value.filter, force = true)
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

    /** The Halloween list is optional furniture: if it fails the page just doesn't show it. */
    private suspend fun loadHalloween() {
        try {
            val films = cache.getOrPut("halloween") { repository.halloweenTop100() }
            _state.update { it.copy(halloween = films.takeIf { list -> list.isNotEmpty() }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Halloween list failed", e)
        }
    }

    // ── Filters ──────────────────────────────────────────────────────────────

    fun setGenre(genre: Genre?) = update { it.copy(genreId = genre?.id, mood = if (genre != null) null else it.mood) }

    fun setMood(mood: Mood?) = update { it.copy(mood = mood, genreId = if (mood != null) null else it.genreId) }

    fun setDecade(decade: Decade?) = update { it.copy(decade = decade) }

    fun setProvider(provider: Provider?) = update { it.copy(provider = provider) }

    /** Lists a streaming service's trending titles; picking it again (or any filter) goes back. */
    fun setService(service: StreamingService?) {
        filterJob?.cancel()
        _state.update { it.copy(service = service, serviceResults = null, filter = BrowseFilter(sort = it.filter.sort), filterResults = null) }
        if (service == null) return
        filterJob = viewModelScope.launch {
            val movies = runCatching { repository.streamingCatalog(kind, service.id) }.getOrDefault(emptyList())
            _state.update { if (it.service == service) it.copy(serviceResults = movies) else it }
        }
    }

    /** Picks a ready-made list (Trending, Top rated, ...) and drops any filters. */
    fun setList(list: BrowseList) {
        _state.update { it.copy(list = list) }
        clearFilters()
    }

    fun setSort(sort: BrowseSort) = update { it.copy(sort = sort) }

    fun clearFilters() = update { BrowseFilter(sort = it.sort) }

    private fun update(change: (BrowseFilter) -> BrowseFilter) {
        // Choosing a filter leaves any streaming-service list.
        if (_state.value.service != null) _state.update { it.copy(service = null, serviceResults = null) }
        applyFilter(change(_state.value.filter), force = false)
    }

    private fun applyFilter(filter: BrowseFilter, force: Boolean) {
        filterJob?.cancel()
        if (filter.isEmpty) {
            _state.update { it.copy(filter = filter, filterResults = null) }
            return
        }
        _state.update { it.copy(filter = filter, filterResults = null) }
        filterJob = viewModelScope.launch {
            val key = "filter-${filter.genreExpr}-${filter.decade?.startYear}-${filter.provider?.id}-${filter.sort}"
            try {
                val results = if (force) repository.browseFiltered(kind, filter).also { cache[key] = it }
                else cache.getOrPut(key) { repository.browseFiltered(kind, filter) }
                _state.update { if (it.filter == filter) it.copy(filterResults = results) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Filter load failed", e)
                _state.update { if (it.filter == filter) it.copy(filterResults = emptyList()) else it }
            }
        }
    }

    private companion object {
        const val TAG = "MediaBrowseViewModel"
    }
}
