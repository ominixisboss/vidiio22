package com.ominix.vidiio.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.ominix.vidiio.data.repository.AppTheme
import com.ominix.vidiio.data.repository.ColorTheme
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.data.repository.ProxyConfig
import com.ominix.vidiio.data.repository.ProxyType
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.SubtitleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val proxyEnabled: StateFlow<Boolean> = settingsRepository.proxyEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val proxyType: StateFlow<ProxyType> = settingsRepository.proxyTypeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProxyType.SOCKS5)
    val proxyHost: StateFlow<String> = settingsRepository.proxyHostFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val proxyPort: StateFlow<Int> = settingsRepository.proxyPortFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val proxyUser: StateFlow<String> = settingsRepository.proxyUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val proxyPass: StateFlow<String> = settingsRepository.proxyPassFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _proxyTestResult = MutableStateFlow<String?>(null)
    val proxyTestResult: StateFlow<String?> = _proxyTestResult.asStateFlow()

    val selectedSources: StateFlow<Set<String>> = settingsRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), setOf("vidsrc", "videasy", "vadapav", "vuflix", "cinejoy", "movy", "a111477", "knaben", "tg"))

    val stremioAddons: StateFlow<Set<String>> = settingsRepository.stremioAddonsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val subdlApiKey: StateFlow<String?> = settingsRepository.subdlApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Subtitle appearance, applied to the player's SubtitleView. */
    val subtitleStyle: StateFlow<SubtitleStyle> = settingsRepository.subtitleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleStyle())

    /** True: ASS tracks keep their own styling. False: they follow the settings above. */
    val assNativeStyling: StateFlow<Boolean> = settingsRepository.assNativeStylingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAssNativeStyling(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAssNativeStyling(enabled) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        viewModelScope.launch { settingsRepository.setSubtitleStyle(style) }
    }

    /** Preferred subtitle languages, most-wanted first. */
    val subtitleLanguages: StateFlow<List<String>> = settingsRepository.subtitleLanguagesFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsRepository.DEFAULT_SUBTITLE_LANGUAGES
        )

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

    /**
     * Selects or leaves the DYNAMIC theme.
     *
     * Writes the same preference the colour picker writes, rather than a second flag that
     * competes with it. Turning it off falls back to RED - the picker sits directly above
     * for anything else.
     */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setColorTheme(if (enabled) ColorTheme.DYNAMIC else ColorTheme.RED)
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setAvoidCameraCutout(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAvoidCameraCutout(enabled)
        }
    }

    fun saveProxy(enabled: Boolean, type: ProxyType, host: String, port: Int, user: String, pass: String) {
        viewModelScope.launch {
            settingsRepository.setProxy(enabled, type, host, port, user, pass)
        }
    }

    fun clearProxyTestResult() { _proxyTestResult.value = null }

    /** Fetches the public IP through the given proxy so the user can confirm it works. */
    fun testProxy(type: ProxyType, host: String, port: Int, user: String, pass: String) {
        viewModelScope.launch {
            _proxyTestResult.value = "Testing…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cfg = ProxyConfig(type, host.trim(), port, user.trim().ifBlank { null }, pass.ifBlank { null })
                    val jType = if (type == ProxyType.SOCKS5) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
                    val builder = okhttp3.OkHttpClient.Builder()
                        .proxy(java.net.Proxy(jType, java.net.InetSocketAddress.createUnresolved(cfg.host, cfg.port)))
                        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    if (type == ProxyType.HTTP && !cfg.username.isNullOrBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            response.request.newBuilder()
                                .header("Proxy-Authorization", okhttp3.Credentials.basic(cfg.username, cfg.password ?: ""))
                                .build()
                        }
                    }
                    if (type == ProxyType.SOCKS5 && !cfg.username.isNullOrBlank()) {
                        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                            override fun getPasswordAuthentication() =
                                java.net.PasswordAuthentication(cfg.username, (cfg.password ?: "").toCharArray())
                        })
                    }
                    val client = builder.build()
                    val req = okhttp3.Request.Builder().url("https://api.ipify.org?format=text").build()
                    client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) "Connected — public IP: ${r.body?.string()?.trim()}"
                        else "Proxy responded ${r.code}"
                    }
                }.getOrElse { "Failed: ${it.message ?: it.javaClass.simpleName}" }
            }
            _proxyTestResult.value = result
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

    /** Accepts a comma-separated list, e.g. "en, es, fr". Order is the preference order. */
    fun setSubtitleLanguages(raw: String) {
        viewModelScope.launch {
            settingsRepository.setSubtitleLanguages(raw.split(","))
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }
}
