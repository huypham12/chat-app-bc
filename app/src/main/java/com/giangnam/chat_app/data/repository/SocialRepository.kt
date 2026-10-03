package com.giangnam.chat_app.data.repository

import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SocialRepository private constructor() {

    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private val relationshipsRef = db.getReference(Constants.NODE_RELATIONSHIPS)
    private val userBlocksRef = db.getReference(Constants.NODE_USER_BLOCKS)
    private val notificationRepository = NotificationRepository()

    companion object {
        @Volatile
        private var instance: SocialRepository? = null
        fun getInstance(): SocialRepository {
            return instance ?: synchronized(this) {
                instance ?: SocialRepository().also { instance = it }
            }
        }
    }

    // ==========================================
    // FRIEND SYSTEM
    // ==========================================

    fun sendFriendRequest(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        val timestamp = System.currentTimeMillis()
        val updates = hashMapOf<String, Any>(
            "$myUid/friendRequestsSent/$targetUid" to timestamp,
            "$targetUid/friendRequestsReceived/$myUid" to timestamp
        )
        relationshipsRef.updateChildren(updates)
            .addOnSuccessListener { 
                CoroutineScope(Dispatchers.IO).launch {
                    notificationRepository.sendOrUpdateNotification(
                        receiverId = targetUid,
                        type = "friend_request",
                        actorId = myUid
                    )
                    com.giangnam.chat_app.data.repository.ChatRepository.getInstance()
                        .sendPushNotification(targetUid, "Bạn có một lời mời kết bạn mới")
                }
                onComplete(true) 
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun acceptFriendRequest(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        val timestamp = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            // Add to friends
            "$myUid/friends/$targetUid" to timestamp,
            "$targetUid/friends/$myUid" to timestamp,
            // Remove from requests
            "$myUid/friendRequestsReceived/$targetUid" to null,
            "$targetUid/friendRequestsSent/$myUid" to null
        )
        relationshipsRef.updateChildren(updates)
            .addOnSuccessListener { 
                CoroutineScope(Dispatchers.IO).launch {
                    notificationRepository.sendOrUpdateNotification(
                        receiverId = targetUid,
                        type = "friend_request_accepted",
                        actorId = myUid
                    )
                    com.giangnam.chat_app.data.repository.ChatRepository.getInstance()
                        .sendPushNotification(targetUid, "Đã chấp nhận lời mời kết bạn của bạn")
                }
                onComplete(true) 
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun rejectOrCancelFriendRequest(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "$myUid/friendRequestsReceived/$targetUid" to null,
            "$myUid/friendRequestsSent/$targetUid" to null,
            "$targetUid/friendRequestsReceived/$myUid" to null,
            "$targetUid/friendRequestsSent/$myUid" to null
        )
        relationshipsRef.updateChildren(updates)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun unfriend(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "$myUid/friends/$targetUid" to null,
            "$targetUid/friends/$myUid" to null
        )
        relationshipsRef.updateChildren(updates)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // ==========================================
    // BLOCK SYSTEM
    // ==========================================

    fun blockUser(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        // When blocking, we optionally also unfriend and remove pending requests just to be clean
        val timestamp = System.currentTimeMillis()
        val blockTask = userBlocksRef.child(myUid).child("blockedUsers").child(targetUid).setValue(timestamp)
        
        // Unfriend as well
        val unfriendUpdates = hashMapOf<String, Any?>(
            "$myUid/friends/$targetUid" to null,
            "$targetUid/friends/$myUid" to null,
            "$myUid/friendRequestsReceived/$targetUid" to null,
            "$myUid/friendRequestsSent/$targetUid" to null,
            "$targetUid/friendRequestsReceived/$myUid" to null,
            "$targetUid/friendRequestsSent/$myUid" to null
        )
        relationshipsRef.updateChildren(unfriendUpdates)

        blockTask.addOnSuccessListener { onComplete(true) }
                 .addOnFailureListener { onComplete(false) }
    }

    fun unblockUser(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        userBlocksRef.child(myUid).child("blockedUsers").child(targetUid).removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Helper to check relationship status
    fun listenToRelationshipStatus(myUid: String, targetUid: String, onStatusChange: (FriendStatus) -> Unit) {
        relationshipsRef.child(myUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                when {
                    snapshot.child("friends").hasChild(targetUid) -> onStatusChange(FriendStatus.FRIEND)
                    snapshot.child("friendRequestsSent").hasChild(targetUid) -> onStatusChange(FriendStatus.REQUEST_SENT)
                    snapshot.child("friendRequestsReceived").hasChild(targetUid) -> onStatusChange(FriendStatus.REQUEST_RECEIVED)
                    else -> onStatusChange(FriendStatus.NONE)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // handle error
            }
        })
    }
    
    fun checkBlockStatus(myUid: String, targetUid: String, onResult: (Boolean, Boolean) -> Unit) {
        // Returns (amIBlockedByThem, didIBlockThem)
        userBlocksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val didIBlockThem = snapshot.child(myUid).child("blockedUsers").hasChild(targetUid)
                val amIBlockedByThem = snapshot.child(targetUid).child("blockedUsers").hasChild(myUid)
                onResult(amIBlockedByThem, didIBlockThem)
            }
            override fun onCancelled(error: DatabaseError) {
                onResult(false, false)
            }
        })
    }
    
    // ==========================================
    // FOLLOW SYSTEM
    // ==========================================

    fun followUser(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        val timestamp = System.currentTimeMillis()
        val updates = hashMapOf<String, Any>(
            "$myUid/following/$targetUid" to timestamp,
            "$targetUid/followers/$myUid" to timestamp
        )
        relationshipsRef.updateChildren(updates)
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch {
                    notificationRepository.sendOrUpdateNotification(
                        receiverId = targetUid,
                        type = "follow",
                        actorId = myUid
                    )
                }
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun unfollowUser(myUid: String, targetUid: String, onComplete: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "$myUid/following/$targetUid" to null,
            "$targetUid/followers/$myUid" to null
        )
        relationshipsRef.updateChildren(updates)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}

enum class FriendStatus {
    NONE, REQUEST_SENT, REQUEST_RECEIVED, FRIEND
}
