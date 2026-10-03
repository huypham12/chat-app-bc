package com.giangnam.chat_app.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Relationship(
    var friends: Map<String, Long> = emptyMap(),               // uid -> timestamp
    var friendRequestsSent: Map<String, Long> = emptyMap(),    // uid -> timestamp
    var friendRequestsReceived: Map<String, Long> = emptyMap() // uid -> timestamp
)