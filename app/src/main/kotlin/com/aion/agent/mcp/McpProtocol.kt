package com.aion.agent.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// JSON-RPC message envelope
@Serializable
data class JsonRpcMessage(
    @SerialName("jsonrpc") val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String? = null,
    val params: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

// MCP tool definitions
@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonElement,
)

@Serializable
data class McpResourceDefinition(
    val name: String,
    val uri: String,
    val description: String,
    val mimeType: String = "text/plain",
)

@Serializable
data class McpPromptDefinition(
    val name: String,
    val description: String,
    val arguments: List<McpPromptArgument> = emptyList(),
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String,
    val required: Boolean = false,
)

// Capability negotiation
@Serializable
data class McpCapabilities(
    val tools: Map<String, Boolean> = mapOf("listChanged" to false),
    val resources: Map<String, Boolean> = mapOf("listChanged" to false),
    val prompts: Map<String, Boolean> = mapOf("listChanged" to false),
)
