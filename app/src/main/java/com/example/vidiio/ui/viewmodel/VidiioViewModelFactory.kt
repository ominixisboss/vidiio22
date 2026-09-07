package com.example.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.repository.MovieRepository
import com.example.vidiio.data.repository.SettingsRepository
import com.example.vidiio.data.repository.FavoriteRepository
import com.example.vidiio.data.repository.DownloadRepository
import com.example.vidiio.data.repository.WatchProgressRepository
import com.example.vidiio.download.DownloadManager
import com.example.vidiio.data.api.SubdlService
import android.content.Context

class VidiioViewModelFactory(
    private val movieRepository: MovieRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val favoriteRepository: FavoriteRepository? = null,
    private val downloadRepository: DownloadRepository? = null,
    private val downloadManager: DownloadManager? = null,
    private val subdlService: SubdlService? = null,
    private val watchProgressRepository: WatchProgressRepository? = null,
    private val context: Context? = null,
    private val movie: Movie? = null,
    private val initialEpisodeId: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    movieRepository ?: throw IllegalArgumentException("MovieRepository is required"),
                    watchProgressRepository ?: throw IllegalArgumentException("WatchProgressRepository is required"),
                    settingsRepository ?: throw IllegalArgumentException("SettingsRepository is required")
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(movieRepository ?: throw IllegalArgumentException("MovieRepository is required")) as T
            }
            modelClass.isAssignableFrom(DetailsViewModel::class.java) -> {
                DetailsViewModel(
                    movieRepository ?: throw IllegalArgumentException("MovieRepository is required"),
                    favoriteRepository ?: throw IllegalArgumentException("FavoriteRepository is required"),
                    downloadRepository ?: throw IllegalArgumentException("DownloadRepository is required"),
                    downloadManager ?: throw IllegalArgumentException("DownloadManager is required"),
                    subdlService ?: throw IllegalArgumentException("SubdlService is required"),
                    settingsRepository ?: throw IllegalArgumentException("SettingsRepository is required"),
                    watchProgressRepository ?: throw IllegalArgumentException("WatchProgressRepository is required"),
                    movie ?: throw IllegalArgumentException("Movie is required"),
                    initialEpisodeId
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    settingsRepository ?: throw IllegalArgumentException("SettingsRepository is required"),
                    context ?: throw IllegalArgumentException("Context is required")
                ) as T
            }
            modelClass.isAssignableFrom(FavoritesViewModel::class.java) -> {
                FavoritesViewModel(favoriteRepository ?: throw IllegalArgumentException("FavoriteRepository is required")) as T
            }
            modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> {
                DownloadsViewModel(
                    downloadRepository ?: throw IllegalArgumentException("DownloadRepository is required"),
                    downloadManager ?: throw IllegalArgumentException("DownloadManager is required")
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
