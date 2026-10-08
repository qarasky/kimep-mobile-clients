# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.qarasky.unofficialkimep.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class dev.qarasky.unofficialkimep.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
