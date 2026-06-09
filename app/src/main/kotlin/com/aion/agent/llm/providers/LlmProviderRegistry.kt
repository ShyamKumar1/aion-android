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
    val supportsModelList: Boolean = true,
    val notes: String = "",
    val modelListPath: String = "models",
)

data class ProviderModel(
    val id: String,
    val displayName: String,
    val contextWindow: Int,
    val supportsTools: Boolean = true,
    val notes: String = "",
)

/**
 * Registry of all supported LLM providers. All speak the OpenAI Chat
 * Completions API (POST /v1/chat/completions with SSE streaming),
 * so [CloudLlmEngine] is a single implementation that swaps config.
 */
object LlmProviderRegistry {

    val OpenRouter = ProviderConfig(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        modelListPath = "models",
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

    val NvidiaNim = ProviderConfig(
        id = "nvidia-nim",
        displayName = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1/",
        modelListPath = "models",
        apiKeyHeader = "Authorization",
        apiKeyPrefix = "Bearer ",
        availableModels = listOf(
            ProviderModel("nvidia/llama-3.1-nemotron-70b-instruct", "Nemotron 70B", 131_072, notes = "Best NVIDIA chat model"),
            ProviderModel("nvidia/nemotron-4-340b-instruct", "Nemotron 4 340B", 4_096, notes = "NVIDIA's flagship, no tools"),
            ProviderModel("meta/llama-3.1-70b-instruct", "Llama 3.1 70B (NIM)", 131_072),
            ProviderModel("meta/llama-3.1-8b-instruct", "Llama 3.1 8B (NIM)", 131_072, notes = "Smaller, faster"),
            ProviderModel("mistralai/mistral-large", "Mistral Large", 131_072),
            ProviderModel("google/gemma-3-12b-it", "Gemma 3 12B", 8_192, notes = "Google lightweight"),
        ),
        supportsToolCalling = false,
        notes = "Hosted NIM at build.nvidia.com — OpenAI-compatible.",
    )

    val OpenAI = ProviderConfig(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        availableModels = listOf(
            ProviderModel("gpt-4o", "GPT-4o", 128_000, notes = "Latest multimodal flagship"),
            ProviderModel("gpt-4o-mini", "GPT-4o Mini", 128_000, notes = "Cheap, fast, good tool use"),
            ProviderModel("gpt-4-turbo", "GPT-4 Turbo", 128_000),
            ProviderModel("gpt-3.5-turbo", "GPT-3.5 Turbo", 16_000, notes = "Legacy, very cheap"),
        ),
        notes = "Direct OpenAI API — requires a paid OpenAI API key.",
    )

    val Groq = ProviderConfig(
        id = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        availableModels = listOf(
            ProviderModel("llama-3.3-70b-versatile", "Llama 3.3 70B", 131_072, notes = "Fastest — Groq LPUs"),
            ProviderModel("llama-3.1-8b-instant", "Llama 3.1 8B", 131_072, notes = "Very fast, good for simple tasks"),
            ProviderModel("mixtral-8x7b-32768", "Mixtral 8x7B", 32_768),
            ProviderModel("gemma2-9b-it", "Gemma 2 9B", 8_192),
        ),
        notes = "Blazing fast inference on Groq hardware. Free tier available.",
    )

    val Together = ProviderConfig(
        id = "together",
        displayName = "Together AI",
        baseUrl = "https://api.together.xyz/v1/",
        availableModels = listOf(
            ProviderModel("meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo", "Llama 3.1 70B Turbo", 131_072),
            ProviderModel("meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo", "Llama 3.1 8B Turbo", 131_072),
            ProviderModel("mistralai/Mixtral-8x22B-Instruct-v0.1", "Mixtral 8x22B", 65_536),
            ProviderModel("Qwen/Qwen2.5-72B-Instruct-Turbo", "Qwen 2.5 72B", 131_072),
        ),
        notes = "Broad open-weight model selection.",
    )

    val DeepInfra = ProviderConfig(
        id = "deepinfra",
        displayName = "DeepInfra",
        baseUrl = "https://api.deepinfra.com/v1/openai/",
        availableModels = listOf(
            ProviderModel("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Llama 3.3 70B Turbo", 131_072, notes = "Fast, affordable inference"),
            ProviderModel("Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B", 131_072),
            ProviderModel("mistralai/Mixtral-8x22B-Instruct-v0.1", "Mixtral 8x22B", 65_536),
            ProviderModel("deepseek-ai/DeepSeek-V4-Flash", "DeepSeek V4 Flash", 32_000, notes = "Fast, low cost"),
        ),
        notes = "Broad open-weight model selection at competitive prices.",
    )

    val OpencodeGo = ProviderConfig(
        id = "opencode-go",
        displayName = "OpenCode Go",
        baseUrl = "https://opencode.ai/zen/go/v1/",
        modelListPath = "models",
        apiKeyHeader = "Authorization",
        apiKeyPrefix = "Bearer ",
        defaultHeaders = mapOf("User-Agent" to "opencode/1.15.13"),
        supportsToolCalling = false,
        availableModels = listOf(
            ProviderModel("deepseek-v4-flash", "DeepSeek V4 Flash", 32_000, notes = "Fast, cheap — best for coding"),
            ProviderModel("deepseek-v4-pro", "DeepSeek V4 Pro", 64_000, notes = "Premium DeepSeek reasoning"),
            ProviderModel("minimax-m3", "MiniMax M3", 32_000, notes = "Strong all-around model"),
            ProviderModel("minimax-m2.7", "MiniMax M2.7", 32_000),
            ProviderModel("minimax-m2.5", "MiniMax M2.5", 32_000),
            ProviderModel("glm-5", "GLM-5", 32_000),
            ProviderModel("glm-5.1", "GLM-5.1", 32_000),
            ProviderModel("kimi-k2.5", "Kimi K2.5", 128_000, notes = "Long context"),
            ProviderModel("kimi-k2.6", "Kimi K2.6", 128_000),
            ProviderModel("qwen3.7-max", "Qwen 3.7 Max", 32_000),
            ProviderModel("qwen3.6-plus", "Qwen 3.6 Plus", 32_000),
            ProviderModel("qwen3.5-plus", "Qwen 3.5 Plus", 32_000),
            ProviderModel("mimo-v2.5", "MiMo V2.5", 32_000, notes = "Very cheap, fast coding"),
            ProviderModel("mimo-v2.5-pro", "MiMo V2.5 Pro", 32_000),
            ProviderModel("mimo-v2-pro", "MiMo V2 Pro", 32_000),
            ProviderModel("mimo-v2-omni", "MiMo V2 Omni", 32_000),
        ),
        notes = "Go subscription at opencode.ai. $5 first month, then $10/mo.",
    )

    val All: List<ProviderConfig> = listOf(OpenRouter, OpenAI, Groq, Together, OpencodeGo, NvidiaNim)

    fun byId(id: String): ProviderConfig? = All.firstOrNull { it.id == id }
}

/**
 * OpenAI-compatible chat completions request payload.
 * All Phase-1 providers speak this schema.
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
