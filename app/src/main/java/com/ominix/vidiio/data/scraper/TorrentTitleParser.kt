package com.ominix.vidiio.data.scraper

import java.util.regex.Pattern

/**
 * Pulls structured fields out of a scene-style torrent name.
 *
 * There used to be two of these - `data.scraper.TorrentTitleParser` (used by the torrent
 * scrapers) and `torrent.TorrentTitleParser` (used by TorrentManager and
 * MediaFileSelector) - and they had drifted into disagreeing about what a given name
 * means. That matters: the scrapers use [ParsedTitle.season] / [ParsedTitle.episode] to
 * decide whether a torrent is the episode the user asked for, and MediaFileSelector uses
 * them to pick which file inside a multi-file torrent to play. Two parsers meant those
 * two decisions could disagree about the same string.
 *
 * This is the union of both, keeping whichever behaviour was better defined:
 *  - `1x01` episode numbering, which only the torrent copy handled.
 *  - Years at the very start or very end of a name, which neither copy handled: one
 *    required a separator before the year, the other required one after.
 *  - An explicit resolution list rather than `[0-9]{3,4}[pi]`, which also matched the
 *    "264p" in strings like "x264p".
 *
 * Behaviour is pinned by TorrentTitleParserTest.
 */
class TorrentTitleParser {

    data class ParsedTitle(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val resolution: String? = null
    )

    fun parse(rawTitle: String): ParsedTitle {
        // Where the metadata starts is where the title ends. Collected as we match.
        val metadataStarts = mutableListOf<Int>()

        var year: Int? = null
        YEAR.matcher(rawTitle).let { m ->
            if (m.find()) {
                year = m.group(1)?.toIntOrNull()
                metadataStarts += m.start(1)
            }
        }

        var season: Int? = null
        var episode: Int? = null
        val se = SEASON_EPISODE.matcher(rawTitle)
        if (se.find()) {
            season = se.group(1)?.toIntOrNull()
            episode = se.group(2)?.toIntOrNull()
            metadataStarts += se.start()
        } else {
            val alt = ALT_SEASON_EPISODE.matcher(rawTitle)
            if (alt.find()) {
                season = alt.group(1)?.toIntOrNull()
                episode = alt.group(2)?.toIntOrNull()
                metadataStarts += alt.start()
            }
        }

        var resolution: String? = null
        RESOLUTION.matcher(rawTitle).let { m ->
            if (m.find()) {
                resolution = m.group().lowercase()
                metadataStarts += m.start()
            }
        }

        // A release tag can appear before any of the above ("PROPER.Show.S01E01"), so it
        // is another candidate for where the title stops.
        RELEASE_TAG.matcher(rawTitle).let { m ->
            if (m.find()) metadataStarts += m.start()
        }

        val titleEnd = metadataStarts.filter { it > 0 }.minOrNull() ?: rawTitle.length
        val title = rawTitle.substring(0, titleEnd)
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .trim('-', '(', '[', ' ')
            .trim()

        return ParsedTitle(
            title = title,
            year = year,
            season = season,
            episode = episode,
            resolution = resolution
        )
    }

    private companion object {
        /**
         * A four-digit year, bounded by a non-alphanumeric or by either end of the string.
         * Lookaround rather than consuming the boundary, so `start(1)` points at the year.
         */
        val YEAR: Pattern = Pattern.compile("(?<![a-zA-Z0-9])((?:19|20)\\d{2})(?![a-zA-Z0-9])")

        /**
         * S01E01, s1e1, S01.E01.
         *
         * The episode group is bounded at four digits, not three: bounded-greedy
         * stopped after three, so One Piece's "S01E1015" parsed as episode 101 and
         * matched against the wrong episode. Verified by TorrentTitleParserTest.
         */
        val SEASON_EPISODE: Pattern =
            Pattern.compile("s(\\d{1,2})[.\\-_ ]?e(\\d{1,4})(?!\\d)", Pattern.CASE_INSENSITIVE)

        /** 1x01. Bounded so it cannot fire inside a dimension like "1920x1080". */
        val ALT_SEASON_EPISODE: Pattern =
            Pattern.compile("(?<![a-zA-Z0-9])(\\d{1,2})x(\\d{1,2})(?![a-zA-Z0-9])")

        /**
         * Explicit list, not `[0-9]{3,4}[pi]`: that also matched the "264p" you get from
         * "x264p" and similar.
         */
        val RESOLUTION: Pattern = Pattern.compile(
            "(?<![a-zA-Z0-9])(2160p|1440p|1080p|1080i|720p|576p|480p|4k|8k|uhd|fhd)(?![a-zA-Z0-9])",
            Pattern.CASE_INSENSITIVE
        )

        /** Quality/source/codec words that are never part of a title. */
        val RELEASE_TAG: Pattern = Pattern.compile(
            "(?<![a-zA-Z0-9])(repack|proper|limited|extended|unrated|remux|bluray|blu-ray|" +
                "brrip|bdrip|dvdrip|web-?dl|webrip|hdtv|hdrip|telesync|" +
                "x26[45]|h[.\\-]?26[45]|hevc|xvid|divx|" +
                "aac|ac3|dts|truehd|atmos|flac)(?![a-zA-Z0-9])",
            Pattern.CASE_INSENSITIVE
        )

        val WHITESPACE_RUN = Regex("\\s{2,}")
    }
}
