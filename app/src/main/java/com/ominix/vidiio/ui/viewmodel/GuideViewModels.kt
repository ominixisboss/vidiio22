package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.guide.GuideEntry
import com.ominix.vidiio.data.guide.TvGuideService
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.repository.BrowseFilter
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseSort
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TvGuideState(
    val date: LocalDate = LocalDate.now(),
    /** null while loading. */
    val shows: List<GuideEntry>? = null,
    val failed: Boolean = false,
)

/** The TV Guide: which shows air on the picked day, and at what time. */
class TvGuideViewModel(
    private val service: TvGuideService,
    private val repository: MovieRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvGuideState())
    val state: StateFlow<TvGuideState> = _state.asStateFlow()

    private val cache = HashMap<LocalDate, List<GuideEntry>>()
    private var job: Job? = null

    init {
        select(LocalDate.now())
    }

    fun select(date: LocalDate) {
        job?.cancel()
        _state.update { it.copy(date = date, shows = cache[date], failed = false) }
        if (cache.containsKey(date)) return
        job = viewModelScope.launch {
            try {
                val shows = service.day(date)
                cache[date] = shows
                _state.update { if (it.date == date) it.copy(shows = shows) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("TvGuideViewModel", "Guide for $date failed", e)
                _state.update { if (it.date == date) it.copy(shows = emptyList(), failed = true) else it }
            }
        }
    }

    fun retry() {
        cache.remove(_state.value.date)
        select(_state.value.date)
    }

    /** The details-page title for a guide row: the best TMDB TV match for the show's name. */
    suspend fun resolve(entry: GuideEntry): Movie? {
        val tv = repository.search(entry.showName).filter { it.type == MovieType.TV_SHOW && it.posterUrl.isNotBlank() }
        return tv.firstOrNull { it.title.equals(entry.showName, ignoreCase = true) } ?: tv.firstOrNull()
    }
}

data class CollectionState(val films: List<Movie>? = null, val failed: Boolean = false)

/** A studio's films, best first. */
class CollectionViewModel(
    private val repository: MovieRepository,
    private val title: String,
    private val studioId: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectionState())
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val filter = BrowseFilter(provider = Provider(title, studioId), sort = BrowseSort.TOP_RATED)
                _state.value = CollectionState(films = repository.browseFiltered(BrowseKind.MOVIES, filter))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CollectionViewModel", "Collection $title failed", e)
                _state.value = CollectionState(films = emptyList(), failed = true)
            }
        }
    }
}
