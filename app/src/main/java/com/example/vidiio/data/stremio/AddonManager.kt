package com.example.vidiio.data.stremio

import android.util.Log
import com.example.vidiio.data.api.StremioService
import com.example.vidiio.data.model.stremio.Manifest
import com.example.vidiio.data.model.stremio.StremioMeta
import com.example.vidiio.data.model.stremio.StremioStream
import com.example.vidiio.data.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class AddonManager(
    private val stremioService: StremioService,
    private val settingsRepository: SettingsRepository
) {
    private val manifestCache = mutableMapOf<String, Manifest>()

    suspend fun getInstalledAddons(): List<Manifest> = coroutineScope {
        val urls = settingsRepository.stremioAddonsFlow.first()
        val sortedUrls = urls.sortedByDescending { it.contains("torrentio") }
        sortedUrls.map { url ->
            async {
                manifestCache[url] ?: try {
                    val manifest = stremioService.getManifest(url)
                    manifestCache[url] = manifest
                    manifest
                } catch (e: Exception) {
                    Log.e("AddonManager", "Error fetching manifest from $url", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun getStreams(type: String, id: String): List<StremioStream> = coroutineScope {
        val addons = getInstalledAddons()
        val streamAddons = addons.filter { manifest ->
            val resources = manifest.resources
            resources != null && (resources.contains("stream") || resources.any { it is Map<*, *> && it["name"] == "stream" })
        }

        streamAddons.map { manifest ->
            async {
                try {
                    val manifestUrl = settingsRepository.stremioAddonsFlow.first().find { url ->
                        url.contains(manifest.id) || manifestCache[url] == manifest
                    } ?: return@async emptyList()
                    
                    val addonBaseUrl = manifestUrl.substringBeforeLast("/") + "/"
                    val url = "${addonBaseUrl}stream/$type/$id.json"
                    stremioService.getStreams(url).streams
                } catch (e: Exception) {
                    Log.e("AddonManager", "Error fetching streams from ${manifest.name}", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun getCatalogs(): List<Pair<String, List<StremioMeta>>> = coroutineScope {
        val addons = getInstalledAddons()
        val catalogAddons = addons.filter { it.catalogs != null && it.catalogs.isNotEmpty() }

        catalogAddons.flatMap { manifest ->
            val manifestUrl = settingsRepository.stremioAddonsFlow.first().find { url ->
                url.contains(manifest.id) || manifestCache[url] == manifest
            } ?: return@flatMap emptyList()
            val addonBaseUrl = manifestUrl.substringBeforeLast("/") + "/"

            manifest.catalogs!!.map { catalog ->
                async {
                    try {
                        val url = "${addonBaseUrl}catalog/${catalog.type}/${catalog.id}.json"
                        val response = stremioService.getCatalog(url)
                        (catalog.name ?: manifest.name) to response.metas
                    } catch (e: Exception) {
                        Log.e("AddonManager", "Error fetching catalog ${catalog.name} from ${manifest.name}", e)
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }
}
