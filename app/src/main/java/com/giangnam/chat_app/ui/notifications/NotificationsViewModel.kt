package com.giangnam.chat_app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giangnam.chat_app.data.model.Notification
import com.giangnam.chat_app.data.repository.NotificationRepository
import com.giangnam.chat_app.utils.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel : ViewModel() {

    private val notificationRepository = NotificationRepository()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        fetchNotifications()
    }

    private fun fetchNotifications() {
        val userId = Utils.getUserId()
        if (userId.isEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            notificationRepository.observeNotifications(userId).collect { list ->
                _notifications.value = list
                _isLoading.value = false
            }
        }
    }

    fun markAllAsRead() {
        val userId = Utils.getUserId()
        if (userId.isEmpty()) return
        
        viewModelScope.launch {
            notificationRepository.markAllAsRead(userId)
        }
    }

    fun markAsRead(notificationId: String) {
        val userId = Utils.getUserId()
        if (userId.isEmpty()) return

        viewModelScope.launch {
            notificationRepository.markAsRead(userId, notificationId)
        }
    }
}
