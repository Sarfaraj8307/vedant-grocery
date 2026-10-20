# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-keep class io.ktor.** { *; }

# kotlinx.serialization — the Supabase SDK models are @Serializable, and R8 must
# not strip their generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.vedant.admin.**$$serializer { *; }
-keepclassmembers class com.vedant.admin.** {
    *** Companion;
}
-keepclasseswithmembers class com.vedant.admin.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Wire DTOs (T03) -------------------------------------------------------
# The PostgREST DTOs and the Money value class are decoded reflectively by
# kotlinx.serialization. R8 must keep every declared member and the @SerialName
# mappings, or a release build silently decodes into empty/renamed fields — a
# data-integrity bug, not a crash. Keep the whole DTO + serializer surface.
-keepclassmembers class com.vedant.admin.data.remote.dto.** {
    *;
}
-keep class com.vedant.admin.data.remote.dto.** { *; }
-keep class com.vedant.admin.core.money.MoneySerializer { *; }
-keep,includedescriptorclasses class com.vedant.admin.core.money.** { *; }

# Keep @SerialName/@Serializable metadata on our own serializable types.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault