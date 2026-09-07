package com.example.vidiio.data.stremio

import android.util.Log
import com.example.vidiio.data.api.StremioService
import com.example.vidiio.data.model.stremio.Manifest
import com.example.vidiio.data.model.stremio.StremioMeta
import com.example.vidiio.data.model.stremio.StremioMetaDetail
import com.example.vidiio.data.model.stremio.StremioStream
import com.example.vidiio.data.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** url + resolved manifest for one installed Stremio addon. */
data class InstalledAddon(val url: String, val manifest: Manifest) {
    val baseUrl: String get() = url.substringBeforeLast('/') + "/"
}

class AddonManager(
    private val stremioService: StremioService,
    private val settingsRepository: SettingsRepository
) {
    private val manifestCache = mutableMapOf<String, Manifest>()

    /** User-added addons plus addons bundled permanently with the app. */
    private suspend fun allAddonUrls(): List<String> {
        val user = settingsRepository.stremioAddonsFlow.first()
        return (BUILTIN_ADDON_URLS + user).distinct()
            .sortedByDescending { it.contains("torrentio") }
    }

    suspend fun getInstalledAddons(): List<InstalledAddon> = coroutineScope {
        allAddonUrls().map { url ->
            async {
                val m = manifestCache[url] ?: try {
                    stremioService.getManifest(url).also { manifestCache[url] = it }
                } catch (e: Exception) {
                    Log.e("AddonManager", "Error fetching manifest from $url", e); null
                }
                m?.let { InstalledAddon(url, it) }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun getStreams(type: String, id: String): List<StremioStream> = coroutineScope {
        val streamAddons = getInstalledAddons().filter { it.manifest.hasResource("stream") }
        streamAddons.map { addon ->
            async {
                try {
                    stremioService.getStreams("${addon.baseUrl}stream/$type/$id.json").streams
                } catch (e: Exception) {
                    Log.e("AddonManager", "Error fetching streams from ${addon.manifest.name}", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    /** Full metadata (with episode list) for one item from whichever addon serves it. */
    suspend fun getMeta(type: String, id: String): StremioMetaDetail? = coroutineScope {
        val metaAddons = getInstalledAddons().filter { it.manifest.hasResource("meta") }
        metaAddons.map { addon ->
            async {
                try {
                    stremioService.getMeta("${addon.baseUrl}meta/$type/$id.json").meta
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().firstOrNull { it != null }
    }

    suspend fun getCatalogs(): List<Pair<String, List<StremioMeta>>> = coroutineScope {
        getInstalledAddons()
            .filter { it.url !in BUILTIN_ADDON_URLS && !it.manifest.catalogs.isNullOrEmpty() }
            .flatMap { addon ->
                addon.manifest.catalogs!!.map { catalog ->
                    async {
                        try {
                            val url = "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}.json"
                            (catalog.name ?: addon.manifest.name) to stremioService.getCatalog(url).metas
                        } catch (e: Exception) {
                            Log.e("AddonManager", "Error fetching catalog ${catalog.name}", e); null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
    }

    /** Catalogs from the bundled anime addons only (shown inside the Anime area of Home). */
    suspend fun getAnimeCatalogs(): List<Pair<String, List<StremioMeta>>> = coroutineScope {
        getInstalledAddons()
            .filter { it.url in BUILTIN_ADDON_URLS && !it.manifest.catalogs.isNullOrEmpty() }
            .flatMap { addon ->
                addon.manifest.catalogs!!.map { catalog ->
                    async {
                        runCatching {
                            val url = "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}.json"
                            (catalog.name ?: addon.manifest.name) to stremioService.getCatalog(url).metas
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull().filter { it.second.isNotEmpty() }
    }

    private fun Manifest.hasResource(name: String): Boolean {
        val res = resources ?: return false
        return res.any { r ->
            (r is String && r == name) || (r is Map<*, *> && r["name"] == name)
        }
    }

    companion object {
        /** Permanently bundled addons the user can't remove. */
        val BUILTIN_ADDON_URLS = listOf(
            "https://fedew04.github.io/OnePaceStremio/manifest.json"
        )
    }
}
