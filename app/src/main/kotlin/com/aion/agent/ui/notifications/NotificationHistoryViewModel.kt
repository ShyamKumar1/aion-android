package com.aion.agent.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.memory.db.NotificationDao
import com.aion.agent.memory.db.NotificationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val notifications: StateFlow<List<NotificationEntity>> =
        notificationDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
