package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.WatchProgressDao
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.WatchProgress
import kotlinx.coroutines.flow.Flow

class WatchProgressRepository(private val dao: WatchProgressDao) {

    /** Recently watched, newest first. Only entries that are actually mid-way through. */
    val continueWatching: Flow<List<WatchProgress>> = dao.getRecent()

    suspend fun get(id: String): WatchProgress? = dao.get(id)

    /**
     * Record playback position. Near the start (<30s) nothing is stored; near the end
     * (>92%) the entry is cleared so finished titles drop off the row.
     */
    suspend fun save(movie: Movie, episode: Episode?, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val fraction = positionMs.toFloat() / durationMs
        if (positionMs < 30_000L) return
        if (fraction > 0.92f) {
            dao.delete(movie.id)
            return
        }
        dao.upsert(
            WatchProgress(
                id = movie.id,
                title = movie.title,
                posterUrl = movie.posterUrl,
                backdropUrl = movie.backdropUrl,
                synopsis = movie.synopsis,
                year = movie.year,
                rating = movie.rating,
                type = movie.type.name,
                source = movie.source,
                imdbId = movie.imdbId,
                episodeId = episode?.id,
                episodeLabel = episode?.let { "S${it.seasonNumber}:E${it.episodeNumber}" },
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(id: String) = dao.delete(id)
}
