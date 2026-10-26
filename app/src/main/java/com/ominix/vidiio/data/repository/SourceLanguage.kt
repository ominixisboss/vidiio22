package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.StreamSource

/**
 * Guesses whether a release is in English from its name. Scrapers return torrent and file names
 * and no language field, so the tags people put in those names are all there is.
 */
/** Releases a streaming stick cannot decode: 4K, Dolby Vision, HDR. Rarely worth picking by default. */
object SourceHeaviness {
    private val HEAVY = Regex("""(?i)(?<![a-z0-9])(2160p|4k|uhd|dolby[ .-_]?vision|dv|hdr10+?|hdr|hevc[ .-_]?10)(?![a-z0-9])""")
    fun isHeavy(name: String): Boolean = HEAVY.containsMatchIn(name)
}

object SourceLanguage {

    /** Releases whose name says another language is the one in them. */
    private val FOREIGN = Regex(
        """(?i)(?<![a-z0-9])(ita|italian|italiano|french|truefrench|vostfr|vff|vfq|german|deutsch|ger|spanish|espanol|español|latino|castellano|portuguese|pt-br|dublado|russian|rus|hindi|tamil|telugu|malayalam|polish|pl-dub|turkish|thai|arabic|czech|dutch|swedish|nordic|hungarian|korean-dub|chinese|mandarin|cantonese)(?![a-z0-9])"""
    )

    /** Names that say English outright, or promise an English track alongside others. */
    private val ENGLISH = Regex("""(?i)(?<![a-z0-9])(eng|english|dual[ .\-_]?audio|multi[ .\-_]?audio|en-us|en-gb)(?![a-z0-9])""")

    /** 0 = says English, 1 = says nothing either way, 2 = says another language. */
    fun rank(name: String): Int = when {
        ENGLISH.containsMatchIn(name) -> 0
        FOREIGN.containsMatchIn(name) -> 2
        else -> 1
    }

    fun isLikelyEnglish(name: String): Boolean = rank(name) < 2
}

/**
 * Chooses the stream to start when the user picks an episode: the best one that is known to work
 * and is not in another language. [sources] must already be ordered best-first.
 *
 * Returns null while it is still worth waiting for a better answer (sources are still being found
 * and checked). Once searching is over, or [giveUp] says the wait has gone on long enough, it
 * settles for a working source in any language, then any source not known to be dead.
 */
fun pickStreamToPlay(
    sources: List<StreamSource>,
    health: Map<String, SourceHealth>,
    searching: Boolean,
    giveUp: Boolean,
): StreamSource? {
    fun status(s: StreamSource) = health[s.url]?.status
    val working = sources.filter { status(it) == SourceStatus.WORKING }

    // Best first choice: works, English, and light enough for a TV stick to decode.
    working.firstOrNull { SourceLanguage.isLikelyEnglish(it.serverName) && !SourceHeaviness.isHeavy(it.serverName) }?.let { return it }
    working.firstOrNull { SourceLanguage.isLikelyEnglish(it.serverName) }?.let { if (!searching || giveUp) return it }

    if (searching && !giveUp) return null
    return working.firstOrNull()
        ?: sources.firstOrNull { status(it) != SourceStatus.DEAD && status(it) != SourceStatus.CHECKING }
}
