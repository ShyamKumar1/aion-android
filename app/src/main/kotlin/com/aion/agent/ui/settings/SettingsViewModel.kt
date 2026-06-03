package com.aion.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.data.ProviderRepository
import com.aion.agent.llm.providers.LlmProviderRegistry
import com.aion.agent.llm.providers.ProviderConfig
import com.aion.agent.llm.providers.ProviderModel
import com.aion.agent.util.AionJsonParser
import com.aion.agent.util.AionLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * State + actions for the Settings screen.
 *
 * Features:
 *  - Pick provider and model
 *  - Enter / clear / view-masked API key
 *  - Test API key and fetch live model list from provider's /v1/models
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val logger: AionLogger,
    private val jsonParser: AionJsonParser,
    private val httpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val active = providerRepository.activeProvider()
            val model = providerRepository.activeModelId()
            val key = providerRepository.activeApiKey()
            val hasKey = key != null
            _state.update {
                it.copy(
                    providers = LlmProviderRegistry.All,
                    activeProviderId = active?.id,
                    activeModelId = model,
                    hasApiKey = hasKey,
                    savedKey = key ?: "",
                )
            }
        }
    }

    fun onProviderSelected(providerId: String) {
        val provider = LlmProviderRegistry.byId(providerId) ?: return
        val defaultModel = provider.availableModels.firstOrNull()?.id
            ?: ""
        viewModelScope.launch {
            providerRepository.setActiveProvider(providerId, defaultModel)
            val key = providerRepository.activeApiKey()
            val hasKey = key != null
            _state.update {
                it.copy(
                    activeProviderId = providerId,
                    activeModelId = defaultModel,
                    hasApiKey = hasKey,
                    savedKey = key ?: "",
                    testResult = null,
                    fetchedModels = null,
                )
            }
            // If key exists for this provider, try to fetch models
            if (hasKey && provider.supportsModelList) {
                doFetchModels(provider)
            }
        }
    }

    fun onModelSelected(modelId: String) {
        val providerId = _state.value.activeProviderId ?: return
        viewModelScope.launch {
            providerRepository.setActiveProvider(providerId, modelId)
            _state.update { it.copy(activeModelId = modelId) }
        }
    }

    fun onApiKeyChanged(value: String) {
        _state.update { it.copy(apiKeyInput = value, saveMessage = null) }
    }

    /** Save the API key, test it, and fetch available models. */
    fun onSaveApiKey() {
        val providerId = _state.value.activeProviderId ?: return
        val key = _state.value.apiKeyInput.trim()
        if (key.isEmpty()) return

        _state.update { it.copy(isSaving = true, saveMessage = null, testResult = null) }

        viewModelScope.launch(Dispatchers.IO) {
            providerRepository.setApiKey(providerId, key)
            _state.update {
                it.copy(
                    apiKeyInput = "",
                    hasApiKey = true,
                    savedKey = key,
                    isSaving = false,
                    saveMessage = "Key saved.",
                )
            }

            // Test the key by fetching the model list
            val provider = LlmProviderRegistry.byId(providerId) ?: return@launch
            if (provider.supportsModelList) {
                doFetchModels(provider)
            } else {
                _state.update {
                    it.copy(testResult = TestResult.Success("Key saved. Static model list — use a model below."))
                }
            }
        }
    }

    fun onClearApiKey() {
        val providerId = _state.value.activeProviderId ?: return
        viewModelScope.launch {
            providerRepository.clearApiKey(providerId)
            _state.update {
                it.copy(
                    apiKeyInput = "",
                    hasApiKey = false,
                    savedKey = "",
                    saveMessage = null,
                    testResult = null,
                    fetchedModels = null,
                    activeModelId = null,
                )
            }
        }
    }

    fun onSaveMessageDismissed() {
        _state.update { it.copy(saveMessage = null) }
    }

    fun onTestResultDismissed() {
        _state.update { it.copy(testResult = null) }
    }

    private suspend fun doFetchModels(provider: ProviderConfig) {
        _state.update { it.copy(isTesting = true, testResult = null) }
        try {
            val models = withContext(Dispatchers.IO) {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(provider.baseUrl.trimEnd('/') + "/" + provider.modelListPath.trimStart('/'))
                        .header("Accept", "application/json")
                        .header(provider.apiKeyHeader, provider.apiKeyPrefix + (providerRepository.activeApiKey() ?: ""))
                        .build()
                )
                    .execute()
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${body.take(200)}")
                }
                jsonParser.parseModelList(body)
            }
            if (models.isNotEmpty()) {
                val currentModel = _state.value.activeModelId
                val firstModelId = models.first().id
                providerRepository.setActiveProvider(provider.id, currentModel ?: firstModelId)
                _state.update {
                    it.copy(
                        fetchedModels = models,
                        activeModelId = currentModel ?: firstModelId,
                        isTesting = false,
                        testResult = TestResult.Success("Connected. ${models.size} models available."),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isTesting = false,
                        testResult = TestResult.Success("Connected. Using default model list."),
                    )
                }
            }
        } catch (t: Throwable) {
            logger.e("SettingsVM", t) { "Model fetch failed for ${provider.id}" }
            // Fall back to static model list from provider config
            val staticModels = provider.availableModels
            if (staticModels.isNotEmpty()) {
                val currentModel = _state.value.activeModelId
                providerRepository.setActiveProvider(provider.id, currentModel ?: staticModels.first().id)
                _state.update {
                    it.copy(
                        fetchedModels = staticModels,
                        activeModelId = currentModel ?: staticModels.first().id,
                        isTesting = false,
                        testResult = TestResult.Success("Using default model list (fetch failed: ${t.message?.take(80)})"),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isTesting = false,
                        testResult = TestResult.Error("Couldn't fetch model list: ${t.message?.take(100)}"),
                    )
                }
            }
        }
    }
}

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val activeModelId: String? = null,
    val apiKeyInput: String = "",
    val hasApiKey: Boolean = false,
    val savedKey: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val saveMessage: String? = null,
    val testResult: TestResult? = null,
    val fetchedModels: List<ProviderModel>? = null,
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    val activeModel: ProviderModel?
        get() {
            val id = activeModelId ?: return null
            // Check fetched models first, then fall back to hardcoded
            return fetchedModels?.firstOrNull { it.id == id }
                ?: activeProvider?.availableModels?.firstOrNull { it.id == id }
        }

    val availableModels: List<ProviderModel>
        get() = fetchedModels ?: activeProvider?.availableModels ?: emptyList()
}

sealed class TestResult {
    abstract val message: String
    data class Success(override val message: String) : TestResult()
    data class Error(override val message: String) : TestResult()
}
