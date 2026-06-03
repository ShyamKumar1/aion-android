package com.aion.agent.memory

import com.aion.agent.memory.db.MemoryDao
import com.aion.agent.util.AionLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU eviction with importance threshold.
 *
 * Ensures the memory store doesn't grow unbounded. When [MAX_MEMORIES]
 * is exceeded, the [EVICTION_BATCH] least recently used memories with
 * importance below [IMPORTANCE_THRESHOLD] are pruned.
 *
 * Per AION_GUIDELINES §3, memories with high importance (user-saved facts,
 * profile info) are never evicted automatically.
 */
@Singleton
class ForgettingPolicy @Inject constructor(
    private val logger: AionLogger,
) {
    /**
     * Check if eviction is needed and prune if so. Called after every
     * [MemoryRepository.store] call.
     */
    suspend fun apply(memoryDao: MemoryDao) {
        val count = memoryDao.count()
        if (count <= MAX_MEMORIES) return

        val toEvict = memoryDao.leastRecentlyUsed(EVICTION_BATCH)
        var evicted = 0
        for (mem in toEvict) {
            if (mem.importance < IMPORTANCE_THRESHOLD) {
                memoryDao.delete(mem.id)
                evicted++
            }
        }
        if (evicted > 0) {
            logger.d(TAG) { "Evicted $evicted memories (LRU, importance < $IMPORTANCE_THRESHOLD)" }
        }
    }

    companion object {
        private const val TAG = "Forgetting"
        const val MAX_MEMORIES = 500
        const val EVICTION_BATCH = 50
        /** Memories with importance below this threshold are eligible for eviction. */
        const val IMPORTANCE_THRESHOLD = 0.3f
    }
}
