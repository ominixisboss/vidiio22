package com.example.vidiio

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import androidx.room.Room
import com.example.vidiio.data.db.VidiioDatabase
import com.example.vidiio.data.repository.DownloadRepository
import com.example.vidiio.data.repository.FavoriteRepository
import com.example.vidiio.data.repository.MovieRepository
import com.example.vidiio.data.repository.SettingsRepository
import com.example.vidiio.download.DownloadManager
import com.example.vidiio.torrent.TorrentEngine
import com.example.vidiio.data.scraper.CinejoyScraper
import com.example.vidiio.data.scraper.MovyScraper
import com.example.vidiio.data.scraper.A111477Scraper
import com.example.vidiio.data.scraper.VidSrcScraper
import com.example.vidiio.data.scraper.VidEasyScraper
import com.example.vidiio.data.scraper.KnabenScraper
import com.example.vidiio.data.scraper.TorrentGalaxyScraper
import com.example.vidiio.data.scraper.OneThreeThreeSevenXScraper
import com.example.vidiio.data.scraper.YtsScraper
import com.example.vidiio.data.scraper.VadapavScraper
import com.example.vidiio.data.scraper.VuflixScraper
import com.example.vidiio.data.api.TMDBService
import com.example.vidiio.data.api.StremioService
import com.example.vidiio.data.api.SubdlService
import com.example.vidiio.data.stremio.AddonManager
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class VidiioApplication : Application() {

    lateinit var movieRepository: MovieRepository
        private set

    lateinit var okHttpClient: OkHttpClient
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var favoriteRepository: FavoriteRepository
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var downloadManager: DownloadManager
        private set

    lateinit var torrentEngine: TorrentEngine
        private set

    lateinit var addonManager: AddonManager
        private set

    lateinit var subdlService: SubdlService
        private set

    override fun onCreate() {
        super.onCreate()
        Rive.init(this)

        settingsRepository = SettingsRepository(this)

        val database = Room.databaseBuilder(
            applicationContext,
            VidiioDatabase::class.java, "vidiio-database"
        ).fallbackToDestructiveMigration().build()

        favoriteRepository = FavoriteRepository(database.favoriteDao())
        downloadRepository = DownloadRepository(database.downloadDao())
        downloadManager = DownloadManager(this, downloadRepository)
        torrentEngine = TorrentEngine(this)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val bootstrapClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
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
}
