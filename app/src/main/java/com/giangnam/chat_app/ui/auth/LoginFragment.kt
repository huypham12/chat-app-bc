package com.giangnam.chat_app.ui.auth

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.giangnam.chat_app.R
import com.giangnam.chat_app.databinding.FragmentLoginBinding
import com.giangnam.chat_app.ui.main.MainActivity
import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.io.File

class LoginFragment : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private val viewModel: AuthViewModel by activityViewModels()
    private lateinit var audioFile: File
    private var currentPhoneNumber = ""
    private var targetUid = ""
    private var savedVoiceprint = ""
    private var isRegisterMode = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(layoutInflater)
        audioFile = File(requireContext().externalCacheDir?.absolutePath, "voice_login.m4a")

        isRegisterMode = arguments?.getBoolean("isRegisterMode", false) ?: false
        viewModel.setAuthIntent(if (isRegisterMode) AuthIntent.REGISTER else AuthIntent.LOGIN)
        
        updateUI()

        setupListeners()
        observeViewModel()

        return binding.root
    }

    private fun updateUI() {
        if (isRegisterMode) {
            binding.tvLoginTitle.text = "Tạo tài khoản"
            binding.tvSubtitle.text = "Nhập số điện thoại để bắt đầu đăng ký tài khoản mới."
            binding.layoutPhoneInput.visibility = View.VISIBLE
            binding.layoutChallenge.visibility = View.GONE
        } else {
            binding.tvLoginTitle.text = "Đăng nhập"
            binding.tvSubtitle.text = "Nhập số điện thoại đăng ký tài khoản của bạn."
            binding.layoutPhoneInput.visibility = View.VISIBLE
            binding.layoutChallenge.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Tự động phân cụm SĐT khi gõ giúp mắt dễ nhìn
        binding.etNumber.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().replace(Regex("[^0-9]"), "")
                val formatted = StringBuilder()

                for (i in digits.indices) {
                    if (i == 3 || i == 6) {
                        formatted.append(" ")
                    }
                    formatted.append(digits[i])
                }

                s.replace(0, s.length, formatted.toString())
                isFormatting = false
            }
        })

        binding.btnContinue.setOnClickListener {
            val rawInput = binding.etNumber.text.toString()

            if (rawInput.replace(Regex("[^0-9]"), "").length < 9) {
                Toast.makeText(requireContext(), "Số điện thoại không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentPhoneNumber = viewModel.normalizePhone(rawInput)
            viewModel.checkUserExists(currentPhoneNumber)
        }

        binding.btnRecord.setOnTouchListener { v, event ->
            handleVoiceTouch(v, event)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleVoiceTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.performClick()
                v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).start()
                binding.tvStatus.text = "Đang thu âm ĐĂNG NHẬP..."
                binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startRecording(audioFile)
                } else {
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 111)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                binding.tvStatus.text = "Đang xử lý..."
                viewModel.stopRecording()

                val code = binding.tvExpectedCode.text.toString().replace(" ", "")
                Handler(Looper.getMainLooper()).postDelayed({
                    viewModel.verifyVoice(audioFile, currentPhoneNumber, code, savedVoiceprint, targetUid)
                }, 500)
                return true
            }
        }
        return false
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.progressBarLogin.visibility = View.VISIBLE
                    }
                    is AuthState.UserIdentification -> {
                        binding.progressBarLogin.visibility = View.GONE
                        handleUserIdentification(state.exists)
                    }
                    is AuthState.OtpSent -> {
                        binding.progressBarLogin.visibility = View.GONE
                        if (findNavController().currentDestination?.id == R.id.loginFragment) {
                            val bundle = Bundle().apply { putString("number", currentPhoneNumber) }
                            findNavController().navigate(R.id.action_loginFragment_to_OTPFragment, bundle)
                        }
                    }
                    is AuthState.Success -> {
                        binding.progressBarLogin.visibility = View.GONE
                        if (viewModel.userExists.value) {
                            Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            requireActivity().finish()
                        } else {
                            // New user - Let OTPFragment or the process handle navigation to UserDetailFragment
                            android.util.Log.d("LoginFragment", "New user detected, skipping direct MainActivity navigation")
                        }
                    }
                    is AuthState.Error -> {
                        binding.progressBarLogin.visibility = View.GONE
                        binding.tvSubtitle.text = "Xác thực không thành công"
                        Toast.makeText(requireContext(), "Lỗi Firebase: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun handleUserIdentification(exists: Boolean) {
        // Clear previous listeners to avoid behavior overlap
        binding.btnContinue.setOnClickListener(null)
        binding.btnSmsChallenge.setOnClickListener(null)

        if (isRegisterMode) {
            if (exists) {
                // User wants to register but account exists -> Redirect to Login
                Toast.makeText(requireContext(), "Số điện thoại này đã được đăng ký. Vui lòng đăng nhập.", Toast.LENGTH_LONG).show()
                binding.tvSubtitle.text = "Số điện thoại đã có tài khoản."
                binding.btnContinue.text = "Chuyển sang Đăng nhập"
                binding.btnContinue.setOnClickListener {
                    isRegisterMode = false
                    viewModel.setAuthIntent(AuthIntent.LOGIN)
                    updateUI()
                    setupListeners()
                }
            } else {
                // New user - Proceed to OTP
                binding.btnContinue.text = "Tiếp tục"
                binding.btnContinue.setOnClickListener {
                    viewModel.sendOTP(currentPhoneNumber, requireActivity())
                }
                // Auto trigger if user just clicked the original continue
                viewModel.sendOTP(currentPhoneNumber, requireActivity())
            }
        } else {
            // Login Mode
            if (exists) {
                // Existing user - Show Login choices
                val hash = com.giangnam.chat_app.utils.Utils.hashPhoneNumber(currentPhoneNumber)
                FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("registeredPhones")
                    .child(hash).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            targetUid = snapshot.child("uid").value?.toString() ?: ""
                            savedVoiceprint = snapshot.child("voiceprint").value?.toString() ?: ""
                            val hasVoiceprint = snapshot.child("hasVoiceprint").value as? Boolean ?: false

                            binding.tvSubtitle.text = "Chào mừng trở lại!"
                            binding.layoutPhoneInput.visibility = View.GONE
                            binding.layoutChallenge.visibility = View.VISIBLE

                            if (!hasVoiceprint || savedVoiceprint.isEmpty()) {
                                binding.btnVoiceChallenge.visibility = View.GONE
                            }
                        }
                    }
                
                binding.btnSmsChallenge.setOnClickListener {
                    viewModel.sendOTP(currentPhoneNumber, requireActivity())
                }
            } else {
                // Login attempted but user does not exist -> Redirect to Register
                Toast.makeText(requireContext(), "Số điện thoại chưa được đăng ký.", Toast.LENGTH_LONG).show()
                binding.tvSubtitle.text = "Tài khoản không tồn tại."
                binding.btnContinue.text = "Chuyển sang Đăng ký"
                binding.btnContinue.setOnClickListener {
                    isRegisterMode = true
                    viewModel.setAuthIntent(AuthIntent.REGISTER)
                    updateUI()
                    setupListeners()
                }
            }
        }

        binding.btnVoiceChallenge.setOnClickListener {
            binding.layoutVoiceInput.visibility = View.VISIBLE
            binding.btnSmsChallenge.visibility = View.GONE
            binding.btnVoiceChallenge.visibility = View.GONE
            
            val randomCode = (100000..999999).random().toString()
            binding.tvExpectedCode.text = randomCode.substring(0, 3) + " " + randomCode.substring(3, 6)
        }
    }
}