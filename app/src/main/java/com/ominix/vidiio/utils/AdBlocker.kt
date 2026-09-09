package com.ominix.vidiio.utils

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.coroutines.CancellationException

object AdBlocker {
    private val adDomains = setOf(
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "googletagmanager.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adtech.de",
        "casalemedia.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "appnexus.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "juicyads.com",
        "ad-maven.com",
        "onclickads.net",
        "yllix.com",
        "adsterra.com",
        "mobicow.com",
        "revenuehits.com",
        "bidvertiser.com",
        "clickadu.com",
        "adcash.com",
        "hilltopads.com",
        "activerevenue.com",
        "evadav.com",
        "mgid.com",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "zeropark.com",
        "propellerclick.com",
        "a.bestcontentfood.top",
        "a.bestcontentmarket.top",
        "vidoza.net",
        "vidmoly.to",
        "gomo.to",
        "2embed.to",
        "upstream.to",
        "dood.to",
        "dood.so",
        "dood.ws",
        "fembed.com",
        "fembed.net",
        "streamtape.com",
        "voe.sx",
    )

    fun isAd(url: String): Boolean {
        if (url.isEmpty()) return false
        
        // Block data URIs if they are too large (often used for tracking pixels or scripts)
        if (url.startsWith("data:") && (url.length > 1024)) return true

        return try {
            val host = URI(url).host?.lowercase() ?: return false
            adDomains.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }
}
