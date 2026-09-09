package com.ominix.vidiio

import android.app.Application
import android.util.Log
import app.rive.runtime.kotlin.core.Rive
import com.ominix.vidiio.data.db.VidiioDatabase
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.FavoriteRepository
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.ProxyConfig
import com.ominix.vidiio.data.repository.ProxyType
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.WatchProgressRepository
import com.ominix.vidiio.download.DownloadManager
import com.ominix.vidiio.torrent.TorrServerEngine
import com.ominix.vidiio.data.scraper.CinejoyScraper
import com.ominix.vidiio.data.scraper.MovyScraper
import com.ominix.vidiio.data.scraper.A111477Scraper
import com.ominix.vidiio.data.scraper.VidSrcScraper
import com.ominix.vidiio.data.scraper.VidEasyScraper
import com.ominix.vidiio.data.scraper.KnabenScraper
import com.ominix.vidiio.data.scraper.TorrentGalaxyScraper
import com.ominix.vidiio.data.scraper.OneThreeThreeSevenXScraper
import com.ominix.vidiio.data.scraper.YtsScraper
import com.ominix.vidiio.data.scraper.VadapavScraper
import com.ominix.vidiio.data.scraper.VuflixScraper
import com.ominix.vidiio.data.api.TMDBService
import com.ominix.vidiio.data.api.StremioService
import com.ominix.vidiio.data.api.SubdlService
import com.ominix.vidiio.data.api.TmdbApiKeyInterceptor
import com.ominix.vidiio.data.stremio.AddonManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Service locator for the app graph.
 *
 * Two rules hold this together:
 *
 * 1. **[onCreate] must not block.** It runs on the main thread before the first frame, so
 *    anything touching disk or the network belongs in a `by lazy` below (built on whichever
 *    thread first needs it) or on [applicationScope].
 * 2. **Proxy settings load asynchronously.** See [proxyConfig].
 */
class VidiioApplication : Application() {

    /** Outlives every screen; used for app-wide settings collection. Never cancelled. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val database: VidiioDatabase by lazy { VidiioDatabase.build(this) }

    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(database.favoriteDao()) }
    val downloadRepository: DownloadRepository by lazy { DownloadRepository(database.downloadDao()) }
    val downloadManager: DownloadManager by lazy { DownloadManager(this, downloadRepository) }
    val watchProgressRepository: WatchProgressRepository by lazy {
        WatchProgressRepository(database.watchProgressDao())
    }

    /** Embedded TorrServer engine used for movie/series torrent streaming (PlayTorrio-style). */
    val torrServerEngine: TorrServerEngine by lazy { TorrServerEngine(this) }

    // ── Proxy ("VPN") ────────────────────────────────────────────────────────────
    //
    // This used to be `runBlocking { proxyConfigFlow.first() }` in onCreate. A first-ever
    // DataStore read is a disk read plus a proto parse, and doing it on the main thread
    // stalls cold start before the splash can draw.
    //
    // Instead a collector on applicationScope keeps [proxyConfig] current and
    // [proxySelector] reads it per connection - which also means changing the proxy takes
    // effect on the next connection rather than requiring an app restart.

    /**
     * Active proxy config, or null for direct. Written from [applicationScope], read from
     * OkHttp's dispatcher threads, hence `@Volatile`.
     */
    @Volatile
    var proxyConfig: ProxyConfig? = null
        private set

    /** Completes on the first emission, so no request can race ahead of the setting. */
    private val proxyLoaded = CompletableDeferred<Unit>()

    /**
     * Per-request proxy decision.
     *
     * Deliberately a selector rather than `OkHttpClient.Builder.proxy()`: the value is read
     * at connection time, so it picks up changes, and loopback can be excluded.
     */
    private val proxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val cfg = proxyConfig ?: return DIRECT

            // The embedded TorrServer listens on loopback. Routing that through an external
            // proxy cannot work, and would hand it a list of what is being streamed.
            val host = uri?.host
            if (host == "127.0.0.1" || host == "::1" || host.equals("localhost", ignoreCase = true)) {
                return DIRECT
            }

            val javaType = if (cfg.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
            return listOf(Proxy(javaType, InetSocketAddress.createUnresolved(cfg.host, cfg.port)))
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, e: IOException?) {
            Log.w(TAG, "Proxy connect failed for $uri via $sa", e)
        }
    }

    /**
     * Holds the very first request until the proxy setting has loaded.
     *
     * This runs on an OkHttp dispatcher thread, never the main thread, so blocking here is
     * safe - and it is what stops a cold-start request from leaking past a configured
     * proxy. The timeout degrades a broken DataStore to "direct" instead of hanging every
     * request forever.
     */
    private val proxyGateInterceptor = Interceptor { chain ->
        if (!proxyLoaded.isCompleted) {
            val loaded = runBlocking {
                withTimeoutOrNull(PROXY_LOAD_TIMEOUT_MS) { proxyLoaded.await() }
            }
            if (loaded == null) {
                Log.w(TAG, "Proxy settings did not load in ${PROXY_LOAD_TIMEOUT_MS}ms - going direct")
            }
        }
        chain.proceed(chain.request())
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────────

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            // BODY buffers every full response into memory before returning it - with the
            // HTML-scraping sources that means multi-MB pages copied + UTF-8 decoded on the
            // hot path. HEADERS keeps the useful request/response lines without that cost.
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }

    /** Shared base: proxy handling and timeouts. No logging, no DNS override. */
    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .proxySelector(proxySelector)
        .addInterceptor(proxyGateInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)

    private val dns by lazy {
        val bootstrapClient = baseClientBuilder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001")
            ))
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        baseClientBuilder()
            .addInterceptor(loggingInterceptor)
            .dns(dns)
            .build()
    }

    /**
     * OkHttp client for media playback (ExoPlayer): same config as [okHttpClient] minus
     * logging, because a logging interceptor buffers whole response bodies, which is fatal
     * for streaming video.
     *
     * Built from [baseClientBuilder] rather than by clearing interceptors off
     * [okHttpClient] - clearing drops the proxy gate too, which would let the first
     * playback request slip past a configured proxy.
     */
    val playbackHttpClient: OkHttpClient by lazy {
        baseClientBuilder().dns(dns).build()
    }

    // ── API services and repositories ────────────────────────────────────────────

    /**
     * TMDB client. Separate from [okHttpClient] purely so the api_key interceptor is not
     * attached to the client the Stremio addons and scrapers use.
     */
    private val tmdbHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .addInterceptor(TmdbApiKeyInterceptor(BuildConfig.TMDB_API_KEY))
            .build()
    }

    private val tmdbRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(tmdbHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    /**
     * Stremio addon calls. Same base URL as [tmdbRetrofit] (the addon endpoints are
     * absolute @Url values anyway) but on the un-keyed client.
     */
    private val stremioRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    val subdlService: SubdlService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.subdl.com/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SubdlService::class.java)
    }

    val addonManager: AddonManager by lazy {
        AddonManager(stremioRetrofit.create(StremioService::class.java), settingsRepository)
    }

    val movieRepository: MovieRepository by lazy {
        MovieRepository(
            tmdbRetrofit.create(TMDBService::class.java),
            listOf(
                CinejoyScraper(okHttpClient),
                MovyScraper(okHttpClient),
                A111477Scraper(okHttpClient),
                VidSrcScraper(okHttpClient),
                VidEasyScraper(okHttpClient),
                KnabenScraper(okHttpClient),
                TorrentGalaxyScraper(okHttpClient),
                OneThreeThreeSevenXScraper(okHttpClient),
                YtsScraper(okHttpClient),
                VadapavScraper(okHttpClient),
                VuflixScraper(okHttpClient)
            ),
            settingsRepository,
            addonManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        Rive.init(this)

        applicationScope.launch {
            settingsRepository.proxyConfigFlow.collectLatest { cfg ->
                proxyConfig = cfg
                applyProxyAuthenticator(cfg)
                proxyLoaded.complete(Unit)
            }
        }
    }

    /** SOCKS5 username/password auth is done through the JVM-wide Authenticator. */
    private fun applyProxyAuthenticator(cfg: ProxyConfig?) {
        if (cfg == null || cfg.type != ProxyType.SOCKS5 || cfg.username.isNullOrBlank()) {
            // Clear any previously installed authenticator, or credentials from an old
            // config outlive the config itself.
            java.net.Authenticator.setDefault(null)
            return
        }
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                if (requestingHost.equals(cfg.host, ignoreCase = true) && requestingPort == cfg.port) {
                    return java.net.PasswordAuthentication(cfg.username, (cfg.password ?: "").toCharArray())
                }
                return null
            }
        })
    }

    private companion object {
        const val TAG = "VidiioApplication"
        const val PROXY_LOAD_TIMEOUT_MS = 3_000L
        val DIRECT: List<Proxy> = listOf(Proxy.NO_PROXY)
    }
}
