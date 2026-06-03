package com.aion.agent.llm

import com.aion.agent.core.AionException
import com.aion.agent.skills.SkillDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for all LLM backends. Implementations:
 *   - [CloudLlmEngine]  (Phase 1) — OpenAI-compatible HTTP
 *   - [LocalLlmEngine]  (Phase 2) — llama.cpp via llama-android
 *   - [EdgeServerEngine] (Phase 1 stretch) — Ollama/vLLM LAN discovery
 *
 * All implementations must:
 *  - Stream tokens via [streamReply] as a cold [Flow]
 *  - Honor tool/function definitions via [streamReplyWithTools]
 *  - Honor the cancellation of the collecting coroutine
 *  - Return [Result] failures with [AionException] subtypes
 */
interface LlmEngine {

    /** Stable identifier for this backend (e.g. "cloud-openrouter", "local-llama"). */
    val backendId: String

    /** True if a model is currently loaded and ready to serve requests. */
    suspend fun isReady(): Boolean

    /** Human-readable name of the currently loaded model, or null if not ready. */
    suspend fun currentModelName(): String?

    /**
     * Stream a chat reply.
     *
     * @param request the [LlmRequest] with messages, system prompt, optional tools.
     * @return a [Flow] of token chunks. The flow completes when generation finishes
     *         and emits a [LlmError] (or a final chunk) if the call fails.
     */
    fun streamReply(request: LlmRequest): Flow<LlmEvent>
}

/**
 * What the LLM stream emits. [Token] chunks are accumulated by the UI; [Done]
 * marks the end of a successful response; [LlmError] terminates the flow with
 * a typed failure.
 */
sealed class LlmEvent {
    data class Token(val text: String) : LlmEvent()
    data class ToolCall(
        val toolName: String,
        val argumentsJson: String,
    ) : LlmEvent()
    data class Done(val usage: LlmUsage?) : LlmEvent()
    data class LlmError(val cause: Throwable) : LlmEvent()
}

/**
 * Token usage reported by some providers. Optional — not every cloud provider
 * returns this in stream mode.
 */
data class LlmUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)

/**
 * A single LLM call. Per AION_GUIDELINES §10, prompts are bounded and tool
 * definitions are included only when tool use is expected.
 */
data class LlmRequest(
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val tools: List<SkillDefinition> = emptyList(),
    val maxTokens: Int = 512,
    val temperature: Float = 0.4f,
    val stream: Boolean = true,
)

/**
 * One message in a conversation. Roles: system / user / assistant / tool.
 */
data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
)

enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }
