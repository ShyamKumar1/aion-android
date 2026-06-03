package com.aion.agent.skills

import android.content.Context
import android.net.Uri
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles import and export of .skill YAML files.
 *
 * Per AION_GUIDELINES §13:
 *  - Skills are sandboxed and validated before registration
 *  - External skills are treated as untrusted until verified
 */
@Singleton
class SkillPackager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scriptEngine: SkillScriptEngine,
    private val skillRegistry: SkillRegistry,
    private val logger: AionLogger,
) {

    /**
     * Import a .skill file from a content URI.
     * Validates the YAML content and registers the skill.
     */
    suspend fun importFromUri(uri: Uri): Result<YamlSkillDefinition> = runCatching {
        val content = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        } ?: throw IllegalArgumentException("Cannot read file")

        // Parse and validate
        val definition = scriptEngine.parse(content).getOrThrow()

        // Check for duplicate IDs
        if (skillRegistry.byId(definition.id) != null) {
            throw IllegalArgumentException("Skill '${definition.id}' already exists")
        }

        // Convert to AgentSkill and register
        val agentSkill = scriptEngine.toAgentSkill(definition)
        skillRegistry.register(agentSkill)
        logger.i(TAG) { "Imported skill: ${definition.id} v${definition.version}" }
        definition
    }

    /**
     * Export a skill definition as a .skill YAML string.
     */
    fun export(skill: AgentSkill): String = buildString {
        val d = skill.definition
        appendLine("id: ${d.id}")
        appendLine("name: ${d.name}")
        appendLine("version: ${d.version}")
        appendLine("description: |")
        appendLine("  ${d.description}")
        appendLine("keywords: [${d.keywords.joinToString(", ")}]")
        appendLine("required_permissions: [${d.requiredPermissions.joinToString(", ")}]")
        appendLine("required_capability: ${d.requiredCapability.name}")
    }

    companion object {
        private const val TAG = "SkillPackager"
    }
}
