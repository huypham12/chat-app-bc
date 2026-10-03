package com.giangnam.chat_app.ui.search

import androidx.lifecycle.ViewModel
import com.giangnam.chat_app.data.repository.UserRepository
import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query

class SearchViewModel : ViewModel() {
    private val userRepository = UserRepository.getInstance()

    fun getUsersQuery(searchText: String): Query {
        val dbRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS)
        
        // Normalize input
        val normalized = com.giangnam.chat_app.utils.PhoneUtils.normalize(searchText)
        val digitsOnly = searchText.filter { it.isDigit() }
        
        // 1. If it's likely a phone number search
        // Check if normalized result is longer than just +84 (meaning user typed something)
        if (digitsOnly.isNotEmpty()) {
            return dbRef.orderByChild("phoneNumber")
                .startAt(normalized)
                .endAt(normalized + "\uf8ff")
        }
        
        // 2. Default: Search by Username
        return dbRef.orderByChild("userName")
            .startAt(searchText)
            .endAt(searchText + "\uf8ff")
    }
}