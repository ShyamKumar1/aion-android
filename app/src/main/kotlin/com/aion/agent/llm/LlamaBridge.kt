package com.aion.agent.llm

import com.aion.agent.util.AionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level JNI bridge to llama.cpp. Per AION_GUIDELINES §10, this is the
 * ONLY class that directly calls native methods. All callers go through
 * [LocalLlmEngine].
 *
 * Thread safety: native load/unload/generate are synchronized by a mutex
 * in the C++ layer. The Kotlin side ensures no concurrent generation by
 * running on [Dispatchers.Default] (blocking I/O thread for native ops).
 *
 * The companion object's `init` block loads the native library via
 * [System.loadLibrary]. If the library is not available (e.g. on x86
 * emulator), [isAvailable] returns false and all operations are no-ops.
 */
@Singleton
class LlamaBridge @Inject constructor(
    private val logger: AionLogger,
) {

    /** Whether the native library is loaded and ready. */
    val isAvailable: Boolean
        get() = _nativeAvailable

    /** Whether a model is currently loaded in the native layer. */
    var isLoaded: Boolean = false
        private set

    /** Model name (filename) of the currently loaded model, null if not loaded. */
    var loadedModelName: String? = null
        private set

    /**
     * Load a GGUF model from [filePath]. Blocking — call from a background thread.
     *
     * @param filePath Absolute path to the .gguf file.
     * @param contextLength Model context size in tokens.
     * @param gpuLayers Number of layers to offload to GPU (0 = CPU only).
     * @return [Result.success] if loaded, [Result.failure] with [LlamaException] otherwise.
     */
    fun loadModel(
        filePath: String,
        contextLength: Int = 4096,
        gpuLayers: Int = 0,
    ): Result<Unit> = runCatching {
        check(filePath.isNotBlank()) { "Model path must not be blank" }
        check(_nativeAvailable) { "llama.cpp native library not available" }
        val ok = nativeLoadModel(filePath, contextLength, gpuLayers)
        check(ok) { "nativeLoadModel returned false — check logcat for llama.cpp error" }
        isLoaded = true
        loadedModelName = filePath.substringAfterLast('/')
        logger.d(TAG) { "Model loaded: $loadedModelName (ctx=$contextLength, gpu=$gpuLayers)" }
    }

    /**
     * Unload the model and free all resources. Safe to call when no model is loaded.
     */
    fun unloadModel() {
        if (!_nativeAvailable) return
        if (!isLoaded) return
        nativeUnloadModel()
        isLoaded = false
        loadedModelName = null
        logger.d(TAG) { "Model unloaded" }
    }

    /**
     * Generate tokens for [prompt]. Emits tokens via [Flow], one string per token.
     * The flow completes when generation finishes or [maxTokens] is reached.
     *
     * @param prompt The input text.
     * @param maxTokens Maximum tokens to generate.
     * @param temperature Sampling temperature (0.0 = greedy, 1.0 = creative).
     * @return A cold [Flow] emitting token strings on [Dispatchers.Default].
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
    ): Flow<String> = callbackFlow {
        if (!_nativeAvailable || !isLoaded) {
            throw LlamaException("Model not loaded or native unavailable")
        }
        nativeGenerate(prompt, maxTokens, temperature) { tokenText ->
            trySend(tokenText)
        }
        awaitClose { /* generation completed or cancelled */ }
    }.flowOn(Dispatchers.Default)

    /**
     * Estimate token count for [text] using the loaded model's tokenizer.
     * Returns 0 if no model is loaded or native is unavailable.
     */
    fun tokenCount(text: String): Int {
        if (!_nativeAvailable || !isLoaded) return 0
        return nativeTokenCount(text)
    }

    // ---- Native JNI declarations ----

    private external fun nativeIsAvailable(): Boolean
    private external fun nativeLoadModel(path: String, nCtx: Int, nGpuLayers: Int): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: GeneratedTokenCallback,
    )
    private external fun nativeTokenCount(text: String): Int

    companion object {
        private const val TAG = "LlamaBridge"

        /** Whether the native library was successfully loaded. */
        private val _nativeAvailable: Boolean by lazy {
            try {
                System.loadLibrary("llamabridge")
                true
            } catch (t: UnsatisfiedLinkError) {
                false
            }
        }

        init {
            // Trigger lazy loading
            _nativeAvailable.also { available ->
                if (!available) {
                    android.util.Log.w(TAG, "Native library 'llamabridge' not found — local LLM unavailable")
                }
            }
        }
    }
}

/**
 * Exception thrown by [LlamaBridge] when native operations fail.
 */
class LlamaException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Functional interface used by the JNI callback. Each time llama.cpp emits
 * a token, the native code calls [onToken] on a JNI thread. The Kotlin side
 * forwards it to a [callbackFlow] channel.
 */
fun interface GeneratedTokenCallback {
    fun onToken(text: String)
}
