package com.giangnam.chat_app.data.repository

import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UserRepository private constructor() {

    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private val usersRef = db.getReference(Constants.NODE_USERS)

    companion object {
        @Volatile
        private var instance: UserRepository? = null

        fun getInstance(): UserRepository =
            instance ?: synchronized(this) {
                instance ?: UserRepository().also { instance = it }
            }
    }

    fun getUserById(userId: String, onComplete: (User?) -> Unit) {
        usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            onComplete(snapshot.getValue(User::class.java))
        }.addOnFailureListener {
            onComplete(null)
        }
    }

    fun getAllUsers(onComplete: (List<User>) -> Unit) {
        usersRef.get().addOnSuccessListener { snapshot ->
            val userList = mutableListOf<User>()
            for (userSnapshot in snapshot.children) {
                userSnapshot.getValue(User::class.java)?.let { userList.add(it) }
            }
            onComplete(userList)
        }.addOnFailureListener {
            onComplete(emptyList())
        }
    }

    fun updateUser(user: User, onComplete: (Boolean) -> Unit) {
        usersRef.child(user.uid).setValue(user).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun isUsernameAvailable(username: String, currentUid: String, onComplete: (Boolean) -> Unit) {
        usersRef.orderByChild("userName").equalTo(username).get().addOnSuccessListener { snapshot ->
            var available = true
            for (child in snapshot.children) {
                val user = child.getValue(User::class.java)
                if (user != null && user.uid != currentUid) {
                    available = false
                    break
                }
            }
            onComplete(available)
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun listenToUserStatus(userId: String, onStatusChange: (Boolean, Long) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").value as? Boolean ?: false
                val lastSeen = snapshot.child("lastSeen").value as? Long ?: 0L
                onStatusChange(isOnline, lastSeen)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        usersRef.child(userId).addValueEventListener(listener)
        return listener
    }

    fun removeStatusListener(userId: String, listener: ValueEventListener) {
        usersRef.child(userId).removeEventListener(listener)
    }

    fun setupUserPresence(userId: String) {
        val userRef = usersRef.child(userId)
        val connectedRef = db.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userRef.child("status").setValue("online")
                    userRef.child("isOnline").setValue(true)
                    
                    userRef.child("status").onDisconnect().setValue("offline")
                    userRef.child("isOnline").onDisconnect().setValue(false)
                    userRef.child("lastSeen").onDisconnect().setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}