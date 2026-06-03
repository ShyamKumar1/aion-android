package com.aion.agent.ui.chat

import com.aion.agent.memory.db.ConversationEntity

/**
 * One chat message as seen by the UI.
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val pendingConfirmation: ConfirmationRequest? = null,
) {
    enum class Role { USER, ASSISTANT, SYSTEM, TOOL }
}

data class ConfirmationRequest(
    val skillId: String,
    val prompt: String,
    val params: Map<String, String> = emptyMap(),
)

/**
 * UI state for the chat screen. One per [ChatViewModel].
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val isResponding: Boolean = false,
    val isReady: Boolean = false,
    val errorMessage: String? = null,
    val inputText: String = "",
    val currentConversationId: String? = null,
    val showConversationList: Boolean = false,
)
