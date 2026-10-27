package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.tmdb.TMDBMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFiltersTest {
    private fun film(id: Int, avg: Double, votes: Int, poster: String? = "/p.jpg") = TMDBMovie(
        id = id, title = "Film $id", name = null, posterPath = poster, overview = null,
        voteAverage = avg, voteCount = votes, releaseDate = "2000-01-01", firstAirDate = null, mediaType = null
    )

    @Test fun `a few perfect scores do not beat a well-loved film`() {
        assertTrue(weightedRating(9.5, 12) < weightedRating(8.0, 12_000))
    }

    @Test fun `ranking puts the well-loved first, drops duplicates and posterless films`() {
        val ranked = rankHalloween(
            listOf(film(1, 9.5, 12), film(2, 8.0, 12_000), film(3, 7.0, 5_000), film(2, 8.0, 12_000), film(4, 9.9, 9_999, poster = null))
        )
        assertEquals(listOf(2, 3, 1), ranked.map { it.id })
    }

    @Test fun `the list is capped at 100`() {
        assertEquals(100, rankHalloween((1..240).map { film(it, 7.0, 1000 + it) }).size)
    }

    @Test fun `filters know when nothing is selected`() {
        assertTrue(BrowseFilter().isEmpty)
        assertFalse(BrowseFilter(decade = BrowseOptions.decades.first()).isEmpty)
    }

    @Test fun `a specific genre wins over a mood, and a mood is any-of`() {
        val mood = BrowseOptions.movieMoods.first { it.label == "Scary" }
        assertEquals("27", BrowseFilter(mood = mood).genreExpr)
        assertEquals("35", BrowseFilter(genreId = 35, mood = mood).genreExpr)
        assertEquals("35|10751|10749", BrowseFilter(mood = BrowseOptions.movieMoods.first()).genreExpr)
    }

    @Test fun `decade ends nine years after it starts`() = assertEquals(1999, Decade("1990s", 1990).endYear)
}
