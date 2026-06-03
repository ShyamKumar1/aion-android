package com.aion.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.data.SettingsRepository
import com.aion.agent.llm.ModelManager
import com.aion.agent.system.BatteryMonitor
import com.aion.agent.system.SleepController
import com.aion.agent.system.SleepState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Battery Dashboard screen.
 */
data class BatteryDashboardState(
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val cpuTimeMs: Long = 0,
    val modelLoaded: Boolean = false,
    val modelName: String? = null,
    val sleepState: SleepState = SleepState.Active,
    val sleepTimeoutMinutes: Int = 5,
)

/**
 * ViewModel for the Battery & Performance dashboard.
 * Collects from [BatteryMonitor], [SleepController], and [ModelManager].
 */
@HiltViewModel
class BatteryDashboardViewModel @Inject constructor(
    private val batteryMonitor: BatteryMonitor,
    private val sleepController: SleepController,
    private val settings: SettingsRepository,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BatteryDashboardState())
    val state: StateFlow<BatteryDashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    /** Refresh all stats from the system. */
    fun refresh() {
        viewModelScope.launch {
            batteryMonitor.refresh()
            val cpuTimeMs = settings.getCpuTimeMs()
            _state.update {
                it.copy(
                    batteryLevel = batteryMonitor.batteryLevel.toInt(),
                    isCharging = batteryMonitor.isCharging,
                    cpuTimeMs = cpuTimeMs,
                    modelLoaded = modelManager.isReady,
                    modelName = modelManager.loadedModelName,
                    sleepState = sleepController.sleepState.value,
                    sleepTimeoutMinutes = settings.getSleepTimeoutMinutes(),
                )
            }
        }
    }

    /** Update the sleep timeout duration. */
    fun setSleepTimeout(minutes: Int) {
        viewModelScope.launch {
            sleepController.setTimeoutMinutes(minutes)
            _state.update { it.copy(sleepTimeoutMinutes = minutes) }
        }
    }

    /** Reset battery usage statistics. */
    fun resetStats() {
        viewModelScope.launch {
            batteryMonitor.resetStats()
            refresh()
        }
    }
}
