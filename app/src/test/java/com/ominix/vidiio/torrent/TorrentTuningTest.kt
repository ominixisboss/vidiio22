package com.ominix.vidiio.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentTuningTest {
    @Test
    fun `trackers are appended to a magnet`() {
        val out = TorrentTuning.withTrackers("magnet:?xt=urn:btih:abc&dn=Show", listOf("udp://t1:1/announce"))
        assertEquals("magnet:?xt=urn:btih:abc&dn=Show&tr=udp%3A%2F%2Ft1%3A1%2Fannounce", out)
    }

    @Test
    fun `trackers already in the magnet are not repeated`() {
        val m = "magnet:?xt=urn:btih:abc&tr=udp%3A%2F%2Ft1%3A1%2Fannounce"
        val out = TorrentTuning.withTrackers(m, listOf("udp://t1:1/announce", "udp://t2:2/announce"))
        assertEquals(1, Regex("t1%3A1").findAll(out).count())
        assertTrue(out.contains("t2%3A2"))
    }

    @Test
    fun `non-magnet links pass through`() {
        assertEquals("https://x/y.mp4", TorrentTuning.withTrackers("https://x/y.mp4"))
    }

    @Test
    fun `cache is smaller on low memory devices`() {
        assertTrue(TorrentTuning.cacheBytes(true) < TorrentTuning.cacheBytes(false))
    }
}
