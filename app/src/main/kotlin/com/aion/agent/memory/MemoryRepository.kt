package com.aion.agent.memory

import com.aion.agent.memory.db.MemoryDao
import com.aion.agent.memory.db.MemoryEntity
import com.aion.agent.util.AionLogger
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single access point for all memory operations.
 *
 * Stores persistent facts about the user and their preferences.
 * Retrieval uses keyword matching for Phase 3 (vector search via
 * sqlite-vec is Phase 4+).
 *
 * Per AION_GUIDELINES §3, memory data stays on-device and never leaves
 * without explicit user consent via the cloud LLM disclosure.
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val forgettingPolicy: ForgettingPolicy,
    private val logger: AionLogger,
) {
    /**
     * Store a fact in memory. If the key already exists, updates the value
     * and resets the access timestamp.
     */
    suspend fun store(
        key: String,
        value: String,
        category: String = "fact",
        importance: Float = 0.5f,
    ) {
        require(importance in 0f..1f) { "Importance must be 0.0–1.0" }

        val existing = memoryDao.byKey(key)
        if (existing != null) {
            memoryDao.update(existing.copy(
                value = value,
                lastAccessedAt = clock.millis(),
                importance = importance,
            ))
            logger.d(TAG) { "Memory updated: $key = ${value.take(100)}" }
            return
        }

        memoryDao.insert(MemoryEntity(
            id = java.util.UUID.randomUUID().toString(),
            key = key,
            value = value,
            category = category,
            importance = importance,
            createdAt = clock.millis(),
            lastAccessedAt = clock.millis(),
        ))
        logger.d(TAG) { "Memory stored: $key ($category, importance=$importance)" }

        // Apply forgetting policy after insert to stay within bounds
        forgettingPolicy.apply(memoryDao)
    }

    /**
     * Retrieve relevant memories for [query].
     *
     * Phase 3: keyword matching. Phase 4+: vector similarity search.
     */
    suspend fun retrieve(query: String, limit: Int = 5): List<MemoryEntity> {
        val queryLower = query.lowercase()
        val allMemories = memoryDao.observeAll().first()
        val scored = allMemories.map { mem ->
            val keywordScore = when {
                mem.key.lowercase() in queryLower -> 1.0f
                queryLower.contains(mem.key.lowercase()) -> 0.5f
                else -> 0.0f
            }
            val importanceScore = mem.importance * 0.3f
            mem to (keywordScore + importanceScore)
        }
        return scored.sortedByDescending { it.second }
            .take(limit)
            .onEach { (mem, _) -> touch(mem) }
            .map { it.first }
    }

    /** Retrieve memories by category. */
    suspend fun byCategory(category: String): List<MemoryEntity> =
        memoryDao.byCategory(category)

    /** Count total stored memories. */
    suspend fun count(): Int = memoryDao.count()

    /** Delete a specific memory by ID. */
    suspend fun delete(id: String) = memoryDao.delete(id)

    /** Wipe all memories. */
    suspend fun wipeAll() = memoryDao.deleteAll()

    private suspend fun touch(memory: MemoryEntity) {
        memoryDao.update(memory.copy(
            lastAccessedAt = clock.millis(),
            accessCount = memory.accessCount + 1,
        ))
    }

    companion object {
        private const val TAG = "MemoryRepo"
        private val clock = java.time.Clock.systemUTC()
    }
}
