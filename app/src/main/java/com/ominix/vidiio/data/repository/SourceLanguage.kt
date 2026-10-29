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
    private val DUB = Regex("""(?i)(?<![a-z0-9])(dub|dubbed|english[ .-_]?dub|eng[ .-_]?dub)(?![a-z0-9])""")

    /** An English dub: what anime viewers want over a subbed original. */
    fun isDub(name: String): Boolean = DUB.containsMatchIn(name)

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
    // A verified direct link starts in seconds; a torrent has to find peers first. So any working direct link
    // that passes the checks below beats every torrent, however well seeded (the sort is stable, so the existing
    // order is kept within direct links and within torrents).
    fun isDirect(s: StreamSource) = !s.url.startsWith("magnet:")
    val workingAll = sources.filter { status(it) == SourceStatus.WORKING }
    val working = workingAll.sortedBy { !isDirect(it) }

    // Best first choice: works, English, and light enough for a TV stick to decode.
    working.firstOrNull { SourceLanguage.isLikelyEnglish(it.serverName) && !SourceHeaviness.isHeavy(it.serverName) }?.let { return it }
    working.firstOrNull { SourceLanguage.isLikelyEnglish(it.serverName) }?.let { if (!searching || giveUp) return it }

    if (searching && !giveUp) return null
    return working.firstOrNull()
        ?: sources.firstOrNull { status(it) != SourceStatus.DEAD && status(it) != SourceStatus.CHECKING }
}

/** What an audio track's language tag tells us. Pure, so it can be tested. */
object AudioLanguage {
    private val ENGLISH_TAGS = setOf("en", "eng")
    private val UNKNOWN_TAGS = setOf("", "und", "mis", "zxx")

    fun isEnglish(tag: String?): Boolean {
        val t = tag?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return false
        return t in ENGLISH_TAGS
    }

    fun isKnown(tag: String?): Boolean = (tag?.lowercase()?.substringBefore('-') ?: "") !in UNKNOWN_TAGS

    /**
     * True when the file says what its audio is and none of it is English: certain enough to move
     * on to another source. Untagged ("und") audio is never judged - it cannot be told apart
     * without analysing the sound itself.
     */
    fun definitelyNotEnglish(tags: List<String?>): Boolean =
        tags.isNotEmpty() && tags.all { isKnown(it) } && tags.none { isEnglish(it) }
}
