package com.aion.agent.ui.settings

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_provider),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Provider chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.providers.forEach { provider ->
                    val selected = state.activeProviderId == provider.id
                    AssistChip(
                        onClick = { viewModel.onProviderSelected(provider.id) },
                        label = { Text(provider.displayName) },
                        leadingIcon = {
                            Text(if (selected) "✓" else "·")
                        },
                    )
                    Text(
                        text = provider.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Model picker
            state.activeProvider?.let { provider ->
                Text(
                    text = stringResource(R.string.settings_model),
                    style = MaterialTheme.typography.titleMedium,
                )
                ModelDropdown(
                    models = provider.availableModels.map { it.id to it.displayName },
                    selected = state.activeModelId,
                    onSelected = viewModel::onModelSelected,
                )
            }

            // API key
            Text(
                text = stringResource(R.string.settings_api_key),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = viewModel::onApiKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(if (state.hasApiKey) "•••••••• (set)" else "Paste your API key")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = viewModel::onSaveApiKey,
                    enabled = state.apiKeyInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = viewModel::onClearApiKey,
                    enabled = state.hasApiKey,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear")
                }
            }

            if (state.saveMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.saveMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Build: ${BuildConfig.VERSION_NAME} · debug",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<Pair<String, String>>,
    selected: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = models.firstOrNull { it.first == selected }?.second
        ?: models.firstOrNull()?.second
        ?: "—"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
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
