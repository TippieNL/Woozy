# kotlinx.serialization: keep generated serializers for DTOs.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class app.woozy.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.woozy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces are accessed reflectively.
-keep,allowobfuscation interface app.woozy.data.api.** { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# MapLibre native bindings.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
