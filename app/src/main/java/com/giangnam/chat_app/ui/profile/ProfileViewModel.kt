package com.giangnam.chat_app.ui.profile

import androidx.lifecycle.ViewModel
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository.getInstance()
    private val chatRepository = ChatRepository.getInstance()

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile

    fun fetchUserProfile(userId: String) {
        userRepository.getUserById(userId) { user ->
            _userProfile.value = user
        }
    }

    fun updateUserProfile(user: User, onComplete: (Boolean) -> Unit) {
        userRepository.updateUser(user) { success ->
            if (success) {
                // Instant update: update the local state flow immediately
                _userProfile.value = user
            }
            onComplete(success)
        }
    }

    fun uploadProfileImage(base64Image: String, onComplete: (String?) -> Unit) {
        chatRepository.uploadImageToImgBB(base64Image, onComplete)
    }

    fun checkUsernameAvailability(username: String, currentUid: String, onComplete: (Boolean) -> Unit) {
        userRepository.isUsernameAvailable(username, currentUid, onComplete)
    }

    private val authRepository = com.giangnam.chat_app.data.repository.AuthRepository.getInstance()
    private var mediaRecorder: android.media.MediaRecorder? = null

    fun enrollVoice(audioFile: java.io.File, phoneNumber: String, onResult: (String?) -> Unit) {
        authRepository.enrollVoice(audioFile, phoneNumber) { success, voiceprint, _ ->
            onResult(if (success) voiceprint else null)
        }
    }

    fun startRecording(file: java.io.File) {
        mediaRecorder = android.media.MediaRecorder().apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Ghi âm lỗi: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Dừng ghi âm lỗi: ${e.message}")
            }
        }
        mediaRecorder = null
    }

    // ==========================================
    // SOCIAL ACTIONS
    // ==========================================
    private val socialRepository = com.giangnam.chat_app.data.repository.SocialRepository.getInstance()

    private val _friendStatus = MutableStateFlow(com.giangnam.chat_app.data.repository.FriendStatus.NONE)
    val friendStatus: StateFlow<com.giangnam.chat_app.data.repository.FriendStatus> = _friendStatus

    private val _blockStatus = MutableStateFlow<Pair<Boolean, Boolean>>(Pair(false, false)) // (amIBlocked, didIBlock)
    val blockStatus: StateFlow<Pair<Boolean, Boolean>> = _blockStatus

    fun listenToRelationshipStatus(myUid: String, targetUid: String) {
        socialRepository.listenToRelationshipStatus(myUid, targetUid) { status ->
            _friendStatus.value = status
        }
    }

    fun checkBlockStatus(myUid: String, targetUid: String) {
        socialRepository.checkBlockStatus(myUid, targetUid) { amIBlocked, didIBlock ->
            _blockStatus.value = Pair(amIBlocked, didIBlock)
        }
    }

    fun sendFriendRequest(myUid: String, targetUid: String) = socialRepository.sendFriendRequest(myUid, targetUid) {}
    fun acceptFriendRequest(myUid: String, targetUid: String) = socialRepository.acceptFriendRequest(myUid, targetUid) {}
    fun rejectOrCancelFriendRequest(myUid: String, targetUid: String) = socialRepository.rejectOrCancelFriendRequest(myUid, targetUid) {}
    fun unfriend(myUid: String, targetUid: String) = socialRepository.unfriend(myUid, targetUid) {}

    fun blockUser(myUid: String, targetUid: String) {
        socialRepository.blockUser(myUid, targetUid) {
            checkBlockStatus(myUid, targetUid)
        }
    }

    fun unblockUser(myUid: String, targetUid: String) {
        socialRepository.unblockUser(myUid, targetUid) {
            checkBlockStatus(myUid, targetUid)
        }
    }
}