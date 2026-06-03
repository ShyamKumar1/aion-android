package com.aion.agent.skills

import com.aion.agent.core.AgentCapability
import kotlinx.serialization.Serializable

/**
 * Static description of a skill. Used for BM25 routing and tool-call wiring.
 * Skills are registered in [SkillRegistry] at startup and exposed to the LLM
 * as function/tool definitions in [com.aion.agent.llm.CloudLlmEngine].
 *
 * @param id stable kebab-case identifier. Becomes the OpenAI function name.
 * @param name human-readable display name (UI).
 * @param description what the skill does; sent to the LLM as the tool description.
 * @param keywords 5-20 terms used by [Bm25Router] for input matching.
 * @param parameters the LLM-callable parameters with JSON-schema types.
 * @param requiredPermissions the Android system permissions needed.
 * @param requiredCapability the minimum [AgentCapability] tier.
 */
@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val keywords: List<String>,
    val parameters: List<SkillParameter> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val requiredCapability: AgentCapability = AgentCapability.MINIMAL,
    val version: String = "1.0.0",
)

@Serializable
data class SkillParameter(
    val name: String,
    val description: String,
    val jsonType: String = "string",
    val required: Boolean = true,
    val enum: List<String> = emptyList(),
)

/**
 * Result of executing a skill. Sealed so the agent loop and UI can pattern-match
 * without `instanceof` chains.
 */
sealed class SkillResult {
    abstract val summary: String

    data class Success(
        val output: String,
        override val summary: String,
        val data: Map<String, String> = emptyMap(),
    ) : SkillResult()

    data class ConfirmationRequired(
        val prompt: String,
        override val summary: String,
    ) : SkillResult()

    data class Failure(
        val reason: String,
        override val summary: String,
    ) : SkillResult()

    data class Timeout(
        override val summary: String = "Skill took too long",
    ) : SkillResult()
}

/**
 * Runtime interface every skill implements. The [SkillRegistry] holds the
 * static [SkillDefinition] + a reference to an implementation.
 *
 * Per AION_GUIDELINES §13:
 *  - [execute] must complete within 30s.
 *  - [canHandle] must not call the LLM (used for BM25, not a router).
 *  - [execute] must not access system services not declared in
 *    [SkillDefinition.requiredPermissions].
 */
interface AgentSkill {
    val definition: SkillDefinition

    /**
     * Cheap confidence score 0.0..1.0 for whether this skill can handle
     * the given input. The [Bm25Router] combines all skills' canHandle
     * scores into a single ranking. Must be O(keywords) and side-effect free.
     */
    fun canHandle(input: String): Float = 0f

    /**
     * Execute the skill. Per AION_GUIDELINES N1, mutating skills should
     * return [SkillResult.ConfirmationRequired] rather than acting directly.
     */
    suspend fun execute(params: Map<String, String>): SkillResult
}
