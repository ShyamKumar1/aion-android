package com.aion.agent.ui.chat

/**
 * One chat message as seen by the UI. Distinct from [com.aion.agent.memory.db.MessageEntity]
 * so the UI doesn't depend on Room types.
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
    val isResponding: Boolean = false,
    val isReady: Boolean = false,
    val errorMessage: String? = null,
    val inputText: String = "",
    val currentConversationId: String? = null,
)
