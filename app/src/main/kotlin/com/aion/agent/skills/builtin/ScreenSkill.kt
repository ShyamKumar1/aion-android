package com.aion.agent.skills.builtin

import com.aion.agent.core.AgentCapability
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for reading screen content. Screen reading is not a discrete
 * invocation — it is provided continuously by the agent loop when
 * [AgentCapability.FULL] is active (AccessibilityService streaming screen
 * state to the LLM context). This skill exists so the BM25 router and tool
 * registry can advertise the capability; [execute] returns a message
 * explaining that screen reading is handled by the agent loop itself.
 */
@Singleton
class ScreenSkill @Inject constructor() : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "screen.read",
        name = "Read Screen",
        description = "Reads the text content currently visible on the screen",
        keywords = listOf(
            "screen", "display", "see", "read screen", "what's on screen",
            "look", "view", "read", "visible", "show",
        ),
        parameters = emptyList(),
        requiredCapability = AgentCapability.FULL,
    )

    override fun canHandle(input: String): Float {
        val lower = input.lowercase()
        return if ("screen" in lower || "see" in lower || "look" in lower) 0.6f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        return SkillResult.Success(
            output = "Screen reading is handled by the agent loop via AccessibilityService when Full Access is enabled.",
            summary = "Screen content is automatically provided to the agent when Full Access is enabled in Settings.",
        )
    }
}
