package com.ominix.vidiio.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface VidiioRoute : NavKey {
    @Serializable
    data object Splash : VidiioRoute

    @Serializable
    data object Home : VidiioRoute {
        val icon = Icons.Rounded.Home
        val label = "Home"
    }

    @Serializable
    data object Movies : VidiioRoute {
        val icon = Icons.Rounded.Movie
        val label = "Movies"
    }

    @Serializable
    data object TvShows : VidiioRoute {
        val icon = Icons.Rounded.Tv
        val label = "TV Shows"
    }

    /** What is airing on a given day, with a date strip. */
    @Serializable
    data object TvGuide : VidiioRoute

    /** A studio's best films (Marvel, Pixar, ...), opened from the Top MovieLists row on Home. */
    @Serializable
    data class Collection(val title: String, val studioId: Int) : VidiioRoute

    /** The ranked Top 100 Halloween movies; opened from the Movies page. */
    @Serializable
    data object Halloween : VidiioRoute

    @Serializable
    data object Anime : VidiioRoute {
        val icon = Icons.Rounded.AutoAwesome
        val label = "Anime"
    }

    @Serializable
    data class Search(val query: String = "") : VidiioRoute {
        @kotlinx.serialization.Transient
        val icon = Icons.Rounded.Search
        val label = "Search"
    }

    /** Phone-only hub for My List, Downloads and Settings, which do not fit the bottom bar. */
    @Serializable
    data object Library : VidiioRoute {
        val icon = Icons.Rounded.VideoLibrary
        val label = "Library"
    }

    @Serializable
    data object Favorites : VidiioRoute {
        val icon = Icons.Rounded.Favorite
        val label = "Favorites"
    }

    @Serializable
    data object Downloads : VidiioRoute {
        val icon = Icons.Rounded.Download
        val label = "Downloads"
    }

    @Serializable
    data object Settings : VidiioRoute {
        val icon = Icons.Rounded.Settings
        val label = "Settings"
    }

    @Serializable
    data class Details(
        val movie: com.ominix.vidiio.data.model.Movie,
        /** Set by the TV Guide: open straight into this episode and start the best stream. */
        val autoPlaySeason: Int? = null,
        val autoPlayEpisode: Int? = null,
    ) : VidiioRoute

    @Serializable
    data class Player(
        val movie: com.ominix.vidiio.data.model.Movie,
        val episodeId: String? = null,
        val source: com.ominix.vidiio.data.model.StreamSource? = null
    ) : VidiioRoute
}
