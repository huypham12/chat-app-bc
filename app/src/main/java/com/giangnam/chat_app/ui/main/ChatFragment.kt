package com.giangnam.chat_app.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.databinding.FragmentChatBinding
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    lateinit var binding: FragmentChatBinding
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: RecentChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatBinding.inflate(layoutInflater)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            setupRecyclerView()
            setupObservers()
            setupSearch()
            viewModel.fetchAllUserNames()
            var dX = 0f
            var dY = 0f
            var startX = 0f
            var startY = 0f
            var isDragging = false
            val clickThreshold = 10f * resources.displayMetrics.density

            binding.fabAiChat.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        dX = view.x - event.rawX
                        dY = view.y - event.rawY
                        startX = event.rawX
                        startY = event.rawY
                        isDragging = false
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(event.rawX - startX) > clickThreshold || Math.abs(event.rawY - startY) > clickThreshold) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val parent = view.parent as ViewGroup
                            var newY = event.rawY + dY
                            var newX = event.rawX + dX
                            newY = Math.max(0f, Math.min(newY, (parent.height - view.height).toFloat()))
                            newX = Math.max(0f, Math.min(newX, (parent.width - view.width).toFloat()))
                            view.x = newX
                            view.y = newY
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            val intent = android.content.Intent(requireContext(), com.giangnam.chat_app.ui.chat.ChatActivity::class.java)
                            intent.putExtra("userId", "AI_BOT_001")
                            intent.putExtra("userName", "Trợ lý AI")
                            startActivity(intent)
                        } else {
                            val parent = view.parent as ViewGroup
                            val paddingPx = 16f * resources.displayMetrics.density
                            val screenWidth = parent.width
                            val edgeX = if (view.x + view.width / 2 > screenWidth / 2) {
                                screenWidth.toFloat() - view.width.toFloat() - paddingPx
                            } else {
                                paddingPx
                            }
                            view.animate()
                                .x(edgeX)
                                .setInterpolator(android.view.animation.OvershootInterpolator())
                                .setDuration(300)
                                .start()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = RecentChatAdapter(emptyMap()) { item ->
            val isCurrentlyMuted = item.inbox.isMuted || (item.inbox.mutedUntil == -1L || item.inbox.mutedUntil > System.currentTimeMillis())
            
            val bottomSheet = ConversationActionBottomSheet(
                isPinned = item.inbox.isPinned,
                isMuted = isCurrentlyMuted
            ) { action ->
                val currentUserId = Utils.getUserId()
                if (currentUserId.isEmpty()) return@ConversationActionBottomSheet
                
                when (action) {
                    ConversationActionBottomSheet.Action.PIN_UNPIN -> {
                        val newValue = !item.inbox.isPinned
                        ChatRepository.getInstance().updateInboxProperty(currentUserId, item.room.roomId, "isPinned", newValue)
                        Toast.makeText(context, if(newValue) "Đã ghim" else "Đã bỏ ghim", Toast.LENGTH_SHORT).show()
                    }
                    ConversationActionBottomSheet.Action.MUTE_UNMUTE -> {
                        if (!isCurrentlyMuted) {
                            showMuteDurationDialog(currentUserId, item.room.roomId)
                        } else {
                            ChatRepository.getInstance().updateInboxProperty(currentUserId, item.room.roomId, "isMuted", false)
                            ChatRepository.getInstance().updateInboxProperty(currentUserId, item.room.roomId, "mutedUntil", 0L)
                            Toast.makeText(context, "Đã bật thông báo", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ConversationActionBottomSheet.Action.DELETE -> {
                        ChatRepository.getInstance().deleteConversation(currentUserId, item.room.roomId)
                        Toast.makeText(context, "Đã xóa hội thoại", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            bottomSheet.show(childFragmentManager, "ConversationAction")
        }

        binding.recentChats.layoutManager = LinearLayoutManager(context)
        binding.recentChats.adapter = adapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.allUserNames.collect { nameMap ->
                if (nameMap.isNotEmpty()) {
                    // Update adapter with user names, we need to create a new adapter instance
                    // or add a method to update the map, but passing a new instance is tricky.
                    // For now, we will recreate adapter or since Kotlin doesn't allow changing val, we can 
                    // just let it be handled by passing it once. But since allUserNames loads asynchronously,
                    // we should recreate adapter.
                    val oldList = adapter.currentList
                    adapter = RecentChatAdapter(nameMap, adapter.onLongClick)
                    binding.recentChats.adapter = adapter
                    adapter.submitList(oldList)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.recentChats.collect { list ->
                updateList(list)
            }
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateList(viewModel.recentChats.value)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showMuteDurationDialog(userId: String, roomId: String) {
        val options = arrayOf("15 phút", "1 giờ", "8 giờ", "24 giờ", "Cho đến khi tôi bật lại")
        var selectedIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle("Tắt thông báo")
            .setSingleChoiceItems(options, 0) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                val duration = when (selectedIndex) {
                    0 -> 15 * 60 * 1000L // 15 mins
                    1 -> 60 * 60 * 1000L // 1 hour
                    2 -> 8 * 60 * 60 * 1000L // 8 hours
                    3 -> 24 * 60 * 60 * 1000L // 24 hours
                    else -> -1L // Until turned back on
                }
                
                val mutedUntil = if (duration == -1L) -1L else System.currentTimeMillis() + duration
                ChatRepository.getInstance().updateInboxProperty(userId, roomId, "isMuted", true)
                ChatRepository.getInstance().updateInboxProperty(userId, roomId, "mutedUntil", mutedUntil)
                Toast.makeText(context, "Đã tắt thông báo", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updateList(originalList: List<RecentChatItem>) {
        val searchQuery = binding.searchEditText.text.toString().trim().lowercase()
        val currentUserId = Utils.getUserId()
        
        val filteredList = originalList.filter { item ->
            // Bỏ qua các hội thoại bị ẩn
            if (item.inbox.isHidden && searchQuery.isEmpty()) return@filter false
            
            if (searchQuery.isEmpty()) return@filter true
            
            if (item.room.type == "group") {
                val groupName = item.room.name?.lowercase() ?: ""
                groupName.contains(searchQuery)
            } else {
                // Tìm kiếm theo tên 1-1
                val otherUserId = item.room.userIds.firstOrNull { id -> id != currentUserId } ?: currentUserId
                val userName = viewModel.allUserNames.value[otherUserId]?.lowercase() ?: ""
                userName.contains(searchQuery)
            }
        }

        if (filteredList.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recentChats.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recentChats.visibility = View.VISIBLE
            adapter.submitList(filteredList)
        }
    }
}