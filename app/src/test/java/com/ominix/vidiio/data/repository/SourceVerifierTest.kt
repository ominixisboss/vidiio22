package com.ominix.vidiio.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceVerifierTest {

    private fun judge(code: Int, type: String?, body: String = "", hls: Boolean = false) =
        SourceVerifier.judgeResponse(code, type, body, hls).status

    @Test
    fun `a video response is working`() {
        assertEquals(SourceStatus.WORKING, judge(200, "video/mp4", "\u0000\u0000\u0000"))
        assertEquals(SourceStatus.WORKING, judge(206, "video/x-matroska"))
        assertEquals(SourceStatus.WORKING, judge(200, "application/octet-stream"))
        assertEquals(SourceStatus.WORKING, judge(200, null))
    }

    @Test
    fun `an html page behind a video url is dead`() {
        assertEquals(SourceStatus.DEAD, judge(200, "text/html; charset=utf-8", "<!DOCTYPE html>"))
        assertEquals(SourceStatus.DEAD, judge(200, "application/octet-stream", "<html><body>Blocked"))
        assertEquals(SourceStatus.DEAD, judge(200, "application/json", "{\"error\":\"gone\"}"))
    }

    @Test
    fun `error statuses are dead`() {
        for (code in listOf(401, 403, 404, 410, 500, 503)) {
            assertEquals("HTTP $code", SourceStatus.DEAD, judge(code, "video/mp4"))
        }
    }

    @Test
    fun `being rate limited says nothing about the link`() {
        assertEquals(SourceStatus.UNKNOWN, judge(429, null))
        assertEquals(SourceStatus.UNKNOWN, judge(408, null))
    }

    @Test
    fun `an hls playlist must really be a playlist`() {
        assertEquals(SourceStatus.WORKING, judge(200, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-VERSION:3", hls = true))
        // Byte-order mark and leading whitespace are tolerated.
        assertEquals(SourceStatus.WORKING, judge(200, "text/plain", "﻿\n#EXTM3U\n", hls = true))
        // The url says m3u8 but the server answered with an error page.
        assertEquals(SourceStatus.DEAD, judge(200, "text/html", "<html>nope</html>", hls = true))
        assertEquals(SourceStatus.DEAD, judge(200, "application/x-mpegurl", "not a playlist"))
    }

    @Test
    fun `working direct links carry no note but dead ones explain themselves`() {
        assertEquals("HTTP 404", SourceVerifier.judgeResponse(404, null, "", false).note)
        assertEquals("Not a video", SourceVerifier.judgeResponse(200, "text/html", "", false).note)
    }

    @Test
    fun `torrents are judged by seeders`() {
        assertEquals(SourceStatus.WORKING, SourceVerifier.judgeTorrent(12).status)
        assertEquals("12 seeders", SourceVerifier.judgeTorrent(12).note)
        assertEquals(SourceStatus.DEAD, SourceVerifier.judgeTorrent(0).status)
        assertEquals(SourceStatus.UNKNOWN, SourceVerifier.judgeTorrent(null).status)
    }

    @Test
    fun `torrent and embed detection`() {
        assertTrue(SourceVerifier.isTorrent("magnet:?xt=urn:btih:abc"))
        assertTrue(SourceVerifier.isTorrent("https://example.com/file.torrent?x=1"))
        assertFalse(SourceVerifier.isTorrent("https://example.com/video.mp4"))
        assertTrue(SourceVerifier.isEmbed("https://vidsrc.me/embed/movie/tt123"))
        assertFalse(SourceVerifier.isEmbed("https://cdn.example.com/video.mp4"))
    }
}
