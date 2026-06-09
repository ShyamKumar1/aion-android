package com.aion.agent.skills

import com.aion.agent.core.AgentCapability
import com.aion.agent.util.AionLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * A skill defined in JSON format. Validated by [SkillScriptEngine].
 */
@Serializable
data class YamlSkillDefinition(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String,
    val keywords: List<String>,
    @kotlinx.serialization.SerialName("required_permissions")
    val requiredPermissions: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("required_capability")
    val requiredCapability: String = "MINIMAL",
    val triggers: List<YamlTrigger> = emptyList(),
    val steps: List<YamlStep>,
)

@Serializable
data class YamlTrigger(
    val type: String, // "time" | "phrase" | "event" | "state"
    val value: String,
)

@Serializable
data class YamlStep(
    val id: String = "",
    val tool: String,
    val params: Map<String, String> = emptyMap(),
    @kotlinx.serialization.SerialName("on_error")
    val onError: String = "stop", // "stop" | "continue" | "retry(3)"
)

/**
 * Engine that parses and executes JSON skill definitions.
 *
 * Per AION_GUIDELINES §13:
 *  - Maximum 20 steps per skill
 *  - Skills are sandboxed (restricted coroutine scope, 30s timeout)
 *  - Template expressions use {{ }} syntax
 */
@Singleton
class SkillScriptEngine @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val logger: AionLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "SkillScript"
        const val MAX_EXECUTION_SECONDS = 30L
        const val MAX_STEPS = 20
    }

    /**
     * Parse a JSON skill string into a [YamlSkillDefinition].
     * Accepts JSON input. YAML support will be added in a future phase
     * when a YAML parsing library is included.
     */
    fun parse(jsonContent: String): Result<YamlSkillDefinition> = runCatching {
        val def = json.decodeFromString<YamlSkillDefinition>(jsonContent)
        require(def.id.isNotBlank()) { "Skill id is required" }
        require(def.name.isNotBlank()) { "Skill name is required" }
        require(def.keywords.size in 5..20) { "Keywords must be 5-20" }
        require(def.steps.isNotEmpty()) { "At least one step required" }
        require(def.steps.size <= MAX_STEPS) { "Maximum $MAX_STEPS steps allowed" }
        require(def.requiredCapability in listOf("MINIMAL", "PARTIAL", "FULL")) {
            "Invalid capability: ${def.requiredCapability}"
        }
        def
    }

    /**
     * Execute a parsed [YamlSkillDefinition] with the given context.
     * Returns [SkillResult] for the entire skill.
     */
    suspend fun execute(
        definition: YamlSkillDefinition,
        context: Map<String, String> = emptyMap(),
    ): SkillResult {
        return try {
            withTimeout(MAX_EXECUTION_SECONDS * 1000) {
                executeSteps(definition, context)
            }
        } catch (e: CancellationException) {
            SkillResult.Timeout(summary = "Skill '${definition.id}' timed out after ${MAX_EXECUTION_SECONDS}s")
        } catch (t: Throwable) {
            logger.e(TAG, t) { "Skill '${definition.id}' execution failed" }
            SkillResult.Failure(reason = t.message ?: "Unknown error", summary = "Skill execution failed")
        }
    }

    private suspend fun executeSteps(
        definition: YamlSkillDefinition,
        context: Map<String, String>,
    ): SkillResult {
        val variables = mutableMapOf<String, String>()
        variables.putAll(context)

        for (step in definition.steps) {
            val resolvedParams = resolveTemplates(step.params, variables)
            val targetSkill = skillRegistry.byId(step.tool)
            if (targetSkill == null) {
                val msg = "Step references unknown tool: ${step.tool}"
                return SkillResult.Failure(reason = msg, summary = msg)
            }

            val result = targetSkill.execute(resolvedParams)
            when (result) {
                is SkillResult.Success -> {
                    variables["steps.${step.id}.result"] = result.output
                    variables["steps.${step.id}.summary"] = result.summary
                }
                is SkillResult.Failure -> {
                    return when (step.onError) {
                        "continue" -> continue
                        else -> result
                    }
                }
                is SkillResult.ConfirmationRequired -> return result
                is SkillResult.Timeout -> return result
            }
        }
        return SkillResult.Success(
            output = "All steps completed",
            summary = "Executed ${definition.steps.size} steps for '${definition.name}'",
        )
    }

    /**
     * Resolve {{ }} template expressions in parameter values.
     * Supports: steps.<id>.result, steps.<id>.summary, context.<key>
     */
    private fun resolveTemplates(
        params: Map<String, String>,
        variables: Map<String, String>,
    ): Map<String, String> {
        if (!params.values.any { it.contains("{{") }) return params
        return params.mapValues { (_, value) ->
            var resolved = value
            val regex = Regex("""\{\{\s*([^}]+)\s*}}""")
            resolved = regex.replace(resolved) { match ->
                val key = match.groupValues[1]
                when {
                    key.startsWith("steps.") || key.startsWith("context.") -> {
                        variables[key] ?: match.value
                    }
                    else -> match.value
                }
            }
            resolved
        }
    }

    /**
     * Convert a parsed skill to an [AgentSkill] runtime wrapper.
     */
    fun toAgentSkill(definition: YamlSkillDefinition): AgentSkill {
        return object : AgentSkill {
            override val definition: SkillDefinition = SkillDefinition(
                id = definition.id,
                name = definition.name,
                description = definition.description,
                keywords = definition.keywords,
                requiredCapability = AgentCapability.valueOf(definition.requiredCapability),
                version = definition.version,
            )

            override suspend fun execute(params: Map<String, String>): SkillResult {
                return this@SkillScriptEngine.execute(definition, params)
            }
        }
    }
}
