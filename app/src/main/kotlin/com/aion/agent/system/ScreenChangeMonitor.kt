package com.aion.agent.system

import com.aion.agent.util.AionLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Debounces rapid screen change events from [AgentAccessibilityService].
 * Emits the latest screen content only after [debounceMs] of silence.
 *
 * This prevents the agent loop from being overwhelmed during high-frequency
 * events like typing or scrolling.
 *
 * TODO: Wire this into [AgentLoop] or [TriggerEngine]. Currently,
 * [AgentAccessibilityService.onAccessibilityEvent] directly captures
 * screen content via [AccessibilityTree] without debouncing. This monitor
 * should be integrated in Phase 3 when high-frequency screen events
 * (scrolling, typing) need debouncing before reaching the LLM context.
 */
@Singleton
class ScreenChangeMonitor @Inject constructor(
    private val logger: AionLogger,
) {
    private val _screenUpdates = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 2)
    val screenUpdates: SharedFlow<String> = _screenUpdates.asSharedFlow()

    private var debounceJob: Job? = null

    /**
     * Called by [AgentAccessibilityService] when a screen change is detected.
     * [content] is the token-efficient string from [AccessibilityTree].
     */
    fun onScreenChanged(content: String, scope: CoroutineScope) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            _screenUpdates.tryEmit(content)
            logger.d(TAG) { "Screen content emitted (${content.length} chars)" }
        }
    }

    /** Cancel pending debounce. */
    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
    }

    companion object {
        private const val TAG = "ScreenMonitor"
        /** Debounce window: wait 50ms after the last event before emitting. */
        const val DEBOUNCE_MS = 50L
    }
}
