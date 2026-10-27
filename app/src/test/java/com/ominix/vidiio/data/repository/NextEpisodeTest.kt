package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {
    private fun ep(s: Int, e: Int) = Episode(id = "s${s}e$e", name = "E$e", episodeNumber = e, seasonNumber = s)
    private val show = Movie(
        id = "1", title = "Show", posterUrl = "", source = "tmdb", type = MovieType.TV_SHOW,
        seasons = listOf(
            Season(0, listOf(ep(0, 1))),
            Season(1, listOf(ep(1, 2), ep(1, 1))),
            Season(2, listOf(ep(2, 1), ep(2, 2))),
        )
    )

    @Test fun `next within a season, out of order input`() = assertEquals("s1e2", nextEpisodeAfter(show, ep(1, 1))?.id)

    @Test fun `rolls over into the next season`() = assertEquals("s2e1", nextEpisodeAfter(show, ep(1, 2))?.id)

    @Test fun `last episode of the show has no next`() = assertNull(nextEpisodeAfter(show, ep(2, 2)))

    @Test fun `specials are skipped when leaving season 1 backwards or forwards`() {
        // From the finale of season 2 there is nothing, and season 0 never appears mid-run.
        assertEquals("s1e1", nextEpisodeAfter(show.copy(seasons = show.seasons), ep(0, 1))?.id)
    }

    @Test fun `unknown or missing current gives null`() {
        assertNull(nextEpisodeAfter(show, null))
        assertNull(nextEpisodeAfter(show, ep(9, 9)))
    }
}
