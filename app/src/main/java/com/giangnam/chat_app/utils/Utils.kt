package com.giangnam.chat_app.utils

import com.google.firebase.auth.FirebaseAuth


object Utils {
    private var firebaseAuthInstance: FirebaseAuth? = null
    fun getFirebaseAuthInstance(): FirebaseAuth {
        if (firebaseAuthInstance == null) {
            firebaseAuthInstance = FirebaseAuth.getInstance()
        }
        return firebaseAuthInstance!!
    }

    fun getUserId(): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.uid ?: ""
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun hashPhoneNumber(phone: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(phone.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        val hexString = StringBuilder(2 * hash.size)
        for (i in hash.indices) {
            val hex = Integer.toHexString(0xff and hash[i].toInt())
            if (hex.length == 1) {
                hexString.append('0')
            }
            hexString.append(hex)
        }
        return hexString.toString()
    }
}