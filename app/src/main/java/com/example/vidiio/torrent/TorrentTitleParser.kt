package com.example.vidiio.torrent

import java.util.regex.Pattern

class TorrentTitleParser {
    data class ParseResult(
        val title: String,
        val season: Int? = null,
        val episode: Int? = null,
        val year: Int? = null,
        val resolution: String? = null,
        val codec: String? = null,
        val audio: String? = null,
        val group: String? = null
    )

    fun parse(rawTitle: String): ParseResult {
        var title = rawTitle
        var season: Int? = null
        var episode: Int? = null
        var year: Int? = null
        var resolution: String? = null
        var codec: String? = null
        var audio: String? = null
        var group: String? = null

        // Year
        val yearPattern = Pattern.compile("[^a-zA-Z0-9](19[0-9]{2}|20[0-2][0-9])[^a-zA-Z0-9]")
        val yearMatcher = yearPattern.matcher(rawTitle)
        if (yearMatcher.find()) {
            year = yearMatcher.group(1)?.toIntOrNull()
        }

        // Season & Episode (S01E01, 1x01, etc)
        val s01e01Pattern = Pattern.compile("[sS]([0-9]{1,2})[eE]([0-9]{1,2})")
        val s01e01Matcher = s01e01Pattern.matcher(rawTitle)
        if (s01e01Matcher.find()) {
            season = s01e01Matcher.group(1)?.toIntOrNull()
            episode = s01e01Matcher.group(2)?.toIntOrNull()
        } else {
            val simpleEpPattern = Pattern.compile("([0-9]{1,2})x([0-9]{1,2})")
            val simpleEpMatcher = simpleEpPattern.matcher(rawTitle)
            if (simpleEpMatcher.find()) {
                season = simpleEpMatcher.group(1)?.toIntOrNull()
                episode = simpleEpMatcher.group(2)?.toIntOrNull()
            }
        }

        // Resolution
        val resPattern = Pattern.compile("([0-9]{3,4}[pi])|4[kK]|UHD|FHD")
        val resMatcher = resPattern.matcher(rawTitle)
        if (resMatcher.find()) {
            resolution = resMatcher.group(0)?.lowercase()
        }

        // Codec
        val codecPattern = Pattern.compile("([hH][-. ]?26[45])|[xX][-. ]?26[45]|[hH][eE][vV][cC]|[aA][vV][cC]")
        val codecMatcher = codecPattern.matcher(rawTitle)
        if (codecMatcher.find()) {
            codec = codecMatcher.group(0)?.lowercase()?.replace("[-. ]".toRegex(), "")
        }

        // Clean title - everything before the first match or common separator
        val separators = listOf(" ", ".", "_", "-")
        val possibleTitleEndIndices = mutableListOf<Int>()
        
        // Add indices of found metadata
        if (yearMatcher.find(0)) possibleTitleEndIndices.add(yearMatcher.start())
        if (s01e01Matcher.find(0)) possibleTitleEndIndices.add(s01e01Matcher.start())
        
        val firstMetadataIndex = possibleTitleEndIndices.minOrNull() ?: rawTitle.length
        title = rawTitle.substring(0, firstMetadataIndex)
            .replace(".", " ")
            .replace("_", " ")
            .trim()

        return ParseResult(
            title = title,
            season = season,
            episode = episode,
            year = year,
            resolution = resolution,
            codec = codec,
            audio = audio,
            group = group
        )
    }
}
