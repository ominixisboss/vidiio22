package com.ominix.vidiio.data.anime

import com.ominix.vidiio.ui.screens.cleanDescription
import com.ominix.vidiio.ui.screens.compactCount
import com.ominix.vidiio.ui.screens.formatCountdown
import com.ominix.vidiio.ui.screens.relativeAgo
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AnimeTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    // ── seasons ──

    @Test
    fun `months map to anilist seasons`() {
        assertEquals(AnimeSeason.WINTER, AnimeSeason.ofMonth(Calendar.JANUARY))
        assertEquals(AnimeSeason.WINTER, AnimeSeason.ofMonth(Calendar.MARCH))
        assertEquals(AnimeSeason.SPRING, AnimeSeason.ofMonth(Calendar.APRIL))
        assertEquals(AnimeSeason.SUMMER, AnimeSeason.ofMonth(Calendar.SEPTEMBER))
        assertEquals(AnimeSeason.FALL, AnimeSeason.ofMonth(Calendar.OCTOBER))
        assertEquals(AnimeSeason.FALL, AnimeSeason.ofMonth(Calendar.DECEMBER))
    }

    @Test
    fun `next season rolls the year after fall`() {
        assertEquals(SeasonYear(AnimeSeason.SPRING, 2026), SeasonYear(AnimeSeason.WINTER, 2026).next())
        assertEquals(SeasonYear(AnimeSeason.WINTER, 2027), SeasonYear(AnimeSeason.FALL, 2026).next())
        assertEquals(SeasonYear(AnimeSeason.FALL, 2026), SeasonYear.at(millis(2026, Calendar.OCTOBER, 26), utc))
    }

    @Test
    fun `start of today is local midnight`() {
        val noon = millis(2026, Calendar.OCTOBER, 26, 12)
        val midnight = AnimeRepository.startOfToday(noon, utc)
        assertEquals(noon / 1000 - 12 * 3600, midnight)
    }

    // ── matching an AniList title to a TMDB result ──

    private fun score(candidate: String, year: Int?, vararg queries: String, animeYear: Int? = null) =
        AnimeRepository.matchScore(candidate, year, queries.toList(), animeYear)

    @Test
    fun `exact title match scores highest`() {
        assertEquals(100, score("Jujutsu Kaisen", null, "Jujutsu Kaisen"))
        assertEquals(100, score("Frieren: Beyond Journey's End", null, "Frieren - Beyond Journey s End"))
    }

    @Test
    fun `a season suffix on the anilist title still matches the series`() {
        // AniList names each season; TMDB has one entry for the whole show.
        assertTrue(score("Frieren: Beyond Journey's End", null, "Frieren: Beyond Journey's End Season 2") >= 70)
        assertTrue(score("Bleach", null, "Bleach: Thousand-Year Blood War") >= AnimeRepository.MIN_MATCH_SCORE)
    }

    @Test
    fun `search variants strip the arc and season suffixes`() {
        val blood = AniMedia(title = AniTitle(english = "BLEACH: Thousand-Year Blood War - The Calamity", romaji = "BLEACH: Sennen Kessen-hen - Kashin-tan"))
        assertTrue("BLEACH: Thousand-Year Blood War" in AnimeRepository.queryVariants(blood))
        val frieren = AniMedia(title = AniTitle(english = "Frieren: Beyond Journey's End Season 2"))
        val variants = AnimeRepository.queryVariants(frieren)
        assertEquals("Frieren: Beyond Journey's End Season 2", variants.first())
        assertTrue("Frieren: Beyond Journey's End" in variants)
        assertTrue(AnimeRepository.queryVariants(AniMedia()).isEmpty())
    }

    @Test
    fun `the more specific series beats the bare one`() {
        val queries = arrayOf("BLEACH: Thousand-Year Blood War - The Calamity")
        val bare = score("Bleach", 2004, *queries, animeYear = 2026)
        val arc = score("Bleach: Thousand-Year Blood War", 2022, *queries, animeYear = 2026)
        assertTrue("arc  should beat bare ", arc > bare)
    }

    @Test
    fun `a matching year adds a bonus`() {
        assertEquals(115, score("Jujutsu Kaisen", 2020, "Jujutsu Kaisen", animeYear = 2021))
        assertEquals(100, score("Jujutsu Kaisen", 2010, "Jujutsu Kaisen", animeYear = 2021))
    }

    @Test
    fun `unrelated titles do not match`() {
        assertEquals(0, score("Naruto", null, "One Piece"))
        // A short title must not match just because it appears inside a longer one.
        assertEquals(0, score("Ai", null, "Kaiju No. 8"))
    }

    // ── formatting ──

    @Test
    fun `countdown reads naturally`() {
        assertEquals("now", formatCountdown(30))
        assertEquals("12m", formatCountdown(12 * 60L))
        assertEquals("5h 12m", formatCountdown(5 * 3600L + 12 * 60))
        assertEquals("2d 4h", formatCountdown(2 * 86_400L + 4 * 3600))
    }

    @Test
    fun `relative time and counts`() {
        assertEquals("just now", relativeAgo(30))
        assertEquals("35m ago", relativeAgo(35 * 60L))
        assertEquals("5h ago", relativeAgo(5 * 3600L))
        assertEquals("950", compactCount(950))
        assertEquals("412K", compactCount(412_345))
        assertEquals("1.2M", compactCount(1_234_567))
    }

    @Test
    fun `descriptions lose html and entities`() {
        assertEquals("A boy & his \"dog\".", cleanDescription("A boy &amp; his &quot;dog&quot;.<br>"))
        assertEquals("", cleanDescription(null))
    }

    // ── parsing a real AniList response ──

    @Test
    fun `parses an anilist media page`() {
        val json = """
        {"data":{"Page":{"pageInfo":{"hasNextPage":true},"media":[
          {"id":209219,"idMal":63712,
           "title":{"romaji":"Tensei Goblin Dakedo Shitsumon Aru?","english":"So What's Wrong with Getting Reborn as a Goblin?","native":null},
           "format":"ONA","status":"RELEASING","episodes":null,"season":"FALL","seasonYear":2026,
           "averageScore":66,"popularity":20235,"trending":331,"bannerImage":null,
           "coverImage":{"extraLarge":"https://s4.anilist.co/x.jpg","color":"#a1c950"},
           "genres":["Action","Comedy"],
           "nextAiringEpisode":{"episode":4,"airingAt":1788000000},
           "studios":{"nodes":[{"name":"TRIGGER"}]},"isAdult":false,"unknownField":123}
        ]}}}
        """.trimIndent()
        val parsed = Moshi.Builder().build().adapter(AniResponse::class.java).fromJson(json)
        val media = parsed?.data?.page?.media?.single()
        assertNotNull(media)
        media!!
        assertEquals("So What's Wrong with Getting Reborn as a Goblin?", media.displayTitle)
        assertEquals(6.6, media.score10!!, 0.001)
        assertEquals(4, media.nextAiringEpisode?.episode)
        assertEquals("TRIGGER", media.mainStudio)
        assertEquals(2026, media.year)
        assertFalse(media.isMovie)
        assertEquals("https://s4.anilist.co/x.jpg", media.posterUrl)
        assertTrue(parsed.data!!.page!!.pageInfo!!.hasNextPage)
    }

    @Test
    fun `display title falls back from english to romaji to native`() {
        assertEquals("Romaji", AniMedia(title = AniTitle(romaji = "Romaji", english = null)).displayTitle)
        assertEquals("Native", AniMedia(title = AniTitle(native = "Native")).displayTitle)
        assertEquals("Untitled", AniMedia().displayTitle)
        assertNull(AniMedia(averageScore = 0).score10)
    }
}
