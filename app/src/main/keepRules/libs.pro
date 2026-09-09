# ── Third-party ──────────────────────────────────────────────────────────────
# OkHttp, Retrofit, Room, Coil and media3 all ship consumer keep rules, so they
# need nothing here. These two are the exceptions worth being explicit about.

# rive-android and ass-media are both JNI-backed: native code resolves these
# classes and their callbacks by name, which R8's reachability analysis cannot
# follow. Stripping or renaming them fails at runtime the first time a Rive
# animation renders or an ASS subtitle track is opened.
-keep class app.rive.runtime.kotlin.** { *; }
-keep class io.github.peerless2012.ass.** { *; }

# Jsoup is used as a plain API by the scrapers, but its Node hierarchy is
# instantiated reflectively during parsing.
-keep class org.jsoup.nodes.** { *; }
