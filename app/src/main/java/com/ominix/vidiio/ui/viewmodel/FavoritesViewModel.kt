package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.toMovie
import com.ominix.vidiio.data.repository.FavoriteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(val movies: List<Movie>) : FavoritesUiState
    data class Error(val message: String) : FavoritesUiState
}

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    val uiState: StateFlow<FavoritesUiState> = favoriteRepository.favorites
        .map { favorites ->
            FavoritesUiState.Success(favorites.map { it.toMovie() })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FavoritesUiState.Loading
        )
}
