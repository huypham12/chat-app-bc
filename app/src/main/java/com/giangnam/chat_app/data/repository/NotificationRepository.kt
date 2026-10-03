package com.giangnam.chat_app.data.repository

import com.giangnam.chat_app.data.model.Notification
import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NotificationRepository {

    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)

    /**
     * Get the reference to a user's notification node
     */
    private fun getNotificationsRef(userId: String) =
        db.getReference(Constants.NODE_NOTIFICATIONS).child(userId)

    /**
     * Observe notifications for the Notification Center (Tab Chuông)
     */
    fun observeNotifications(userId: String): Flow<List<Notification>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifications = snapshot.children.mapNotNull {
                    val notif = it.getValue(Notification::class.java)
                    if (notif != null) {
                        notif.notificationId = it.key ?: ""
                        notif.isRead = it.child("isRead").getValue(Boolean::class.java) ?: false
                    }
                    notif
                }.sortedByDescending { it.updatedAt }
                trySend(notifications)
            }
            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        
        // Listen to all notifications and sort locally
        val query = getNotificationsRef(userId)
        query.addValueEventListener(listener)
        
        awaitClose { query.removeEventListener(listener) }
    }

    /**
     * Observe unread notification count for Badges
     */
    fun observeUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count {
                    val isReadVal = it.child("isRead").value ?: it.child("read").value
                    val isRead = when (isReadVal) {
                        is Boolean -> isReadVal
                        is String -> isReadVal.toBoolean()
                        else -> false
                    }
                    !isRead
                }
                trySend(count)
            }
            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        
        getNotificationsRef(userId).addValueEventListener(listener)
        
        awaitClose { getNotificationsRef(userId).removeEventListener(listener) }
    }

    /**
     * Mark a specific notification as read
     */
    suspend fun markAsRead(userId: String, notificationId: String) {
        try {
            val updates = mapOf(
                "isRead" to true,
                "read" to true
            )
            getNotificationsRef(userId).child(notificationId).updateChildren(updates).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Mark all notifications as read (useful when user opens the Notification tab)
     */
    suspend fun markAllAsRead(userId: String) {
        try {
            val snapshot = getNotificationsRef(userId).get().await()
            val updates = hashMapOf<String, Any>()
            
            snapshot.children.forEach { doc ->
                val isReadVal = doc.child("isRead").value ?: doc.child("read").value
                val isRead = when (isReadVal) {
                    is Boolean -> isReadVal
                    is String -> isReadVal.toBoolean()
                    else -> false
                }
                
                if (!isRead) {
                    val key = doc.key
                    if (key != null) {
                        updates["$key/isRead"] = true
                        updates["$key/read"] = true
                    }
                }
            }
            
            if (updates.isNotEmpty()) {
                getNotificationsRef(userId).updateChildren(updates).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Smart Grouping logic:
     * Check if a similar unread notification exists (e.g. same type, same target).
     * If yes, update it (increase count, update actorId). If no, create a new one.
     */
    suspend fun sendOrUpdateNotification(
        receiverId: String,
        type: String,
        actorId: String,
        roomId: String? = null,
        messageId: String? = null,
        title: String? = null,
        body: String? = null,
        extraData: Map<String, String>? = null
    ) {
        try {
            // Read all and filter locally to avoid Index requirements
            val snapshot = getNotificationsRef(receiverId).get().await()
            var existingNotif: Notification? = null
            var existingKey: String? = null
            
            for (child in snapshot.children) {
                val notif = child.getValue(Notification::class.java)
                if (notif != null && !notif.isRead && notif.type == type) {
                    // For grouping by room if applicable
                    if (roomId == null || notif.roomId == roomId) {
                        existingNotif = notif
                        existingKey = child.key
                        break
                    }
                }
            }
            
            if (existingNotif != null && existingKey != null) {
                // Update existing (Grouping)
                val newCount = existingNotif.count + 1
                
                val updates = hashMapOf<String, Any>(
                    "$existingKey/count" to newCount,
                    "$existingKey/actorId" to actorId, // Update to the latest actor
                    "$existingKey/updatedAt" to System.currentTimeMillis()
                )
                if (title != null) updates["$existingKey/title"] = title
                if (body != null) updates["$existingKey/body"] = body
                if (extraData != null) updates["$existingKey/extraData"] = extraData
                if (messageId != null) updates["$existingKey/messageId"] = messageId
                
                getNotificationsRef(receiverId).updateChildren(updates).await()
            } else {
                // Create new
                val newId = UUID.randomUUID().toString()
                val newNotif = Notification(
                    notificationId = newId,
                    type = type,
                    actorId = actorId,
                    receiverId = receiverId,
                    roomId = roomId,
                    messageId = messageId,
                    title = title,
                    body = body,
                    count = 1,
                    extraData = extraData
                )
                
                getNotificationsRef(receiverId).child(newId).setValue(newNotif).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
