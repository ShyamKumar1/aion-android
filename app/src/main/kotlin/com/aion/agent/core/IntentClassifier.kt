package com.aion.agent.core

import com.aion.agent.skills.SkillRegistry
import com.aion.agent.system.CapabilityManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-1 intent classifier. Heuristic-based, no LLM. Maps user input to
 * one of three intents:
 *  - [AgentIntent.Chat] for conversational input
 *  - [AgentIntent.ToolCall] when a skill matches above the BM25 threshold
 *  - [AgentIntent.Unknown] for ambiguous input
 *
 * Phase 2 replaces this with a local 3B model call. The interface stays the
 * same, so the [AgentLoop] doesn't change.
 */
@Singleton
class IntentClassifier @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val capabilityManager: CapabilityManager,
) {

    suspend fun classify(input: String): AgentIntent {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AgentIntent.Empty
        val tier = capabilityManager.capability.value
        val ranked = skillRegistry.rankFor(trimmed, tier)
        val top = ranked.firstOrNull() ?: return AgentIntent.Chat(trimmed)
        if (top.score < BM25_THRESHOLD) return AgentIntent.Chat(trimmed)
        if (ranked.size >= 2 && (top.score - ranked[1].score) < AMBIGUITY_MARGIN) {
            // Ambiguous — let the LLM clarify via natural chat
            return AgentIntent.Chat(trimmed)
        }
        return AgentIntent.ToolCall(
            skillId = top.skill.definition.id,
            confidence = top.score,
        )
    }

    private companion object {
        // Mirrors Bm25Router default; re-declared here so IntentClassifier stays self-contained.
        const val BM25_THRESHOLD = 0.35f
        const val AMBIGUITY_MARGIN = 0.05f
    }
}
