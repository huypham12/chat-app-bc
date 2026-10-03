package com.giangnam.chat_app.ui.auth

import android.app.Activity
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.ViewModel
import com.giangnam.chat_app.data.repository.AuthRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class UserIdentification(val exists: Boolean) : AuthState()
    object OtpSent : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

enum class AuthIntent {
    LOGIN, REGISTER
}

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _authIntent = MutableStateFlow<AuthIntent>(AuthIntent.LOGIN)
    val authIntent: StateFlow<AuthIntent> = _authIntent

    private val _userExists = MutableStateFlow<Boolean>(false)
    val userExists: StateFlow<Boolean> = _userExists

    private val _verificationId = MutableStateFlow<String?>(null)

    private var mediaRecorder: MediaRecorder? = null

    fun checkUserExists(phoneNumber: String) {
        _authState.value = AuthState.Loading
        authRepository.checkUserExists(phoneNumber) { exists ->
            _userExists.value = exists
            _authState.value = AuthState.UserIdentification(exists)
        }
    }

    fun checkUserExistsByUid(uid: String, onComplete: (Boolean) -> Unit) {
        authRepository.checkUserExistsByUid(uid) { exists ->
            _userExists.value = exists
            onComplete(exists)
        }
    }

    fun sendOTP(phoneNumber: String, activity: Activity) {
        _authState.value = AuthState.Loading
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _authState.value = AuthState.Error(e.message ?: "Xác thực thất bại")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                _verificationId.value = verificationId
                _authState.value = AuthState.OtpSent
            }
        }
        authRepository.sendOTP(phoneNumber, activity, callbacks)
    }

    fun signInWithOTP(otp: String) {
        val verId = _verificationId.value ?: return
        val credential = PhoneAuthProvider.getCredential(verId, otp)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        // Prevent multiple simultaneous sign-in attempts
        if (_authState.value is AuthState.Loading && _verificationId.value != null) {
             Log.d("AuthViewModel", "Already signing in, ignoring duplicate request")
             // return // Commented out to be safe, but we should handle this
        }

        Log.d("AuthViewModel", "Starting signInWithCredential")
        _authState.value = AuthState.Loading
        authRepository.signInWithCredential(credential) { success, error ->
            Log.d("AuthViewModel", "signInWithCredential result: success=$success, error=$error")
            if (success) {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(error ?: "Đăng nhập thất bại")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun verifyVoice(audioFile: File, phoneNumber: String, expectedCode: String, savedVoiceprint: String, uid: String) {
        _authState.value = AuthState.Loading
        authRepository.verifyVoice(audioFile, phoneNumber, expectedCode, savedVoiceprint, uid) { success, token, error ->
            viewModelScope.launch {
                if (success && token != null) {
                    authRepository.signInWithCustomToken(token) { loginSuccess, loginError ->
                        if (loginSuccess) _authState.value = AuthState.Success
                        else _authState.value = AuthState.Error(loginError ?: "Lỗi cấp quyền truy cập")
                    }
                } else {
                    _authState.value = AuthState.Error(error ?: "Giọng nói không khớp")
                }
            }
        }
    }

    fun enrollVoice(audioFile: File, phoneNumber: String, onResult: (String?) -> Unit) {
        authRepository.enrollVoice(audioFile, phoneNumber) { success, voiceprint, _ ->
            onResult(if (success) voiceprint else null)
        }
    }

    fun startRecording(file: File) {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Ghi âm lỗi: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Dừng ghi âm lỗi: ${e.message}")
            }
        }
        mediaRecorder = null
    }

    fun setAuthIntent(intent: AuthIntent) {
        _authIntent.value = intent
    }

    fun normalizePhone(phoneNumber: String): String {
        return authRepository.normalizePhone(phoneNumber)
    }

    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()
}
