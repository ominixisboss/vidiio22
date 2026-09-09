import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "com.ominix.vidiio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Deliberately still com.example.vidiio while the source package is com.ominix.vidiio.
        // applicationId is the install identity: changing it orphans every existing
        // install (no update path, local library gone). Change it in the same release
        // that first ships to Play, not before.
        applicationId = "com.example.vidiio"

        // 24, not 23: res/xml/network_security_config.xml is only honoured from API 24,
        // so on 23 the cleartext lockdown silently does nothing.
        minSdk = 24
        targetSdk = 37
        // Single source of truth for the shipped version. CI can override without
        // editing the file: -PversionCode=42 -PversionName=1.4.2
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 2
        versionName = (project.findProperty("versionName") as String?) ?: "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    // Release signing. Reads app/keystore.properties (gitignored) or, for CI, the
    // KEYSTORE_* environment variables. When neither is present the release build
    // stays unsigned rather than failing, so a plain `assembleRelease` still works
    // for a local smoke test.
    val keystorePropsFile = rootProject.file("app/keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use(::load)
    }
    fun secret(key: String, env: String): String? =
        (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

    val releaseStorePath = secret("storeFile", "KEYSTORE_FILE")

    signingConfigs {
        if (releaseStorePath != null) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = secret("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "KEY_ALIAS")
                keyPassword = secret("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate. Keep rules live in
            // src/main/keepRules/*.pro - AGP feeds every file in that directory to R8.
            // Turning this off (as it was) shipped every unused class in every
            // dependency, which is most of why the release APK was ~45MB.
            optimization {
                enable = true
            }
            isShrinkResources = true

            signingConfig = signingConfigs.findByName("release")
        }

        debug {
            // Keep debug builds fast and debuggable - never shrink them.
            optimization {
                enable = false
            }
            isShrinkResources = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // The TorrServer engine ships as libtorrserver.so but is a Go executable,
            // not a real shared object - never let AGP strip it.
            keepDebugSymbols += "**/libtorrserver.so"
            // rive-android and ass-kt both bundle libc++_shared.so - take one.
            pickFirsts += "**/libc++_shared.so"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // Committed Room schemas (app/schemas) are what MigrationTestHelper replays against,
    // so they must ship to the androidTest APK as assets.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

// Room writes the exported schema JSON here on every build. Commit each new file:
// it is the only record of what the previous schema looked like, and without it no
// migration can be written or tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.jsoup)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.cast)
    implementation(libs.ass.media)
    implementation(libs.play.services.cast.framework)
    implementation(libs.rive.android)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

// ─────────────────────────────────────────────────────────────────────────────
// TorrServer engine binary (movie/series torrent streaming, PlayTorrioV3 parity)
//
// PlayTorrioV3 streams via TorrServer, fetched from GitHub Releases at build time by
// torrserver_flutter. This task does the same: it downloads the official TorrServer
// Android binary for each wanted ABI and drops it into jniLibs as `libtorrserver.so`,
// so it is packaged in the APK and executable from nativeLibraryDir at runtime.
//
// Pinned version and, when available, SHA-256 of each asset. Override the ABI set with
//   ./gradlew assembleDebug -Ptorrserver.abis=arm64-v8a,armeabi-v7a,x86_64
// or point at pre-downloaded binaries with the TORRSERVER_LOCAL_BINARIES env var
// (a directory containing files named e.g. TorrServer-android-arm64).
// ─────────────────────────────────────────────────────────────────────────────
val torrServerVersion = "MatriX.144.1"
val torrServerAssets = mapOf(
    "arm64-v8a" to "TorrServer-android-arm64",
    "armeabi-v7a" to "TorrServer-android-arm7",
    "x86_64" to "TorrServer-android-amd64",
    "x86" to "TorrServer-android-386",
)
// sha256 of each raw release asset for torrServerVersion (empty => integrity check skipped).
val torrServerSha256 = mapOf(
    "TorrServer-android-arm64" to "bb7e9b4d0dc894f8da3e32496e7487be93b8f8b04ada549396a7ab4dc85ea63b",
    "TorrServer-android-arm7" to "dd6c9dcfa11a450bff6ebaa8992b1823c32e3b9417657f93c8852271ded3949e",
    "TorrServer-android-amd64" to "58f3152471d01a86454b74f49029e62cdc2b3844451151d950cb40130a81ccb3",
    "TorrServer-android-386" to "70373fd25e9aaa42d8904e296bb8dca24c2037afebd4d8771a1bc1f2bfdc47c9",
)

val downloadTorrServer = tasks.register("downloadTorrServer") {
    group = "torrserver"
    description = "Downloads the TorrServer engine binary into jniLibs/<abi>/libtorrserver.so"

    val jniDir = layout.projectDirectory.dir("src/main/jniLibs").asFile
    val versionMarker = File(jniDir, ".torrserver-version")
    val wantedAbis = ((project.findProperty("torrserver.abis") as String?)
        ?: "arm64-v8a,armeabi-v7a")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // A real TorrServer binary is a >1MB ELF ("\x7fELF"). This rejects stub/placeholder
    // files so a dev's throwaway libtorrserver.so can never get packaged.
    fun File.isElfBinary(): Boolean = exists() && length() > 1_000_000L &&
        inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            .let { it[0] == 0x7f.toByte() && it[1] == 'E'.code.toByte() && it[2] == 'L'.code.toByte() && it[3] == 'F'.code.toByte() }

    outputs.dir(jniDir)
    outputs.upToDateWhen {
        versionMarker.exists() && versionMarker.readText().trim() == torrServerVersion &&
            wantedAbis.all { File(jniDir, "$it/libtorrserver.so").isElfBinary() }
    }

    doLast {
        if (versionMarker.exists() && versionMarker.readText().trim() != torrServerVersion) {
            logger.lifecycle("TorrServer version changed -> cleaning stale jniLibs")
            jniDir.deleteRecursively()
        }

        val localDir = System.getenv("TORRSERVER_LOCAL_BINARIES")?.let { File(it) }

        for (abi in wantedAbis) {
            val asset = torrServerAssets[abi] ?: run {
                logger.warn("Unknown ABI '$abi' - skipping"); continue
            }
            val target = File(jniDir, "$abi/libtorrserver.so")
            if (target.isElfBinary()) continue
            if (target.exists()) {
                logger.lifecycle("Replacing non-ELF $abi/libtorrserver.so (${target.length()} bytes)")
                target.delete()
            }
            target.parentFile.mkdirs()

            val local = localDir?.resolve(asset)
            if (local != null && local.exists()) {
                logger.lifecycle("Using local TorrServer binary for $abi: $local")
                local.copyTo(target, overwrite = true)
            } else {
                val url = "https://github.com/YouROK/TorrServer/releases/download/$torrServerVersion/$asset"
                logger.lifecycle("Downloading TorrServer $torrServerVersion for $abi ...")
                val tmp = File.createTempFile("torrserver-", ".bin")
                try {
                    URI(url).toURL().openStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                    }
                    val expected = torrServerSha256[asset].orEmpty()
                    if (expected.isNotEmpty()) {
                        val md = MessageDigest.getInstance("SHA-256")
                        tmp.inputStream().use { s ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n)
                            }
                        }
                        val got = md.digest().joinToString("") { "%02x".format(it) }
                        if (!got.equals(expected, ignoreCase = true)) {
                            throw GradleException("SHA-256 mismatch for $asset: expected $expected, got $got")
                        }
                        logger.lifecycle("Verified SHA-256 for $asset")
                    } else {
                        logger.warn("No pinned SHA-256 for $asset - integrity check skipped")
                    }
                    tmp.copyTo(target, overwrite = true)
                } finally {
                    tmp.delete()
                }
            }
            target.setReadable(true, false)
            target.setExecutable(true, false)
            logger.lifecycle("TorrServer ready: ${target.relativeTo(rootDir)} (${target.length()} bytes)")
        }

        versionMarker.parentFile.mkdirs()
        versionMarker.writeText(torrServerVersion)
    }
}

tasks.named("preBuild") { dependsOn(downloadTorrServer) }