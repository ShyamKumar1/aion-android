package com.aion.agent.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.memory.db.LogEntry
import com.aion.agent.util.LogLevel
import com.aion.agent.util.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogViewerUiState(
    val logs: List<LogEntry> = emptyList(),
    val logCount: Int = 0,
    val isLoading: Boolean = true,
    val filterLevels: List<String> = LogLevel.ALL,
    val filterCategory: String = "",
    val filterTag: String = "",
    val searchQuery: String = "",
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val expandedLogId: Long? = null,
    val showClearConfirmation: Boolean = false,
    val statusMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepo: LogRepository,
) : ViewModel() {

    private val _filterLevels = MutableStateFlow(LogLevel.ALL)
    private val _filterCategory = MutableStateFlow("")
    private val _filterTag = MutableStateFlow("")
    private val _searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Reactive query: rebuild whenever any filter changes
            combine(_filterLevels, _filterCategory, _filterTag, _searchQuery) { levels, cat, tag, search ->
                FilterParams(levels, cat, tag, search)
            }.flatMapLatest { params ->
                logRepo.query(
                    levels = params.levels,
                    categories = if (params.category.isEmpty()) emptyList() else listOf(params.category),
                    tag = params.tag,
                    search = params.search,
                )
            }.collect { logs ->
                _uiState.update {
                    it.copy(logs = logs, isLoading = false, logCount = logs.size)
                }
            }
        }

        // Load filter options
        viewModelScope.launch {
            val categories = logRepo.distinctCategories()
            val tags = logRepo.distinctTags()
            _uiState.update { it.copy(availableCategories = categories, availableTags = tags) }
        }
    }

    fun setLevelFilter(levels: List<String>) {
        _filterLevels.value = levels
        _uiState.update { it.copy(filterLevels = levels) }
    }

    fun setCategoryFilter(category: String) {
        _filterCategory.value = category
        _uiState.update { it.copy(filterCategory = category) }
    }

    fun setTagFilter(tag: String) {
        _filterTag.value = tag
        _uiState.update { it.copy(filterTag = tag) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleExpanded(logId: Long) {
        _uiState.update {
            it.copy(expandedLogId = if (it.expandedLogId == logId) null else logId)
        }
    }

    fun showClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }

    fun dismissClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    fun clearAllLogs() {
        logRepo.clearAll()
        _uiState.update {
            it.copy(
                showClearConfirmation = false,
                statusMessage = "All logs cleared.",
            )
        }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    /** Build an exportable plain-text representation of visible logs. */
    fun buildExportText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== AION Log Export ===")
        sb.appendLine("Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        sb.appendLine("Entries: ${_uiState.value.logs.size}")
        sb.appendLine("Filters: levels=${_uiState.value.filterLevels}, category=${_uiState.value.filterCategory}, tag=${_uiState.value.filterTag}")
        sb.appendLine()
        for (entry in _uiState.value.logs) {
            val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(entry.timestamp))
            sb.appendLine("[$ts] [${entry.level}] [${entry.category}] [${entry.tag}] ${entry.message}")
            if (!entry.details.isNullOrBlank()) {
                sb.appendLine("  └─ ${entry.details}")
            }
        }
        return sb.toString()
    }

    private data class FilterParams(
        val levels: List<String>,
        val category: String,
        val tag: String,
        val search: String,
    )
}
