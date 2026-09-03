package com.example.vidiio.data.scraper

import java.util.regex.Pattern

class TorrentTitleParser {
    data class ParsedTitle(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val resolution: String? = null
    )

    fun parse(rawTitle: String): ParsedTitle {
        var title = rawTitle.replace(".", " ").replace("_", " ").trim()
        
        val yearMatch = Pattern.compile("(?<=[^a-zA-Z0-9])(19|20)\\d{2}(?=[^a-zA-Z0-9]|$)").matcher(title)
        var year: Int? = null
        if (yearMatch.find()) {
            year = yearMatch.group().toIntOrNull()
            title = title.substring(0, yearMatch.start()).trim()
        }

        val seasonEpisodeMatch = Pattern.compile("S(\\d{1,2})E(\\d{1,2})", Pattern.CASE_INSENSITIVE).matcher(rawTitle)
        var season: Int? = null
        var episode: Int? = null
        if (seasonEpisodeMatch.find()) {
            season = seasonEpisodeMatch.group(1).toIntOrNull()
            episode = seasonEpisodeMatch.group(2).toIntOrNull()
            title = rawTitle.substring(0, seasonEpisodeMatch.start()).replace(".", " ").replace("_", " ").trim()
        }

        val resolutionMatch = Pattern.compile("(2160p|1080p|720p|480p|4k|2k)", Pattern.CASE_INSENSITIVE).matcher(rawTitle)
        var resolution: String? = null
        if (resolutionMatch.find()) {
            resolution = resolutionMatch.group().lowercase()
        }

        // Clean title further - remove anything after the title
        title = title.split(Regex("(?i)\\b(REPACK|PROPER|LIMITED|EXTENDED|DIRECTORS CUT|UNRATED|1080p|720p|480p|2160p|4k|h264|h265|x264|x265|bluray|brrip|dvdrip|web-dl|webrip|hdtv)\\b"))[0].trim()

        return ParsedTitle(title, year, season, episode, resolution)
    }
}
