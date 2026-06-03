package com.aion.agent.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aion.agent.core.AgentCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for user settings. Per AION_GUIDELINES §7:
 *  - API keys are stored in EncryptedSharedPreferences (Android Keystore-backed).
 *  - Non-secret settings (active provider, model) live in DataStore.
 *
 * The two stores are kept in sync via the same logical "user settings" surface.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val ds = context.aionDataStore
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ---- Non-secret preferences (DataStore) ----

    val activeProviderIdFlow: Flow<String?> = ds.data.map { it[KEY_PROVIDER] }
    val activeModelIdFlow: Flow<String?> = ds.data.map { it[KEY_MODEL] }
    val desiredCapabilityFlow: Flow<String?> = ds.data.map { it[KEY_CAPABILITY] }
    val hasCompletedOnboardingFlow: Flow<Boolean> = ds.data.map { it[KEY_ONBOARDED] == "true" }

    suspend fun activeProviderId(): String? = activeProviderIdFlow.first()
    suspend fun activeModelId(): String? = activeModelIdFlow.first()
    suspend fun desiredCapability(): AgentCapability =
        desiredCapabilityFlow.first()?.let { runCatching { AgentCapability.valueOf(it) }.getOrNull() }
            ?: AgentCapability.MINIMAL

    suspend fun setActiveProvider(providerId: String, modelId: String) {
        ds.edit { prefs ->
            prefs[KEY_PROVIDER] = providerId
            prefs[KEY_MODEL] = modelId
        }
    }

    suspend fun setDesiredCapability(capability: AgentCapability) {
        ds.edit { it[KEY_CAPABILITY] = capability.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        ds.edit { it[KEY_ONBOARDED] = if (completed) "true" else "false" }
    }

    // ---- Secrets (EncryptedSharedPreferences) ----

    fun activeApiKey(providerId: String): String? = securePrefs.getString(apiKeyPrefKey(providerId), null)

    fun setApiKey(providerId: String, apiKey: String) {
        securePrefs.edit().putString(apiKeyPrefKey(providerId), apiKey).apply()
    }

    fun clearApiKey(providerId: String) {
        securePrefs.edit().remove(apiKeyPrefKey(providerId)).apply()
    }

    private fun apiKeyPrefKey(providerId: String? = null): String =
        if (providerId == null) KEY_API_KEY_PREFIX
        else "$KEY_API_KEY_PREFIX:$providerId"

    private companion object {
        const val DATASTORE_NAME = "aion_settings"
        const val SECURE_PREFS_NAME = "aion_secure_prefs"

        val KEY_PROVIDER: Preferences.Key<String> = stringPreferencesKey("active_provider")
        val KEY_MODEL: Preferences.Key<String> = stringPreferencesKey("active_model")
        val KEY_CAPABILITY: Preferences.Key<String> = stringPreferencesKey("capability")
        val KEY_ONBOARDED: Preferences.Key<String> = stringPreferencesKey("onboarded")
        val KEY_API_KEY_PREFIX = "api_key"
    }
}

private val Context.aionDataStore by preferencesDataStore(name = "aion_settings")
