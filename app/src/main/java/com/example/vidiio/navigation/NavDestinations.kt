package com.example.vidiio.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
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
    data object Search : VidiioRoute {
        val icon = Icons.Rounded.Search
        val label = "Search"
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
    data class Details(val movie: com.example.vidiio.data.model.Movie) : VidiioRoute

    @Serializable
    data class Player(
        val movie: com.example.vidiio.data.model.Movie,
        val episodeId: String? = null,
        val source: com.example.vidiio.data.model.StreamSource? = null
    ) : VidiioRoute
}
