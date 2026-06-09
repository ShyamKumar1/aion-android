package com.aion.agent.data

import com.aion.agent.memory.db.ConversationDao
import com.aion.agent.memory.db.ConversationEntity
import com.aion.agent.memory.db.MessageDao
import com.aion.agent.memory.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/write API over the conversation tables. The UI observes Flows from
 * here; we never expose Room entities directly to Compose — domain models
 * map in the ViewModel.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun getMessages(conversationId: String): List<MessageEntity> =
        messageDao.forConversation(conversationId)

    /**
     * Creates a new conversation for the current session.
     * This always creates a fresh conversation — "resume last conversation"
     * will land in Phase 2 with conversation history management.
     */
    suspend fun createNewSessionConversation(): ConversationEntity {
        return createConversation()
    }

    /** Get all conversations, newest first. */
    suspend fun getConversationList(): List<ConversationEntity> =
        conversationDao.getAllSync()

    /** Permanently delete a conversation and all its messages. */
    suspend fun deleteConversation(id: String) {
        conversationDao.delete(id)
        // Messages are cascade-deleted by Room's foreign key constraint.
    }

    suspend fun createConversation(title: String = "New chat"): ConversationEntity {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            preview = "",
            messageCount = 0,
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
        toolCallId: String? = null,
        toolName: String? = null,
        status: String = "complete",
    ): MessageEntity {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = System.currentTimeMillis(),
            toolCallId = toolCallId,
            toolName = toolName,
            status = status,
        )
        messageDao.insert(entity)
        val count = messageDao.countForConversation(conversationId)
        conversationDao.updatePreview(
            id = conversationId,
            preview = content.take(120),
            updatedAt = entity.createdAt,
            count = count,
        )
        return entity
    }

    suspend fun updateMessageContent(messageId: String, content: String, status: String = "complete") {
        val current = messageDao.byId(messageId) ?: return
        messageDao.update(current.copy(content = content, status = status))
    }
}
