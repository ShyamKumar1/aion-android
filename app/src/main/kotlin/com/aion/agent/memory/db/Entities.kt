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
