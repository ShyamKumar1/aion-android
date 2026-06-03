package com.aion.agent.mcp

import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolMapper @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val json: Json,
) {
    fun toToolDefinition(skill: AgentSkill): McpToolDefinition = McpToolDefinition(
        name = skill.definition.id,
        description = skill.definition.description,
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                for (param in skill.definition.parameters) {
                    putJsonObject(param.name) {
                        put("type", param.jsonType)
                        put("description", param.description)
                    }
                }
            })
            put("required", buildJsonObject {
                for (param in skill.definition.parameters.filter { it.required }) {
                    put(param.name, true)
                }
            })
        },
    )

    fun allToolDefinitions(): List<McpToolDefinition> =
        skillRegistry.all().map { toToolDefinition(it) }

    fun findSkill(toolName: String): AgentSkill? =
        skillRegistry.byId(toolName)
}
