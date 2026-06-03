package com.aion.agent.memory.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A conversation between the user and the agent. Each conversation holds
 * an ordered list of [MessageEntity]s.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "preview")
    val preview: String = "",

    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
)

/**
 * A single message in a conversation. Content is the raw text; for tool
 * invocations, [toolCallId] and [toolName] are populated. [status] tracks
 * whether streaming finished cleanly or was interrupted.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversation_id")],
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String, // "user" | "assistant" | "system" | "tool"

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String? = null,

    @ColumnInfo(name = "tool_name")
    val toolName: String? = null,

    @ColumnInfo(name = "status")
    val status: String = "complete", // "complete" | "incomplete" | "failed"
)

/**
 * A notification captured by [com.aion.agent.system.AgentNotificationListener].
 * Stored for notification history and feeding into agent context.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index("package_name"),
        Index("posted_at"),
    ],
)
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "category")
    val category: String, // "message" | "alert" | "spam" | "system" | "other"

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "posted_at")
    val postedAt: Long,

    @ColumnInfo(name = "dismissed")
    val dismissed: Boolean = false,

    @ColumnInfo(name = "action_taken")
    val actionTaken: String? = null, // "read" | "snoozed" | "dismissed" | null
)

/**
 * A persistent fact stored in the memory system. Categories include
 * "user_profile", "preference", "fact", "learned_pattern". Importance
 * (0.0–1.0) determines how aggressively [ForgettingPolicy] evicts it.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index("category"),
        Index("last_accessed_at"),
    ],
)
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "importance")
    val importance: Float,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,

    @ColumnInfo(name = "access_count")
    val accessCount: Int = 0,
)

/**
 * An LLM-generated summary of a range of messages in a conversation.
 * Used by [ContextManager] to stay within token budgets without losing
 * the gist of earlier conversation turns.
 */
@Entity(
    tableName = "context_summaries",
    indices = [Index("conversation_id")],
)
data class ContextSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "message_range_start")
    val messageRangeStart: Int,

    @ColumnInfo(name = "message_range_end")
    val messageRangeEnd: Int,

    @ColumnInfo(name = "token_count")
    val tokenCount: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
