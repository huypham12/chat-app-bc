package com.giangnam.chat_app.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giangnam.chat_app.data.model.ChatRoom
import com.giangnam.chat_app.data.model.UserInbox
import com.giangnam.chat_app.data.repository.UserRepository
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RecentChatItem(
    val inbox: UserInbox,
    val room: ChatRoom
)

class MainViewModel : ViewModel() {

    private val userRepository = UserRepository.getInstance()
    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)

    private val _allUserNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val allUserNames: StateFlow<Map<String, String>> = _allUserNames

    private val _allUsers = MutableStateFlow<Map<String, com.giangnam.chat_app.data.model.User>>(emptyMap())
    val allUsers: StateFlow<Map<String, com.giangnam.chat_app.data.model.User>> = _allUsers

    private val _recentChats = MutableStateFlow<List<RecentChatItem>>(emptyList())
    val recentChats: StateFlow<List<RecentChatItem>> = _recentChats

    private val _totalUnreadChatCount = MutableStateFlow(0)
    val totalUnreadChatCount: StateFlow<Int> = _totalUnreadChatCount

    private var inboxListener: ValueEventListener? = null
    private val individualRoomListeners = mutableMapOf<String, ValueEventListener>()

    // Cache to hold Rooms data locally
    private val cachedRooms = mutableMapOf<String, ChatRoom>()

    fun fetchAllUserNames() {
        userRepository.getAllUsers { users ->
            val nameMap = users.associate { it.uid!! to it.username }
            val userMap = users.associate { it.uid!! to it }
            _allUserNames.value = nameMap
            _allUsers.value = userMap
        }
    }

    fun setupUserPresence(userId: String) {
        userRepository.setupUserPresence(userId)
    }
    
    private val notificationRepository = com.giangnam.chat_app.data.repository.NotificationRepository()
    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount
    
    fun observeUnreadNotifications() {
        val userId = Utils.getUserId()
        if (userId.isEmpty()) return
        
        viewModelScope.launch {
            notificationRepository.observeUnreadCount(userId).collect { count ->
                _unreadNotificationCount.value = count
            }
        }
    }

    private val _pendingFriendRequestsCount = MutableStateFlow(0)
    val pendingFriendRequestsCount: StateFlow<Int> = _pendingFriendRequestsCount

    private var friendRequestsListener: ValueEventListener? = null

    fun observePendingFriendRequests() {
        val userId = Utils.getUserId()
        if (userId.isEmpty()) return

        val friendRequestsRef = db.getReference(Constants.NODE_RELATIONSHIPS)
            .child(userId).child("friendRequestsReceived")

        friendRequestsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _pendingFriendRequestsCount.value = snapshot.childrenCount.toInt()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("MainViewModel", "Failed to read friend requests", error.toException())
            }
        }
        friendRequestsRef.addValueEventListener(friendRequestsListener!!)
    }

    fun startListeningToRecentChats() {
        val userId = Utils.getUserId()
        if (userId.isEmpty()) return

        val inboxRef = db.getReference(Constants.NODE_USER_INBOX).child(userId)

        // Listen to user inbox
        inboxListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val inboxes = mutableListOf<UserInbox>()
                val newRoomIds = mutableSetOf<String>()
                for (inboxSnapshot in snapshot.children) {
                    val inbox = inboxSnapshot.getValue(UserInbox::class.java)
                    if (inbox != null) {
                        val roomId = inboxSnapshot.key ?: ""
                        inbox.roomId = roomId
                        inboxes.add(inbox)
                        newRoomIds.add(roomId)
                    }
                }
                currentInboxList = inboxes
                
                // Add new listeners for newly added rooms
                for (roomId in newRoomIds) {
                    if (!individualRoomListeners.containsKey(roomId)) {
                        val listener = object : ValueEventListener {
                            override fun onDataChange(roomSnapshot: DataSnapshot) {
                                val room = roomSnapshot.getValue(ChatRoom::class.java)
                                if (room != null) {
                                    cachedRooms[roomId] = room
                                    updateRecentChats(currentInboxList)
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                Log.e("MainViewModel", "Failed to read room $roomId", error.toException())
                            }
                        }
                        individualRoomListeners[roomId] = listener
                        db.getReference(Constants.NODE_ROOMS).child(roomId).addValueEventListener(listener)
                    }
                }
                
                // Remove listeners for rooms that are no longer in inbox
                val iterator = individualRoomListeners.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!newRoomIds.contains(entry.key)) {
                        db.getReference(Constants.NODE_ROOMS).child(entry.key).removeEventListener(entry.value)
                        cachedRooms.remove(entry.key)
                        iterator.remove()
                    }
                }

                updateRecentChats(inboxes)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("MainViewModel", "Failed to read inbox", error.toException())
            }
        }
        inboxRef.addValueEventListener(inboxListener!!)
    }

    private var currentInboxList: List<UserInbox> = emptyList()

    private fun updateRecentChats(inboxes: List<UserInbox>) {
        val result = mutableListOf<RecentChatItem>()
        for (inbox in inboxes) {
            val room = cachedRooms[inbox.roomId]
            if (room != null) {
                result.add(RecentChatItem(inbox, room))
            }
        }
        
        // Sắp xếp: Ghim lên trước, sau đó theo thời gian cập nhật mới nhất
        result.sortWith(compareByDescending<RecentChatItem> { it.inbox.isPinned }
            .thenByDescending { it.room.updatedAt })
            
        _recentChats.value = result
        
        // Calculate total unread count
        var totalUnread = 0
        for (item in result) {
            if (!item.inbox.isHidden) {
                totalUnread += item.inbox.unreadCount
            }
        }
        _totalUnreadChatCount.value = totalUnread
    }

    fun stopListeningToRecentChats() {
        val userId = Utils.getUserId()
        if (userId.isNotEmpty()) {
            inboxListener?.let {
                db.getReference(Constants.NODE_USER_INBOX).child(userId).removeEventListener(it)
            }
            individualRoomListeners.forEach { (roomId, listener) ->
                db.getReference(Constants.NODE_ROOMS).child(roomId).removeEventListener(listener)
            }
            individualRoomListeners.clear()
            inboxListener = null
            
            friendRequestsListener?.let {
                db.getReference(Constants.NODE_RELATIONSHIPS)
                    .child(userId).child("friendRequestsReceived").removeEventListener(it)
                friendRequestsListener = null
            }
        }
    }
}