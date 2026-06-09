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
 *
 * Every call also delegates to [LogRepository] so logs are persisted in the
 * in-app database and viewable from the Log Viewer screen.
 */
@Singleton
class AionLogger @Inject constructor(
    private val logRepo: LogRepository,
) {

    fun d(tag: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        val msg = redact(message())
        Log.d(tag, msg)
        logRepo.d(tag, msg)
    }

    fun i(tag: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        val msg = redact(message())
        Log.i(tag, msg)
        logRepo.i(tag, msg)
    }

    fun w(tag: String, message: () -> String) {
        val msg = redact(message())
        Log.w(tag, msg)
        logRepo.w(tag, msg)
    }

    fun w(tag: String, t: Throwable, message: () -> String) {
        val msg = redact(message())
        Log.w(tag, msg, t)
        logRepo.w(tag, t, msg)
    }

    fun e(tag: String, message: () -> String) {
        val msg = redact(message())
        Log.e(tag, msg)
        logRepo.e(tag, msg)
    }

    fun e(tag: String, t: Throwable, message: () -> String) {
        val msg = redact(message())
        Log.e(tag, msg, t)
        logRepo.e(tag, t, msg)
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
            Regex("""sk-ant-[A-Za-z0-9_-]{16,}"""),
            Regex("""sk-proj-[A-Za-z0-9_-]{16,}"""),
            Regex("""t1v[A-Za-z0-9_-]{16,}"""),
        )
    }
}
