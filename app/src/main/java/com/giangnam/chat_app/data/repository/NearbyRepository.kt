package com.giangnam.chat_app.data.repository

import com.giangnam.chat_app.data.model.NearbyUserDTO
import com.giangnam.chat_app.data.model.UserLocation
import com.giangnam.chat_app.data.model.UserSettings
import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.math.*

class NearbyRepository private constructor() {

    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private val locationsRef = db.getReference(Constants.NODE_LOCATIONS)
    private val settingsRef = db.getReference(Constants.NODE_USER_SETTINGS)
    private val blocksRef = db.getReference(Constants.NODE_USER_BLOCKS)
    private val usersRef = db.getReference(Constants.NODE_USERS)

    companion object {
        @Volatile
        private var instance: NearbyRepository? = null

        fun getInstance(): NearbyRepository =
            instance ?: synchronized(this) {
                instance ?: NearbyRepository().also { instance = it }
            }
    }

    /**
     * Cập nhật vị trí của người dùng hiện tại lên Firebase.
     */
    fun updateLocation(userId: String, lat: Double, lng: Double, onComplete: (Boolean) -> Unit) {
        // Read settings first to ensure we don't violate privacy if they just disabled it
        settingsRef.child(userId).child("nearbyEnabled").get().addOnSuccessListener { snapshot ->
            val isEnabled = snapshot.getValue(Boolean::class.java) ?: false
            if (!isEnabled) {
                // Feature is disabled, do not upload location and stop
                onComplete(false)
                return@addOnSuccessListener
            }
            
            val currentTime = System.currentTimeMillis()
            val expiresAt = currentTime + Constants.LOCATION_EXPIRATION_MS
            
            val location = UserLocation(
                lat = lat,
                lng = lng,
                geohash = "", // Can add Geohash logic if needed
                isEnabled = true,
                updatedAt = currentTime,
                expiresAt = expiresAt
            )
            
            locationsRef.child(userId).setValue(location).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    /**
     * Tắt tính năng Nearby, vô hiệu hoá public location.
     */
    fun disableNearby(userId: String, onComplete: (Boolean) -> Unit) {
        // Update user settings
        settingsRef.child(userId).child("nearbyEnabled").setValue(false).addOnSuccessListener {
            // Invalidate location
            locationsRef.child(userId).child("isEnabled").setValue(false).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }
    
    /**
     * Bật tính năng Nearby.
     */
    fun enableNearby(userId: String, onComplete: (Boolean) -> Unit) {
        settingsRef.child(userId).child("nearbyEnabled").setValue(true).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    private var locationsListener: ValueEventListener? = null

    /**
     * Lấy và lắng nghe realtime danh sách những người xung quanh.
     */
    fun startObservingNearbyUsers(currentUserId: String, currentLat: Double, currentLng: Double, onComplete: (List<NearbyUserDTO>) -> Unit) {
        stopObservingNearbyUsers() // Clear previous listener
        
        locationsListener = object : ValueEventListener {
            override fun onDataChange(locationsSnapshot: DataSnapshot) {
                val nearbyUsers = mutableListOf<NearbyUserDTO>()
                val currentTime = System.currentTimeMillis()
                
                // Kiểm tra cài đặt nearbyEnabled của chính mình từ NODE_USER_SETTINGS cho chính xác nhất
                settingsRef.child(currentUserId).child("nearbyEnabled").get().addOnSuccessListener { settingSnapshot ->
                    val isMyNearbyEnabled = settingSnapshot.getValue(Boolean::class.java) ?: false
                    if (!isMyNearbyEnabled) {
                        onComplete(emptyList())
                        return@addOnSuccessListener
                    }
                    
                    val checkTasks = mutableListOf<String>()
                
                for (child in locationsSnapshot.children) {
                    val userId = child.key ?: continue
                    if (userId == currentUserId) continue // Skip self
                    
                    val userLocation = child.getValue(UserLocation::class.java) ?: continue
                    
                    if (!userLocation.isEnabled || userLocation.expiresAt < currentTime) continue
                    
                    val distanceKm = calculateDistanceKm(currentLat, currentLng, userLocation.lat, userLocation.lng)
                    if (distanceKm > Constants.NEARBY_RADIUS_KM) continue
                    
                    checkTasks.add(userId)
                }
                
                if (checkTasks.isEmpty()) {
                    onComplete(emptyList())
                    return@addOnSuccessListener
                }
                
                var completedCount = 0
                val validNearbyUsers = mutableListOf<NearbyUserDTO>()
                
                checkTasks.forEach { userId ->
                    validateUserVisibility(currentUserId, userId) { isVisible, displayName, avatarUrl ->
                        if (isVisible && displayName != null) {
                            val userLocation = locationsSnapshot.child(userId).getValue(UserLocation::class.java)
                            if (userLocation != null) {
                                val distanceKm = calculateDistanceKm(currentLat, currentLng, userLocation.lat, userLocation.lng)
                                
                                // Obfuscate location for privacy (e.g. +/- ~30-50m)
                                // 0.0004 degrees is roughly 45 meters
                                val offsetLat = (Math.random() - 0.5) * 0.0008
                                val offsetLng = (Math.random() - 0.5) * 0.0008
                                
                                validNearbyUsers.add(NearbyUserDTO(
                                    userId = userId, 
                                    displayName = displayName, 
                                    avatarUrl = avatarUrl, 
                                    distance = distanceKm,
                                    approxLat = userLocation.lat + offsetLat,
                                    approxLng = userLocation.lng + offsetLng
                                ))
                            }
                        }
                        
                        completedCount++
                        if (completedCount == checkTasks.size) {
                            validNearbyUsers.sortBy { it.distance }
                            onComplete(validNearbyUsers)
                        }
                    }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(emptyList())
            }
        }
        
        locationsRef.addValueEventListener(locationsListener!!)
    }

    fun stopObservingNearbyUsers() {
        locationsListener?.let { 
            locationsRef.removeEventListener(it)
            locationsListener = null
        }
    }
    
    private fun validateUserVisibility(currentUserId: String, targetUserId: String, onComplete: (Boolean, String?, String?) -> Unit) {
        // Check target user's Nearby Settings
        settingsRef.child(targetUserId).get().addOnSuccessListener { settingsSnapshot ->
            val settings = settingsSnapshot.getValue(UserSettings::class.java)
            
            // Rule: User must have nearbyEnabled and locationSharingEnabled
            if (settings == null || !settings.nearbyEnabled || !settings.locationSharingEnabled) {
                onComplete(false, null, null)
                return@addOnSuccessListener
            }
            
            // Check Blocks
            blocksRef.child(currentUserId).child("blockedUsers").child(targetUserId).get().addOnSuccessListener { myBlockSnapshot ->
                if (myBlockSnapshot.exists()) {
                    onComplete(false, null, null) // I blocked them
                    return@addOnSuccessListener
                }
                
                blocksRef.child(targetUserId).child("blockedUsers").child(currentUserId).get().addOnSuccessListener { theirBlockSnapshot ->
                    if (theirBlockSnapshot.exists()) {
                        onComplete(false, null, null) // They blocked me
                        return@addOnSuccessListener
                    }
                    
                    // All privacy rules passed, fetch user info
                    usersRef.child(targetUserId).get().addOnSuccessListener { userSnapshot ->
                        val displayName = userSnapshot.child("displayName").value as? String
                        val avatarUrl = userSnapshot.child("avatarUrl").value as? String
                        onComplete(true, displayName ?: "Unknown", avatarUrl)
                    }.addOnFailureListener {
                        onComplete(false, null, null)
                    }
                }.addOnFailureListener { onComplete(false, null, null) }
            }.addOnFailureListener { onComplete(false, null, null) }
        }.addOnFailureListener {
            onComplete(false, null, null)
        }
    }

    /**
     * Haversine formula to calculate distance between two lat/lng coordinates in km
     */
    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
