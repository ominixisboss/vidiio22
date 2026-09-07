package com.example.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vidiio.data.model.Category
import com.example.vidiio.data.model.WatchProgress
import com.example.vidiio.data.repository.HomeStyle
import com.example.vidiio.data.repository.MovieRepository
import com.example.vidiio.data.repository.SettingsRepository
import com.example.vidiio.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val categories: List<Category>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val movieRepository: MovieRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val continueWatching: StateFlow<List<WatchProgress>> =
        watchProgressRepository.continueWatching
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeStyle: StateFlow<HomeStyle> = settingsRepository.homeStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStyle.VIDIIO)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val categories = movieRepository.getHomeCategories()
                _uiState.value = HomeUiState.Success(categories)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun removeContinueWatching(id: String) {
        viewModelScope.launch { watchProgressRepository.remove(id) }
    }
}
