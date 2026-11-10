# DealMate R8 rules. Retrofit, OkHttp, Hilt, Room, Coil, WorkManager and kotlinx.serialization ship
# their own consumer rules; the entries below cover what those cannot know about this app.

-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# --- kotlinx.serialization: generated serializers of the API DTOs ---
-keepclassmembers class com.dealmate.app.data.remote.dto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dealmate.app.data.remote.dto.**$$serializer { *; }
-keep class com.dealmate.app.data.remote.dto.*Serializer { *; }

# --- Retrofit: service interface + generic signatures of suspend functions ---
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.dealmate.app.data.remote.DealMateApi { *; }

# --- Room: entities are referenced from generated code only ---
-keep class com.dealmate.app.data.local.** { *; }

# --- Hilt workers are created reflectively by name through the worker factory ---
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
