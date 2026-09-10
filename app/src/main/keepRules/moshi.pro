# ── Moshi ────────────────────────────────────────────────────────────────────
# All JSON models use @JsonClass(generateAdapter = true), so adapters are
# generated at compile time and there is no reflective Kotlin adapter to keep.
# Moshi still LOOKS UP those adapters reflectively, by appending "JsonAdapter"
# to the model's class name — so both the model and its generated adapter must
# keep their names, or every API response fails to parse in release only.

-keep class com.ominix.vidiio.data.model.** { *; }
-keep class com.ominix.vidiio.torrent.Ts** { *; }

# Generated adapters, wherever they landed.
-keep class **JsonAdapter { <init>(...); *; }
-keepnames class com.squareup.moshi.internal.NullSafeJsonAdapter

# @Json(name = "...") must survive, or field renaming silently changes the wire
# format the adapter expects.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
