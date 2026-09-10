# Vidiio

Android streaming app. Catalogue from TMDB, playback sources from Stremio addons and a
set of HTML scrapers, torrent streaming through an embedded TorrServer engine, plus
downloads, styled ASS/SSA subtitles, Chromecast and Android TV.

Built with Compose, Media3 and Room. `minSdk` 24.

## Building

You need Android Studio (or a JDK 25 toolchain) and the Android SDK for API 37.

```bash
./gradlew :app:assembleDebug
```

The first build runs the `downloadTorrServer` task, which fetches the TorrServer engine
binary from GitHub Releases into `app/src/main/jniLibs/<abi>/libtorrserver.so`. It is
pinned by version and verified against a SHA-256, and it needs network access on that
first run. Two ways to control it:

```bash
./gradlew :app:assembleDebug -Ptorrserver.abis=arm64-v8a,x86_64
```

```bash
TORRSERVER_LOCAL_BINARIES=/path/to/dir ./gradlew :app:assembleDebug
```

The first overrides the ABI set (default `arm64-v8a,armeabi-v7a`); the second uses
binaries you already have instead of downloading. That directory should contain the
release assets under their original names, e.g. `TorrServer-android-arm64`.

## Release builds

```bash
./gradlew :app:assembleRelease
```

Release builds run R8. Keep rules live in `app/src/main/keepRules/*.keep` — R8 failures
show up at runtime rather than at build time, so smoke-test an actual release APK
(browse, search, play, subtitles, a download, torrent streaming) rather than trusting a
green build. `build/outputs/mapping/release/mapping.txt` deobfuscates release stack
traces.

Signing is optional: without it the release APK is simply unsigned. To sign, copy
`app/keystore.properties.example` to `app/keystore.properties` and fill it in, or set
`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` in the environment.

Version is overridable so CI does not have to edit the file:

```bash
./gradlew :app:assembleRelease -PversionCode=42 -PversionName=1.4.2
```

## Configuration

| What | Where |
| --- | --- |
| TMDB API key | `tmdb.apiKey` in `local.properties`, or `TMDB_API_KEY` in the environment |
| SubDL API key | Entered by the user in Settings |
| Signing | `app/keystore.properties`, or `KEYSTORE_*` env vars |

All three files are gitignored. The TMDB key falls back to a hardcoded one so a fresh
clone builds and runs without setup.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

The second needs a device or emulator. It includes `VidiioDatabaseMigrationTest`, which
replays the committed Room schemas in `app/schemas/`. When you bump the database version,
build once so KSP writes the new schema JSON, commit it, and add the migration to
`data/db/Migrations.kt` — schema bumps without a migration fail at database open rather
than wiping the user's library.

## Layout

```
data/api         Retrofit services (TMDB, Stremio, SubDL)
data/scraper     HTML/JSON scrapers, one per source, behind a common Scraper interface
data/stremio     Stremio addon manifest handling and stream mapping
data/repository  Repositories and DataStore-backed settings
data/db          Room database, DAOs, migrations
torrent          Embedded TorrServer engine, session and file selection
download         HTTP / HLS / torrent downloaders and the foreground service
ui/screens       Compose screens
ui/player        Player UI, split into components
ui/viewmodel     ViewModels and their factories
```

Dependencies are wired by hand in `VidiioApplication`, which acts as a service locator.
`onCreate` must stay non-blocking — it runs before the first frame, so anything touching
disk or network belongs in a `by lazy` or on the application scope.
