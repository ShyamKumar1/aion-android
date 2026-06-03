package com.aion.agent.mcp

import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callback invoked after each MCP action is handled.
 * @param toolName  the method name (or actual tool name for tools/call)
 * @param params    the request params as a JSON string
 * @param result    "OK" or "ERROR"
 */
typealias AuditCallback = (toolName: String, params: String, result: String) -> Unit

/**
 * Handles MCP protocol messages per v2025-03-26.
 *
 * Supports: initialize, tools/list, tools/call, resources/list, resources/read, ping
 */
@Singleton
class McpProtocolHandler @Inject constructor(
    private val toolMapper: McpToolMapper,
    private val json: Json,
    private val logger: AionLogger,
) {

    suspend fun handle(
        message: String,
        clientId: String,
        auditCallback: AuditCallback? = null,
    ): String {
        return try {
            val request = json.decodeFromString<JsonRpcMessage>(message)
            val method = request.method
            if (method == null) {
                val errResp = errorResponse(request.id, -32600, "Method not specified")
                auditCallback?.invoke("unknown", message.take(200), "ERROR")
                return errResp
            }
            val params = request.params?.let { it as? JsonObject } ?: JsonObject(emptyMap())

            val response = when (method) {
                "initialize" -> handleInitialize(request.id)
                "tools/list" -> handleToolsList(request.id)
                "tools/call" -> handleToolCall(request.id, params, clientId)
                "resources/list" -> handleResourcesList(request.id)
                "ping" -> handlePing(request.id)
                "notifications/initialized" -> handlePing(request.id)
                else -> errorResponse(request.id, -32601, "Method not found: $method")
            }

            // Determine audit name: for tools/call, log the actual tool being invoked
            val auditName = if (method == "tools/call") {
                params["name"]?.jsonPrimitive?.content ?: method
            } else {
                method
            }
            val auditResult = if (response.contains("\"error\"")) "ERROR" else "OK"
            auditCallback?.invoke(auditName, params.toString(), auditResult)

            response
        } catch (t: Throwable) {
            logger.e(TAG, t) { "MCP protocol error" }
            val errResp = errorResponse(null, -32603, "Internal error: ${t.message}")
            auditCallback?.invoke("error", t.message ?: "Unknown", "ERROR")
            errResp
        }
    }

    private fun handleInitialize(id: String?): String = json.encodeToString(
        JsonRpcMessage.serializer(),
        JsonRpcMessage(id = id, result = buildJsonObject {
            put("protocolVersion", "2025-03-26")
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", false) }
                putJsonObject("resources") { put("listChanged", false) }
            }
            put("serverName", "AION")
            put("serverVersion", "1.0.0")
        })
    )

    private fun handleToolsList(id: String?): String = json.encodeToString(
        JsonRpcMessage.serializer(),
        JsonRpcMessage(id = id, result = buildJsonObject {
            putJsonArray("tools") {
                for (tool in toolMapper.allToolDefinitions()) {
                    val toolJson = buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("inputSchema", tool.inputSchema)
                    }
                    add(toolJson)
                }
            }
        })
    )

    private suspend fun handleToolCall(id: String?, params: JsonObject, clientId: String): String {
        val name = params["name"]?.jsonPrimitive?.content ?: return errorResponse(id, -32602, "Missing tool name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        val skill = toolMapper.findSkill(name)
        if (skill == null) return errorResponse(id, -32604, "Tool not found: $name")

        val argMap = arguments.toMap().mapValues { it.value.jsonPrimitive.content }
        val result = skill.execute(argMap)

        val (text, isError) = when (result) {
            is SkillResult.Success -> result.summary to false
            is SkillResult.Failure -> "Error: ${result.reason}" to true
            is SkillResult.ConfirmationRequired -> result.prompt to false
            is SkillResult.Timeout -> "Tool timed out" to true
        }

        return json.encodeToString(
            JsonRpcMessage.serializer(),
            JsonRpcMessage(id = id, result = buildJsonObject {
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                }
                put("isError", isError)
            })
        )
    }

    private fun handleResourcesList(id: String?): String = json.encodeToString(
        JsonRpcMessage.serializer(),
        JsonRpcMessage(id = id, result = buildJsonObject {
            putJsonArray("resources") {
                add(buildJsonObject {
                    put("uri", "aion://notifications/recent")
                    put("name", "Recent Notifications")
                    put("description", "Recently captured notifications")
                    put("mimeType", "text/plain")
                })
            }
        })
    )

    private fun handlePing(id: String?): String = json.encodeToString(
        JsonRpcMessage.serializer(),
        JsonRpcMessage(id = id, result = buildJsonObject { })
    )

    private fun errorResponse(id: String?, code: Int, msg: String): String = json.encodeToString(
        JsonRpcMessage.serializer(),
        JsonRpcMessage(id = id, error = JsonRpcError(code = code, message = msg))
    )

    companion object {
        private const val TAG = "McpProtocol"
    }
}
