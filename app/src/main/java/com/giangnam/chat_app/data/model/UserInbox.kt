package com.giangnam.chat_app.data.model

import com.google.firebase.database.PropertyName

data class UserInbox(
    var roomId: String = "",
    
    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    
    @get:PropertyName("isMuted")
    @set:PropertyName("isMuted")
    var isMuted: Boolean = false,
    
    @get:PropertyName("isHidden")
    @set:PropertyName("isHidden")
    var isHidden: Boolean = false,
    
    var unreadCount: Int = 0,
    var lastReadMessageId: String = "",
    var lastReadAt: Long = 0L,
    var lastActivityAt: Long = 0L,
    var clearedAt: Long = 0L,
    var mutedUntil: Long = 0L
)