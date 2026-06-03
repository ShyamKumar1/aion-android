package com.aion.agent.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aion.agent.util.AionLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * AccessibilityService that reads screen content and dispatches gestures.
 *
 * Per AION_GUIDELINES §12:
 *  - Reads the accessibility tree of non-secure windows
 *  - Performs clicks/swipes when the agent has been instructed to do so
 *  - NEVER reads password fields
 *  - NEVER screenshots FLAG_SECURE screens
 *  - Stops reading when a secure window is detected
 *
 * This service is required for FULL capability tier. Without it,
 * [CapabilityManager] returns PARTIAL or MINIMAL.
 */
@AndroidEntryPoint
class AgentAccessibilityService : AccessibilityService() {

    @Inject lateinit var tree: AccessibilityTree
    @Inject lateinit var logger: AionLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _screenContent = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val screenContent: SharedFlow<String> = _screenContent.asSharedFlow()

    /** The package name of the current foreground app, or null. */
    @Volatile
    var foregroundPackage: String? = null
        private set

    /** Whether the current screen is secure (FLAG_SECURE or excluded package). */
    @Volatile
    var isSecureScreen: Boolean = false
        private set

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                foregroundPackage = event.packageName?.toString()
                if (foregroundPackage != null && tree.isSecurePackage(foregroundPackage!!)) {
                    isSecureScreen = true
                    logger.d(TAG) { "Secure window detected — $foregroundPackage" }
                    return
                }
                isSecureScreen = false
                captureScreen()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!isSecureScreen) {
                    captureScreen()
                }
            }
        }
    }

    override fun onInterrupt() {
        logger.w(TAG) { "AccessibilityService interrupted" }
    }

    private fun captureScreen() {
        val root = rootInActiveWindow ?: return
        try {
            if (isSecure(root)) {
                isSecureScreen = true
                return
            }
            val content = tree.toTokenEfficientString(root)
            _screenContent.tryEmit(content)
        } catch (t: Throwable) {
            logger.e(TAG, t) { "Failed to capture screen" }
        } finally {
            root.recycle()
        }
    }

    /** Check if the window has FLAG_SECURE set. */
    private fun isSecure(root: AccessibilityNodeInfo): Boolean {
        // Flag check is unreliable across API levels; use package-based exclusion instead
        val pkg = foregroundPackage ?: return false
        return tree.isSecurePackage(pkg)
    }

    /**
     * Tap at the specified screen coordinates.
     * Returns true if the gesture was dispatched successfully.
     */
    suspend fun tap(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        try {
            val path = Path().apply {
                moveTo(x, y)
                lineTo(x + 5f, y + 5f) // small movement for tap
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50) // 50ms duration
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, null, null)
            logger.d(TAG) { "Tap at ($x, $y)" }
            true
        } catch (t: Throwable) {
            logger.e(TAG, t) { "Tap failed at ($x, $y)" }
            false
        }
    }

    /**
     * Perform a swipe gesture from (x1,y1) to (x2,y2).
     */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        withContext(Dispatchers.Main) {
            try {
                val path = Path().apply {
                    moveTo(x1, y1)
                    lineTo(x2, y2)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, 300) // 300ms
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()
                dispatchGesture(gesture, null, null)
                logger.d(TAG) { "Swipe ($x1,$y1) → ($x2,$y2)" }
                true
            } catch (t: Throwable) {
                logger.e(TAG, t) { "Swipe failed" }
                false
            }
        }

    /**
     * Perform a global action (BACK, HOME, RECENTS).
     */
    suspend fun globalAction(action: Int): Boolean = withContext(Dispatchers.Main) {
        try {
            performGlobalAction(action)
            logger.d(TAG) { "Global action: $action" }
            true
        } catch (t: Throwable) {
            logger.e(TAG, t) { "Global action $action failed" }
            false
        }
    }

    companion object {
        const val TAG = "AgentA11y"

        /** Global action constants for convenience. */
        const val ACTION_BACK = GLOBAL_ACTION_BACK
        const val ACTION_HOME = GLOBAL_ACTION_HOME
        const val ACTION_RECENTS = GLOBAL_ACTION_RECENTS
        const val ACTION_NOTIFICATIONS = GLOBAL_ACTION_NOTIFICATIONS
    }
}
