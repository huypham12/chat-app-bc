package com.giangnam.chat_app.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.giangnam.chat_app.R
import com.giangnam.chat_app.databinding.FragmentOTPBinding
import kotlinx.coroutines.launch
import java.util.Locale

class OTPFragment : Fragment() {

    private lateinit var binding: FragmentOTPBinding
    private val viewModel: AuthViewModel by activityViewModels()
    private var countDownTimer: CountDownTimer? = null

    private var number: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOTPBinding.inflate(layoutInflater)
        getUserNumber()
        
        // Start Timer
        startTimer()
        
        // Note: Do NOT call sendOTP here if it was already initiated by LoginFragment
        // Unless we are coming back from a failure or user manually clicked resend

        setupListeners()
        observeViewModel()

        return binding.root
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigate(R.id.action_OTPFragment_to_loginFragment)
        }

        binding.btnContinue.setOnClickListener {
            val otp = binding.etOtp.text.toString()
            if (otp.length != 6) {
                Toast.makeText(requireContext(), "Vui lòng nhập đúng 6 số OTP", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.signInWithOTP(otp)
            }
        }
        
        binding.tvResend.setOnClickListener {
            if (binding.tvResend.isEnabled) {
                sendOTP()
                startTimer()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is AuthState.OtpSent -> {
                        binding.progressBar.visibility = View.GONE
                        // Removed Toast since it might be confusing if user uses hardcoded test numbers
                    }
                    is AuthState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        
                        val uid = com.giangnam.chat_app.utils.Utils.getUserId()
                        if (uid.isNotEmpty()) {
                            binding.progressBar.visibility = View.VISIBLE
                            viewModel.checkUserExistsByUid(uid) { exists ->
                                binding.progressBar.visibility = View.GONE
                                
                                when (viewModel.authIntent.value) {
                                    AuthIntent.LOGIN -> {
                                        if (exists) {
                                            navigateToMain()
                                        } else {
                                            // Safety: Should not happen if Login was successful but profile missing
                                            Toast.makeText(requireContext(), "Không tìm thấy hồ sơ. Vui lòng hoàn tất đăng ký.", Toast.LENGTH_SHORT).show()
                                            navigateToUserDetail()
                                        }
                                    }
                                    AuthIntent.REGISTER -> {
                                        if (exists) {
                                            // Safety: Already registered? Just go to Main
                                            navigateToMain()
                                        } else {
                                            navigateToUserDetail()
                                        }
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "Lỗi định danh user", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun navigateToMain() {
        viewModel.resetState()
        val intent = android.content.Intent(requireContext(), com.giangnam.chat_app.ui.main.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    private fun navigateToUserDetail() {
        val bundle = Bundle().apply { putString("number", number) }
        viewModel.resetState()
        findNavController().navigate(R.id.action_OTPFragment_to_userDetail, bundle)
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        binding.tvResend.isEnabled = false
        binding.tvResend.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvTimer.text = String.format(Locale.getDefault(), "00:%02d", seconds)
            }

            override fun onFinish() {
                binding.tvTimer.text = "00:00"
                binding.tvResend.isEnabled = true
                binding.tvResend.setTextColor(resources.getColor(R.color.primary, null))
            }
        }.start()
    }

    private fun sendOTP() {
        if (number.isNotEmpty()) {
            viewModel.sendOTP(number, requireActivity())
        }
    }

    private fun getUserNumber() {
        number = arguments?.getString("number") ?: ""
        binding.number.text = "+84 $number"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }
}