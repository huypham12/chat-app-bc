package com.giangnam.chat_app.ui.common

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.giangnam.chat_app.R
import com.giangnam.chat_app.databinding.FragmentSplashBinding
import com.giangnam.chat_app.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth

class SplashFragment : Fragment() {
    private lateinit var binding: FragmentSplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private var splashRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSplashBinding.inflate(layoutInflater, container, false)

        splashRunnable = Runnable {
            if (!isAdded) return@Runnable
            val currentUser = FirebaseAuth.getInstance().currentUser

            if (currentUser != null) {
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            } else {
                try {
                    findNavController().navigate(R.id.action_splashFragment_to_welcomeFragment)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        splashRunnable?.let { handler.postDelayed(it, 1500) }

        return binding.root
    }

    override fun onDestroyView() {
        splashRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}
