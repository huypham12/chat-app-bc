package com.giangnam.chat_app.data.model

data class RoomStatus(
    var typing: Map<String, Boolean> = emptyMap(), // uid -> boolean
    var presence: Map<String, UserPresence> = emptyMap() // uid -> UserPresence
)

data class UserPresence(
    var state: String = "offline", // online | offline
    var lastSeen: Long = 0L
)