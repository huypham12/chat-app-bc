package com.giangnam.chat_app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.giangnam.chat_app.R
import com.giangnam.chat_app.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)

        binding.btnLogin.setOnClickListener {
            val bundle = Bundle().apply { putBoolean("isRegisterMode", false) }
            findNavController().navigate(R.id.action_welcomeFragment_to_loginFragment, bundle)
        }

        binding.btnRegister.setOnClickListener {
            val bundle = Bundle().apply { putBoolean("isRegisterMode", true) }
            findNavController().navigate(R.id.action_welcomeFragment_to_loginFragment, bundle)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}