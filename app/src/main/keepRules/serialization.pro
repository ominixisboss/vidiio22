# ── kotlinx.serialization ────────────────────────────────────────────────────
# Movie, StreamSource and NavDestinations are @Serializable. Navigation3's
# type-safe routes resolve serializers reflectively via the synthetic
# `$serializer` / `Companion.serializer()` members, which R8 cannot see are
# reachable — losing them breaks navigation at runtime, not at build time.

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclassmembers class **$* {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ominix.vidiio.**$$serializer { *; }
-keepclassmembers class com.ominix.vidiio.** {
    *** Companion;
}
