package com.ominix.vidiio.torrent

/**
 * Settings that make a torrent start playing sooner and stay smooth, and the public trackers added
 * to every magnet so there are more peers to connect to in the first seconds.
 */
object TorrentTuning {

    /** Memory is the limit on a streaming stick; a bigger read-ahead cache absorbs peer hiccups. */
    fun cacheBytes(isLowMemory: Boolean): Long = if (isLowMemory) 96L shl 20 else 256L shl 20

    /**
     * Engine settings, merged over whatever the engine already has (a "set" replaces the whole
     * block, so unspecified fields would reset to zero).
     */
    fun settings(isLowMemory: Boolean): Map<String, Any> = mapOf(
        "CacheSize" to cacheBytes(isLowMemory),
        // Start playing once this much of the cache is filled, not after a long preload.
        "PreloadCache" to 8,
        // Fetch ahead of the playhead, so it doesn't stall waiting on the next piece.
        "ReaderReadAHead" to 95,
        // More simultaneous peers, and more DHT lookups to find them.
        "ConnectionsLimit" to 300,
        "DhtConnectionLimit" to 600,
        // 0 = unlimited.
        "DownloadRateLimit" to 0,
        // Add our trackers to the ones the torrent already lists.
        "RetrackersMode" to 1,
        "TorrentDisconnectTimeout" to 30,
        "DisableUPNP" to false,
        "ForceEncrypt" to false,
    )

    /** Long-lived public UDP/HTTP trackers. Order is irrelevant; the engine contacts them in parallel. */
    val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://wepzone.net:6969/announce",
        "https://tracker.tamersunion.org:443/announce",
    )

    /** Adds [TRACKERS] to a magnet link, skipping any it already lists. Other links pass through. */
    fun withTrackers(magnet: String, trackers: List<String> = TRACKERS): String {
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) return magnet
        val existing = Regex("[?&]tr=([^&]+)").findAll(magnet)
            .map { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8").lowercase() }.toSet()
        val extra = trackers.filter { it.lowercase() !in existing }
            .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }
        return magnet + extra
    }
}
