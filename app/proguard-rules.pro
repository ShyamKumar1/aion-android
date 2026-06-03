# Add project specific ProGuard rules here.

# Keep JNI surface for llama.cpp bridge (Phase 2)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep,allowobfuscation,allowshrinking class com.aion.agent.llm.LlamaBridge
-keep,allowobfuscation,allowshrinking class com.aion.agent.llm.LlamaBridge$* { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.RetainedLifecycleImpl

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep our internal model classes for the LLM layer
-keep class com.aion.agent.llm.providers.** { *; }
-keep class com.aion.agent.core.AionException { *; }
-keep class com.aion.agent.core.AionException$* { *; }
