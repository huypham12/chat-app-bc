package com.giangnam.chat_app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.data.repository.SocialRepository
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

class ContactsViewModel : ViewModel() {

    private val socialRepository = SocialRepository.getInstance()
    private val userRepository = UserRepository.getInstance()
    private val relationshipsRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_RELATIONSHIPS)
    private val myUid = Utils.getUserId()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends

    private val _friendRequests = MutableStateFlow<List<User>>(emptyList())
    val friendRequests: StateFlow<List<User>> = _friendRequests

    private val _mutualFriends = MutableStateFlow<List<User>>(emptyList())
    val mutualFriends: StateFlow<List<User>> = _mutualFriends

    init {
        listenToSocialData()
    }

    private fun listenToSocialData() {
        if (myUid.isEmpty()) return

        relationshipsRef.child(myUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Fetch friends
                val friendIds = snapshot.child("friends").children.mapNotNull { it.key }
                fetchUsers(friendIds) { users -> _friends.value = users }

                // Fetch friend requests received
                val requestIds = snapshot.child("friendRequestsReceived").children.mapNotNull { it.key }
                fetchUsers(requestIds) { users -> _friendRequests.value = users }

                // Very simple suggestion algorithm for now: all users who are not friends, not requested, not me
                suggestFriends(friendIds + requestIds + snapshot.child("friendRequestsSent").children.mapNotNull { it.key } + myUid)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchUsers(uids: List<String>, onComplete: (List<User>) -> Unit) {
        if (uids.isEmpty()) {
            onComplete(emptyList())
            return
        }
        val result = mutableListOf<User>()
        var count = 0
        uids.forEach { uid ->
            userRepository.getUserById(uid) { user ->
                if (user != null) result.add(user)
                count++
                if (count == uids.size) {
                    onComplete(result)
                }
            }
        }
    }

    private fun suggestFriends(excludeUids: List<String>) {
        userRepository.getAllUsers { allUsers ->
            val suggestions = allUsers.filter { it.uid !in excludeUids }.take(10) // show up to 10 suggestions
            _mutualFriends.value = suggestions
        }
    }

    fun acceptFriendRequest(targetUid: String) {
        socialRepository.acceptFriendRequest(myUid, targetUid) {}
    }

    fun sendFriendRequest(targetUid: String) {
        socialRepository.sendFriendRequest(myUid, targetUid) {}
    }
}
