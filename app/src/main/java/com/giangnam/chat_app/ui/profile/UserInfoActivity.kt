package com.giangnam.chat_app.ui.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.giangnam.chat_app.databinding.ActivityUserInfoBinding
import kotlinx.coroutines.launch

class UserInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserInfoBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Nhận ID của người dùng từ ChatActivity truyền sang
        val userId = intent.getStringExtra("userId") ?: return

        // Nút Back: Đóng màn hình này để quay lại Chat
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Tải thông tin người dùng từ Firebase
        loadUserInfo(userId)
        observeViewModel()
        setupSocialActions(userId)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.userProfile.collect { user ->
                user?.let {
                    binding.infoName.text = it.username
                    binding.infoPhone.text = it.phoneNumber

                    if (it.avatarUrl?.isNotEmpty() == true) {
                        Glide.with(this@UserInfoActivity)
                            .load(it.avatarUrl)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(binding.infoAvatar)
                    }
                    
                    if (it.coverUrl?.isNotEmpty() == true) {
                        Glide.with(this@UserInfoActivity)
                            .load(it.coverUrl)
                            .placeholder(R.drawable.bg_search_bar)
                            .centerCrop()
                            .into(binding.profileCover)
                    }

                    // Open Chat when clicking message button
                    binding.btnMessage.setOnClickListener { _ ->
                        val intent = android.content.Intent(this@UserInfoActivity, com.giangnam.chat_app.ui.chat.ChatActivity::class.java).apply {
                            putExtra("userName", it.username)
                            putExtra("userId", it.uid)
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun loadUserInfo(userId: String) {
        viewModel.fetchUserProfile(userId)
    }

    private fun setupSocialActions(targetUserId: String) {
        val myUid = com.giangnam.chat_app.utils.Utils.getUserId()
        if (myUid == targetUserId) {
            binding.btnFriendAction.visibility = android.view.View.GONE
            binding.btnBlockUser.visibility = android.view.View.GONE
            binding.btnMessage.visibility = android.view.View.GONE
            return
        }

        viewModel.listenToRelationshipStatus(myUid, targetUserId)
        viewModel.checkBlockStatus(myUid, targetUserId)

        // Observe Friend Status
        lifecycleScope.launch {
            viewModel.friendStatus.collect { status ->
                when (status) {
                    com.giangnam.chat_app.data.repository.FriendStatus.NONE -> {
                        binding.btnFriendAction.text = "Thêm bạn bè"
                        binding.btnFriendAction.setIconResource(R.drawable.ic_person)
                        binding.btnFriendAction.setOnClickListener {
                            viewModel.sendFriendRequest(myUid, targetUserId)
                        }
                    }
                    com.giangnam.chat_app.data.repository.FriendStatus.REQUEST_SENT -> {
                        binding.btnFriendAction.text = "Hủy lời mời"
                        binding.btnFriendAction.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
                        binding.btnFriendAction.setOnClickListener {
                            viewModel.rejectOrCancelFriendRequest(myUid, targetUserId)
                        }
                    }
                    com.giangnam.chat_app.data.repository.FriendStatus.REQUEST_RECEIVED -> {
                        binding.btnFriendAction.text = "Chấp nhận kết bạn"
                        binding.btnFriendAction.setIconResource(android.R.drawable.ic_menu_add)
                        binding.btnFriendAction.setOnClickListener {
                            viewModel.acceptFriendRequest(myUid, targetUserId)
                        }
                    }
                    com.giangnam.chat_app.data.repository.FriendStatus.FRIEND -> {
                        binding.btnFriendAction.text = "Bạn bè"
                        binding.btnFriendAction.setIconResource(android.R.drawable.checkbox_on_background)
                        binding.btnFriendAction.setOnClickListener {
                            // Dialog confirm unfriend
                            androidx.appcompat.app.AlertDialog.Builder(this@UserInfoActivity)
                                .setTitle("Hủy kết bạn")
                                .setMessage("Bạn có chắc chắn muốn hủy kết bạn không?")
                                .setPositiveButton("Hủy kết bạn") { _, _ ->
                                    viewModel.unfriend(myUid, targetUserId)
                                }
                                .setNegativeButton("Đóng", null)
                                .show()
                        }
                    }
                }
            }
        }

        // Observe Block Status
        lifecycleScope.launch {
            viewModel.blockStatus.collect { (amIBlocked, didIBlock) ->
                if (didIBlock) {
                    binding.tvBlockText.text = "Bỏ chặn người dùng"
                    binding.tvBlockText.setTextColor(android.graphics.Color.parseColor("#4B5563"))
                    binding.iconBlock.setColorFilter(android.graphics.Color.parseColor("#4B5563"))
                    binding.btnBlockUser.setOnClickListener {
                        viewModel.unblockUser(myUid, targetUserId)
                    }
                    // Hide friend button if blocked
                    binding.btnFriendAction.visibility = android.view.View.GONE
                } else {
                    binding.tvBlockText.text = "Chặn người dùng"
                    binding.tvBlockText.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                    binding.iconBlock.setColorFilter(android.graphics.Color.parseColor("#EF4444"))
                    binding.btnBlockUser.setOnClickListener {
                        androidx.appcompat.app.AlertDialog.Builder(this@UserInfoActivity)
                            .setTitle("Chặn người dùng")
                            .setMessage("Người này sẽ không thể nhắn tin hay xem hồ sơ của bạn. Lịch sử trò chuyện vẫn được giữ nguyên.")
                            .setPositiveButton("Chặn") { _, _ ->
                                viewModel.blockUser(myUid, targetUserId)
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                    if (myUid != targetUserId) {
                        binding.btnFriendAction.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }
    }

}