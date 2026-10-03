package com.giangnam.chat_app.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.giangnam.chat_app.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModels()
    private val mainViewModel: com.giangnam.chat_app.ui.main.MainViewModel by activityViewModels()
    private lateinit var adapter: NotificationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
        
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(mainViewModel.allUsers.value) { notification ->
            if (!notification.isRead) {
                viewModel.markAsRead(notification.notificationId)
            }
            
            // Navigate based on notification type
            when (notification.type) {
                "mention", "group_added", "friend_request_accepted" -> {
                    val intent = android.content.Intent(requireContext(), com.giangnam.chat_app.ui.chat.ChatActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        if (notification.type == "friend_request_accepted") {
                            putExtra("userId", notification.actorId)
                        } else {
                            putExtra("roomId", notification.roomId)
                        }
                    }
                    startActivity(intent)
                }
                "friend_request", "follow" -> {
                    val bundle = android.os.Bundle().apply {
                        putString("userId", notification.actorId)
                    }
                    androidx.navigation.fragment.NavHostFragment.findNavController(this@NotificationsFragment)
                        .navigate(com.giangnam.chat_app.R.id.profileFragment, bundle)
                }
            }
        }
        binding.rvNotifications.adapter = adapter
    }

    private fun observeViewModel() {
        binding.tvMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        // Observe users to update adapter when users load
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.allUsers.collect { users ->
                adapter.updateUsersMap(users)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notifications.collect { list ->
                adapter.submitList(list)
                val unreadCount = list.count { !it.isRead }
                binding.tvHeader.text = if (unreadCount > 0) "Thông báo ($unreadCount)" else "Thông báo"
                
                if (list.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvNotifications.visibility = View.VISIBLE
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
