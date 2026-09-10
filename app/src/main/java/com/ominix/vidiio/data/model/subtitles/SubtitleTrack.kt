package com.ominix.vidiio.data.model.subtitles

/**
 * One selectable external subtitle, independent of which provider supplied it.
 *
 * The player used to speak SubDL's wire model ([SubdlSubtitle]) directly, which meant
 * SubDL was structurally the only possible source: adding a second provider would have
 * meant either faking SubdlSubtitle objects or changing every call site. This is the
 * provider-agnostic shape everything above the data layer now uses.
 *
 * @param source Display name of where this came from - "SubDL", or the addon's own name.
 *   Shown in the subtitle menu so the same film from two providers is distinguishable.
 */
data class SubtitleTrack(
    val id: String,
    val url: String,
    val language: String,
    val label: String,
    val source: String,
) {
    /**
     * Best-effort human name for the language.
     *
     * Providers are inconsistent: SubDL returns names ("English") or 2-letter codes,
     * Stremio addons return 3-letter ISO 639-2 ("eng"). Anything unrecognised is passed
     * through as-is rather than dropped, because a wrong-looking label is better than a
     * subtitle the user cannot find.
     */
    val languageLabel: String
        get() = when (val code = language.trim().lowercase()) {
            "" -> "Unknown"
            in ISO3_TO_NAME -> ISO3_TO_NAME.getValue(code)
            in ISO2_TO_NAME -> ISO2_TO_NAME.getValue(code)
            else -> language.replaceFirstChar { it.uppercase() }
        }

    private companion object {
        val ISO2_TO_NAME = mapOf(
            "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
            "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "pl" to "Polish",
            "ru" to "Russian", "ar" to "Arabic", "tr" to "Turkish", "ja" to "Japanese",
            "ko" to "Korean", "zh" to "Chinese", "hi" to "Hindi", "sv" to "Swedish",
            "da" to "Danish", "fi" to "Finnish", "no" to "Norwegian", "cs" to "Czech",
            "el" to "Greek", "he" to "Hebrew", "hu" to "Hungarian", "ro" to "Romanian",
            "id" to "Indonesian", "th" to "Thai", "vi" to "Vietnamese", "uk" to "Ukrainian",
        )
        val ISO3_TO_NAME = mapOf(
            "eng" to "English", "spa" to "Spanish", "fre" to "French", "fra" to "French",
            "ger" to "German", "deu" to "German", "ita" to "Italian", "por" to "Portuguese",
            "dut" to "Dutch", "nld" to "Dutch", "pol" to "Polish", "rus" to "Russian",
            "ara" to "Arabic", "tur" to "Turkish", "jpn" to "Japanese", "kor" to "Korean",
            "chi" to "Chinese", "zho" to "Chinese", "hin" to "Hindi", "swe" to "Swedish",
            "dan" to "Danish", "fin" to "Finnish", "nor" to "Norwegian", "cze" to "Czech",
            "ces" to "Czech", "gre" to "Greek", "ell" to "Greek", "heb" to "Hebrew",
            "hun" to "Hungarian", "rum" to "Romanian", "ron" to "Romanian",
            "ind" to "Indonesian", "tha" to "Thai", "vie" to "Vietnamese", "ukr" to "Ukrainian",
        )
    }
}

/** Maps SubDL's wire model onto the shared shape. */
fun SubdlSubtitle.toSubtitleTrack(): SubtitleTrack? {
    val href = url ?: return null
    return SubtitleTrack(
        id = "subdl:$id",
        url = href,
        language = language,
        label = releaseName ?: name,
        source = "SubDL",
    )
}
