package com.giangnam.chat_app.data.model

data class UserLocation(
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var geohash: String = "",
    var isEnabled: Boolean = true,
    var updatedAt: Long = 0L,
    var expiresAt: Long = 0L
)