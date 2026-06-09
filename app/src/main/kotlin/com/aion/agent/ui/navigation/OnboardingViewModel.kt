package com.aion.agent.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.core.AgentCapability
import com.aion.agent.data.SettingsRepository
import com.aion.agent.ui.onboarding.OnboardingModelChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lightweight ViewModel that tracks whether onboarding has been completed.
 * Used by [AionNavHost] to decide whether to show the onboarding flow
 * or jump directly into the main app.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val hasCompleted: StateFlow<Boolean> = settings.hasCompletedOnboardingFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun completeOnboarding(capability: AgentCapability, modelChoice: OnboardingModelChoice) {
        viewModelScope.launch {
            settings.setDesiredCapability(capability)
            settings.setOnboardingCompleted(true)
            // In Phase 3+, the model choice would trigger model download or provider setup
        }
    }
}
