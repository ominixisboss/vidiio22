package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.anime.AniMedia
import com.ominix.vidiio.data.anime.AniScheduleEntry
import com.ominix.vidiio.data.anime.AnimeRepository
import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Anime page has so far. Each section loads on its own and shows up as soon as it is
 * ready, so a slow calendar never holds back the shelves above it. `null` means "still loading".
 */
data class AnimeHomeState(
    val trending: List<AniMedia>? = null,
    val airing: List<AniMedia>? = null,
    val newEpisodes: List<AniScheduleEntry>? = null,
    val week: List<AniScheduleEntry>? = null,
    val popularSeason: List<AniMedia>? = null,
    val topRated: List<AniMedia>? = null,
    val allTime: List<AniMedia>? = null,
    val nextSeason: List<AniMedia>? = null,
    /** Anime addon shelves such as One Pace. */
    val addons: List<Category>? = null,
    /** Selected genre chip, and its results (null while loading). */
    val genre: String? = null,
    val genreResults: List<AniMedia>? = null,
    val isRefreshing: Boolean = false,
    /** How many of the sections failed to load. */
    val failures: Int = 0,
) {
    val isLoadingEverything: Boolean
        get() = trending == null && airing == null && popularSeason == null && failures == 0

    /** Nothing at all came back (offline, or AniList is down). */
    val hasNothing: Boolean
        get() = failures > 0 && trending.isNullOrEmpty() && airing.isNullOrEmpty() && popularSeason.isNullOrEmpty()
}

class AnimeViewModel(private val repository: AnimeRepository) : ViewModel() {

    private val _state = MutableStateFlow(AnimeHomeState())
    val state: StateFlow<AnimeHomeState> = _state.asStateFlow()

    /** AniList id of the title currently being matched to a playable entry, for a spinner. */
    private val _resolving = MutableStateFlow<Int?>(null)
    val resolving: StateFlow<Int?> = _resolving.asStateFlow()

    private var loadJob: Job? = null
    private var genreJob: Job? = null

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        loadJob?.cancel()
        _state.update { it.copy(isRefreshing = force, failures = 0) }
        loadJob = viewModelScope.launch {
            // Every section in parallel; each lands in the state the moment it is ready.
            coroutineScope {
                launch { section(force, repository::addonShelves) { s, v -> s.copy(addons = v) } }
                launch { section(force, repository::trending) { s, v -> s.copy(trending = v) } }
                launch { section(force, repository::airingNow) { s, v -> s.copy(airing = v) } }
                launch { section(force, repository::newEpisodes) { s, v -> s.copy(newEpisodes = v) } }
                launch { section(force, repository::popularThisSeason) { s, v -> s.copy(popularSeason = v) } }
                launch { section(force, repository::weekSchedule) { s, v -> s.copy(week = v) } }
                launch { section(force, repository::topRated) { s, v -> s.copy(topRated = v) } }
                launch { section(force, repository::allTimePopular) { s, v -> s.copy(allTime = v) } }
                launch { section(force, repository::nextSeason) { s, v -> s.copy(nextSeason = v) } }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
        // Re-run the chosen genre too, so a refresh really refreshes the page.
        _state.value.genre?.let { selectGenre(it, force = true) }
    }

    /** Loads one section; a failure is counted rather than thrown, so the others carry on. */
    private suspend fun <T : Any> section(
        force: Boolean,
        fetch: suspend (Boolean) -> T,
        apply: (AnimeHomeState, T) -> AnimeHomeState,
    ) {
        try {
            val value = fetch(force)
            _state.update { apply(it, value) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Anime section failed", e)
            _state.update { it.copy(failures = it.failures + 1) }
        }
    }

    fun selectGenre(genre: String?, force: Boolean = false) {
        genreJob?.cancel()
        if (genre == null || (genre == _state.value.genre && !force && _state.value.genreResults != null)) {
            _state.update { it.copy(genre = null, genreResults = null) }
            return
        }
        _state.update { it.copy(genre = genre, genreResults = null) }
        genreJob = viewModelScope.launch {
            try {
                val results = repository.byGenre(genre, force)
                _state.update { if (it.genre == genre) it.copy(genreResults = results) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Genre load failed", e)
                _state.update { if (it.genre == genre) it.copy(genreResults = emptyList()) else it }
            }
        }
    }

    /**
     * Opens a title: matches it to the TMDB entry the details page works with, then hands that
     * over. If nothing matches, [onNoMatch] gets the title so the caller can fall back to search.
     */
    fun open(media: AniMedia, onMatch: (Movie) -> Unit, onNoMatch: (String) -> Unit) {
        if (_resolving.value != null) return
        _resolving.value = media.id
        viewModelScope.launch {
            val movie = try {
                repository.resolve(media)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Anime resolve failed", e)
                null
            }
            _resolving.value = null
            if (movie != null) onMatch(movie) else onNoMatch(media.displayTitle)
        }
    }

    private companion object {
        const val TAG = "AnimeViewModel"
    }
}
