package com.aion.agent.llm

import com.aion.agent.core.AionException
import com.aion.agent.util.AionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LlmEngine] that runs GGUF models via llama.cpp JNI.
 *
 * Per AION_GUIDELINES §10:
 *  - Model lifecycle is managed by [ModelManager], not this class directly.
 *  - This class is stateless w.r.t. which model is loaded — it delegates
 *    to [LlamaBridge] which holds the native state.
 *  - Streaming uses the 3-5 token buffer from [TokenBuffer] on the consumer side.
 *
 * Thread safety: All JNI calls run on [Dispatchers.Default] via the bridge.
 */
@Singleton
class LocalLlmEngine @Inject constructor(
    private val bridge: LlamaBridge,
    private val logger: AionLogger,
) : LlmEngine {

    override val backendId: String = "local-llama"

    /** The path to the currently loaded GGUF file. Null if no model loaded. */
    var loadedModelPath: String? = null
        private set

    override suspend fun isReady(): Boolean = bridge.isLoaded

    override suspend fun currentModelName(): String? =
        bridge.loadedModelName

    /**
     * Load a GGUF model into the bridge.
     *
     * @param filePath absolute path to the .gguf file on device storage.
     * @param contextLength model context size in tokens (default 4096).
     * @param gpuLayers number of layers to offload to GPU (0 = CPU only).
     */
    suspend fun loadModel(
        filePath: String,
        contextLength: Int = 4096,
        gpuLayers: Int = DEFAULT_GPU_LAYERS,
    ): Result<Unit> = kotlinx.coroutines.withContext(Dispatchers.Default) {
        bridge.loadModel(filePath, contextLength, gpuLayers)
            .onSuccess {
                loadedModelPath = filePath
                logger.d(TAG) { "Local model loaded: ${bridge.loadedModelName}" }
            }
            .onFailure { t ->
                logger.e(TAG, t) { "Failed to load model: $filePath" }
            }
    }

    /** Unload the current model. Safe to call when none loaded. */
    suspend fun unloadModel(): Unit = kotlinx.coroutines.withContext(Dispatchers.Default) {
        bridge.unloadModel()
        loadedModelPath = null
        logger.d(TAG) { "Local model unloaded" }
    }

    override fun streamReply(request: LlmRequest): Flow<LlmEvent> = flow {
        if (!bridge.isLoaded) {
            emit(LlmEvent.LlmError(
                AionException.ModelNotLoadedException(bridge.loadedModelName ?: "(none)")
            ))
            return@flow
        }

        // Build a plain-text prompt from the structured request.
        // Uses ChatML-style formatting for chat models.
        val prompt = buildPrompt(request)

        bridge.generate(
            prompt = prompt,
            maxTokens = request.maxTokens,
            temperature = request.temperature,
        ).collect { tokenText ->
            emit(LlmEvent.Token(tokenText))
        }

        emit(LlmEvent.Done(null))
    }
        .flowOn(Dispatchers.Default)
        .catch { t ->
            logger.e(TAG, t) { "Local LLM stream failed" }
            emit(LlmEvent.LlmError(t))
        }

    /**
     * Build a plain-text prompt from the structured [LlmRequest].
     * Uses ChatML delimiters (<|im_start|> / <|im_end|>) which work with
     * most instruct-tuned models including Qwen 2.5.
     */
    private fun buildPrompt(request: LlmRequest): String = buildString {
        if (request.systemPrompt.isNotBlank()) {
            appendLine("<|im_start|>system")
            appendLine(request.systemPrompt.trim())
            appendLine("<|im_end|>")
        }
        for (msg in request.messages) {
            when (msg.role) {
                LlmRole.SYSTEM -> {
                    appendLine("<|im_start|>system")
                    appendLine(msg.content.trim())
                }
                LlmRole.USER -> {
                    appendLine("<|im_start|>user")
                    appendLine(msg.content.trim())
                }
                LlmRole.ASSISTANT -> {
                    appendLine("<|im_start|>assistant")
                    appendLine(msg.content.trim())
                }
                LlmRole.TOOL -> {
                    appendLine("<|im_start|>tool")
                    appendLine(msg.content.trim())
                }
            }
            appendLine("<|im_end|>")
        }
        appendLine("<|im_start|>assistant")
    }

    companion object {
        private const val TAG = "LocalLlm"
        /** Default GPU layers for Qwen2.5 3B on Adreno 7xx. 0 = CPU-only. */
        const val DEFAULT_GPU_LAYERS = 0
    }
}
