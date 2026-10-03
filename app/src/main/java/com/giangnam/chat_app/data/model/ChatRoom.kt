package com.giangnam.chat_app.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ChatRoom(
    var roomId: String = "",
    var type: String = "private", // private | group
    var userIds: ArrayList<String> = ArrayList(),
    var name: String? = "",
    var avatarUrl: String? = "",
    var createdBy: String = "",
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var lastMessage: LastMessage? = null
)

data class LastMessage(
    var messageId: String = "",
    var senderId: String = "",
    var type: String = "text",
    var preview: String = "",
    var timestamp: Long = 0L,
    var isRead: Boolean = false
)