package com.aion.agent.llm.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /v1/models for OpenAI-compatible providers.
 * Most providers return this format; a few (like OpenRouter) may differ.
 */
@Serializable
data class OpenAiModelListResponse(
    val data: List<OpenAiModelEntry> = emptyList(),
    @SerialName("object") val obj: String? = null,
)

@Serializable
data class OpenAiModelEntry(
    val id: String,
    @SerialName("object") val obj: String? = null,
    val created: Long? = null,
    @SerialName("owned_by") val ownedBy: String? = null,
)
