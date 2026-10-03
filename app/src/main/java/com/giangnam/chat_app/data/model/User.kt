package com.giangnam.chat_app.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class User(
    var uid: String = "",
    
    @get:PropertyName("userName")
    @set:PropertyName("userName")
    var username: String = "", // Unified field
    
    var displayName: String = "",
    var bio: String = "Hey there! I am using Chat App.",
    
    var avatarUrl: String? = "",
    var coverUrl: String? = "",
    var phoneNumber: String = "",
    var email: String? = "",
    var gender: String? = "",
    var dateOfBirth: String? = "",
    var locationInfo: String? = "",
    var createdAt: Long = 0L,
    var lastSeen: Long = 0L,
    var isOnline: Boolean = false,
    var voiceprint: String = "",
    var isVoiceAuthEnabled: Boolean = true
)