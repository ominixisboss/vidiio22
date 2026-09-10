package com.ominix.vidiio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.util.Log
import kotlinx.coroutines.CancellationException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme {
    DARK, LIGHT, SYSTEM
}

enum class ColorTheme {
    RED, BLUE, GREEN, PURPLE, ORANGE, TEAL, PINK, INDIGO, GOLD, MONO,

    /**
     * Material You: colours derived from the device wallpaper. Android 12+ only;
     * falls back to RED below that, since there is no wallpaper palette to read.
     */
    DYNAMIC
}

/** Home-screen layout preset. Restyles the hero, cards, rows and accent of the Home tab. */
enum class HomeStyle {
    VIDIIO, NETFLIX, HULU, PRIME, DISNEY
}

enum class ProxyType { SOCKS5, HTTP }

/** Outline style drawn around subtitle glyphs, for legibility over bright video. */
enum class SubtitleEdge { NONE, OUTLINE, DROP_SHADOW, RAISED, DEPRESSED }

/**
 * How subtitles are drawn. Applied to the player's SubtitleView.
 *
 * Colours are ARGB packed into a Long because DataStore has no colour type and Int
 * preferences cannot hold 0xFF------ without overflowing into negatives on read.
 */
data class SubtitleStyle(
    val textScale: Float = 1.0f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x00000000,
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE,
)

/** Proxy ("VPN") config applied to all app HTTP traffic and the torrent engine. */
data class ProxyConfig(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?
) {
    /** socks5://user:pass@host:port — the form the torrent engine's ALL_PROXY env expects. */
    fun toUri(): String {
        val scheme = if (type == ProxyType.SOCKS5) "socks5" else "http"
        val auth = if (!username.isNullOrBlank()) {
            "${username}${if (!password.isNullOrBlank()) ":$password" else ""}@"
        } else ""
        return "$scheme://$auth$host:$port"
    }
}

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val PLAYBACK_QUALITY = stringPreferencesKey("playback_quality")
        val THEME = stringPreferencesKey("theme")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val HOME_STYLE = stringPreferencesKey("home_style")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AVOID_CAMERA_CUTOUT = booleanPreferencesKey("avoid_camera_cutout")
        val SOURCES = stringSetPreferencesKey("sources")
        val STREMIO_ADDONS = stringSetPreferencesKey("stremio_addons")
        val SUBDL_API_KEY = stringPreferencesKey("subdl_api_key")
        val SUBTITLE_LANGUAGES = stringPreferencesKey("subtitle_languages")
        val SUBTITLE_TEXT_SCALE = floatPreferencesKey("subtitle_text_scale")
        val SUBTITLE_TEXT_COLOR = longPreferencesKey("subtitle_text_color")
        val SUBTITLE_BG_COLOR = longPreferencesKey("subtitle_bg_color")
        val SUBTITLE_EDGE = stringPreferencesKey("subtitle_edge")
        val DYNAMIC_THEME_MIGRATED = booleanPreferencesKey("dynamic_theme_migrated")
        val ASS_NATIVE_STYLING = booleanPreferencesKey("ass_native_styling")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USER = stringPreferencesKey("proxy_user")
        val PROXY_PASS = stringPreferencesKey("proxy_pass")
    }

    val playbackQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PLAYBACK_QUALITY] ?: "Auto"
    }

    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeString = preferences[PreferencesKeys.THEME] ?: AppTheme.SYSTEM.name
        AppTheme.valueOf(themeString)
    }

    val colorThemeFlow: Flow<ColorTheme> = context.dataStore.data.map { preferences ->
        val themeString = preferences[PreferencesKeys.COLOR_THEME] ?: ColorTheme.RED.name
        try {
            ColorTheme.valueOf(themeString)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "SettingsRepository failed", e)
            ColorTheme.RED
        }
    }

    val homeStyleFlow: Flow<HomeStyle> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.HOME_STYLE] ?: HomeStyle.VIDIIO.name
        try {
            HomeStyle.valueOf(raw)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "SettingsRepository failed", e)
            HomeStyle.VIDIIO
        }
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
    }

    /** When true, the video player is inset so the front-camera cutout never covers the picture. */
    val avoidCameraCutoutFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AVOID_CAMERA_CUTOUT] ?: false
    }

    val sourcesFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SOURCES] ?: setOf("vidsrc", "videasy", "vadapav", "vuflix", "cinejoy", "movy", "a111477", "knaben", "tg")
    }

    val stremioAddonsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STREMIO_ADDONS] ?: setOf("https://torrentio.strem.fun/manifest.json")
    }

    val subdlApiKeyFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SUBDL_API_KEY]
    }

    suspend fun setPlaybackQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_QUALITY] = quality
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme.name
        }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLOR_THEME] = theme.name
        }
    }

    suspend fun setHomeStyle(style: HomeStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOME_STYLE] = style.name
        }
    }

    /**
     * Folds the old standalone "dynamic colour" flag into [ColorTheme].
     *
     * That flag defaulted to on and took precedence over the colour picker, so on Android
     * 12+ every swatch produced the same wallpaper palette. ColorTheme is the only input
     * now - but flipping that over would silently change the look of any install that had
     * the flag on, so anyone who did gets ColorTheme.DYNAMIC instead. Runs once.
     */
    suspend fun migrateDynamicThemeIfNeeded() {
        val preferences = context.dataStore.data.first()
        if (preferences[PreferencesKeys.DYNAMIC_THEME_MIGRATED] == true) return
        val hadDynamicOn = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
        context.dataStore.edit { prefs ->
            if (hadDynamicOn) prefs[PreferencesKeys.COLOR_THEME] = ColorTheme.DYNAMIC.name
            prefs[PreferencesKeys.DYNAMIC_THEME_MIGRATED] = true
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setAvoidCameraCutout(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AVOID_CAMERA_CUTOUT] = enabled
        }
    }

    val proxyEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.PROXY_ENABLED] ?: false }
    val proxyTypeFlow: Flow<ProxyType> = context.dataStore.data.map {
        runCatching { ProxyType.valueOf(it[PreferencesKeys.PROXY_TYPE] ?: "SOCKS5") }.getOrDefault(ProxyType.SOCKS5)
    }
    val proxyHostFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.PROXY_HOST] ?: "" }
    val proxyPortFlow: Flow<Int> = context.dataStore.data.map { it[PreferencesKeys.PROXY_PORT] ?: 0 }
    val proxyUserFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.PROXY_USER] ?: "" }
    val proxyPassFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.PROXY_PASS] ?: "" }

    /** The active proxy config, or null when disabled / not fully configured. */
    val proxyConfigFlow: Flow<ProxyConfig?> = context.dataStore.data.map { p ->
        if (p[PreferencesKeys.PROXY_ENABLED] != true) return@map null
        val host = p[PreferencesKeys.PROXY_HOST]?.trim().orEmpty()
        val port = p[PreferencesKeys.PROXY_PORT] ?: 0
        if (host.isEmpty() || port !in 1..65535) return@map null
        ProxyConfig(
            type = runCatching { ProxyType.valueOf(p[PreferencesKeys.PROXY_TYPE] ?: "SOCKS5") }.getOrDefault(ProxyType.SOCKS5),
            host = host,
            port = port,
            username = p[PreferencesKeys.PROXY_USER]?.trim()?.takeIf { it.isNotEmpty() },
            password = p[PreferencesKeys.PROXY_PASS]?.takeIf { it.isNotEmpty() }
        )
    }

    suspend fun setProxy(
        enabled: Boolean,
        type: ProxyType,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        context.dataStore.edit { p ->
            p[PreferencesKeys.PROXY_ENABLED] = enabled
            p[PreferencesKeys.PROXY_TYPE] = type.name
            p[PreferencesKeys.PROXY_HOST] = host.trim()
            p[PreferencesKeys.PROXY_PORT] = port
            p[PreferencesKeys.PROXY_USER] = username.trim()
            p[PreferencesKeys.PROXY_PASS] = password
        }
    }

    suspend fun setSources(sources: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOURCES] = sources
        }
    }

    suspend fun setStremioAddons(addons: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREMIO_ADDONS] = addons
        }
    }

    suspend fun setSubdlApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SUBDL_API_KEY] = apiKey
        }
    }

    /**
     * Preferred subtitle languages, most-wanted first, as ISO 639-1 codes.
     *
     * Stored as a comma-separated string rather than a string set because order is the
     * whole point - it decides which language a provider is asked for first and how the
     * subtitle menu is sorted, and DataStore's string set is unordered.
     *
     * Defaults to English, which is what the code did unconditionally before this existed.
     */
    val subtitleLanguagesFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SUBTITLE_LANGUAGES]
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_SUBTITLE_LANGUAGES
    }

    suspend fun setSubtitleLanguages(languages: List<String>) {
        context.dataStore.edit { preferences ->
            val cleaned = languages.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            preferences[PreferencesKeys.SUBTITLE_LANGUAGES] = cleaned.joinToString(",")
        }
    }

    /**
     * Whether ASS/SSA tracks keep their own styling (libass) or are rendered as plain
     * text the appearance settings can restyle.
     *
     * These are mutually exclusive and not a matter of implementation effort: libass
     * rasterises ASS to bitmaps, positioning, fonts and karaoke included, so there is no
     * text left for the player to restyle. Rendering ASS as plain text means giving all
     * of that up. Defaults to true - faithful styling - which is what the player did
     * before this was a choice.
     */
    val assNativeStylingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ASS_NATIVE_STYLING] ?: true
    }

    suspend fun setAssNativeStyling(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ASS_NATIVE_STYLING] = enabled
        }
    }

    /** How subtitles should be drawn. Defaults match the player's previous fixed look. */
    val subtitleStyleFlow: Flow<SubtitleStyle> = context.dataStore.data.map { preferences ->
        SubtitleStyle(
            textScale = preferences[PreferencesKeys.SUBTITLE_TEXT_SCALE] ?: 1.0f,
            textColor = preferences[PreferencesKeys.SUBTITLE_TEXT_COLOR] ?: 0xFFFFFFFF,
            backgroundColor = preferences[PreferencesKeys.SUBTITLE_BG_COLOR] ?: 0x00000000,
            edge = runCatching {
                SubtitleEdge.valueOf(preferences[PreferencesKeys.SUBTITLE_EDGE] ?: "OUTLINE")
            }.getOrDefault(SubtitleEdge.OUTLINE),
        )
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SUBTITLE_TEXT_SCALE] = style.textScale
            preferences[PreferencesKeys.SUBTITLE_TEXT_COLOR] = style.textColor
            preferences[PreferencesKeys.SUBTITLE_BG_COLOR] = style.backgroundColor
            preferences[PreferencesKeys.SUBTITLE_EDGE] = style.edge.name
        }
    }

    companion object {
        val DEFAULT_SUBTITLE_LANGUAGES = listOf("en")
    }
}

private const val TAG = "SettingsRepository"