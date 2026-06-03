package com.aion.agent.llm.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Static configuration for a cloud LLM provider. Per AION_GUIDELINES §10,
 * providers are OpenAI-compatible by default — this is just a thin config
 * pointing at the right base URL and adding provider-specific headers.
 *
 * Adding a new provider means: add an entry in [LlmProviderRegistry] and
 * pick a model. No code changes.
 */
data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val apiKeyHeader: String = "Authorization",
    val apiKeyPrefix: String = "Bearer ",
    val requiresCustomHeader: Boolean = false,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val availableModels: List<ProviderModel>,
    val supportsToolCalling: Boolean = true,
    val notes: String = "",
)

data class ProviderModel(
    val id: String,
    val displayName: String,
    val contextWindow: Int,
    val supportsTools: Boolean = true,
    val notes: String = "",
)

/**
 * The three providers AION supports in Phase 1. All three speak the
 * OpenAI Chat Completions API (POST /v1/chat/completions with SSE streaming),
 * so [CloudLlmEngine] is a single implementation that swaps config.
 */
object LlmProviderRegistry {

    val OpenRouter = ProviderConfig(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        defaultHeaders = mapOf(
            "HTTP-Referer" to "https://github.com/ShyamKumar1/aion-android",
            "X-Title" to "AION",
        ),
        availableModels = listOf(
            ProviderModel("openai/gpt-4o-mini", "GPT-4o mini", 128_000, notes = "Cheap, fast, good tool use"),
            ProviderModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", 200_000, notes = "Best tool use, long context"),
            ProviderModel("google/gemini-2.0-flash-exp", "Gemini 2.0 Flash", 1_000_000, notes = "Massive context, fast"),
            ProviderModel("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B", 131_072, notes = "Open weights, strong"),
        ),
        notes = "Single API key for every model. Recommended default.",
    )

    val OpencodeGo = ProviderConfig(
        id = "opencode-go",
        displayName = "Opencode Go",
        baseUrl = "https://api.opencode.ai/v1/",
        availableModels = listOf(
            ProviderModel("minimax-m3", "MiniMax M3", 32_000, notes = "Default Opencode model"),
            ProviderModel("deepseek-v4-flash", "DeepSeek V4 Flash", 32_000, notes = "Fast DeepSeek variant"),
        ),
        notes = "Used by Opencode CLI. Free tier available — confirm at opencode.ai.",
    )

    val NvidiaNim = ProviderConfig(
        id = "nvidia-nim",
        displayName = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1/",
        apiKeyHeader = "Authorization",
        apiKeyPrefix = "Bearer ",
        availableModels = listOf(
            ProviderModel("meta/llama-3.1-70b-instruct", "Llama 3.1 70B (NIM)", 131_072),
            ProviderModel("meta/llama-3.1-8b-instruct", "Llama 3.1 8B (NIM)", 131_072, notes = "Smaller, faster"),
            ProviderModel("nvidia/nemotron-4-340b-instruct", "Nemotron 4 340B", 4_096, notes = "NVIDIA's flagship"),
        ),
        notes = "Free tier at build.nvidia.com. Hosted NIM, no infra required.",
    )

    val All: List<ProviderConfig> = listOf(OpenRouter, OpencodeGo, NvidiaNim)

    fun byId(id: String): ProviderConfig? = All.firstOrNull { it.id == id }
}

/**
 * OpenAI-compatible chat completions request payload.
 * We use this exact schema because every Phase-1 provider speaks it.
 */
@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float,
    val max_tokens: Int,
    val stream: Boolean,
    val tools: List<OpenAiTool>? = null,
)

@Serializable
internal data class OpenAiMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
internal data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiToolCallFunction,
)

@Serializable
internal data class OpenAiToolCallFunction(
    val name: String,
    val arguments: String,
)

@Serializable
internal data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunction,
)

@Serializable
internal data class OpenAiFunction(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonElement,
)

@Serializable
internal data class OpenAiStreamChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<OpenAiStreamChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
internal data class OpenAiStreamChoice(
    val index: Int = 0,
    val delta: OpenAiStreamDelta = OpenAiStreamDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAiStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null,
)

@Serializable
internal data class OpenAiToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiToolCallFunctionDelta? = null,
)

@Serializable
internal data class OpenAiToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)
