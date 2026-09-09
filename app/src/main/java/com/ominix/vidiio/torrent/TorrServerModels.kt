package com.ominix.vidiio.torrent

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Subset of TorrServer's `state.TorrentStatus` JSON returned by the `/torrents` API.
 * Field names match TorrServer MatriX exactly (see server/torr/state/state.go).
 */
@JsonClass(generateAdapter = true)
data class TsTorrent(
    val name: String = "",
    val hash: String = "",
    val stat: Int = 0,
    @Json(name = "stat_string") val statString: String = "",
    @Json(name = "loaded_size") val loadedSize: Long = 0L,
    @Json(name = "torrent_size") val torrentSize: Long = 0L,
    @Json(name = "preloaded_bytes") val preloadedBytes: Long = 0L,
    @Json(name = "preload_size") val preloadSize: Long = 0L,
    @Json(name = "download_speed") val downloadSpeed: Double = 0.0,
    @Json(name = "upload_speed") val uploadSpeed: Double = 0.0,
    @Json(name = "total_peers") val totalPeers: Int = 0,
    @Json(name = "pending_peers") val pendingPeers: Int = 0,
    @Json(name = "active_peers") val activePeers: Int = 0,
    @Json(name = "connected_seeders") val connectedSeeders: Int = 0,
    @Json(name = "file_stats") val fileStats: List<TsFileStat> = emptyList(),
) {
    /** TorrServer torrent states. 3 (preload) and above mean metadata is available. */
    val hasMetadata: Boolean get() = fileStats.isNotEmpty()
}

@JsonClass(generateAdapter = true)
data class TsFileStat(
    val id: Int = 0,
    val path: String = "",
    val length: Long = 0L,
)
