package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie

/**
 * The episode after [current], across season boundaries, in watching order. Specials (season 0)
 * are skipped unless that is where the viewer already is. Null at the end of the show.
 */
fun nextEpisodeAfter(movie: Movie, current: Episode?): Episode? {
    current ?: return null
    val inSpecials = current.seasonNumber == 0
    val ordered = movie.seasons
        .filter { inSpecials || it.seasonNumber > 0 }
        .sortedBy { it.seasonNumber }
        .flatMap { season -> season.episodes.sortedBy { it.episodeNumber } }
    val index = ordered.indexOfFirst { it.id == current.id }
    return if (index >= 0) ordered.getOrNull(index + 1) else null
}
