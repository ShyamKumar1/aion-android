package com.aion.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.data.ProviderRepository
import com.aion.agent.llm.providers.LlmProviderRegistry
import com.aion.agent.llm.providers.ProviderConfig
import com.aion.agent.llm.providers.ProviderModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State + actions for the Settings screen. Lets the user:
 *  - Pick a provider (OpenRouter / Opencode Go / NVIDIA NIM)
 *  - Pick a model within that provider
 *  - Enter / clear the API key (stored in EncryptedSharedPreferences)
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val active = providerRepository.activeProvider()
            val model = providerRepository.activeModelId()
            _state.update {
                it.copy(
                    providers = LlmProviderRegistry.All,
                    activeProviderId = active?.id,
                    activeModelId = model,
                    hasApiKey = providerRepository.activeApiKey() != null,
                )
            }
        }
    }

    fun onProviderSelected(providerId: String) {
        val provider = LlmProviderRegistry.byId(providerId) ?: return
        // Default to first model when provider changes
        val defaultModel = provider.availableModels.firstOrNull()?.id
        viewModelScope.launch {
            providerRepository.setActiveProvider(providerId, defaultModel ?: "")
            _state.update {
                it.copy(
                    activeProviderId = providerId,
                    activeModelId = defaultModel,
                )
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
        _state.update { it.copy(apiKeyInput = value) }
    }

    fun onSaveApiKey() {
        val providerId = _state.value.activeProviderId ?: return
        val key = _state.value.apiKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            providerRepository.setApiKey(providerId, key)
            _state.update {
                it.copy(
                    apiKeyInput = "",
                    hasApiKey = true,
                    saveMessage = "API key saved.",
                )
            }
        }
    }

    fun onClearApiKey() {
        val providerId = _state.value.activeProviderId ?: return
        viewModelScope.launch {
            providerRepository.clearApiKey(providerId)
            _state.update {
                it.copy(
                    hasApiKey = false,
                    saveMessage = "API key cleared.",
                )
            }
        }
    }

    fun onSaveMessageDismissed() {
        _state.update { it.copy(saveMessage = null) }
    }
}

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val activeModelId: String? = null,
    val apiKeyInput: String = "",
    val hasApiKey: Boolean = false,
    val saveMessage: String? = null,
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    val activeModel: ProviderModel?
        get() = activeProvider?.availableModels?.firstOrNull { it.id == activeModelId }
}
