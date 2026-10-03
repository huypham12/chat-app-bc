package com.giangnam.chat_app.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserBlock(
    var blockedUsers: Map<String, Long> = emptyMap() // uid -> timestamp
)