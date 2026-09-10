# ── Project ──────────────────────────────────────────────────────────────────

# Enums are round-tripped through valueOf(String) in the Room type converters
# (DownloadType/DownloadStatus), the entity mappers (MovieType) and every
# settings flow (AppTheme/ColorTheme/HomeStyle/ProxyType). Obfuscating the
# constant names makes each of those throw IllegalArgumentException on a value
# that was written by a previous, unobfuscated build.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Services and the Application are named in the manifest; AGP keeps those, but
# they are also started via explicit Intent(context, X::class.java).
-keep class com.example.vidiio.VidiioApplication
-keep class com.example.vidiio.torrent.TorrentService
-keep class com.example.vidiio.download.DownloadService

# Useful release stack traces. Without this, a crash report is unreadable.
# Keep the mapping.txt that R8 writes under build/outputs/mapping/release/.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
