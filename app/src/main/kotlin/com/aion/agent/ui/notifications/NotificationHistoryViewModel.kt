package com.aion.agent.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.memory.db.NotificationDao
import com.aion.agent.memory.db.NotificationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Notification History screen.
 * Observes all stored notifications from [NotificationDao].
 */
@HiltViewModel
class NotificationHistoryViewModel @Inject constructor(
    notificationDao: NotificationDao,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val notifications: StateFlow<List<NotificationEntity>> = try {
        notificationDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } catch (e: Exception) {
        _error.value = "Failed to load: ${e.message}"
        MutableStateFlow(emptyList())
    }
}
