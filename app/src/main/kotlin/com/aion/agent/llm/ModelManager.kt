package com.aion.agent.llm

import android.app.ActivityManager
import android.content.Context
import com.aion.agent.core.AionException
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle state of a model slot.
 */
sealed class ModelState {
    /** No model loaded, no load in progress. */
    data object Idle : ModelState()

    /** A load operation is in progress. */
    data class Loading(val modelName: String) : ModelState()

    /** Model is loaded and ready for inference. */
    data class Ready(val modelName: String) : ModelState()

    /** Load failed; [error] is a user-actionable message. */
    data class Error(val modelName: String, val error: String) : ModelState()
}

/**
 * Manages model lifecycle for the CLASSIFIER slot.
 *
 * Per AION_GUIDELINES §10:
 *  - CLASSIFIER slot: loaded on first user interaction after app start,
 *    stays resident unless sleep mode activates.
 *  - PLANNER slot: constants are defined (PLANNER_ESTIMATED_BYTES) but not
 *    yet implemented. Planned for Phase 3 when multi-model pipelines are needed.
 *    See AION_PLAN for details on the classifier → planner routing architecture.
 *
 * RAM check (N6): before loading any model, queries [ActivityManager.MemoryInfo].
 * If [availMem] < [requiredBytes] * 1.2, refuses to load.
 *
 * Single source of truth for model state via [state] StateFlow.
 */
@Singleton
class ModelManager @Inject constructor(
    private val engine: LocalLlmEngine,
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) {
    private val _state = MutableStateFlow<ModelState>(ModelState.Idle)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Whether a model is currently loaded and ready. */
    val isReady: Boolean get() = _state.value is ModelState.Ready

    /** The name of the currently loaded classifier model, or null. */
    val loadedModelName: String?
        get() = (_state.value as? ModelState.Ready)?.modelName

    /**
     * Load the intent classifier model.
     *
     * @param filePath path to the GGUF file on device storage.
     * @param estimatedBytes approximate RAM needed for pre-check.
     * @param gpuLayers GPU offload layers.
     */
    suspend fun loadClassifier(
        filePath: String,
        estimatedBytes: Long = CLASSIFIER_ESTIMATED_BYTES,
        gpuLayers: Int = 0,
    ): Result<Unit> {
        val modelName = filePath.substringAfterLast('/')

        // Already loaded with the same model?
        if (engine.isReady() && engine.currentModelName() == modelName) {
            _state.value = ModelState.Ready(modelName)
            logger.d(TAG) { "Classifier already loaded: $modelName" }
            return Result.success(Unit)
        }

        // Unload any previously loaded model
        if (engine.isReady()) {
            engine.unloadModel()
        }

        // RAM check (N6 — never load if insufficient RAM)
        val ramCheck = checkRam(estimatedBytes)
        if (ramCheck.isFailure) {
            _state.value = ModelState.Error(modelName, ramCheck.exceptionOrNull()?.message ?: "Insufficient RAM")
            return ramCheck
        }

        _state.value = ModelState.Loading(modelName)

        return withContext(Dispatchers.Default) {
            engine.loadModel(filePath, gpuLayers = gpuLayers)
                .onSuccess {
                    _state.value = ModelState.Ready(modelName)
                    logger.d(TAG) { "Classifier loaded: $modelName" }
                }
                .onFailure { t ->
                    val msg = t.message ?: "Unknown error"
                    _state.value = ModelState.Error(modelName, msg)
                    logger.e(TAG, t) { "Failed to load classifier: $modelName" }
                }
        }
    }

    /** Mark classifier slot as ready externally (e.g. after sleep reload). */
    fun markReady(modelName: String) {
        _state.value = ModelState.Ready(modelName)
    }

    /** Unload the classifier model. Safe to call when not loaded. */
    suspend fun unloadClassifier() {
        if (!engine.isReady()) {
            _state.value = ModelState.Idle
            return
        }
        withContext(Dispatchers.Default) {
            engine.unloadModel()
        }
        _state.value = ModelState.Idle
        logger.d(TAG) { "Classifier unloaded" }
    }

    /**
     * Check available RAM against required amount (N6).
     * Returns [Result.failure] with [InsufficientRamException] if insufficient.
     */
    private fun checkRam(requiredBytes: Long): Result<Unit> {
        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memInfo)
        if (memInfo.availMem < requiredBytes * RAM_HEADROOM_MULTIPLIER) {
            val availableMb = memInfo.availMem / 1_000_000
            val requiredMb = requiredBytes / 1_000_000
            logger.w(TAG) {
                "RAM check: need ${requiredMb}MB, have ${availableMb}MB " +
                "(need ${(requiredBytes * RAM_HEADROOM_MULTIPLIER) / 1_000_000}MB with headroom)"
            }
            return Result.failure(
                AionException.InsufficientRamException(
                    (requiredBytes * RAM_HEADROOM_MULTIPLIER).toLong(),
                    memInfo.availMem,
                )
            )
        }
        return Result.success(Unit)
    }

    companion object {
        private const val TAG = "ModelManager"
        /** Estimated RAM for Qwen2.5-3B-Q4_K_M (~1.8GB). */
        const val CLASSIFIER_ESTIMATED_BYTES: Long = 1_800_000_000L
        /** Estimated RAM for 7B model (~3.5GB). Not yet implemented — reserved for Phase 3. */
        const val PLANNER_ESTIMATED_BYTES: Long = 3_500_000_000L
        /** Estimated RAM for embedding model (~200MB). */
        const val EMBEDDING_ESTIMATED_BYTES: Long = 200_000_000L
        /** Require 1.2x available RAM over model size before loading. */
        const val RAM_HEADROOM_MULTIPLIER = 1.2
    }
}
