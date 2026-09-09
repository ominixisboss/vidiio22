package com.ominix.vidiio

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import com.ominix.vidiio.data.db.VidiioDatabase
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.FavoriteRepository
import com.ominix.vidiio.data.repository.MovieRepository
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
import com.ominix.vidiio.data.stremio.AddonManager
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class VidiioApplication : Application() {

    lateinit var movieRepository: MovieRepository
        private set

    lateinit var okHttpClient: OkHttpClient
        private set

    /**
     * OkHttp client for media playback (ExoPlayer). Same config as [okHttpClient] but with
     * no interceptors - a BODY-level logging interceptor buffers entire response bodies,
     * which is fatal for streaming video.
     */
    lateinit var playbackHttpClient: OkHttpClient
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var favoriteRepository: FavoriteRepository
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var downloadManager: DownloadManager
        private set

    lateinit var watchProgressRepository: WatchProgressRepository
        private set


    /** Embedded TorrServer engine used for movie/series torrent streaming (PlayTorrio-style). */
    lateinit var torrServerEngine: TorrServerEngine
        private set

    lateinit var addonManager: AddonManager
        private set

    lateinit var subdlService: SubdlService
        private set

    /** Active proxy config, resolved once at startup. Null = direct. */
    var proxyConfig: com.ominix.vidiio.data.repository.ProxyConfig? = null
        private set

    override fun onCreate() {
        super.onCreate()
        Rive.init(this)

        settingsRepository = SettingsRepository(this)

        // Read the proxy ("VPN") config once at startup. Changing it needs an app restart.
        proxyConfig = kotlinx.coroutines.runBlocking { settingsRepository.proxyConfigFlow.first() }
        applyProxyAuthenticator(proxyConfig)

        val database = VidiioDatabase.build(this)

        favoriteRepository = FavoriteRepository(database.favoriteDao())
        downloadRepository = DownloadRepository(database.downloadDao())
        downloadManager = DownloadManager(this, downloadRepository)
        watchProgressRepository = WatchProgressRepository(database.watchProgressDao())
        torrServerEngine = TorrServerEngine(this)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BODY buffers every full response into memory before returning it — with the
            // HTML-scraping sources that means multi-MB pages copied + UTF-8 decoded on the
            // hot path. HEADERS keeps the useful request/response lines without that cost.
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val bootstrapClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .applyProxy(proxyConfig)
            .build()

        val dns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001")
            ))
            .build()

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .applyProxy(proxyConfig)
            .build()

        playbackHttpClient = okHttpClient.newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        val tmdbService = retrofit.create(TMDBService::class.java)
        val stremioService = retrofit.create(StremioService::class.java)

        addonManager = AddonManager(stremioService, settingsRepository)

        val subdlRetrofit = Retrofit.Builder()
            .baseUrl("https://api.subdl.com/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        subdlService = subdlRetrofit.create(SubdlService::class.java)

        val cinejoyScraper = CinejoyScraper(okHttpClient)
        val movyScraper = MovyScraper(okHttpClient)
        val a111477Scraper = A111477Scraper(okHttpClient)
        val vidsrcScraper = VidSrcScraper(okHttpClient)
        val videasyScraper = VidEasyScraper(okHttpClient)
        val knabenScraper = KnabenScraper(okHttpClient)
        val tgScraper = TorrentGalaxyScraper(okHttpClient)
        val ottsxScraper = OneThreeThreeSevenXScraper(okHttpClient)
        val ytsScraper = YtsScraper(okHttpClient)
        val vadapavScraper = VadapavScraper(okHttpClient)
        val vuflixScraper = VuflixScraper(okHttpClient)

        movieRepository = MovieRepository(
            tmdbService,
            listOf(
                cinejoyScraper, 
                movyScraper, 
                a111477Scraper, 
                vidsrcScraper, 
                videasyScraper, 
                knabenScraper, 
                tgScraper, 
                ottsxScraper,
                ytsScraper,
                vadapavScraper, 
                vuflixScraper
            ),
            settingsRepository,
            addonManager
        )
    }

    private fun OkHttpClient.Builder.applyProxy(
        cfg: com.ominix.vidiio.data.repository.ProxyConfig?
    ): OkHttpClient.Builder {
        if (cfg == null) return this
        val javaType = if (cfg.type == com.ominix.vidiio.data.repository.ProxyType.SOCKS5)
            java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
        proxy(java.net.Proxy(javaType, java.net.InetSocketAddress.createUnresolved(cfg.host, cfg.port)))
        if (cfg.type == com.ominix.vidiio.data.repository.ProxyType.HTTP && !cfg.username.isNullOrBlank()) {
            proxyAuthenticator { _, response ->
                val credential = okhttp3.Credentials.basic(cfg.username, cfg.password ?: "")
                response.request.newBuilder().header("Proxy-Authorization", credential).build()
            }
        }
        return this
    }

    /** SOCKS5 username/password auth is done through the JVM-wide Authenticator. */
    private fun applyProxyAuthenticator(cfg: com.ominix.vidiio.data.repository.ProxyConfig?) {
        if (cfg == null || cfg.type != com.ominix.vidiio.data.repository.ProxyType.SOCKS5 ||
            cfg.username.isNullOrBlank()
        ) return
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                if (requestingHost.equals(cfg.host, ignoreCase = true) && requestingPort == cfg.port) {
                    return java.net.PasswordAuthentication(cfg.username, (cfg.password ?: "").toCharArray())
                }
                return null
            }
        })
    }
}
