# Add project specific ProGuard rules here.
# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep data classes used by serialization
-keep class com.example.jkt48bonusmonitor.data.** { *; }
-keep class com.example.jkt48bonusmonitor.domain.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
