package com.aion.agent.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * AION's local database. Per AION_GUIDELINES §11, message content stays
 * here (for the user's history). NotificationEntity, MemoryEntity, and
 * ContextSummaryEntity are reserved for Phase 3 and added in a migration.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AionDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "aion.db"
    }
}
