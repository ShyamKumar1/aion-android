package com.aion.agent.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    /** Observe all logs ordered newest-first. */
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LogEntry>>

    /** Observe logs filtered by minimum level (e.g. WARN includes WARN + ERROR + FATAL). */
    @Query("SELECT * FROM logs WHERE level IN (:levels) ORDER BY timestamp DESC")
    fun observeByLevel(levels: List<String>): Flow<List<LogEntry>>

    /** Observe logs for a specific category. */
    @Query("SELECT * FROM logs WHERE category = :category ORDER BY timestamp DESC")
    fun observeByCategory(category: String): Flow<List<LogEntry>>

    /** Observe logs matching a tag prefix. */
    @Query("SELECT * FROM logs WHERE tag LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    fun observeByTag(tag: String): Flow<List<LogEntry>>

    /** Full-text search on message content. */
    @Query("SELECT * FROM logs WHERE message LIKE '%' || :query || '%' OR details LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<LogEntry>>

    /** Full featured query with all optional filters. Empty list = no filter. */
    @Query("""
        SELECT * FROM logs
        WHERE level IN (:levels)
          AND (:hasCategoryFilter = 0 OR category IN (:categories))
          AND (:tagFilter = '' OR tag LIKE '%' || :tagFilter || '%')
          AND (:searchQuery = '' OR message LIKE '%' || :searchQuery || '%' OR details LIKE '%' || :searchQuery || '%')
        ORDER BY timestamp DESC
    """)
    fun query(
        levels: List<String>,
        hasCategoryFilter: Int = 0,
        categories: List<String> = emptyList(),
        tagFilter: String = "",
        searchQuery: String = "",
    ): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("DELETE FROM logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM logs")
    fun observeCount(): Flow<Int>

    /** Get distinct categories present in logs. */
    @Query("SELECT DISTINCT category FROM logs ORDER BY category")
    suspend fun distinctCategories(): List<String>

    /** Get distinct tags present in logs. */
    @Query("SELECT DISTINCT tag FROM logs ORDER BY tag")
    suspend fun distinctTags(): List<String>

    /** Get total size estimate via row count (actual byte size would require raw query). */
    @Query("SELECT COUNT(*) FROM logs")
    suspend fun totalRows(): Int
}
