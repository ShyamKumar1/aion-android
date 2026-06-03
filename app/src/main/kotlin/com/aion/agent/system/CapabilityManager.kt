package com.aion.agent.system

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.aion.agent.core.AgentCapability
import com.aion.agent.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes which Android permissions are currently granted and emits the
 * resulting [AgentCapability] tier. This is the single source of truth for
 * "what can AION do right now."
 *
 * Per AION_GUIDELINES §4, every feature that conditionally requires a
 * capability collects from this flow — there is no local cache in any
 * ViewModel or repository.
 *
 * Per AION_PLAN §4, capability degrades gracefully: a user with no
 * AccessibilityService enabled will see PARTIAL; a user with nothing
 * enabled will see MINIMAL.
 */
@Singleton
class CapabilityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val _capability = MutableStateFlow(detect())
    val capability: StateFlow<AgentCapability> = _capability.asStateFlow()

    /**
     * Recompute the current capability. Call this after the user grants or
     * revokes a permission in Settings.
     */
    fun refresh() {
        _capability.value = detect()
    }

    /**
     * Force a specific tier — used by onboarding when the user has selected
     * their starting capability but hasn't yet enabled the corresponding
     * system permission. The next [refresh] will confirm the real tier.
     */
    suspend fun setDesired(tier: AgentCapability) {
        settings.setDesiredCapability(tier)
        refresh()
    }

    /**
     * Whether the system is currently letting AION see notifications.
     * Reads [android.provider.Settings.Secure.ENABLED_NOTIFICATION_LISTENERS].
     */
    fun hasNotificationListenerAccess(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val pkg = context.packageName
        return flat.split(":").any { it.startsWith(pkg) }
    }

    private fun detect(): AgentCapability {
        // MINIMAL is always available — chat needs nothing special.
        if (!hasAccessibilityServiceEnabled()) {
            return if (hasNotificationListenerAccess()) {
                AgentCapability.PARTIAL
            } else {
                AgentCapability.MINIMAL
            }
        }
        return AgentCapability.FULL
    }

    private fun hasAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabled = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            )
            if (enabled != 1) return false
            val services = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val pkg = context.packageName
            services.split(":").any { it.startsWith(pkg) }
        } catch (t: Throwable) {
            false
        }
    }
}
