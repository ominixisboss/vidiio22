package com.example.vidiio.torrent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the bundled TorrServer engine as a subprocess and exposes its loopback HTTP API,
 * exactly the way PlayTorrioV3 does through `torrserver_flutter`
 * (lib/src/torrserver_controller_subprocess.dart).
 *
 * The engine binary ships in the APK as `jniLibs/<abi>/libtorrserver.so` (fetched at build
 * time by the `downloadTorrServer` Gradle task) and is executed from `nativeLibraryDir`,
 * the only app-writable location Android still allows `exec()` from on API 29+.
 */
class TorrServerEngine(private val context: Context) {

    @Volatile
    var isAvailable: Boolean = false
        private set

    @Volatile
    private var process: Process? = null

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    private var api: TorrServerApi? = null

    val baseUrl: String get() = "http://127.0.0.1:$port"

    fun getApi(): TorrServerApi? = api

    private val binaryFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    private val dataDir: File
        get() = File(context.filesDir, "torrserver").apply { mkdirs() }

    /** True once the engine binary is present in the APK for this device's ABI. */
    val isInstalled: Boolean get() = binaryFile.exists()

    /**
     * Starts the engine if it isn't already running and blocks until `/echo` responds.
     * Safe to call repeatedly and from multiple threads.
     */
    @Synchronized
    fun ensureStarted(): Boolean {
        if (isAvailable && process?.isAlive == true && api?.echo() != null) return true
        stop()

        val bin = binaryFile
        if (!bin.exists()) {
            Log.e(TAG, "TorrServer binary not found at ${bin.absolutePath}. Run the downloadTorrServer Gradle task.")
            return false
        }
        if (!bin.canExecute()) runCatching { bin.setExecutable(true, false) }

        val dir = dataDir
        // Shut down any TorrServer left behind by a previous run / crash so it can't hold
        // the BoltDB lock on our data dir, then take the first free port in our range.
        shutdownStaleInstances()
        val chosenPort = pickPort()

        val proc = try {
            ProcessBuilder(
                bin.absolutePath,
                "-p", chosenPort.toString(),
                "-d", dir.absolutePath,
            ).apply {
                directory(dir)
                redirectErrorStream(true)
                // Route the torrent engine's traffic through the user's proxy ("VPN"),
                // the same way qBittorrent-style SOCKS proxying works.
                (context.applicationContext as? com.example.vidiio.VidiioApplication)?.proxyConfig?.let { cfg ->
                    val uri = cfg.toUri()
                    environment()["ALL_PROXY"] = uri
                    environment()["all_proxy"] = uri
                    if (cfg.type == com.example.vidiio.data.repository.ProxyType.HTTP) {
                        environment()["HTTP_PROXY"] = uri
                        environment()["HTTPS_PROXY"] = uri
                        environment()["http_proxy"] = uri
                        environment()["https_proxy"] = uri
                    }
                    environment()["NO_PROXY"] = "127.0.0.1,localhost"
                    Log.i(TAG, "TorrServer proxy: ${cfg.type} ${cfg.host}:${cfg.port}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exec TorrServer binary", e)
            return false
        }

        process = proc
        port = chosenPort
        api = TorrServerApi(baseUrl)

        thread(isDaemon = true, name = "torrserver-log") {
            runCatching {
                proc.inputStream.bufferedReader().forEachLine { Log.d(TAG, "[ts] $it") }
            }
        }

        val deadline = SystemClock.elapsedRealtime() + STARTUP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!proc.isAlive) {
                Log.e(TAG, "TorrServer exited during startup (code ${runCatching { proc.exitValue() }.getOrNull()})")
                stop()
                return false
            }
            if (api?.echo() != null) {
                isAvailable = true
                Log.i(TAG, "TorrServer ready on $baseUrl")
                return true
            }
            Thread.sleep(200)
        }
        Log.e(TAG, "TorrServer did not become ready within ${STARTUP_TIMEOUT_MS}ms")
        stop()
        return false
    }

    @Synchronized
    fun stop() {
        runCatching { api?.shutdown() }
        val proc = process
        process = null
        api = null
        isAvailable = false
        port = 0
        if (proc != null) {
            runCatching { proc.destroy() }
            runCatching {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroyForcibly()
            }
        }
    }

    /** Asks any TorrServer answering in our port range to shut down (frees the DB lock). */
    private fun shutdownStaleInstances() {
        for (p in PORT_RANGE) {
            runCatching {
                TorrServerApi("http://127.0.0.1:$p").takeIf { it.echo(250) != null }?.let {
                    Log.i(TAG, "Shutting down stale TorrServer on port $p")
                    it.shutdown()
                }
            }
        }
        Thread.sleep(200)
    }

    private fun pickPort(): Int {
        for (p in PORT_RANGE) if (isPortFree(p)) return p
        return runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(PORT_RANGE.first)
    }

    private fun isPortFree(port: Int): Boolean = try {
        ServerSocket().use {
            it.reuseAddress = false
            it.bind(InetSocketAddress("127.0.0.1", port))
            true
        }
    } catch (e: IOException) {
        false
    }

    companion object {
        private const val TAG = "TorrServerEngine"
        private const val BINARY_NAME = "libtorrserver.so"
        private val PORT_RANGE = 8090..8099
        private const val STARTUP_TIMEOUT_MS = 20_000L
    }
}
