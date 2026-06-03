package com.aion.agent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aion.agent.R
import com.aion.agent.core.AgentCapability

/**
 * Three-step onboarding: Welcome → Privacy → Capability → Model setup.
 *
 * Phase 1 ships this as a single screen with three cards. The full funnel
 * described in AION_PLAN §8 is built out in Phase 6 polish.
 */
@Composable
fun OnboardingScreen(
    onComplete: (AgentCapability, OnboardingModelChoice) -> Unit,
) {
    var capability by remember { mutableStateOf(AgentCapability.MINIMAL) }
    var modelChoice by remember { mutableStateOf(OnboardingModelChoice.CLOUD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Phase 1 build — chat, cloud LLM, SMS. Set up your provider in the next step.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_capability_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        CapabilityCard(
            tier = AgentCapability.FULL,
            title = stringResource(R.string.onboarding_capability_full),
            desc = stringResource(R.string.onboarding_capability_full_desc),
            selected = capability == AgentCapability.FULL,
            onSelected = { capability = AgentCapability.FULL },
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            tier = AgentCapability.PARTIAL,
            title = stringResource(R.string.onboarding_capability_partial),
            desc = stringResource(R.string.onboarding_capability_partial_desc),
            selected = capability == AgentCapability.PARTIAL,
            onSelected = { capability = AgentCapability.PARTIAL },
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            tier = AgentCapability.MINIMAL,
            title = stringResource(R.string.onboarding_capability_minimal),
            desc = stringResource(R.string.onboarding_capability_minimal_desc),
            selected = capability == AgentCapability.MINIMAL,
            onSelected = { capability = AgentCapability.MINIMAL },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_model_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        ModelChoiceCard(
            choice = OnboardingModelChoice.CLOUD,
            title = stringResource(R.string.onboarding_model_cloud),
            desc = stringResource(R.string.onboarding_model_cloud_desc),
            selected = modelChoice == OnboardingModelChoice.CLOUD,
            onSelected = { modelChoice = OnboardingModelChoice.CLOUD },
        )
        Spacer(Modifier.height(8.dp))
        ModelChoiceCard(
            choice = OnboardingModelChoice.TRY,
            title = stringResource(R.string.onboarding_model_try),
            desc = stringResource(R.string.onboarding_model_try_desc),
            selected = modelChoice == OnboardingModelChoice.TRY,
            onSelected = { modelChoice = OnboardingModelChoice.TRY },
        )
        Spacer(Modifier.height(8.dp))
        ModelChoiceCard(
            choice = OnboardingModelChoice.LOCAL,
            title = stringResource(R.string.onboarding_model_local),
            desc = stringResource(R.string.onboarding_model_local_desc),
            selected = modelChoice == OnboardingModelChoice.LOCAL,
            onSelected = { modelChoice = OnboardingModelChoice.LOCAL },
            enabled = false,
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { onComplete(capability, modelChoice) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_capability_cta))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CapabilityCard(
    tier: AgentCapability,
    title: String,
    desc: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelected,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelected)
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tier.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelChoiceCard(
    choice: OnboardingModelChoice,
    title: String,
    desc: String,
    selected: Boolean,
    onSelected: () -> Unit,
    enabled: Boolean = true,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (enabled) onSelected() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelected, enabled = enabled)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

enum class OnboardingModelChoice { CLOUD, TRY, LOCAL }
