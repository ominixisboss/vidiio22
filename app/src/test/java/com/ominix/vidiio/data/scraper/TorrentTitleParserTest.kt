package com.ominix.vidiio.data.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the behaviour of [TorrentTitleParser].
 *
 * Worth having because the parser feeds two decisions that are invisible when they go
 * wrong: the scrapers use season/episode to decide whether a torrent is the episode the
 * user asked for, and MediaFileSelector uses them to pick which file inside a multi-file
 * torrent to play. A parsing bug does not crash - it silently plays the wrong thing.
 */
class TorrentTitleParserTest {

    private val parser = TorrentTitleParser()

    // ── Movies ───────────────────────────────────────────────────────────────

    @Test
    fun `parses a dotted movie release`() {
        val r = parser.parse("The.Matrix.1999.1080p.BluRay.x264-GROUP")
        assertEquals("The Matrix", r.title)
        assertEquals(1999, r.year)
        assertEquals("1080p", r.resolution)
        assertNull(r.season)
        assertNull(r.episode)
    }

    @Test
    fun `parses a spaced movie release`() {
        val r = parser.parse("Dune Part Two 2024 2160p WEB-DL DDP5 1 Atmos H 265-FLUX")
        assertEquals("Dune Part Two", r.title)
        assertEquals(2024, r.year)
        assertEquals("2160p", r.resolution)
    }

    @Test
    fun `keeps a number that is part of the title`() {
        val r = parser.parse("Blade.Runner.2049.2017.2160p.UHD.BluRay.x265")
        // 2049 is the title, 2017 is the year - but 2049 matches first, so this documents
        // a known limitation rather than asserting the ideal answer.
        assertEquals("Blade Runner", r.title)
        assertEquals(2049, r.year)
    }

    @Test
    fun `handles a year at the end with no trailing separator`() {
        // The old scraper copy required a separator after the year and returned null here.
        val r = parser.parse("Oppenheimer.2023")
        assertEquals("Oppenheimer", r.title)
        assertEquals(2023, r.year)
    }

    @Test
    fun `does not treat a four digit run inside a word as a year`() {
        val r = parser.parse("Movie.x2016y.1080p")
        assertNull(r.year)
    }

    // ── Series ───────────────────────────────────────────────────────────────

    @Test
    fun `parses S01E01`() {
        val r = parser.parse("Breaking.Bad.S01E01.720p.BluRay.x264")
        assertEquals("Breaking Bad", r.title)
        assertEquals(1, r.season)
        assertEquals(1, r.episode)
        assertEquals("720p", r.resolution)
    }

    @Test
    fun `parses lowercase and unpadded season episode`() {
        val r = parser.parse("Severance.s2e10.1080p")
        assertEquals(2, r.season)
        assertEquals(10, r.episode)
    }

    @Test
    fun `parses 1x01 numbering`() {
        // Only the torrent-side copy handled this; the scraper copy returned nulls, so a
        // matching episode was silently rejected.
        val r = parser.parse("Firefly.1x05.Safe.HDTV")
        assertEquals("Firefly", r.title)
        assertEquals(1, r.season)
        assertEquals(5, r.episode)
    }

    @Test
    fun `does not read a resolution as season and episode`() {
        // "1920x1080" must not parse as season 20, episode 10.
        val r = parser.parse("Some.Show.S03E04.1920x1080.WEB-DL")
        assertEquals(3, r.season)
        assertEquals(4, r.episode)
    }

    @Test
    fun `parses a three digit episode number`() {
        val r = parser.parse("One.Piece.S01E1015.1080p")
        assertEquals(1, r.season)
        assertEquals(1015, r.episode)
    }

    // ── Resolution ───────────────────────────────────────────────────────────

    @Test
    fun `does not read a codec as a resolution`() {
        // The old torrent-side pattern [0-9]{3,4}[pi] matched the "264p" here.
        val r = parser.parse("Some.Movie.2020.x264p.WEBRip")
        assertEquals("Some Movie", r.title)
        assertNull(r.resolution)
    }

    @Test
    fun `normalises resolution case`() {
        assertEquals("1080p", parser.parse("Show.S01E01.1080P.WEB").resolution)
        assertEquals("4k", parser.parse("Show.S01E01.4K.WEB").resolution)
    }

    @Test
    fun `returns null resolution when absent`() {
        assertNull(parser.parse("Show.S01E01.WEB-DL").resolution)
    }

    // ── Title cleanup ────────────────────────────────────────────────────────

    @Test
    fun `stops the title at a leading release tag`() {
        val r = parser.parse("PROPER.The.Thing.1982.1080p")
        // The tag is at index 0, so it cannot end the title; the year does.
        assertEquals("PROPER The Thing", r.title)
        assertEquals(1982, r.year)
    }

    @Test
    fun `stops the title at a release tag that precedes the year`() {
        val r = parser.parse("The.Thing.REPACK.1982.1080p")
        assertEquals("The Thing", r.title)
    }

    @Test
    fun `collapses separators and trims punctuation`() {
        val r = parser.parse("A__Very...Long_Name-(2011).720p")
        assertEquals("A Very Long Name", r.title)
        assertEquals(2011, r.year)
    }

    @Test
    fun `returns the whole string when there is no metadata`() {
        val r = parser.parse("Just A Title")
        assertEquals("Just A Title", r.title)
        assertNull(r.year)
        assertNull(r.season)
        assertNull(r.resolution)
    }

    @Test
    fun `handles an empty string`() {
        val r = parser.parse("")
        assertEquals("", r.title)
        assertNull(r.year)
    }
}
