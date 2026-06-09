package com.aion.agent.ui.logs

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aion.agent.memory.db.LogEntry
import com.aion.agent.util.LogLevel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Viewer") },
                actions = {
                    IconButton(onClick = { exportLogs(context, viewModel) }) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = "Export logs",
                        )
                    }
                    IconButton(onClick = { viewModel.showClearConfirmation() }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Clear logs",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Filters ───────────────────────────────────────────────
            FilterBar(
                filterLevels = state.filterLevels,
                filterCategory = state.filterCategory,
                filterTag = state.filterTag,
                searchQuery = state.searchQuery,
                availableCategories = state.availableCategories,
                availableTags = state.availableTags,
                logCount = state.logCount,
                onLevelChange = viewModel::setLevelFilter,
                onCategoryChange = viewModel::setCategoryFilter,
                onTagChange = viewModel::setTagFilter,
                onSearchChange = viewModel::setSearchQuery,
            )

            // ── Content ───────────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.logs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No logs match the current filters.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Try adjusting filters or clearing them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.logs, key = { it.id }) { entry ->
                            LogEntryCard(
                                entry = entry,
                                isExpanded = state.expandedLogId == entry.id,
                                onToggle = { viewModel.toggleExpanded(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Clear confirmation dialog ────────────────────────────────────
    if (state.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearConfirmation,
            title = { Text("Clear all logs?") },
            text = { Text("All ${state.logCount} log entries will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::clearAllLogs) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearConfirmation) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun FilterBar(
    filterLevels: List<String>,
    filterCategory: String,
    filterTag: String,
    searchQuery: String,
    availableCategories: List<String>,
    availableTags: List<String>,
    logCount: Int,
    onLevelChange: (List<String>) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTagChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Level chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LevelChip("All", LogLevel.ALL, filterLevels) { onLevelChange(LogLevel.ALL) }
            LevelChip("WARN+", LogLevel.WARN_PLUS, filterLevels) { onLevelChange(LogLevel.WARN_PLUS) }
            LevelChip("ERROR+", LogLevel.ERROR_PLUS, filterLevels) { onLevelChange(LogLevel.ERROR_PLUS) }
        }

        Spacer(Modifier.height(6.dp))

        // Category + Tag dropdowns and count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Category filter
            Box {
                AssistChip(
                    onClick = { showCategoryMenu = true },
                    label = { Text(if (filterCategory.isEmpty()) "All categories" else filterCategory) },
                    leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All categories") },
                        onClick = { onCategoryChange(""); showCategoryMenu = false },
                    )
                    availableCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = { onCategoryChange(cat); showCategoryMenu = false },
                        )
                    }
                }
            }

            // Tag filter
            Box {
                AssistChip(
                    onClick = { showTagMenu = true },
                    label = { Text(if (filterTag.isEmpty()) "All tags" else filterTag) },
                )
                DropdownMenu(
                    expanded = showTagMenu,
                    onDismissRequest = { showTagMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All tags") },
                        onClick = { onTagChange(""); showTagMenu = false },
                    )
                    availableTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag) },
                            onClick = { onTagChange(tag); showTagMenu = false },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "$logCount entries",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search logs…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
        )
    }
}

@Composable
private fun LevelChip(label: String, levels: List<String>, current: List<String>, onClick: () -> Unit) {
    val selected = current == levels
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
private fun LogEntryCard(
    entry: LogEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> Color(0xFFFF9800)
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.FATAL -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date(entry.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL) 0.15f else 0.5f,
            ),
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Level badge
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(levelColor),
                )
                Spacer(Modifier.width(8.dp))

                // Level text
                Text(
                    text = entry.level,
                    color = levelColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(44.dp),
                )

                // Timestamp
                Text(
                    text = ts,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(62.dp),
                )

                // Category badge
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(52.dp),
                )

                // Tag
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // Expand icon
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle details",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Expanded details
            if (isExpanded && !entry.details.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.08f))
                        .padding(8.dp),
                ) {
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun exportLogs(context: Context, viewModel: LogViewerViewModel) {
    val text = viewModel.buildExportText()
    val file = File(context.cacheDir, "aion_logs_${System.currentTimeMillis()}.txt")
    file.writeText(text)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export AION Logs"))
}
