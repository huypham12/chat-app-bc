package com.giangnam.chat_app.data.model

data class NearbyUserDTO(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val distance: Double, // in km
    val approxLat: Double,
    val approxLng: Double
)
