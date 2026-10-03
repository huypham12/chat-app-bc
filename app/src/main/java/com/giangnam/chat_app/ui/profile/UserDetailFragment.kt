package com.giangnam.chat_app.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.databinding.FragmentUserDetailBinding
import com.giangnam.chat_app.ui.auth.AuthViewModel
import com.giangnam.chat_app.ui.main.MainActivity
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.FirebaseDatabase
import java.io.File

class UserDetailFragment : Fragment() {

    private lateinit var binding: FragmentUserDetailBinding
    private val viewModel: AuthViewModel by viewModels()
    private var username = ""
    private var userNumber = ""
    private var userId = ""
    private lateinit var audioFile: File

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserDetailBinding.inflate(layoutInflater)
        audioFile = File(requireContext().cacheDir, "voice_enroll.wav")
        
        val rawNumber = arguments?.getString("number") ?: ""
        userNumber = viewModel.normalizePhone(rawNumber)
        userId = Utils.getUserId()

        setupListeners()
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.btnContinueName.setOnClickListener {
            username = binding.etUsername.text.toString().trim()
            if (username.isEmpty()) {
                binding.etUsername.error = "Vui lòng nhập tên"
                return@setOnClickListener
            }
            // Transition to Voice Enroll
            binding.layoutNameInput.visibility = View.GONE
            binding.layoutVoiceEnroll.visibility = View.VISIBLE
        }

        binding.btnRecordEnroll.setOnTouchListener { v, event ->
            handleEnrollTouch(v, event)
        }

        binding.btnSkipVoice.setOnClickListener {
            saveUserToFirebase("")
        }
    }

    private fun handleEnrollTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.performClick()
                binding.tvEnrollStatus.text = "Đang thu âm..."
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startRecording(audioFile)
                } else {
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 111)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                binding.tvEnrollStatus.text = "Đang trích xuất vector AI..."
                viewModel.stopRecording()
                viewModel.enrollVoice(audioFile, userNumber) { voiceprint ->
                    activity?.runOnUiThread {
                        if (voiceprint != null) {
                            Toast.makeText(requireContext(), "Đăng ký giọng nói thành công!", Toast.LENGTH_SHORT).show()
                            saveUserToFirebase(voiceprint)
                        } else {
                            binding.tvEnrollStatus.text = "Lỗi AI, hãy thử lại hoặc bỏ qua"
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private fun saveUserToFirebase(voiceprint: String) {
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "Lỗi: Không tìm thấy ID người dùng!", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnSkipVoice.isEnabled = false

        // Final safety check: Does this user already have a profile?
        FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS)
            .child(userId).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // Safety triggered: User already exists, don't overwrite!
                    Toast.makeText(requireContext(), "Tài khoản đã tồn tại, đang chuyển hướng...", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    // Truly a new user, proceed with creation
                    val user = User(
                        uid = userId,
                        phoneNumber = userNumber,
                        username = username,
                        displayName = username,
                        voiceprint = voiceprint,
                        createdAt = System.currentTimeMillis()
                    )

                    FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS)
                        .child(userId)
                        .setValue(user)
                        .addOnSuccessListener {
                            com.giangnam.chat_app.data.repository.AuthRepository.getInstance()
                                .registerPhoneNumber(userNumber, userId, voiceprint) {
                                    activity?.runOnUiThread {
                                        navigateToMain()
                                    }
                                }
                        }
                        .addOnFailureListener {
                            activity?.runOnUiThread {
                                binding.btnSkipVoice.isEnabled = true
                                Toast.makeText(requireContext(), "Lỗi lưu thông tin: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }.addOnFailureListener {
                activity?.runOnUiThread {
                    binding.btnSkipVoice.isEnabled = true
                    Toast.makeText(requireContext(), "Lỗi kiểm tra dữ liệu: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMain() {
        viewModel.resetState()
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }
}