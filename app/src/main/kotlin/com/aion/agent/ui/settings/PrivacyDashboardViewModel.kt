package com.aion.agent.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.data.SettingsRepository
import com.aion.agent.memory.MemoryRepository
import com.aion.agent.system.CapabilityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Privacy Dashboard.
 */
data class PrivacyState(
    val smsPermission: Boolean = false,
    val notificationListener: Boolean = false,
    val accessibilityService: Boolean = false,
    val memoryCount: Int = 0,
    val cloudProviderUsed: Boolean = false,
    val providerName: String? = null,
    val exporting: Boolean = false,
    val wiping: Boolean = false,
    val statusMessage: String? = null,
)

/**
 * Privacy Dashboard — shows what data has been accessed, when, and how often.
 * Provides export and wipe functionality.
 *
 * Per AION_GUIDELINES §11, this is a Phase 3 requirement, not a Phase 6 polish item.
 */
@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityManager: CapabilityManager,
    private val memoryRepository: MemoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyState())
    val state: StateFlow<PrivacyState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            capabilityManager.refresh()
            val capability = capabilityManager.capability.value
            _state.update {
                it.copy(
                    smsPermission = android.Manifest.permission.SEND_SMS.let { perm ->
                        context.checkCallingOrSelfPermission(perm) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    },
                    notificationListener = capabilityManager.hasNotificationListenerAccess(),
                    accessibilityService = capability.name == "FULL",
                    memoryCount = memoryRepository.count(),
                    cloudProviderUsed = settings.activeProviderId() != null,
                    providerName = settings.activeProviderId(),
                )
            }
        }
    }

    fun wipeAllData() {
        viewModelScope.launch {
            _state.update { it.copy(wiping = true, statusMessage = null) }
            try {
                memoryRepository.wipeAll()
                _state.update { it.copy(
                    wiping = false,
                    statusMessage = "All local data wiped successfully.",
                    memoryCount = 0,
                )}
            } catch (t: Throwable) {
                _state.update { it.copy(
                    wiping = false,
                    statusMessage = "Error wiping data: ${t.message}",
                )}
            }
        }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}
