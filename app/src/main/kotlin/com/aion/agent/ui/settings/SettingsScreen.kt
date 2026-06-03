package com.aion.agent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aion.agent.BuildConfig
import com.aion.agent.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToBattery: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Provider Selection ----
            Text(
                text = stringResource(R.string.settings_provider),
                style = MaterialTheme.typography.titleMedium,
            )

            // Provider chips with visual state
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.providers.forEach { provider ->
                    val selected = state.activeProviderId == provider.id
                    val keyExists = state.hasApiKey && selected
                    AssistChip(
                        onClick = { viewModel.onProviderSelected(provider.id) },
                        label = { Text(provider.displayName) },
                        leadingIcon = {
                            if (keyExists) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Key saved",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else if (selected) {
                                Icon(
                                    Icons.Filled.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else null
                        },
                    )
                    if (selected) {
                        Text(
                            text = provider.notes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // ---- Model Selection ----
            state.activeProvider?.let { provider ->
                Text(
                    text = stringResource(R.string.settings_model),
                    style = MaterialTheme.typography.titleMedium,
                )

                if (state.isTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Fetching available models…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                ModelDropdown(
                    models = state.availableModels.map { it.id to it.displayName },
                    selected = state.activeModelId,
                    onSelected = viewModel::onModelSelected,
                    enabled = state.availableModels.isNotEmpty(),
                )

                if (state.availableModels.isEmpty() && !state.isTesting) {
                    Text(
                        text = if (state.hasApiKey) "No models. Set a key first to fetch the list."
                        else "Select a provider and save an API key to fetch models.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- API Key ----
            Text(
                text = stringResource(R.string.settings_api_key),
                style = MaterialTheme.typography.titleMedium,
            )

            ApiKeyField(
                value = state.apiKeyInput,
                hasKey = state.hasApiKey,
                isSaving = state.isSaving,
                onValueChange = viewModel::onApiKeyChanged,
                onSave = viewModel::onSaveApiKey,
                onClear = viewModel::onClearApiKey,
            )

            // Save message
            if (state.saveMessage != null) {
                Text(
                    text = state.saveMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // ---- Test / Fetch result ----
            state.testResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (result) {
                            is TestResult.Success -> MaterialTheme.colorScheme.primaryContainer
                            is TestResult.Error -> MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (result) {
                                is TestResult.Success -> Icons.Filled.CheckCircle
                                is TestResult.Error -> Icons.Filled.Error
                            },
                            contentDescription = null,
                            tint = when (result) {
                                is TestResult.Success -> MaterialTheme.colorScheme.primary
                                is TestResult.Error -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Battery Dashboard link ----
            Text(
                text = "System",
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBattery() },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.BatteryFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Battery & Performance",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Battery level, CPU time, model status, sleep mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Notification History link ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToNotifications() },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Notification History",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Recent notifications captured by AION",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Privacy Dashboard link ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPrivacy() },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Privacy Dashboard",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Permissions, data storage, wipe",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME} · debug build",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ApiKeyField(
    value: String,
    hasKey: Boolean,
    isSaving: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    var showMasked by remember { mutableStateOf(true) }

    when {
        // When a key is ALREADY saved, show it masked and read-only with Clear only
        hasKey && value.isEmpty() -> {
            OutlinedTextField(
                value = "••••••••••••••••",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                trailingIcon = {
                    IconButton(onClick = { showMasked = !showMasked }) {
                        Icon(
                            if (showMasked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle key visibility",
                        )
                    }
                },
                enabled = !isSaving,
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Key saved",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onClear,
                    enabled = !isSaving,
                ) {
                    Text("Clear Key")
                }
            }
        }

        // No key saved — show editable field with Save
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste your API key") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isSaving,
                singleLine = true,
                supportingText = if (isSaving) {{ Text("Testing API key…") }} else null,
            )
            if (isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving and testing…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onSave,
                    enabled = value.isNotBlank() && !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & Test")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<Pair<String, String>>,
    selected: String?,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (models.isEmpty()) "No models" else {
        models.firstOrNull { it.first == selected }?.second
            ?: selected?.let { "$it (custom)" }
            ?: "Select a model"
    }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled && models.isNotEmpty(),
        onExpandedChange = { if (enabled && models.isNotEmpty()) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled && models.isNotEmpty(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { (id, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
