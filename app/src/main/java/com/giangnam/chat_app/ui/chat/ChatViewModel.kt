package com.giangnam.chat_app.ui.chat

import androidx.lifecycle.ViewModel
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class ChatViewModel : ViewModel() {

    private val chatRepository = ChatRepository.getInstance()
    private val userRepository = UserRepository.getInstance()

    private val _otherUserStatus = MutableStateFlow<Pair<Boolean, Long>>(Pair(false, 0L))
    val otherUserStatus: StateFlow<Pair<Boolean, Long>> = _otherUserStatus

    private val _smartReplies = MutableStateFlow<List<String>>(emptyList())
    val smartReplies: StateFlow<List<String>> = _smartReplies

    fun sendMessage(chatRoomId: String, message: Message, isAiBot: Boolean = false) {
        chatRepository.sendMessage(chatRoomId, message) { success ->
            // Handle success/failure if needed
        }
    }

    fun uploadImage(base64Image: String, onComplete: (String?) -> Unit) {
        chatRepository.uploadImageToImgBB(base64Image, onComplete)
    }

    fun uploadAudio(file: File, onComplete: (String?) -> Unit) {
        chatRepository.uploadAudioToCloudinary(file, onComplete)
    }

    fun getGeminiReply(userText: String, onComplete: (String?) -> Unit) {
        chatRepository.getGeminiReply(userText, onComplete)
    }

    fun getSmartReplies(incomingMessage: String) {
        chatRepository.getSmartReplies(incomingMessage) { replies ->
            _smartReplies.value = replies ?: emptyList()
        }
    }

    fun listenToUserStatus(userId: String) {
        userRepository.listenToUserStatus(userId) { isOnline, lastSeen ->
            _otherUserStatus.value = Pair(isOnline, lastSeen)
        }
    }



    fun sendNotification(user2Id: String, messageText: String) {
        chatRepository.sendPushNotification(user2Id, messageText)
    }

    fun revokeMessage(chatRoomId: String, messageId: String) {
        chatRepository.revokeMessage(chatRoomId, messageId) {}
    }

    fun deleteMessageForMe(chatRoomId: String, messageId: String, userId: String) {
        chatRepository.deleteMessageForMe(chatRoomId, messageId, userId) {}
    }

    fun editMessage(chatRoomId: String, messageId: String, newText: String) {
        chatRepository.editMessage(chatRoomId, messageId, newText) {}
    }

    fun addReaction(chatRoomId: String, messageId: String, userId: String, reaction: String) {
        chatRepository.addReaction(chatRoomId, messageId, userId, reaction)
    }

    fun removeReaction(chatRoomId: String, messageId: String, userId: String) {
        chatRepository.removeReaction(chatRoomId, messageId, userId)
    }

    fun updateGroupInfo(chatRoomId: String, newName: String?, newAvatarUrl: String?) {
        chatRepository.updateGroupInfo(chatRoomId, newName, newAvatarUrl) {}
    }

    fun leaveGroup(chatRoomId: String, userId: String) {
        chatRepository.leaveGroup(chatRoomId, userId) {}
    }
}