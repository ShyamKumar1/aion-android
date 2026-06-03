package com.aion.agent.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aion.agent.core.AgentCapability

/**
 * 6-screen onboarding funnel per AION_PLAN §8.
 *
 * 1. Welcome
 * 2. Privacy Promise
 * 3. Capability Choice (FULL / PARTIAL / MINIMAL)
 * 4. Permission Grant (3-step guided flow)
 * 5. Model Setup (cloud / try / local)
 * 6. First Question (transition to main app)
 */
@Composable
fun OnboardingScreen(
    onComplete: (AgentCapability, OnboardingModelChoice) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 6

    var capability by rememberSaveable { mutableStateOf(AgentCapability.PARTIAL) }
    var modelChoice by rememberSaveable { mutableStateOf(OnboardingModelChoice.CLOUD) }
    var permissionStep by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )

        AnimatedContent(targetState = step, label = "onboarding") { currentStep ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (currentStep) {
                    0 -> WelcomeStep()
                    1 -> PrivacyStep()
                    2 -> CapabilityChoiceStep(
                        selected = capability,
                        onSelected = { capability = it },
                    )
                    3 -> PermissionStep(
                        permissionIndex = permissionStep,
                        onNext = { permissionStep++ },
                    )
                    4 -> ModelChoiceStep(
                        selected = modelChoice,
                        onSelected = { modelChoice = it },
                    )
                    5 -> ReadyStep(capability)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.size(1.dp))
            }

            val isLastStep = step == totalSteps - 1
            Button(
                onClick = {
                    if (isLastStep) {
                        onComplete(capability, modelChoice)
                    } else if (step == 3 && permissionStep < 2) {
                        // Permission step — advance internal step first
                        permissionStep++
                    } else {
                        step++
                    }
                },
            ) {
                Text(if (isLastStep) "Start Using AION" else "Continue")
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "AION",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your private AI phone agent",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "See your screen, read notifications, execute tasks autonomously — " +
                "all on your device, using any LLM you choose.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Open Source · AGPLv3 · Privacy-first",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PrivacyStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Everything stays on your phone.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        val points = listOf(
            "Your data never leaves this device unless you choose",
            "Open source — anyone can verify the code",
            "You control which permissions to grant",
            "No accounts. No tracking.",
        )
        points.forEach { point ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✅ ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You can use AION with ZERO cloud services.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CapabilityChoiceStep(
    selected: AgentCapability,
    onSelected: (AgentCapability) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Choose your level of access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        CapabilityCard(
            tier = AgentCapability.FULL, selected = selected == AgentCapability.FULL,
            title = "Full Access (Recommended)",
            desc = "AI sees screen, reads notifications, executes actions across apps. " +
                "Requires Accessibility permissions. Best for power users.",
            onSelected = { onSelected(AgentCapability.FULL) },
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            tier = AgentCapability.PARTIAL, selected = selected == AgentCapability.PARTIAL,
            title = "Notification Access",
            desc = "AI reads notifications, sends SMS, places calls. Cannot see screen. " +
                "Requires Notification permissions. Best for privacy-conscious users.",
            onSelected = { onSelected(AgentCapability.PARTIAL) },
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            tier = AgentCapability.MINIMAL, selected = selected == AgentCapability.MINIMAL,
            title = "Chat Only",
            desc = "AI answers questions via chat. No system access. No special permissions. " +
                "Best for trying it out.",
            onSelected = { onSelected(AgentCapability.MINIMAL) },
        )
    }
}

@Composable
private fun PermissionStep(permissionIndex: Int, onNext: () -> Unit) {
    val steps = listOf(
        "SMS & Calls" to "Used for: sending messages, placing calls",
        "Notifications" to "Used for: reading incoming messages, detecting events",
        "Screen Access" to "Used for: understanding what's on your screen",
    )
    val (title, desc) = steps[permissionIndex.coerceAtMost(2)]

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Grant Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Step ${permissionIndex + 1} of ${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You can change these permissions anytime in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelChoiceStep(
    selected: OnboardingModelChoice,
    onSelected: (OnboardingModelChoice) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "How should AION work?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Don't worry — you can change this later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ModelCard(
            title = "Cloud-first (Recommended)",
            desc = "Works immediately with your API key. Add a local model later.",
            selected = selected == OnboardingModelChoice.CLOUD,
            onClick = { onSelected(OnboardingModelChoice.CLOUD) },
        )
        Spacer(Modifier.height(8.dp))
        ModelCard(
            title = "Try it now (no setup)",
            desc = "Uses cloud demo endpoint with limited queries. Set a real key later.",
            selected = selected == OnboardingModelChoice.TRY,
            onClick = { onSelected(OnboardingModelChoice.TRY) },
        )
        Spacer(Modifier.height(8.dp))
        ModelCard(
            title = "Local model (private)",
            desc = "Download ~1.8GB model. Works fully offline. Recommended for 8GB+ devices.",
            selected = selected == OnboardingModelChoice.LOCAL,
            onClick = { onSelected(OnboardingModelChoice.LOCAL) },
        )
    }
}

@Composable
private fun ReadyStep(capability: AgentCapability) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "AION is ready in ${capability.label} mode.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try asking:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "What can I do with my phone?"
        ).forEach { prompt ->
            Text(
                text = "\"$prompt\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CapabilityCard(
    tier: AgentCapability,
    selected: Boolean,
    title: String,
    desc: String,
    onSelected: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelected,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelected)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
private fun ModelCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
