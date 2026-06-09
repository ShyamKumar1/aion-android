package com.aion.agent.util

import com.aion.agent.memory.db.LogDao
import com.aion.agent.memory.db.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured logging levels mirroring (and extending) android.util.Log.
 */
object LogLevel {
    const val DEBUG = "DEBUG"
    const val INFO = "INFO"
    const val WARN = "WARN"
    const val ERROR = "ERROR"
    const val FATAL = "FATAL"

    val ALL = listOf(DEBUG, INFO, WARN, ERROR, FATAL)
    val WARN_PLUS = listOf(WARN, ERROR, FATAL)
    val ERROR_PLUS = listOf(ERROR, FATAL)

    /** Numeric ordering for filtering — lower = more verbose. */
    fun ordinal(level: String): Int = when (level) {
        DEBUG -> 0
        INFO -> 1
        WARN -> 2
        ERROR -> 3
        FATAL -> 4
        else -> 1
    }
}

/**
 * Log categories for grouping related events.
 */
object LogCategory {
    const val UI = "UI"
    const val NAVIGATION = "NAV"
    const val SKILL = "SKILL"
    const val PROVIDER = "PROVIDER"
    const val NETWORK = "NETWORK"
    const val SYSTEM = "SYSTEM"
    const val MEMORY = "MEMORY"
    const val SECURITY = "SECURITY"
    const val DATABASE = "DB"
    const val SETTINGS = "SETTINGS"
}

/**
 * Centralised, persistent logging system.
 *
 * Every log call writes asynchronously to the Room DB so the user can
 * browse, search, and export them from the in-app Log Viewer.
 *
 * Auto-prune: logs older than [PRUNE_AGE_DAYS] days are deleted on every
 * write if total row count exceeds [PRUNE_AT_ROWS].
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public query API ──────────────────────────────────────────────

    fun observeAll(): Flow<List<LogEntry>> = logDao.observeByLevel(LogLevel.ALL)

    fun query(
        levels: List<String> = LogLevel.ALL,
        categories: List<String> = emptyList(),
        tag: String = "",
        search: String = "",
    ): Flow<List<LogEntry>> {
        return logDao.query(
            levels = if (levels.isEmpty()) LogLevel.ALL else levels,
            hasCategoryFilter = if (categories.isEmpty()) 0 else 1,
            categories = if (categories.isEmpty()) listOf("__NONE__") else categories,
            tagFilter = tag,
            searchQuery = search,
        )
    }

    fun observeCount(): Flow<Int> = logDao.observeCount()

    suspend fun distinctCategories(): List<String> = logDao.distinctCategories()
    suspend fun distinctTags(): List<String> = logDao.distinctTags()

    // ── Write API ─────────────────────────────────────────────────────

    fun d(tag: String, message: String, details: String? = null, category: String = LogCategory.SYSTEM) =
        write(LogLevel.DEBUG, category, tag, message, details)

    fun i(tag: String, message: String, details: String? = null, category: String = LogCategory.SYSTEM) =
        write(LogLevel.INFO, category, tag, message, details)

    fun w(tag: String, message: String, details: String? = null, category: String = LogCategory.SYSTEM) =
        write(LogLevel.WARN, category, tag, message, details)

    fun e(tag: String, message: String, details: String? = null, category: String = LogCategory.SYSTEM) =
        write(LogLevel.ERROR, category, tag, message, details)

    fun fatal(tag: String, message: String, details: String? = null, category: String = LogCategory.SYSTEM) =
        write(LogLevel.FATAL, category, tag, message, details)

    // ── Convenience for throwables ────────────────────────────────────

    fun w(tag: String, t: Throwable, message: String, category: String = LogCategory.SYSTEM) {
        w(tag, message, stackTraceOf(t), category)
    }

    fun e(tag: String, t: Throwable, message: String, category: String = LogCategory.SYSTEM) {
        e(tag, message, stackTraceOf(t), category)
    }

    fun fatal(tag: String, t: Throwable, message: String, category: String = LogCategory.SYSTEM) {
        fatal(tag, message, stackTraceOf(t), category)
    }

    // ── Wipe ──────────────────────────────────────────────────────────

    fun clearAll() {
        scope.launch { logDao.deleteAll() }
    }

    // ── Internal ──────────────────────────────────────────────────────

    private fun write(level: String, category: String, tag: String, message: String, details: String?) {
        scope.launch {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                category = category,
                tag = tag,
                message = message,
                details = details,
            )
            logDao.insert(entry)
            maybePrune()
        }
    }

    @Volatile
    private var writeCount = 0

    private suspend fun maybePrune() {
        writeCount++
        if (writeCount % PRUNE_CHECK_INTERVAL != 0) return
        val count = logDao.totalRows()
        if (count > PRUNE_AT_ROWS) {
            val cutoff = System.currentTimeMillis() - PRUNE_AGE_DAYS * 24L * 60 * 60 * 1000
            logDao.deleteOlderThan(cutoff)
        }
    }

    companion object {
        /** Delete logs older than this many days when pruning triggers. */
        const val PRUNE_AGE_DAYS = 7
        /** Start pruning once we exceed this many rows (~10 MB of log entries). */
        const val PRUNE_AT_ROWS = 10_000
        /** Check pruning every N writes to avoid overhead on every log insert. */
        const val PRUNE_CHECK_INTERVAL = 50
    }
}

/** Build a compact stack-trace string from a throwable. */
private fun stackTraceOf(t: Throwable): String {
    val sw = java.io.StringWriter()
    val pw = java.io.PrintWriter(sw)
    t.printStackTrace(pw)
    pw.flush()
    val trace = sw.toString()
    // Truncate very long traces to keep DB rows small
    return if (trace.length > 2000) trace.take(2000) + "\n…[truncated]" else trace
}
