package com.aion.agent.core

import com.aion.agent.llm.LlmEvent
import com.aion.agent.llm.LlmMessage
import com.aion.agent.llm.LlmRequest
import com.aion.agent.llm.LlmRole
import com.aion.agent.llm.LocalLlmEngine
import com.aion.agent.llm.ModelManager
import com.aion.agent.skills.SkillRegistry
import com.aion.agent.system.CapabilityManager
import com.aion.agent.util.AionLogger
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intent classifier. Phase 2 adds local LLM inference when [ModelManager]
 * has a classifier model loaded. Falls through to Phase 1 BM25 heuristic
 * when no local model is available.
 *
 * The local model classifies into: CHAT, TOOL_CALL (with skill ID + params),
 * or UNKNOWN. Output is structured JSON constrained by the system prompt.
 */
@Singleton
class IntentClassifier @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val capabilityManager: CapabilityManager,
    private val modelManager: ModelManager,
    private val localEngine: LocalLlmEngine,
    private val logger: AionLogger,
) {

    /**
     * Classify [input] into an [AgentIntent].
     *
     * Uses local LLM when available for nuanced classification.
     * Falls back to BM25 heuristic when model not loaded.
     */
    suspend fun classify(input: String): AgentIntent {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AgentIntent.Empty

        // Try local LLM classification if model is loaded
        if (modelManager.isReady) {
            return classifyWithLlm(trimmed)
        }

        // Fallback: BM25 heuristic (Phase 1)
        return classifyHeuristic(trimmed)
    }

    private suspend fun classifyWithLlm(input: String): AgentIntent {
        val request = LlmRequest(
            systemPrompt = CLASSIFICATION_SYSTEM_PROMPT,
            messages = listOf(LlmMessage(LlmRole.USER, input)),
            maxTokens = 128,
            temperature = 0.1f,
        )

        return try {
            val tokens = localEngine.streamReply(request)
                .toList()
                .filterIsInstance<LlmEvent.Token>()
                .joinToString("") { it.text }

            parseClassification(tokens.trim()) ?: classifyHeuristic(input)
        } catch (t: Throwable) {
            logger.w(TAG, t) { "LLM classification failed, falling back to heuristic" }
            classifyHeuristic(input)
        }
    }

    /**
     * Parse the model's JSON response into an [AgentIntent].
     * Expected format:
     *   {"intent": "TOOL_CALL", "tool": "sms.send", "params": {...}}
     *   {"intent": "CHAT"}
     *   {"intent": "UNKNOWN"}
     */
    private fun parseClassification(json: String): AgentIntent? {
        val intentMatch = Regex(""""intent"\s*:\s*"(\w+)"""").find(json) ?: return null
        return when (intentMatch.groupValues[1]) {
            "CHAT" -> AgentIntent.Chat("")
            "TOOL_CALL" -> {
                val toolMatch = Regex(""""tool"\s*:\s*"([\w.]+)"""").find(json)
                val skillId = toolMatch?.groupValues?.get(1) ?: return null
                AgentIntent.ToolCall(
                    skillId = skillId,
                    confidence = 1.0f,
                    extractedParams = parseParams(json),
                )
            }
            "UNKNOWN" -> AgentIntent.Unknown("")
            else -> null
        }
    }

    private fun parseParams(json: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val paramBlock = Regex(""""params"\s*:\s*\{([^}]+)\}""").find(json)
        val content = paramBlock?.groupValues?.get(1) ?: return params
        Regex(""""(\w+)"\s*:\s*"([^"]*)"""").findAll(content).forEach { match ->
            params[match.groupValues[1]] = match.groupValues[2]
        }
        return params
    }

    private suspend fun classifyHeuristic(input: String): AgentIntent {
        val tier = capabilityManager.capability.value
        val ranked = skillRegistry.rankFor(input, tier)
        val top = ranked.firstOrNull() ?: return AgentIntent.Chat(input)
        if (top.score < BM25_THRESHOLD) return AgentIntent.Chat(input)
        if (ranked.size >= 2 && (top.score - ranked[1].score) < AMBIGUITY_MARGIN) {
            return AgentIntent.Chat(input)
        }
        return AgentIntent.ToolCall(
            skillId = top.skill.definition.id,
            confidence = top.score,
        )
    }

    private companion object {
        private const val TAG = "IntentClassifier"
        private const val BM25_THRESHOLD = 0.35f
        private const val AMBIGUITY_MARGIN = 0.05f

        val CLASSIFICATION_SYSTEM_PROMPT = """
            You are an intent classifier for a mobile AI agent. Given a user message,
            classify it as one of:

            - CHAT: conversational query, no tool needed
            - TOOL_CALL: user wants to perform an action via a specific tool
            - UNKNOWN: ambiguous or unclear

            For TOOL_CALL, identify the tool name and extract parameters.

            Respond with JSON only, no explanation:
            {"intent": "CHAT"}
            {"intent": "TOOL_CALL", "tool": "skill.id", "params": {"key": "value"}}
            {"intent": "UNKNOWN"}
        """.trimIndent()
    }
}
