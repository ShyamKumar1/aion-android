package com.aion.agent.data

import com.aion.agent.core.AionException
import com.aion.agent.llm.providers.LlmProviderRegistry
import com.aion.agent.llm.providers.ProviderConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the active cloud provider, its API key, and the
 * selected model. Backed by [SettingsRepository] which uses DataStore +
 * EncryptedSharedPreferences.
 *
 * Per AION_GUIDELINES §7, API keys are never passed via constructor and never
 * logged. They are read at call time by the consumer (e.g. [CloudLlmEngine]).
 */
@Singleton
class ProviderRepository @Inject constructor(
    private val settings: SettingsRepository,
) {

    suspend fun activeProviderId(): String? = settings.activeProviderId()
    suspend fun activeProvider(): ProviderConfig? = activeProviderId()?.let(LlmProviderRegistry::byId)
    suspend fun activeModelId(): String? = settings.activeModelId()
    suspend fun activeApiKey(): String? {
        val providerId = activeProviderId() ?: return null
        return settings.activeApiKey(providerId)
    }
    suspend fun hasActiveProvider(): Boolean = activeProviderId() != null && activeApiKey() != null

    suspend fun activeProviderOrThrow(): ProviderConfig =
        activeProvider()
            ?: throw AionException.InvalidConfigurationException("No active provider selected")

    suspend fun setActiveProvider(providerId: String, modelId: String) {
        settings.setActiveProvider(providerId, modelId)
    }

    suspend fun setApiKey(providerId: String, apiKey: String) {
        settings.setApiKey(providerId, apiKey)
    }

    suspend fun clearApiKey(providerId: String) {
        settings.clearApiKey(providerId)
    }
}
