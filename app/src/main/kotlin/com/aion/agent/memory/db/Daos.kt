package com.aion.agent.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    suspend fun getAllSync(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET preview = :preview, updated_at = :updatedAt, message_count = :count WHERE id = :id")
    suspend fun updatePreview(id: String, preview: String, updatedAt: Long, count: Int)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun forConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    suspend fun countForConversation(conversationId: String): Int
}

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY posted_at DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE package_name = :pkg ORDER BY posted_at DESC")
    fun observeByPackage(pkg: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun byId(id: String): NotificationEntity?

    @Query("SELECT package_name FROM notifications GROUP BY package_name ORDER BY MAX(posted_at) DESC")
    suspend fun distinctPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Update
    suspend fun update(notification: NotificationEntity)

    @Query("UPDATE notifications SET dismissed = :dismissed WHERE id = :id")
    suspend fun setDismissed(id: String, dismissed: Boolean = true)

    @Query("DELETE FROM notifications WHERE posted_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE posted_at > :since")
    suspend fun countSince(since: Long): Int
}

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY last_accessed_at DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE key = :key LIMIT 1")
    suspend fun byKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importance DESC")
    suspend fun byCategory(category: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY last_accessed_at ASC LIMIT :limit")
    suspend fun leastRecentlyUsed(limit: Int): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface ContextSummaryDao {

    @Query("SELECT * FROM context_summaries WHERE conversation_id = :convId ORDER BY created_at DESC")
    suspend fun forConversation(convId: String): List<ContextSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: ContextSummaryEntity)

    @Query("DELETE FROM context_summaries WHERE conversation_id = :convId")
    suspend fun deleteForConversation(convId: String)
}
