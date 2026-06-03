package com.aion.agent.skills

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.aion.agent.core.AgentCapability
import com.aion.agent.system.CapabilityManager
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TriggerRule(
    val id: String,
    val skillId: String,
    val type: TriggerType,
    val value: String,     // e.g. "07:00" for time, "good morning" for phrase
    val enabled: Boolean = true,
    val capability: AgentCapability = AgentCapability.MINIMAL,
)

enum class TriggerType { TIME, EVENT, PHRASE, STATE }

data class TriggerEvent(
    val type: TriggerType,
    val value: String,
    val data: Map<String, String> = emptyMap(),
)

/**
 * Engine that watches for trigger conditions and executes associated skills.
 *
 * Per AION_PLAN §14 (Phase 4 Week 20-21):
 *  - Time triggers: WorkManager periodic tasks
 *  - Event triggers: NotificationListener callbacks
 *  - Phrase triggers: Chat message contains trigger phrase
 *  - State triggers: AccessibilityService detects app opened
 *
 * Debouncing prevents double-fire within 30 seconds.
 */
@Singleton
class TriggerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillRegistry: SkillRegistry,
    private val capabilityManager: CapabilityManager,
    private val logger: AionLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _triggers = MutableStateFlow<List<TriggerRule>>(emptyList())
    val triggers: StateFlow<List<TriggerRule>> = _triggers.asStateFlow()
    private val lastFired = mutableMapOf<String, Long>()
    private val debounceMs = 30_000L

    fun register(rule: TriggerRule) {
        _triggers.value = _triggers.value + rule
        logger.d(TAG) { "Trigger registered: ${rule.type} ${rule.value} → ${rule.skillId}" }
    }

    fun unregister(ruleId: String) {
        _triggers.value = _triggers.value.filter { it.id != ruleId }
    }

    /**
     * Called when a trigger event occurs. Finds matching triggers and
     * executes their skills.
     */
    fun onEvent(event: TriggerEvent) {
        val tier = capabilityManager.capability.value
        val matching = _triggers.value.filter { rule ->
            if (!rule.enabled || rule.capability.ordinal > tier.ordinal) return@filter false
            val match = when (event.type) {
                TriggerType.TIME -> rule.type == TriggerType.TIME && rule.value == event.value
                TriggerType.EVENT -> rule.type == TriggerType.EVENT && rule.value == event.value
                TriggerType.PHRASE -> rule.type == TriggerType.PHRASE && event.value.contains(rule.value, ignoreCase = true)
                TriggerType.STATE -> rule.type == TriggerType.STATE && rule.value == event.value
            }
            match && isNotDebounced(rule.id)
        }

        for (rule in matching) {
            scope.launch {
                lastFired[rule.id] = System.currentTimeMillis()
                val skill = skillRegistry.byId(rule.skillId)
                if (skill != null) {
                    logger.i(TAG) { "Trigger firing: ${rule.id} → ${rule.skillId}" }
                    skill.execute(event.data)
                }
            }
        }
    }

    private fun isNotDebounced(ruleId: String): Boolean {
        val last = lastFired[ruleId] ?: return true
        return (System.currentTimeMillis() - last) > debounceMs
    }

    /** Register common phrase-based triggers from user input patterns. */
    fun suggestFromChat(input: String) {
        if (input.contains("every morning", ignoreCase = true) ||
            input.contains("each morning", ignoreCase = true)) {
            logger.i(TAG) { "Detected potential time trigger: $input" }
            // In Phase 4+, this would prompt user to create a trigger
        }
    }

    companion object {
        private const val TAG = "TriggerEngine"
    }
}
