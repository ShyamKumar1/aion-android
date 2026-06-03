package com.aion.agent.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aion.agent.core.AgentCapability
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * 6-screen onboarding funnel per AION_PLAN §8 with actual permission requests.
 *
 * 1. Welcome — AION branding
 * 2. Privacy Promise — privacy guarantees
 * 3. Capability Choice — FULL / PARTIAL / MINIMAL
 * 4. Permission Grant — actual Android permission dialogs
 * 5. Model Setup — cloud / try / local
 * 6. First Question — transition to main app
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: (AgentCapability, OnboardingModelChoice) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 6

    var capability by rememberSaveable { mutableStateOf(AgentCapability.PARTIAL) }
    var modelChoice by rememberSaveable { mutableStateOf(OnboardingModelChoice.CLOUD) }

    // SMS permission state
    val smsPermissionState = rememberPermissionState(Manifest.permission.SEND_SMS)
    // Notification permission (Android 13+)
    val notifPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    val context = LocalContext.current

    // Launcher to open system notification listener settings
    val notifSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )

        AnimatedContent(
            targetState = step,
            label = "onboarding",
        ) { currentStep ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                when (currentStep) {
                    0 -> WelcomeStep()
                    1 -> PrivacyStep()
                    2 -> CapabilityChoiceStep(
                        selected = capability,
                        onSelected = { capability = it },
                    )
                    3 -> PermissionStep(
                        smsGranted = smsPermissionState.status.isGranted,
                        notifGranted = notifPermissionState?.status?.isGranted ?: true,
                        onRequestSms = { smsPermissionState.launchPermissionRequest() },
                        onRequestNotif = { notifPermissionState?.launchPermissionRequest() },
                        onOpenNotifSettings = {
                            notifSettingsLauncher.launch(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        },
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
            val isPermissionStep = step == 3

            Button(
                onClick = {
                    when {
                        isLastStep -> onComplete(capability, modelChoice)
                        isPermissionStep -> {
                            // Try to request permissions
                            if (!smsPermissionState.status.isGranted) {
                                smsPermissionState.launchPermissionRequest()
                            }
                            if (notifPermissionState != null && !notifPermissionState.status.isGranted) {
                                notifPermissionState.launchPermissionRequest()
                            }
                            step++
                        }
                        else -> step++
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (isLastStep) "Start Using AION" else "Continue")
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp),
    ) {
        // Logo area
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Your private AI phone agent",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "See your screen, read notifications, execute tasks autonomously — " +
                "all on your device, using any LLM you choose.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Open Source · AGPLv3 · Privacy-first",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrivacyStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Text(
            text = "Everything stays on your phone.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        val points = listOf(
            "Your data never leaves this device unless you choose",
            "Open source — anyone can verify the code",
            "You control which permissions to grant",
            "No accounts. No tracking.",
        )
        points.forEach { point ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "You can use AION with ZERO cloud services.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CapabilityChoiceStep(
    selected: AgentCapability,
    onSelected: (AgentCapability) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Choose your level of access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        CapabilityCard(
            tier = AgentCapability.FULL, selected = selected == AgentCapability.FULL,
            title = "Full Access",
            desc = "AI sees screen, reads notifications, executes actions across apps. " +
                "Requires Accessibility permissions. Best for power users.",
            badge = "Recommended",
            onSelected = { onSelected(AgentCapability.FULL) },
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            tier = AgentCapability.PARTIAL, selected = selected == AgentCapability.PARTIAL,
            title = "Notification Access",
            desc = "AI reads notifications, sends SMS, places calls. Cannot see screen. " +
                "Requires Notification permissions. Best for privacy-conscious users.",
            badge = null,
            onSelected = { onSelected(AgentCapability.PARTIAL) },
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            tier = AgentCapability.MINIMAL, selected = selected == AgentCapability.MINIMAL,
            title = "Chat Only",
            desc = "AI answers questions via chat. No system access. No special permissions. " +
                "Best for trying it out.",
            badge = null,
            onSelected = { onSelected(AgentCapability.MINIMAL) },
        )
    }
}

@Composable
private fun CapabilityCard(
    tier: AgentCapability,
    selected: Boolean,
    title: String,
    desc: String,
    badge: String?,
    onSelected: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelected,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = onSelected)
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tier.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PermissionStep(
    smsGranted: Boolean,
    notifGranted: Boolean,
    onRequestSms: () -> Unit,
    onRequestNotif: () -> Unit,
    onOpenNotifSettings: () -> Unit,
) {
    val steps = listOf(
        "SMS & Calls" to "Used for: sending messages, placing calls" to smsGranted,
        "Notifications" to "Used for: reading incoming messages, detecting events" to notifGranted,
    )

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Grant Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "AION needs a few permissions to work. Tap each to grant.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        steps.forEachIndexed { index, (pair, granted) ->
            val (title, desc) = pair
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surface,
                ),
                onClick = {
                    if (!granted) {
                        when (index) {
                            0 -> onRequestSms()
                            1 -> onRequestNotif()
                        }
                    }
                },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Status icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (granted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (granted) "✓" else "${index + 1}",
                            color = if (granted) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (granted) "Granted" else "Grant",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenNotifSettings() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚙",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Listener (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Tap to open system settings, then enable AION",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "You can change these permissions anytime in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelChoiceStep(
    selected: OnboardingModelChoice,
    onSelected: (OnboardingModelChoice) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
            title = "Cloud-first",
            desc = "Works immediately with your API key. Add a local model later.",
            selected = selected == OnboardingModelChoice.CLOUD,
            onClick = { onSelected(OnboardingModelChoice.CLOUD) },
        )
        Spacer(Modifier.height(8.dp))
        ModelCard(
            title = "Try it now",
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
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
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

@Composable
private fun ReadyStep(capability: AgentCapability) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "AION is ready in ${capability.label} mode.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Try asking:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "\"What can I do with my phone?\"",
            "\"Send a text to myself with a shopping list\"",
        ).forEach { prompt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

enum class OnboardingModelChoice { CLOUD, TRY, LOCAL }
