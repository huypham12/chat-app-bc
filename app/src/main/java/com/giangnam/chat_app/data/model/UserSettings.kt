package com.giangnam.chat_app.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserSettings(
    var nearbyEnabled: Boolean = false,
    var locationSharingEnabled: Boolean = false,
    var showOnlineStatus: Boolean = true,
    var showLastSeen: Boolean = true,
    var allowMessagesFromNonFriends: Boolean = true,
    var notificationEnabled: Boolean = true
)