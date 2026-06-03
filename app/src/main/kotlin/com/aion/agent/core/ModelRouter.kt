package com.aion.agent.core

import com.aion.agent.llm.CloudLlmEngine
import com.aion.agent.llm.LlmEngine
import com.aion.agent.llm.LocalLlmEngine
import com.aion.agent.system.BatteryMonitor
import com.aion.agent.util.AionLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes queries to the best available [LlmEngine] based on:
 *  - Intent complexity (from IntentClassifier)
 *  - Battery state (BatteryMonitor)
 *  - User preference (RoutePreference)
 *  - Engine availability
 *
 * Fallback chain: Local → Cloud → exception.
 * Edge server (Ollama) is deferred to Phase 3+.
 *
 * Per AION_PLAN §5 (Model Router):
 *  - Simple tool calls & Q&A → local 3B
 *  - Complex reasoning → cloud
 *  - Battery < 20% and not charging → prefer cloud
 */
@Singleton
class ModelRouter @Inject constructor(
    private val localEngine: LocalLlmEngine,
    private val cloudEngine: CloudLlmEngine,
    private val batteryMonitor: BatteryMonitor,
    private val logger: AionLogger,
) {

    /**
     * User-selectable routing preference.
     * Stored in [com.aion.agent.data.SettingsRepository].
     */
    enum class RoutePreference(val label: String) {
        Auto("Auto — let AION decide"),
        AlwaysLocal("Always Local — maximum privacy"),
        MaximumIntelligence("Maximum Intelligence — use best model available"),
        BatterySaver("Battery Saver — cloud preferred on battery"),
    }

    /**
     * Select the best engine for a query.
     *
     * @param complexity A 0.0–1.0 score from IntentClassifier. Low = simple.
     * @param preference User's routing preference.
     * @return The selected [LlmEngine].
     * @throws AionException if no engine is available.
     */
    suspend fun selectEngine(
        complexity: Float = 0.5f,
        preference: RoutePreference = RoutePreference.Auto,
    ): LlmEngine {
        batteryMonitor.refresh()
        val batteryLevel = batteryMonitor.batteryLevel
        val isCharging = batteryMonitor.isCharging
        val localReady = localEngine.isReady()
        val cloudReady = cloudEngine.isReady()

        logger.d(TAG) {
            "Route: complexity=%.2f, pref=%s, bat=%.0f%%, charging=%b, local=%b, cloud=%b"
                .format(complexity, preference.name, batteryLevel, isCharging, localReady, cloudReady)
        }

        return when (preference) {
            RoutePreference.AlwaysLocal -> {
                if (localReady) localEngine
                else if (cloudReady) cloudEngine
                else throw AionException.InvalidConfigurationException(
                    "Local model selected but not loaded, and no cloud provider configured."
                )
            }

            RoutePreference.MaximumIntelligence -> {
                // Cloud is "maximum intelligence" — it has access to larger models.
                if (cloudReady) cloudEngine
                else if (localReady) localEngine
                else throw AionException.InvalidConfigurationException(
                    "No model engine available."
                )
            }

            RoutePreference.BatterySaver -> {
                // On battery: prefer cloud. On charger: prefer local.
                if (isCharging && localReady) localEngine
                else if (cloudReady) cloudEngine
                else if (localReady) localEngine
                else throw AionException.InvalidConfigurationException(
                    "No model engine available."
                )
            }

            RoutePreference.Auto -> {
                // Auto: simple + local ready → local; complex or low batt → cloud
                if (complexity < COMPLEXITY_THRESHOLD && localReady) {
                    if (batteryLevel < LOW_BATTERY_THRESHOLD && !isCharging && cloudReady) {
                        cloudEngine
                    } else {
                        localEngine
                    }
                } else if (cloudReady) {
                    cloudEngine
                } else if (localReady) {
                    localEngine
                } else {
                    throw AionException.InvalidConfigurationException(
                        "No model engine available. Configure a cloud provider or load a local model."
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "ModelRouter"
        /** Queries with complexity below this go to local model. */
        const val COMPLEXITY_THRESHOLD = 0.5f
        /** Below this battery %, prefer cloud to save local model RAM. */
        const val LOW_BATTERY_THRESHOLD = 20f
    }
}
