package com.aion.agent.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * AION's local database. Phase 1: conversations + messages only.
 * Phase 3 adds: notifications, memories, context_summaries.
 *
 * Version history:
 *  - 1: initial (conversations, messages)
 *  - 2: +notifications, memories, context_summaries
 *
 * IMPORTANT: Per AION_GUIDELINES §16, when bumping version in production,
 * write a [androidx.room.migration.Migration] instead of using destructive fallback.
 * For development, [fallbackToDestructiveMigration] is acceptable.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        NotificationEntity::class,
        MemoryEntity::class,
        ContextSummaryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AionDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun notificationDao(): NotificationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun contextSummaryDao(): ContextSummaryDao

    companion object {
        const val NAME = "aion.db"
    }
}
