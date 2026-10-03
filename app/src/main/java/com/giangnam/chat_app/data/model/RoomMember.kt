package com.giangnam.chat_app.data.model

data class RoomMember(
    var uid: String = "",
    var role: String = "member", // admin | member
    var joinedAt: Long = 0L
)