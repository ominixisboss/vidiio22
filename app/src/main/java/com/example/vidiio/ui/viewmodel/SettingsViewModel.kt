package com.example.vidiio.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.vidiio.data.repository.AppTheme
import com.example.vidiio.data.repository.ColorTheme
import com.example.vidiio.data.repository.HomeStyle
import com.example.vidiio.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    val playbackQuality: StateFlow<String> = settingsRepository.playbackQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Auto")

    val theme: StateFlow<AppTheme> = settingsRepository.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    val colorTheme: StateFlow<ColorTheme> = settingsRepository.colorThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.RED)

    val dynamicColorEnabled: StateFlow<Boolean> = settingsRepository.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val homeStyle: StateFlow<HomeStyle> = settingsRepository.homeStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStyle.VIDIIO)

    val avoidCameraCutout: StateFlow<Boolean> = settingsRepository.avoidCameraCutoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedSources: StateFlow<Set<String>> = settingsRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), setOf("vidsrc", "videasy", "vadapav", "vuflix", "cinejoy", "movy", "a111477", "knaben", "tg"))

    val stremioAddons: StateFlow<Set<String>> = settingsRepository.stremioAddonsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val subdlApiKey: StateFlow<String?> = settingsRepository.subdlApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setPlaybackQuality(quality: String) {
        viewModelScope.launch {
            settingsRepository.setPlaybackQuality(quality)
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch {
            settingsRepository.setColorTheme(theme)
        }
    }

    fun setHomeStyle(style: HomeStyle) {
        viewModelScope.launch {
            settingsRepository.setHomeStyle(style)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setAvoidCameraCutout(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAvoidCameraCutout(enabled)
        }
    }

    fun toggleSource(source: String) {
        viewModelScope.launch {
            val currentSources = selectedSources.value.toMutableSet()
            if (currentSources.contains(source)) {
                if (currentSources.size > 1) {
                    currentSources.remove(source)
                }
            } else {
                currentSources.add(source)
            }
            settingsRepository.setSources(currentSources)
        }
    }

    fun addStremioAddon(url: String) {
        viewModelScope.launch {
            val currentAddons = stremioAddons.value.toMutableSet()
            currentAddons.add(url)
            settingsRepository.setStremioAddons(currentAddons)
        }
    }

    fun removeStremioAddon(url: String) {
        viewModelScope.launch {
            val currentAddons = stremioAddons.value.toMutableSet()
            currentAddons.remove(url)
            settingsRepository.setStremioAddons(currentAddons)
        }
    }

    fun setSubdlApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.setSubdlApiKey(apiKey)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }
}
