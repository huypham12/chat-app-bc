package com.giangnam.chat_app.ui.discover

import android.location.Location
import androidx.lifecycle.ViewModel
import com.giangnam.chat_app.data.model.NearbyUserDTO
import com.giangnam.chat_app.data.repository.NearbyRepository
import com.giangnam.chat_app.utils.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiscoverViewModel : ViewModel() {
    private val nearbyRepository = NearbyRepository.getInstance()
    private val currentUserId = Utils.getUserId()

    private val _nearbyUsers = MutableStateFlow<List<NearbyUserDTO>>(emptyList())
    val nearbyUsers: StateFlow<List<NearbyUserDTO>> = _nearbyUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun startObservingNearbyUsers(currentLocation: Location) {
        _isLoading.value = true
        nearbyRepository.startObservingNearbyUsers(currentUserId, currentLocation.latitude, currentLocation.longitude) { users ->
            _nearbyUsers.value = users
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        nearbyRepository.stopObservingNearbyUsers()
    }
}
