package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.CancellationException

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<Movie>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(private val movieRepository: MovieRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun search() {
        val currentQuery = _query.value
        if (currentQuery.isBlank()) return

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val results = movieRepository.search(currentQuery)
                _uiState.value = SearchUiState.Success(results)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "SearchViewModel.search() failed", e)
                _uiState.value = SearchUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

private const val TAG = "SearchViewModel"