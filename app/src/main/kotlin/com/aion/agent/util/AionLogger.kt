package com.aion.agent.util

import android.util.Log
import com.aion.agent.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AION's logger. Per AION_GUIDELINES §3 (Things That Are Banned):
 *  - Never log raw user content (messages, contact names, notification text)
 *  - Strip API key patterns: `sk-...`, `AIza...`, any user-registered prefix
 *  - In release builds, only WARN/ERROR are emitted
 */
@Singleton
class AionLogger @Inject constructor() {

    fun d(tag: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, redact(message()))
    }

    fun i(tag: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        Log.i(tag, redact(message()))
    }

    fun w(tag: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        Log.w(tag, redact(message()))
    }

    fun w(tag: String, t: Throwable, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        Log.w(tag, redact(message()), t)
    }

    fun e(tag: String, message: () -> String) {
        Log.e(tag, redact(message()))
    }

    fun e(tag: String, t: Throwable, message: () -> String) {
        Log.e(tag, redact(message()), t)
    }

    private fun redact(input: String): String {
        var out = input
        for (pattern in REDACTION_PATTERNS) {
            out = pattern.replace(out, "[REDACTED]")
        }
        return out
    }

    private companion object {
        val REDACTION_PATTERNS = listOf(
            Regex("""sk-[A-Za-z0-9_-]{16,}"""),
            Regex("""sk-or-[A-Za-z0-9_-]{16,}"""),
            Regex("""AIza[A-Za-z0-9_-]{16,}"""),
            Regex("""nvapi-[A-Za-z0-9_-]{16,}"""),
            Regex("""(?i)bearer\s+[A-Za-z0-9._~+/-]{16,}"""),
        )
    }
}
