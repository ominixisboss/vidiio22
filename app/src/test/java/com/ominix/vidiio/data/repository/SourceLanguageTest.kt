package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLanguageTest {

    private fun src(name: String) = StreamSource(serverName = name, url = "magnet:?xt=$name")
    private fun working(vararg s: StreamSource) = s.associate { it.url to SourceHealth(SourceStatus.WORKING) }

    @Test
    fun `plain scene names count as english`() {
        assertTrue(SourceLanguage.isLikelyEnglish("The.Dog.Stars.2026.1080p.WEB-DL.x264-GRP"))
        assertEquals(1, SourceLanguage.rank("Frieren S01E05 1080p"))
    }

    @Test
    fun `other languages are recognised`() {
        for (n in listOf(
            "Movie 2026 1080p ITA ENG",
            "Movie.2026.FRENCH.1080p", "Movie 2026 VOSTFR 720p", "Movie 2026 GERMAN DL 1080p",
            "Movie 2026 Latino 1080p", "Movie 2026 RUS 1080p", "Movie 2026 HINDI 720p",
        )) {
            // An explicit ENG in the name wins; the rest are foreign.
            val expected = if (n.contains("ENG")) 0 else 2
            assertEquals(n, expected, SourceLanguage.rank(n))
        }
    }

    @Test
    fun `explicit english and dual audio rank first`() {
        assertEquals(0, SourceLanguage.rank("Show S01 Dual Audio 1080p"))
        assertEquals(0, SourceLanguage.rank("Show.S01.English.1080p"))
        // Words that merely contain a language tag do not count.
        assertFalse(SourceLanguage.rank("Germany.Documentary.2026.1080p") == 2)
        assertFalse(SourceLanguage.rank("Italy.Trip.2026.1080p") == 2)
    }

    @Test
    fun `picks the first working english source`() {
        val fr = src("Show S01E01 FRENCH 1080p")
        val en = src("Show S01E01 1080p")
        val en2 = src("Show S01E01 720p")
        assertEquals(en, pickStreamToPlay(listOf(fr, en, en2), working(fr, en, en2), searching = true, giveUp = false))
    }

    @Test
    fun `waits for an english source while still searching, then settles`() {
        val fr = src("Show S01E01 FRENCH 1080p")
        val health = working(fr)
        assertNull(pickStreamToPlay(listOf(fr), health, searching = true, giveUp = false))
        assertEquals(fr, pickStreamToPlay(listOf(fr), health, searching = false, giveUp = false))
        assertEquals(fr, pickStreamToPlay(listOf(fr), health, searching = true, giveUp = true))
    }

    @Test
    fun `heavy 4K or Dolby Vision sources are skipped when a lighter one works`() {
        val dv = src("The.Dog.Stars.2026.2160p.WEB-DL.DV.HDR10.DDP5.1.H265")
        val hd = src("The Dog Stars 2026 1080p WEBRip x264")
        assertTrue(SourceHeaviness.isHeavy(dv.serverName))
        assertFalse(SourceHeaviness.isHeavy(hd.serverName))
        assertEquals(hd, pickStreamToPlay(listOf(dv, hd), working(dv, hd), searching = true, giveUp = false))
    }

    @Test
    fun `never picks a dead source and returns null when nothing is playable`() {
        val dead = src("Show S01E01 1080p")
        val health = mapOf(dead.url to SourceHealth(SourceStatus.DEAD))
        assertNull(pickStreamToPlay(listOf(dead), health, searching = false, giveUp = true))
        assertNull(pickStreamToPlay(emptyList(), emptyMap(), searching = false, giveUp = true))
    }

    @Test
    fun `falls back to an unverified source when none is confirmed working`() {
        val unknown = src("Show S01E01 1080p")
        val health = mapOf(unknown.url to SourceHealth(SourceStatus.UNKNOWN))
        assertEquals(unknown, pickStreamToPlay(listOf(unknown), health, searching = false, giveUp = false))
    }
}
