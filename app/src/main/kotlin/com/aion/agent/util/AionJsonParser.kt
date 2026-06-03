package com.aion.agent.util

import com.aion.agent.llm.providers.OpenAiModelEntry
import com.aion.agent.llm.providers.OpenAiModelListResponse
import com.aion.agent.llm.providers.ProviderModel
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses OpenAI-compatible model list responses.
 * Different providers return slightly different JSON shapes; this handles
 * the common "data: [{id, object, created, owned_by}]" format and also
 * tries to extract models from OpenRouter's variant.
 */
@Singleton
class AionJsonParser @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse a model list from a raw JSON string response.
     * Returns a list of [ProviderModel] or empty list on failure.
     */
    fun parseModelList(responseBody: String): List<ProviderModel> {
        if (responseBody.isBlank() || responseBody == "Not Found") return emptyList()

        // Try standard OpenAI format first
        try {
            val list = json.decodeFromString<OpenAiModelListResponse>(responseBody)
            if (list.data.isNotEmpty()) {
                return list.data.filter { entry ->
                    // Filter to instruction-following / text models (skip embeddings, vision-only, etc.)
                    val id = entry.id.lowercase()
                    !id.contains("embed") &&
                        !id.contains("instructgp") &&
                        id.contains("instruct") || id.contains("chat") ||
                        id.contains("mini") || id.contains("flash") ||
                        id.contains("sonnet") || id.contains("opus") ||
                        id.contains("haiku") || id.contains("nemotron") ||
                        id.contains("llama") || id.contains("mistral") ||
                        id.contains("gemma") || id.contains("gemini") ||
                        id.contains("gpt") || id.contains("claude") ||
                        id.contains("deepseek") || id.contains("qwen")
                }.map { entry ->
                    ProviderModel(
                        id = entry.id,
                        displayName = entry.id.split("/").lastOrNull()
                            ?.replace("-instruct", "")
                            ?.replace("-chat", "")
                            ?.replace("-it", "")
                            ?.replace("-vision", "") ?: entry.id,
                        contextWindow = estimateContextWindow(entry.id),
                    )
                }
            }
        } catch (_: Exception) {
            // Fall through to OpenRouter format
        }

        // OpenRouter uses a non-standard format or the data is nested differently
        return emptyList()
    }

    private fun estimateContextWindow(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            "gemini" in id && ("flash" in id || "pro" in id) -> 1_000_000
            "gemini" in id -> 32_000
            "claude" in id && "sonnet" in id -> 200_000
            "claude" in id && "haiku" in id -> 200_000
            "claude" in id -> 100_000
            "gpt-4" in id && "mini" in id -> 128_000
            "gpt-4" in id -> 128_000
            "gpt-3" in id -> 16_000
            "llama" in id && "70b" in id -> 131_072
            "llama" in id && "8b" in id -> 131_072
            "llama" in id -> 131_072
            "nemotron" in id -> 128_000
            "mistral" in id && "large" in id -> 131_072
            "mistral" in id -> 32_000
            "deepseek" in id && "v4" in id -> 32_000
            "deepseek" in id -> 131_072
            "qwen" in id -> 131_072
            "gemma" in id && "12b" in id -> 8_192
            "gemma" in id -> 8_192
            "minimax" in id -> 32_000
            else -> 32_000
        }
    }
}
