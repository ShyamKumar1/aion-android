package com.aion.agent.system

import com.aion.agent.data.SettingsRepository
import com.aion.agent.llm.ModelManager
import com.aion.agent.util.AionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sleep state of the local model.
 */
enum class SleepState {
    /** Model is loaded, actively serving requests. */
    Active,
    /** No activity for the timeout period — model may be unloaded. */
    Sleeping,
    /** Model is being reloaded after sleep. */
    Reloading,
}

/**
 * Monitors user activity and unloads the intent classifier model after
 * [SLEEP_TIMEOUT_MS] of inactivity to save battery and RAM.
 *
 * Per AION_PLAN §12 (Phase 2 Week 7):
 *  - 5 min idle → unload model (free ~1.8GB RAM)
 *  - On next trigger → cold reload (~2-3s)
 *  - Active mode: user is chatting → model stays loaded
 */
@Singleton
class SleepController @Inject constructor(
    private val modelManager: ModelManager,
    private val settings: SettingsRepository,
    private val logger: AionLogger,
) {
    private val _sleepState = MutableStateFlow(SleepState.Active)
    val sleepState: StateFlow<SleepState> = _sleepState.asStateFlow()

    private var idleJob: Job? = null
    private var timeoutMs: Long = SLEEP_TIMEOUT_MS

    /** Call this whenever the user interacts with the app. Resets the idle timer. */
    fun onUserInteraction() {
        idleJob?.cancel()
        if (_sleepState.value == SleepState.Sleeping) {
            logger.d(TAG) { "User interaction during sleep — will reload on next request" }
        }
        _sleepState.value = SleepState.Active
    }

    /** Start the idle timer in the provided [scope]. */
    fun start(scope: CoroutineScope) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(timeoutMs)
            if (_sleepState.value == SleepState.Active) {
                logger.d(TAG) { "Idle timeout — entering sleep mode" }
                _sleepState.value = SleepState.Sleeping
                modelManager.unloadClassifier()
                logger.d(TAG) { "Model unloaded by sleep controller" }
            }
        }
    }

    /** Stop the idle timer. */
    fun stop() {
        idleJob?.cancel()
        idleJob = null
    }

    /** Called before inference to reload the model if it was sleeping. */
    suspend fun reloadForInference() {
        if (_sleepState.value != SleepState.Sleeping) return
        _sleepState.value = SleepState.Reloading
        val modelPath = settings.getLastLoadedModelPath()
        if (modelPath != null) {
            modelManager.loadClassifier(modelPath)
        }
        _sleepState.value = SleepState.Active
    }

    /** Update the idle timeout duration. */
    suspend fun setTimeoutMinutes(minutes: Int) {
        timeoutMs = minutes * 60_000L
        settings.setSleepTimeoutMinutes(minutes)
    }

    /** Get the configured timeout in minutes. */
    suspend fun getTimeoutMinutes(): Int = settings.getSleepTimeoutMinutes()

    companion object {
        private const val TAG = "SleepController"
        /** Default idle timeout: 5 minutes. */
        const val SLEEP_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
