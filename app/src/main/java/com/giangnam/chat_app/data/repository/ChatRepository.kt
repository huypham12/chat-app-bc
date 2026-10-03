package com.giangnam.chat_app.data.repository

import android.util.Base64
import android.util.Log
import com.giangnam.chat_app.BuildConfig
import com.giangnam.chat_app.data.model.ChatRoom
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class ChatRepository private constructor() {

    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private val chatRoomRef = db.getReference(Constants.NODE_ROOMS)
    private val userChatRoomsRef = db.getReference(Constants.NODE_USER_INBOX)
    private val roomMembersRef = db.getReference(Constants.NODE_ROOM_MEMBERS)
    private val notificationRepository = NotificationRepository()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            var request = chain.request()
            var response = chain.proceed(request)
            var tryCount = 0
            val maxLimit = 3
            // Nếu lỗi 5xx (như 503 Server Overload) thì tự động thử lại
            while (!response.isSuccessful && response.code >= 500 && tryCount < maxLimit) {
                tryCount++
                response.close()
                Thread.sleep(2000) // Đợi 2s rồi thử lại
                response = chain.proceed(request)
            }
            response
        }
        .build()

    companion object {
        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(): ChatRepository =
            instance ?: synchronized(this) {
                instance ?: ChatRepository().also { instance = it }
            }
    }

    fun getChatRoom(chatRoomId: String, onComplete: (ChatRoom?) -> Unit) {
        chatRoomRef.child(chatRoomId).get().addOnSuccessListener { snapshot ->
            onComplete(snapshot.getValue(ChatRoom::class.java))
        }.addOnFailureListener {
            onComplete(null)
        }
    }

    fun createChatRoom(chatRoom: ChatRoom, onComplete: (Boolean) -> Unit) {
        chatRoomRef.child(chatRoom.roomId).setValue(chatRoom).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Update UserChatRooms for all members
                for (userId in chatRoom.userIds) {
                    val inbox = mapOf(
                        "roomId" to chatRoom.roomId,
                        "lastActivityAt" to chatRoom.updatedAt
                    )
                    userChatRoomsRef.child(userId).child(chatRoom.roomId).updateChildren(inbox)
                    
                    if (chatRoom.type == "group") {
                        val role = if (userId == chatRoom.createdBy) "admin" else "member"
                        val member = com.giangnam.chat_app.data.model.RoomMember(
                            uid = userId,
                            role = role,
                            joinedAt = chatRoom.createdAt
                        )
                        roomMembersRef.child(chatRoom.roomId).child(userId).setValue(member)
                        
                        // Notify members added to group (except creator)
                        if (userId != chatRoom.createdBy) {
                            CoroutineScope(Dispatchers.IO).launch {
                                notificationRepository.sendOrUpdateNotification(
                                    receiverId = userId,
                                    type = "group_added",
                                    actorId = chatRoom.createdBy,
                                    roomId = chatRoom.roomId,
                                    title = "Bạn đã được thêm vào nhóm",
                                    body = "${chatRoom.name ?: "Nhóm mới"}"
                                )
                            }
                        }
                    }
                }
            }
            onComplete(task.isSuccessful)
        }
    }

    fun sendMessage(chatRoomId: String, message: Message, onComplete: (Boolean) -> Unit) {
        val messageRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES).push()
        val messageId = messageRef.key ?: ""
        message.messageId = messageId
        
        val preview = when (message.type) {
            "image" -> "Đã gửi một ảnh"
            "sticker" -> "Đã gửi một nhãn dán"
            "audio", "voice" -> "Đã gửi một tin nhắn thoại"
            else -> message.text ?: ""
        }

        val currentTime = System.currentTimeMillis()
        val updates = mapOf<String, Any>(
            "lastMessage/messageId" to messageId,
            "lastMessage/senderId" to message.senderId,
            "lastMessage/type" to message.type,
            "lastMessage/preview" to preview,
            "lastMessage/timestamp" to message.timestamp,
            "lastMessage/isRead" to message.isRead,
            "updatedAt" to currentTime
        )
        
        chatRoomRef.child(chatRoomId).updateChildren(updates)
        messageRef.setValue(message)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Send notifications for mentions
                    if (message.mentions.isNotEmpty()) {
                        message.mentions.keys.forEach { mentionedUid ->
                            if (mentionedUid != message.senderId) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    notificationRepository.sendOrUpdateNotification(
                                        receiverId = mentionedUid,
                                        type = "mention",
                                        actorId = message.senderId,
                                        roomId = chatRoomId,
                                        messageId = messageId
                                    )
                                }
                            }
                        }
                    }

                    // Update userInbox for members to update sorting order and unread count
                    chatRoomRef.child(chatRoomId).child("userIds").get().addOnSuccessListener { snapshot ->
                        val userIds = snapshot.value as? List<String>
                        userIds?.forEach { uid ->
                            if (uid != message.senderId) {
                                userChatRoomsRef.child(uid).child(chatRoomId).get().addOnSuccessListener { inboxSnapshot ->
                                    val currentUnread = inboxSnapshot.child("unreadCount").getValue(Int::class.java) ?: 0
                                    userChatRoomsRef.child(uid).child(chatRoomId).updateChildren(
                                        mapOf(
                                            "lastActivityAt" to currentTime,
                                            "unreadCount" to currentUnread + 1,
                                            "isHidden" to false
                                        )
                                    )
                                }
                            } else {
                                userChatRoomsRef.child(uid).child(chatRoomId).updateChildren(
                                    mapOf(
                                        "lastActivityAt" to currentTime,
                                        "isHidden" to false
                                    )
                                )
                            }
                        }
                    }
                }
                onComplete(task.isSuccessful)
            }
    }

    fun uploadImageToImgBB(base64Image: String, onComplete: (String?) -> Unit) {
        val requestBody = FormBody.Builder()
            .add("image", base64Image)
            .build()

        val url = Constants.IMGBB_UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("key", BuildConfig.IMGBB_API_KEY)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onComplete(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val responseData = it.body?.string()
                        if (responseData != null) {
                            val jsonObject = JSONObject(responseData)
                            onComplete(jsonObject.getJSONObject("data").getString("url"))
                            return
                        }
                    }
                    onComplete(null)
                }
            }
        })
    }

    fun uploadAudioToCloudinary(file: File, onComplete: (String?) -> Unit) {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
        val url = "https://api.cloudinary.com/v1_1/$cloudName/video/upload"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/3gpp".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onComplete(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val responseData = it.body?.string()
                        if (responseData != null) {
                            val secureAudioUrl = JSONObject(responseData).getString("secure_url")
                            onComplete(secureAudioUrl)
                            return
                        }
                    }
                    onComplete(null)
                }
            }
        })
    }

    fun getGeminiReply(userText: String, onComplete: (String?) -> Unit) {
        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", userText) })
                }
                put(JSONObject().apply { put("parts", partsArray) })
            }
            put("contents", contentsArray)

            val systemInstruction = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", Constants.SYSTEM_PROMPT_BOT) })
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstruction)
            
            val safetySettingsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    put("threshold", "BLOCK_NONE")
                })
            }
            put("safetySettings", safetySettingsArray)
        }.toString()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(Constants.GEMINI_URL)
            .post(requestBody)
            .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (e is java.net.SocketTimeoutException) {
                    onComplete("ERROR: Phản hồi quá lâu, vui lòng thử lại sau.")
                } else {
                    onComplete("ERROR: Lỗi kết nối mạng (${e.message}).")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        if (it.code == 429) {
                            onComplete("ERROR: Hệ thống AI đang quá tải do vượt giới hạn yêu cầu. Vui lòng đợi 1 phút rồi thử lại!")
                        } else if (it.code == 400 || it.code == 403) {
                            onComplete("ERROR: Lỗi xác thực hoặc API Key không hợp lệ (Mã lỗi: ${it.code}).")
                        } else {
                            onComplete("ERROR: Lỗi máy chủ (Mã lỗi: ${it.code}).")
                        }
                        return
                    }
                    try {
                        val responseData = it.body?.string() ?: ""
                        val jsonObject = JSONObject(responseData)
                        val candidates = jsonObject.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val firstPart = parts.getJSONObject(0)
                                val aiReply = firstPart.optString("text")
                                onComplete(aiReply)
                                return
                            } else {
                                val finishReason = firstCandidate.optString("finishReason", "")
                                if (finishReason == "SAFETY" || finishReason == "BLOCK") {
                                    onComplete("Xin lỗi, tôi không thể trả lời câu hỏi này vì lý do nhạy cảm hoặc vi phạm chính sách an toàn.")
                                    return
                                }
                            }
                        }
                        onComplete(null)
                    } catch (e: Exception) {
                        onComplete(null)
                    }
                }
            }
        })
    }

    fun getSmartReplies(incomingMessage: String, onComplete: (List<String>?) -> Unit) {
        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", incomingMessage) })
                }
                put(JSONObject().apply { put("parts", partsArray) })
            }
            put("contents", contentsArray)

            val systemInstruction = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", Constants.SYSTEM_PROMPT_SMART_REPLY) })
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstruction)
            
            val safetySettingsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    put("threshold", "BLOCK_NONE")
                })
            }
            put("safetySettings", safetySettingsArray)
        }.toString()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(Constants.GEMINI_URL)
            .post(requestBody)
            .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onComplete(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onComplete(null)
                        return
                    }
                    try {
                        val responseData = it.body?.string() ?: ""
                        val root = JSONObject(responseData)
                        val candidates = root.optJSONArray("candidates")
                        var textResult: String? = null
                        
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val firstPart = parts.getJSONObject(0)
                                textResult = firstPart.optString("text")
                            }
                        }

                        if (textResult != null) {
                            val replies = mutableListOf<String>()
                            try {
                                val cleanJson = textResult.substringAfter("[").substringBeforeLast("]")
                                val finalJson = "[$cleanJson]"
                                val jsonArray = JSONArray(finalJson)
                                for (i in 0 until jsonArray.length()) {
                                    replies.add(jsonArray.getString(i))
                                }
                            } catch (e: Exception) {
                                // Fallback nếu AI trả về không chuẩn JSON
                                val regex = "\"([^\"]+)\"".toRegex()
                                val matches = regex.findAll(textResult)
                                if (matches.any()) {
                                    for (match in matches) {
                                        replies.add(match.groupValues[1])
                                    }
                                } else {
                                    val lines = textResult.split("\n").filter { it.isNotBlank() }
                                    for (line in lines) {
                                        val cleanLine = line.replace(Regex("^\\d+\\.\\s*"), "").replace("-", "").trim()
                                        if (cleanLine.isNotBlank()) replies.add(cleanLine)
                                    }
                                }
                            }
                            
                            if (replies.isNotEmpty()) {
                                onComplete(replies.take(3)) // Chỉ lấy tối đa 3 gợi ý
                            } else {
                                onComplete(null)
                            }
                        } else {
                            onComplete(null)
                        }
                    } catch (e: Exception) {
                        onComplete(null)
                    }
                }
            }
        })
    }

    fun sendPushNotification(user2Id: String, messageText: String) {
        val jsonBody = JSONObject().apply {
            put("app_id", BuildConfig.ONESIGNAL_APP_ID)
            put("target_channel", "push")
            put("include_external_user_ids", JSONArray().put(user2Id))
            put("headings", JSONObject().put("en", "Tin nhắn mới"))
            put("contents", JSONObject().put("en", messageText))
            put("data", JSONObject().put("senderId", Utils.getUserId()))
        }.toString()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(Constants.ONESIGNAL_NOTIFICATIONS_URL)
            .addHeader("Authorization", "Basic ${BuildConfig.ONESIGNAL_REST_API_KEY}")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }
    
    fun markMessagesAsRead(chatRoomId: String) {
        val currentUserId = Utils.getUserId()

        // 0. Reset unreadCount in UserInbox
        userChatRoomsRef.child(currentUserId).child(chatRoomId).child("unreadCount").setValue(0)

        // 1. Update Room's lastMessage status if it's from the other user
        chatRoomRef.child(chatRoomId).child("lastMessage").get().addOnSuccessListener { snapshot ->
            val senderId = snapshot.child("senderId").value as? String
            val isRead = snapshot.child("isRead").value as? Boolean ?: false
            if (senderId != null && senderId != currentUserId && !isRead) {
                chatRoomRef.child(chatRoomId).child("lastMessage/isRead").setValue(true)
            }
        }

        // 2. Update individual messages status
        val messagesRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES)
        messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (messageSnapshot in snapshot.children) {
                        val senderId = messageSnapshot.child("senderId").value as? String ?: ""
                        val isRead = messageSnapshot.child("isRead").value as? Boolean ?: false

                        if (senderId != currentUserId && !isRead) {
                            messageSnapshot.ref.child("isRead").setValue(true)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun updateInboxProperty(userId: String, roomId: String, property: String, value: Any) {
        userChatRoomsRef.child(userId).child(roomId).child(property).setValue(value)
    }

    fun deleteConversation(userId: String, roomId: String) {
        val updates = mapOf<String, Any>(
            "isHidden" to true,
            "isPinned" to false,
            "isMuted" to false,
            "clearedAt" to System.currentTimeMillis()
        )
        userChatRoomsRef.child(userId).child(roomId).updateChildren(updates)
    }

    fun revokeMessage(chatRoomId: String, messageId: String, onComplete: (Boolean) -> Unit) {
        val messageRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES).child(messageId)
        val updates = mapOf<String, Any>(
            "isRevoked" to true,
            "text" to "",
            "mediaUrl" to "",
            "type" to "text"
        )
        messageRef.updateChildren(updates).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun deleteMessageForMe(chatRoomId: String, messageId: String, userId: String, onComplete: (Boolean) -> Unit) {
        val messageRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES).child(messageId)
        messageRef.child("deletedFor").child(userId).setValue(true).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun editMessage(chatRoomId: String, messageId: String, newText: String, onComplete: (Boolean) -> Unit) {
        val messageRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES).child(messageId)
        val updates = mapOf<String, Any>(
            "text" to newText,
            "isEdited" to true
        )
        messageRef.updateChildren(updates).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun addReaction(chatRoomId: String, messageId: String, userId: String, emoji: String) {
        val reactionRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES).child(messageId).child("reactions").child(userId)
        val reactionObj = com.giangnam.chat_app.data.model.Reaction(emoji, System.currentTimeMillis())
        reactionRef.setValue(reactionObj)
    }

    fun removeReaction(chatRoomId: String, messageId: String, userId: String) {
        val reactionRef = chatRoomRef.child(chatRoomId).child(Constants.NODE_MESSAGES).child(messageId).child("reactions").child(userId)
        reactionRef.removeValue()
    }

    fun updateGroupInfo(chatRoomId: String, newName: String?, newAvatarUrl: String?, onComplete: (Boolean) -> Unit) {
        val updates = mutableMapOf<String, Any>()
        if (newName != null) updates["name"] = newName
        if (newAvatarUrl != null) updates["avatarUrl"] = newAvatarUrl
        
        if (updates.isNotEmpty()) {
            chatRoomRef.child(chatRoomId).updateChildren(updates).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        } else {
            onComplete(true)
        }
    }

    fun leaveGroup(chatRoomId: String, userId: String, onComplete: (Boolean) -> Unit) {
        roomMembersRef.child(chatRoomId).get().addOnSuccessListener { membersSnapshot ->
            var isAdmin = false
            var otherMemberId: String? = null
            
            for (child in membersSnapshot.children) {
                val memberUid = child.key ?: ""
                val role = child.child("role").getValue(String::class.java)
                if (memberUid == userId) {
                    if (role == "admin") isAdmin = true
                } else {
                    if (otherMemberId == null) otherMemberId = memberUid
                }
            }
            
            val doLeave = {
                // 1. Remove from roomMembers node
                roomMembersRef.child(chatRoomId).child(userId).removeValue().addOnCompleteListener { task1 ->
                    if (task1.isSuccessful) {
                        // 2. Remove user from userIds list in ChatRoom
                        chatRoomRef.child(chatRoomId).child("userIds").get().addOnSuccessListener { snapshot ->
                            val userIds = snapshot.value as? MutableList<String> ?: mutableListOf()
                            if (userIds.contains(userId)) {
                                userIds.remove(userId)
                                chatRoomRef.child(chatRoomId).child("userIds").setValue(userIds)
                            }
                        }
                        
                        // 3. Remove isHidden = true logic so they can still see the chat
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                }
            }

            if (isAdmin && otherMemberId != null) {
                transferAdminRole(chatRoomId, userId, otherMemberId) {
                    doLeave()
                }
            } else {
                doLeave()
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun addMemberToGroup(chatRoomId: String, userId: String, onComplete: (Boolean) -> Unit) {
        // 1. Add to RoomMembers node
        val member = com.giangnam.chat_app.data.model.RoomMember(
            uid = userId,
            role = "member",
            joinedAt = System.currentTimeMillis()
        )
        roomMembersRef.child(chatRoomId).child(userId).setValue(member).addOnCompleteListener { task1 ->
            if (task1.isSuccessful) {
                // 2. Add to userIds in ChatRoom
                chatRoomRef.child(chatRoomId).child("userIds").get().addOnSuccessListener { snapshot ->
                    val userIds = snapshot.value as? MutableList<String> ?: mutableListOf()
                    if (!userIds.contains(userId)) {
                        userIds.add(userId)
                        chatRoomRef.child(chatRoomId).child("userIds").setValue(userIds)
                    }
                }

                // 3. Add to UserInbox
                val inbox = mapOf(
                    "roomId" to chatRoomId,
                    "lastActivityAt" to System.currentTimeMillis(),
                    "isHidden" to false
                )
                userChatRoomsRef.child(userId).child(chatRoomId).updateChildren(inbox).addOnCompleteListener { task2 ->
                    if (task2.isSuccessful) {
                        val actorId = Utils.getUserId()
                        CoroutineScope(Dispatchers.IO).launch {
                            notificationRepository.sendOrUpdateNotification(
                                receiverId = userId,
                                type = "group_added",
                                actorId = actorId,
                                roomId = chatRoomId,
                                title = "Bạn đã được thêm vào nhóm",
                                body = "Bởi một thành viên trong nhóm"
                            )
                        }
                    }
                    onComplete(task2.isSuccessful)
                }
            } else {
                onComplete(false)
            }
        }
    }

    fun removeMemberFromGroup(chatRoomId: String, userId: String, onComplete: (Boolean) -> Unit) {
        // Similar to leaveGroup
        leaveGroup(chatRoomId, userId, onComplete)
    }

    fun updateMemberRole(chatRoomId: String, userId: String, newRole: String, onComplete: (Boolean) -> Unit) {
        roomMembersRef.child(chatRoomId).child(userId).child("role").setValue(newRole).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun transferAdminRole(chatRoomId: String, fromUserId: String, toUserId: String, onComplete: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "$fromUserId/role" to "member",
            "$toUserId/role" to "admin"
        )
        roomMembersRef.child(chatRoomId).updateChildren(updates).addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun markMessageAsRead(chatRoomId: String, messageId: String, userId: String) {
        // Update readBy map in message
        val timestamp = System.currentTimeMillis()
        val chatMessagesRef = db.getReference(Constants.NODE_MESSAGES)
        chatMessagesRef.child(chatRoomId).child(messageId).child("readBy").child(userId).setValue(timestamp)
        // Optionally update isRead=true for 1-1 chat
        chatMessagesRef.child(chatRoomId).child(messageId).child("isRead").setValue(true)
    }

    fun updateTypingStatus(chatRoomId: String, userId: String, isTyping: Boolean) {
        val typingRef = db.getReference(Constants.NODE_ROOM_STATUS).child(chatRoomId).child("typingUsers").child(userId)
        if (isTyping) {
            typingRef.setValue(true)
            typingRef.onDisconnect().removeValue()
        } else {
            typingRef.removeValue()
        }
    }

    fun sendSystemMessage(chatRoomId: String, text: String, systemSubjectId: String? = null, systemAction: String? = null) {
        val message = Message(
            senderId = "system",
            type = "system",
            text = text,
            systemSubjectId = systemSubjectId,
            systemAction = systemAction,
            timestamp = System.currentTimeMillis()
        )
        sendMessage(chatRoomId, message) {}
    }
}