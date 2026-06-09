package com.aion.agent.core

import com.aion.agent.llm.LlmMessage
import com.aion.agent.llm.LlmRole
import com.aion.agent.memory.MemoryRepository
import com.aion.agent.memory.db.MessageDao
import com.aion.agent.memory.db.MessageEntity
import com.aion.agent.memory.db.NotificationDao
import com.aion.agent.util.AionLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the [LlmMessage] list sent to the LLM, enforcing token budgets
 * per AION_GUIDELINES §6.
 *
 * Token Budget:
 * | Component | Limit |
 * |---|---|
 * | System prompt | 512 |
 * | Conversation history | 2048 |
 * | Current screen tree | 800 |
 * | Recent notifications | 400 |
 * | Retrieved memory snippets | 600 |
 * | Tool definitions | 400 |
 * | **Total ceiling** | **4760** |
 *
 * Assembly order (never trim system prompt or user message):
 * 1. System prompt
 * 2. Relevant memory snippets
 * 3. Current screen tree (if available)
 * 4. Recent notifications summary
 * 5. Conversation history (trim from oldest, summarize if needed)
 * 6. Current user message
 */
@Singleton
class ContextManager @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val notificationDao: NotificationDao,
    private val messageDao: MessageDao,
    private val logger: AionLogger,
) {
    /**
     * Assemble context for an LLM call, enforcing token budgets.
     *
     * @param history Full message history for this conversation.
     * @param newUserText The current user query (for memory retrieval).
     * @param screenTree Optional token-efficient screen representation.
     */
    suspend fun assemble(
        history: List<MessageEntity>,
        newUserText: String,
        screenTree: String? = null,
    ): List<LlmMessage> {
        val result = mutableListOf<LlmMessage>()
        var budget = TOTAL_CEILING
        val systemPrompt = SYSTEM_PROMPT

        // 1. System prompt (never trimmed)
        result += LlmMessage(LlmRole.SYSTEM, systemPrompt)
        budget -= estimateTokens(systemPrompt)

        // 2. Relevant memory snippets
        val memories = memoryRepository.retrieve(newUserText, limit = 3)
        if (memories.isNotEmpty()) {
            val memoryBlock = memories.joinToString("\n") { "${it.key}: ${it.value}" }
            val memoryText = "Relevant facts:\n$memoryBlock"
            val memoryTokens = estimateTokens(memoryText)
            val memoryBudget = minOf(budget - RESERVED_FOR_USER, MEMORY_BUDGET)
            if (memoryTokens <= memoryBudget) {
                result += LlmMessage(LlmRole.SYSTEM, memoryText)
                budget -= memoryTokens
            }
        }

        // 3. Screen tree (if available and within budget)
        if (screenTree != null) {
            val screenText = "Current screen:\n${screenTree.take(1500)}"
            val screenTokens = estimateTokens(screenText)
            val screenBudget = minOf(budget - RESERVED_FOR_USER, SCREEN_BUDGET)
            if (screenTokens <= screenBudget) {
                result += LlmMessage(LlmRole.SYSTEM, screenText)
                budget -= screenTokens
            }
        }

        // 4. Recent notifications (last 5 min)
        val recentCount = notificationDao.countSince(
            System.currentTimeMillis() - RECENT_NOTIF_WINDOW_MS
        )
        if (recentCount > 0) {
            val notifText = "$recentCount notifications in last 5 minutes"
            val notifTokens = estimateTokens(notifText)
            if (notifTokens <= minOf(budget - RESERVED_FOR_USER, NOTIF_BUDGET)) {
                result += LlmMessage(LlmRole.SYSTEM, notifText)
                budget -= notifTokens
            }
        }

        // 5. Conversation history (trim from oldest, within remaining budget)
        val historyBudget = minOf(budget - RESERVED_FOR_USER, HISTORY_BUDGET)
        result += trimHistory(history, historyBudget)

        return result
    }

    /**
     * Trim conversation history to fit within [budget] tokens.
     * Adds newest messages first until budget is exhausted.
     * At 70% capacity, a summary would be triggered (Phase 4).
     */
    private fun trimHistory(history: List<MessageEntity>, budget: Int): List<LlmMessage> {
        val out = mutableListOf<LlmMessage>()
        var used = 0
        for (msg in history.reversed()) {
            val tokens = estimateTokens(msg.content)
            if (used + tokens > budget) break
            out.add(0, LlmMessage(
                role = when (msg.role) {
                    "user" -> LlmRole.USER
                    "assistant" -> LlmRole.ASSISTANT
                    "system" -> LlmRole.SYSTEM
                    "tool" -> LlmRole.TOOL
                    else -> LlmRole.USER
                },
                content = msg.content,
                toolCallId = msg.toolCallId,
                toolName = msg.toolName,
            ))
            used += tokens
        }

        val totalHistoryTokens = history.sumOf { estimateTokens(it.content) }
        if (used < totalHistoryTokens && totalHistoryTokens > 0) {
            val pct = (used.toFloat() / totalHistoryTokens * 100).toInt()
            logger.d(TAG) { "History trimmed: $pct% used ($used/$totalHistoryTokens tokens)" }
        }

        return out
    }

    /**
     * Returns the system prompt shared across the agent.
     * Single source of truth — other components should call this
     * instead of defining their own SYSTEM_PROMPT.
     */
    fun getSystemPrompt(): String = SYSTEM_PROMPT

    /** Rough token estimation: 4 characters ≈ 1 token. */
    private fun estimateTokens(text: String): Int =
        (text.length / 4) + 1

    companion object {
        private const val TAG = "ContextManager"

        // Token budgets (from AION_GUIDELINES §6)
        const val SYSTEM_BUDGET = 512
        const val HISTORY_BUDGET = 2048
        const val SCREEN_BUDGET = 800
        const val NOTIF_BUDGET = 400
        const val MEMORY_BUDGET = 600
        const val TOOL_BUDGET = 400
        const val TOTAL_CEILING = 4760

        /** Reserve tokens for the current user message. */
        const val RESERVED_FOR_USER = 400

        /** Notification window: 5 minutes. */
        const val RECENT_NOTIF_WINDOW_MS = 5 * 60 * 1000L

        val SYSTEM_PROMPT = """
            You are AION, a private on-device AI agent. Be concise, helpful, and
            never claim to perform actions you cannot actually perform. If asked
            to do something that requires a tool, use the tool. Otherwise, just
            respond.
        """.trimIndent()
    }
}
