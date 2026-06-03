package com.aion.agent.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

    // ---- Battery & sleep preferences ----

    suspend fun setModelLoadBatteryLevel(level: Int) {
        ds.edit { it[KEY_MODEL_LOAD_BATTERY] = level }
    }

    suspend fun addCpuTime(millis: Long) {
        ds.edit { prefs ->
            val current = prefs[KEY_CPU_TIME_MS] ?: 0L
            prefs[KEY_CPU_TIME_MS] = current + millis
        }
    }

    suspend fun getCpuTimeMs(): Long = ds.data.first()[KEY_CPU_TIME_MS] ?: 0L

    suspend fun resetBatteryStats() {
        ds.edit { it.clear() }
    }

    suspend fun setSleepTimeoutMinutes(minutes: Int) {
        ds.edit { it[KEY_SLEEP_TIMEOUT] = minutes }
    }

    suspend fun getSleepTimeoutMinutes(): Int =
        ds.data.first()[KEY_SLEEP_TIMEOUT] ?: 5

    suspend fun setLastLoadedModelPath(path: String) {
        ds.edit { it[KEY_LAST_MODEL_PATH] = path }
    }

    suspend fun getLastLoadedModelPath(): String? =
        ds.data.first()[KEY_LAST_MODEL_PATH]

    suspend fun setRoutePreference(preference: String) {
        ds.edit { it[KEY_ROUTE_PREFERENCE] = preference }
    }

    suspend fun getRoutePreference(): String? =
        ds.data.first()[KEY_ROUTE_PREFERENCE]

    private companion object {
        const val DATASTORE_NAME = "aion_settings"
        const val SECURE_PREFS_NAME = "aion_secure_prefs"

        val KEY_PROVIDER: Preferences.Key<String> = stringPreferencesKey("active_provider")
        val KEY_MODEL: Preferences.Key<String> = stringPreferencesKey("active_model")
        val KEY_CAPABILITY: Preferences.Key<String> = stringPreferencesKey("capability")
        val KEY_ONBOARDED: Preferences.Key<String> = stringPreferencesKey("onboarded")
        val KEY_API_KEY_PREFIX = "api_key"

        // Battery & sleep
        val KEY_MODEL_LOAD_BATTERY = intPreferencesKey("model_load_battery")
        val KEY_CPU_TIME_MS = longPreferencesKey("cpu_time_ms")
        val KEY_MODEL_LOADED_TIME_MS = longPreferencesKey("model_loaded_time_ms")
        val KEY_SLEEP_TIMEOUT = intPreferencesKey("sleep_timeout_minutes")
        val KEY_LAST_MODEL_PATH = stringPreferencesKey("last_model_path")
        val KEY_ROUTE_PREFERENCE = stringPreferencesKey("route_preference")
    }
}

private val Context.aionDataStore by preferencesDataStore(name = "aion_settings")
