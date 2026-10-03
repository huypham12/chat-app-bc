package com.giangnam.chat_app.data.repository

import android.app.Activity
import android.util.Log
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthRepository private constructor() {

    private val auth: FirebaseAuth = Utils.getFirebaseAuthInstance()
    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private val client = OkHttpClient()

    companion object {
        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
    }

    fun normalizePhone(phoneNumber: String): String {
        return com.giangnam.chat_app.utils.PhoneUtils.normalize(phoneNumber)
    }

    fun checkUserExists(phoneNumber: String, onResult: (Boolean) -> Unit) {
        val normalized = normalizePhone(phoneNumber)
        val hash = Utils.hashPhoneNumber(normalized)

        db.getReference("registeredPhones").child(hash).get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.exists())
            }
            .addOnFailureListener { e ->
                Log.e("AuthRepository", "checkUserExists failed", e)
                onResult(false)
            }
    }

    fun registerPhoneNumber(phoneNumber: String, uid: String, voiceprint: String, onComplete: (Boolean) -> Unit) {
        val normalized = normalizePhone(phoneNumber)
        val hash = Utils.hashPhoneNumber(normalized)
        
        val data = mapOf(
            "uid" to uid,
            "hasVoiceprint" to voiceprint.isNotEmpty(),
            "voiceprint" to voiceprint
        )

        db.getReference("registeredPhones").child(hash).setValue(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e("AuthRepository", "registerPhoneNumber failed", e)
                onComplete(false) 
            }
    }

    fun checkUserExistsByUid(uid: String, onResult: (Boolean) -> Unit) {
        db.getReference(Constants.NODE_USERS).child(uid).get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.exists())
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun sendOTP(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        // auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)

        val formattedPhoneNumber = normalizePhone(phoneNumber)

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyVoice(file: File, phoneNumber: String, expectedCode: String, savedVoiceprint: String, uid: String, onResult: (Boolean, String?, String?) -> Unit) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("expected_code", expectedCode)
            .addFormDataPart("saved_voiceprint_json", savedVoiceprint)
            .addFormDataPart("uid", uid)
            .addFormDataPart("audio", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder().url(Constants.VERIFY_VOICE_URL).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, null, "Lỗi kết nối Server")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() ?: ""
                try {
                    val jsonObject = JSONObject(responseData)
                    if (response.isSuccessful && jsonObject.getString("status") == "success") {
                        val customToken = jsonObject.getString("custom_token")
                        onResult(true, customToken, null)
                    } else {
                        onResult(false, null, jsonObject.optString("reason", "Xác thực giọng nói thất bại"))
                    }
                } catch (e: Exception) {
                    onResult(false, null, "Lỗi xử lý dữ liệu AI")
                }
            }
        })
    }

    fun enrollVoice(file: File, phoneNumber: String, onResult: (Boolean, String?, String?) -> Unit) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder().url(Constants.ENROLL_VOICE_URL).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, null, "Lỗi kết nối Server")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() ?: ""
                try {
                    val jsonObject = JSONObject(responseData)
                    if (response.isSuccessful && jsonObject.getString("status") == "success") {
                        val voiceprint = jsonObject.getJSONArray("voiceprint").toString()
                        onResult(true, voiceprint, null)
                    } else {
                        onResult(false, null, jsonObject.optString("reason", "Không thể trích xuất giọng nói"))
                    }
                } catch (e: Exception) {
                    onResult(false, null, "Lỗi xử lý dữ liệu AI")
                }
            }
        })
    }

    fun signInWithCredential(credential: PhoneAuthCredential, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            onComplete(task.isSuccessful, task.exception?.message)
        }
    }

    fun signInWithCustomToken(token: String, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithCustomToken(token).addOnCompleteListener { task ->
            onComplete(task.isSuccessful, task.exception?.message)
        }
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null
}