package com.ominix.vidiio.torrent

/**
 * Intelligent torrent file picker, ported from PlayTorrioV3's
 * `DebridMediaMatcher.computeFileMatchScore` (lib/services/debrid/utils/debrid_media_matcher.dart).
 *
 * Scores every file and returns the best candidate: penalises samples / extras / tiny stubs,
 * rewards larger files, and strongly rewards a season+episode match for series.
 */
object MediaFileSelector {

    private val VIDEO_EXT = setOf(
        ".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".m4v", ".flv", ".wmv", ".iso"
    )

    private val JUNK = Regex(
        "(?:[._\\-/ ]sample[._\\-/ ]|[._\\-/ ]trailer[._\\-/ ]|[._\\-/ ]extras?[._\\-/ ]|" +
            "[._\\-/ ]bonus[._\\-/ ]|featurettes?|deleted[._ -]scenes?|behind[._ -]the[._ -]scenes|preview|interview)",
        RegexOption.IGNORE_CASE
    )

    private val parser = TorrentTitleParser()

    /**
     * @param preferredId a TorrServer file id explicitly chosen by the user (>= 1), or null for auto.
     */
    fun select(
        files: List<TsFileStat>,
        season: Int?,
        episode: Int?,
        preferredId: Int?,
    ): TsFileStat? {
        if (files.isEmpty()) return null
        if (preferredId != null && preferredId >= 1) {
            files.firstOrNull { it.id == preferredId }?.let { return it }
        }
        val media = files.filter { f -> VIDEO_EXT.any { f.path.lowercase().endsWith(it) } }
        val pool = media.ifEmpty { files }
        return pool.maxByOrNull { score(it, season, episode) }
    }

    private fun score(file: TsFileStat, season: Int?, episode: Int?): Double {
        val path = file.path.lowercase()
        var score = 0.0

        if (JUNK.containsMatchIn(path) || path.endsWith(".sample") || path.contains("sample.")) {
            score -= 5000.0
        }

        val sizeGb = file.length / (1024.0 * 1024.0 * 1024.0)
        score += (sizeGb * 25.0).coerceIn(0.0, 100.0)
        if (file.length in 1 until 25L * 1024 * 1024) score -= 2000.0

        val name = file.path.substringAfterLast('/')
        val parsed = parser.parse(name)

        if (season != null && episode != null) {
            when {
                parsed.season == season && parsed.episode == episode -> score += 1500.0
                parsed.season == null && parsed.episode == episode -> score += 1250.0
                else -> {
                    val rx = Regex("s0*$season[ ._x-]*e0*$episode", RegexOption.IGNORE_CASE)
                    val alt = Regex("\\b0*$season[ ._x-]+0*$episode\\b")
                    if (rx.containsMatchIn(path) || alt.containsMatchIn(path)) score += 1100.0
                }
            }
        } else if (episode != null) {
            // Anime-style single episode numbering.
            if (parsed.episode == episode) score += 1200.0
        }

        return score
    }
}
