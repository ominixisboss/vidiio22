package com.example.vidiio.torrent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
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

        val dataDir = File(context.filesDir, "torrserver").apply { mkdirs() }
        val chosenPort = findFreePort()

        // Shut down any orphaned instance left over from a previous run / crash.
        for (p in setOf(chosenPort, DEFAULT_PORT)) {
            runCatching { TorrServerApi("http://127.0.0.1:$p").takeIf { it.echo(300) != null }?.shutdown() }
        }

        val proc = try {
            ProcessBuilder(
                bin.absolutePath,
                "-p", chosenPort.toString(),
                "-d", dataDir.absolutePath,
            ).apply {
                directory(dataDir)
                redirectErrorStream(true)
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
        if (proc != null) {
            runCatching { proc.destroy() }
            runCatching {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroyForcibly()
            }
        }
    }

    private fun findFreePort(): Int = try {
        ServerSocket(0).use { it.localPort }
    } catch (e: Exception) {
        DEFAULT_PORT
    }

    companion object {
        private const val TAG = "TorrServerEngine"
        private const val BINARY_NAME = "libtorrserver.so"
        private const val DEFAULT_PORT = 8090
        private const val STARTUP_TIMEOUT_MS = 20_000L
    }
}
