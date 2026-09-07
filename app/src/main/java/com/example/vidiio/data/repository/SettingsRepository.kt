package com.example.vidiio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme {
    DARK, LIGHT, SYSTEM
}

enum class ColorTheme {
    RED, BLUE, GREEN, PURPLE, ORANGE, TEAL, PINK, INDIGO, GOLD, MONO
}

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val PLAYBACK_QUALITY = stringPreferencesKey("playback_quality")
        val THEME = stringPreferencesKey("theme")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SOURCES = stringSetPreferencesKey("sources")
        val STREMIO_ADDONS = stringSetPreferencesKey("stremio_addons")
        val SUBDL_API_KEY = stringPreferencesKey("subdl_api_key")
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
            ColorTheme.RED
        }
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
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

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
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
}
