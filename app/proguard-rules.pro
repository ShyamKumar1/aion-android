# AION ProGuard/R8 Rules
# Phase 2: llama.cpp JNI — keep native methods
-keep class com.aion.agent.llm.LlamaBridge { *; }
-keep class com.aion.agent.llm.GeneratedTokenCallback { *; }
-keep class com.aion.agent.llm.LlamaException { *; }

# Phase 4: MCP protocol serialization
-keep class com.aion.agent.mcp.** { *; }
-keep class kotlinx.serialization.** { *; }

# Phase 1: Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Ktor / Netty (needed for MCP server)
-keep class io.netty.** { *; }
-keep class io.ktor.** { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep all model/data classes used by Room
-keep class com.aion.agent.memory.db.** { *; }

# General Android rules
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes SourceFile, LineNumberTable
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Application { *; }
-keep class * extends android.view.View { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }

# Keep enum classes (used by AgentCapability, SkillResult, etc.)
-keepclassmembers enum * { *; }
