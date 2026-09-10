package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ominix.vidiio.VidiioApplication
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.player.PlayerViewModel

/**
 * Factories for every screen's ViewModel.
 *
 * These replace a single `VidiioViewModelFactory` that took ten nullable constructor
 * parameters and threw `IllegalArgumentException` at runtime for whichever ones the
 * requested ViewModel happened to need. Two problems with that shape: every mis-wiring
 * was a crash instead of a compile error, and its `isAssignableFrom` dispatch would have
 * matched the wrong branch for any ViewModel subclass.
 *
 * Each function here names exactly what it needs, so a missing dependency will not build.
 *
 * If this file keeps growing, that is the signal to adopt Hilt - KSP is already
 * configured, so it is a small step from here.
 */
object VidiioViewModelFactories {

    fun home(app: VidiioApplication): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                app.movieRepository,
                app.watchProgressRepository,
                app.settingsRepository
            )
        }
    }

    fun search(app: VidiioApplication): ViewModelProvider.Factory = viewModelFactory {
        initializer { SearchViewModel(app.movieRepository) }
    }

    fun favorites(app: VidiioApplication): ViewModelProvider.Factory = viewModelFactory {
        initializer { FavoritesViewModel(app.favoriteRepository) }
    }

    fun downloads(app: VidiioApplication): ViewModelProvider.Factory = viewModelFactory {
        initializer { DownloadsViewModel(app.downloadRepository, app.downloadManager, app) }
    }

    fun settings(app: VidiioApplication): ViewModelProvider.Factory = viewModelFactory {
        // Application context, not the Activity's: this only reaches Coil's caches, and a
        // ViewModel outliving an Activity context is a leak.
        initializer { SettingsViewModel(app.settingsRepository, app) }
    }

    /**
     * Owns the ExoPlayer and the playback session. Keyed alongside [details] so the two
     * ViewModels for one screen share a lifetime.
     */
    @androidx.media3.common.util.UnstableApi
    fun player(app: VidiioApplication): ViewModelProvider.Factory = viewModelFactory {
        initializer { PlayerViewModel(app, app.settingsRepository) }
    }

    /**
     * Used by both the details screen and the player, which share [DetailsViewModel].
     * The caller keys the ViewModel by movie (and episode, for the player).
     */
    fun details(
        app: VidiioApplication,
        movie: Movie,
        initialEpisodeId: String? = null
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            DetailsViewModel(
                app.movieRepository,
                app.favoriteRepository,
                app.downloadRepository,
                app.downloadManager,
                app.subdlService,
                app.settingsRepository,
                app.watchProgressRepository,
                movie,
                initialEpisodeId
            )
        }
    }
}
