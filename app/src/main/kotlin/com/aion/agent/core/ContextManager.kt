package com.aion.agent.core

import com.aion.agent.llm.LlmMessage
import com.aion.agent.llm.LlmRole
import com.aion.agent.memory.db.MessageEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the [LlmMessage] list sent to the LLM. Per AION_GUIDELINES §6 and §10:
 *  - System prompt: 512 tokens (we don't enforce the count yet; we keep the prompt tight)
 *  - Conversation history: 2048 tokens rolling window
 *  - At 70% capacity, summarize oldest 50% of messages (Phase 3 — stubbed here)
 *
 * Phase 1 implementation is naive: include the last N messages where N
 * preserves order. A real token-aware trim is Phase 2.
 */
@Singleton
class ContextManager @Inject constructor() {

    /**
     * Assemble the messages sent to the LLM for a new user turn.
     *
     * @param history full message history for this conversation
     * @param newUserText the message the user just sent
     * @return the message list in order, ready to send to the LLM
     */
    fun assemble(
        history: List<MessageEntity>,
        newUserText: String,
    ): List<LlmMessage> {
        val out = mutableListOf<LlmMessage>()
        val windowed = history.takeLast(MAX_HISTORY_MESSAGES)
        for (m in windowed) {
            val role = when (m.role) {
                "user" -> LlmRole.USER
                "assistant" -> LlmRole.ASSISTANT
                "system" -> LlmRole.SYSTEM
                "tool" -> LlmRole.TOOL
                else -> LlmRole.USER
            }
            out += LlmMessage(
                role = role,
                content = m.content,
                toolCallId = m.toolCallId,
                toolName = m.toolName,
            )
        }
        // The "new user text" is already in history because we appended it
        // in AgentLoop.processUserMessage before calling streamLlmReply.
        // We don't double-append it.
        return out
    }

    private companion object {
        // 50 message rolling window. Real token-budget trim is Phase 2.
        const val MAX_HISTORY_MESSAGES = 50
    }
}
