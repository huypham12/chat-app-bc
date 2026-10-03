package com.giangnam.chat_app.ui.chat

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.giangnam.chat_app.R
import com.giangnam.chat_app.BuildConfig
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import androidx.activity.viewModels
import com.giangnam.chat_app.ui.profile.UserInfoActivity
import com.giangnam.chat_app.databinding.ActivityChatBinding
import com.giangnam.chat_app.data.model.ChatRoom
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.data.model.ReplyPreview
import com.google.firebase.database.FirebaseDatabase
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.File
import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import java.util.Collections


class ChatActivity : AppCompatActivity() {

    lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    var chatRoomId: String = ""
    var user2Id: String = ""
    var adapter: ChatAdapter? = null

    private var smartReplyAdapter: SmartReplyAdapter? = null
    private val smartRepliesList = ArrayList<String>()

    private var editingMessage: Message? = null
    
    private var mentionAdapter: MentionAdapter? = null
    private val groupMembers = mutableListOf<com.giangnam.chat_app.data.model.User>()
    private val currentMentions = mutableMapOf<String, String>() // uid -> username
    private var replyingToMessage: Message? = null

    private var mediaRecorder: android.media.MediaRecorder? = null
    private var audioFilePath: String = ""

    private val requestRecordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                android.widget.Toast.makeText(this, "Cần cấp quyền Micro để ghi âm!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

    private var isTyping = false
    private val typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val typingRunnable = Runnable {
        if (isTyping) {
            isTyping = false
            if (chatRoomId.isNotEmpty()) {
                com.giangnam.chat_app.data.repository.ChatRepository.getInstance().updateTypingStatus(chatRoomId, Utils.getUserId(), false)
            }
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageUri = result.data?.data
                imageUri?.let { uploadImageToImgBB(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        user2Id = intent.getStringExtra("userId") ?: ""
        chatRoomId = intent.getStringExtra("roomId") ?: ""

        if (chatRoomId.isNotEmpty() && user2Id.isEmpty()) {
            // Group Chat
            setupGroupToolbar()
            val userChatRoomRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USER_INBOX)
            userChatRoomRef.child(Utils.getUserId()).child(chatRoomId).child("clearedAt").get().addOnSuccessListener { inboxSnapshot ->
                val clearedAt = inboxSnapshot.getValue(Long::class.java) ?: 0L
                setupMessageAdapter(clearedAt)
                markMessagesAsRead()
            }
        } else {
            // 1-1 Chat
            setupToolbar()
            listenToUserStatus()
            createGetChatRoom()
            listenToBlockStatus()
        }

        observeViewModel()

        mentionAdapter = MentionAdapter(emptyList()) { user ->
            val currentText = binding.message.text.toString()
            val cursorPosition = binding.message.selectionStart
            
            val textBeforeCursor = currentText.substring(0, cursorPosition)
            val atIndex = textBeforeCursor.lastIndexOf("@")
            
            if (atIndex != -1) {
                val beforeAt = currentText.substring(0, atIndex)
                val afterCursor = currentText.substring(cursorPosition)
                
                val newText = "$beforeAt@${user.username} $afterCursor"
                binding.message.setText(newText)
                binding.message.setSelection(beforeAt.length + user.username!!.length + 2)
                currentMentions[user.uid] = user.username!!
            }
            binding.rvMentions.visibility = android.view.View.GONE
        }
        binding.rvMentions.adapter = mentionAdapter

        binding.message.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentText = s.toString()
                
                if (chatRoomId.isNotEmpty() && user2Id.isEmpty()) {
                    val cursorPosition = start + count
                    if (cursorPosition > 0 && currentText.length >= cursorPosition) {
                        val textBeforeCursor = currentText.substring(0, cursorPosition)
                        val lastAtIndex = textBeforeCursor.lastIndexOf("@")
                        
                        if (lastAtIndex != -1) {
                            val query = textBeforeCursor.substring(lastAtIndex + 1).lowercase()
                            val isAtStartOrAfterSpace = lastAtIndex == 0 || textBeforeCursor[lastAtIndex - 1] == ' ' || textBeforeCursor[lastAtIndex - 1] == '\n'
                            
                            if (isAtStartOrAfterSpace && !query.contains(" ")) {
                                val filtered = groupMembers.filter {
                                    it.username?.lowercase()?.contains(query) == true
                                }
                                if (filtered.isNotEmpty()) {
                                    mentionAdapter?.submitList(filtered)
                                    binding.rvMentions.visibility = android.view.View.VISIBLE
                                } else {
                                    binding.rvMentions.visibility = android.view.View.GONE
                                }
                            } else {
                                binding.rvMentions.visibility = android.view.View.GONE
                            }
                        } else {
                            binding.rvMentions.visibility = android.view.View.GONE
                        }
                    } else {
                        binding.rvMentions.visibility = android.view.View.GONE
                    }
                }

                if (currentText.trim().isNotEmpty()) {
                    binding.sendOrMicBtn.setImageResource(R.drawable.ic_send)
                    if (!isTyping) {
                        isTyping = true
                        if (chatRoomId.isNotEmpty()) {
                            com.giangnam.chat_app.data.repository.ChatRepository.getInstance().updateTypingStatus(chatRoomId, Utils.getUserId(), true)
                        }
                    }
                    typingHandler.removeCallbacks(typingRunnable)
                    typingHandler.postDelayed(typingRunnable, 2000)
                } else {
                    binding.sendOrMicBtn.setImageResource(R.drawable.ic_mic)
                    if (isTyping) {
                        isTyping = false
                        typingHandler.removeCallbacks(typingRunnable)
                        if (chatRoomId.isNotEmpty()) {
                            com.giangnam.chat_app.data.repository.ChatRepository.getInstance().updateTypingStatus(chatRoomId, Utils.getUserId(), false)
                        }
                    }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        listenToTypingStatus()
        listenToGroupMembership()

        var isRecording = false
        var recordTimerHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var recordSeconds = 0
        val updateTimerRunnable = object : java.lang.Runnable {
            override fun run() {
                recordSeconds++
                val s = recordSeconds % 60
                val m = recordSeconds / 60
                binding.recordTimeText.text = String.format("%02d:%02d", m, s)
                recordTimerHandler.postDelayed(this, 1000)
            }
        }

        binding.sendOrMicBtn.setOnClickListener {
            val messageText = binding.message.text.toString().trim()

            // TRƯỜNG HỢP 1: Ô text ĐANG CÓ CHỮ -> Hoạt động như nút Gửi bình thường
            if (messageText.isNotEmpty()) {
                if (editingMessage != null) {
                    viewModel.editMessage(chatRoomId, editingMessage!!.messageId, messageText)
                    editingMessage = null
                } else {
                    sendMessage(messageText)
                }
                binding.message.setText("")
                return@setOnClickListener
            }

            // TRƯỜNG HỢP 2: Ô text RỖNG -> Hoạt động như nút Ghi âm
            if (!isRecording) {
                // Chưa ghi âm -> Bắt đầu ghi âm
                val permission = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    isRecording = true
                    binding.inputCardView.visibility = android.view.View.GONE
                    binding.attachButtonsLayout.visibility = android.view.View.GONE
                    binding.recordOverlayLayout.visibility = android.view.View.VISIBLE
                    binding.sendOrMicBtn.setImageResource(R.drawable.ic_send)
                    
                    recordSeconds = 0
                    binding.recordTimeText.text = "00:00"
                    recordTimerHandler.postDelayed(updateTimerRunnable, 1000)
                    startRecording()
                } else {
                    requestRecordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            } else {
                // Đang ghi âm -> Bấm lần 2 để gửi
                isRecording = false
                binding.inputCardView.visibility = android.view.View.VISIBLE
                binding.attachButtonsLayout.visibility = android.view.View.VISIBLE
                binding.recordOverlayLayout.visibility = android.view.View.GONE
                if (binding.message.text.toString().trim().isEmpty()) {
                    binding.sendOrMicBtn.setImageResource(R.drawable.ic_mic)
                }
                
                recordTimerHandler.removeCallbacks(updateTimerRunnable)
                if (mediaRecorder != null) {
                    stopRecording()
                }
            }
        }

        binding.btnCancelRecord.setOnClickListener {
            if (isRecording) {
                isRecording = false
                binding.inputCardView.visibility = android.view.View.VISIBLE
                binding.attachButtonsLayout.visibility = android.view.View.VISIBLE
                binding.recordOverlayLayout.visibility = android.view.View.GONE
                if (binding.message.text.toString().trim().isEmpty()) {
                    binding.sendOrMicBtn.setImageResource(R.drawable.ic_mic)
                }
                
                recordTimerHandler.removeCallbacks(updateTimerRunnable)
                if (mediaRecorder != null) {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                        mediaRecorder = null
                        val file = File(audioFilePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {}
                }
            }
        }

        binding.attachBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.stickerBtn.setOnClickListener {
            fetchGiphyStickersAndShowBottomSheet()
        }
        // Xử lý khi bấm nút Gợi ý AI
        binding.aiSuggestBtn.setOnClickListener {
            var lastIncomingMsg: String? = null
            val itemCount = adapter?.itemCount ?: 0

            // Chạy ngược từ dưới lên để tìm tin nhắn dạng text gần nhất của đối phương
            for (i in itemCount - 1 downTo 0) {
                val msg = adapter?.getItem(i)
                val isTextMessage = msg?.type == "text" || msg?.type.isNullOrEmpty()

                if (msg != null && msg.senderId != Utils.getUserId() && isTextMessage) {
                    lastIncomingMsg = msg.text
                    break
                }
            }

            if (lastIncomingMsg != null) {
                // Đổi icon thành màu xám hoặc xoay tròn nếu muốn, ở đây ta hiện Toast
                android.widget.Toast.makeText(this, "AI đang suy nghĩ...", android.widget.Toast.LENGTH_SHORT).show()
                getSmartRepliesFromGemini(lastIncomingMsg)
            } else {
                android.widget.Toast.makeText(this, "Chưa có tin nhắn chữ nào để AI phân tích!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Bắt sự kiện bấm vào nút Info để mở màn hình Tùy chỉnh hội thoại
        binding.btnInfo.setOnClickListener {
            val settingsIntent = Intent(this, ChatSettingsActivity::class.java)
            settingsIntent.putExtra("userId", user2Id)
            settingsIntent.putExtra("userName", intent.getStringExtra("userName") ?: "Tên Người Dùng")
            settingsIntent.putExtra("roomId", chatRoomId)
            startActivity(settingsIntent)
        }
        
        binding.btnCloseReply.setOnClickListener {
            replyingToMessage = null
            binding.replyPreviewLayout.visibility = android.view.View.GONE
        }

        com.onesignal.OneSignal.Notifications.addForegroundLifecycleListener(
            object : com.onesignal.notifications.INotificationLifecycleListener {
                override fun onWillDisplay(event: com.onesignal.notifications.INotificationWillDisplayEvent) {
                    val additionalData = event.notification.additionalData
                    if (additionalData != null && additionalData.has("senderId")) {
                        val senderId = additionalData.getString("senderId")
                        if (senderId == user2Id) {
                            event.preventDefault()
                        }
                    }
                }
            }
        )
    }

    private fun setupMessageAdapter(clearedAt: Long = 0L) {
        // Cài đặt RecyclerView cho Smart Reply
        smartReplyAdapter = SmartReplyAdapter(smartRepliesList) { selectedReply ->
            sendMessage(selectedReply)
            binding.smartReplyRv.visibility = android.view.View.GONE
        }
        binding.smartReplyRv.adapter = smartReplyAdapter

        val baseQuery = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_ROOMS)
            .child(chatRoomId).child(Constants.NODE_MESSAGES)

        val filterQuery = if (clearedAt > 0) {
            baseQuery.orderByChild("timestamp").startAt(clearedAt.toDouble())
        } else {
            baseQuery
        }

        val option = FirebaseRecyclerOptions.Builder<Message>()
            .setQuery(filterQuery, Message::class.java).build()

        val friendName = intent.getStringExtra("userName") ?: "Người dùng"
        adapter = ChatAdapter(option, friendName, { message ->
            showMessageOptions(message)
        }, { message ->
            showReactionsInfo(message)
        })
        binding.messageLSt.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.messageLSt.adapter = adapter
        adapter?.startListening()

        adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                binding.messageLSt.scrollToPosition(adapter!!.itemCount - 1)
                // Ẩn thanh gợi ý nếu có tin nhắn mới trôi lên
                binding.smartReplyRv.visibility = android.view.View.GONE
                
                // Đánh dấu đã xem khi đang mở màn hình
                markMessagesAsRead()
            }
        })
    }

    private fun sendMessage(message: String) {
        val currentTime = System.currentTimeMillis()
        
        // Chỉ lưu những người thực sự còn tag trong text
        val finalMentions = mutableMapOf<String, Boolean>()
        val finalMentionedNames = mutableListOf<String>()
        for ((uid, name) in currentMentions) {
            if (message.contains("@$name")) {
                finalMentions[uid] = true
                finalMentionedNames.add(name)
            }
        }
        
        val messageModel = Message(
            text = message,
            senderId = Utils.getUserId(),
            timestamp = currentTime,
            mentions = finalMentions,
            mentionedNames = finalMentionedNames
        )
        
        if (replyingToMessage != null) {
            val previewText = replyingToMessage?.text?.takeIf { it.isNotEmpty() } ?: "Đính kèm"
            messageModel.replyTo = ReplyPreview(
                messageId = replyingToMessage!!.messageId,
                senderId = replyingToMessage!!.senderId,
                preview = previewText
            )
            replyingToMessage = null
            binding.replyPreviewLayout.visibility = android.view.View.GONE
        }
        
        viewModel.sendMessage(chatRoomId, messageModel)
        sendNotification(message)
        
        // Gửi thông báo riêng cho những người được nhắc đến trong nhóm
        if (user2Id.isEmpty()) {
            val groupName = binding.chatUserName.text.toString()
            for (uid in finalMentions.keys) {
                if (uid != Utils.getUserId()) {
                    viewModel.sendNotification(uid, "Bạn được nhắc đến trong $groupName: $message")
                }
            }
        }
        
        currentMentions.clear()

        binding.message.setText("")
        if (user2Id == "AI_BOT_001") {
            callGeminiAPI(message)
        }
    }

    fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val otherUser = intent.getStringExtra("userName") ?: "Tên Người Dùng"

        // 1. CẤU HÌNH NÚT GỌI VIDEO ZEGOCLOUD
        binding.btnVideoCall.setIsVideoCall(true)
        val targetUser = ZegoUIKitUser(user2Id, otherUser)
        binding.btnVideoCall.setInvitees(Collections.singletonList(targetUser))

        // 2. CẬP NHẬT ẢNH, TÊN VÀ SĐT LÊN GIAO DIỆN MỚI
        if (user2Id == "AI_BOT_001") {
            binding.btnVideoCall.visibility = android.view.View.GONE
            binding.chatUserName.text = "Trợ lý AI"
            binding.chatUserPhone.text = "Bot Hệ Thống"
            com.bumptech.glide.Glide.with(this)
                .load("https://cdn-icons-png.flaticon.com/512/4712/4712027.png")
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(binding.chatUserAvatar)

        } else if (user2Id.isNotEmpty()) {
            // Gán tạm tên lấy từ Intent để giao diện không bị trống trong lúc đợi mạng
            binding.chatUserName.text = otherUser

            FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS).child(user2Id)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val name = snapshot.child("userName").value?.toString() ?: otherUser
                        val phone = snapshot.child("phoneNumber").value?.toString() ?: ""
                        val imageUrl = snapshot.child("avatarUrl").value?.toString() ?: ""

                        // Gán dữ liệu thật từ Firebase
                        binding.chatUserName.text = name
                        binding.chatUserPhone.text = phone

                        // Tải ảnh đại diện
                        if (imageUrl.isNotEmpty()) {
                            com.bumptech.glide.Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_person)
                                .circleCrop()
                                .into(binding.chatUserAvatar)
                        } else {
                            binding.chatUserAvatar.setImageResource(R.drawable.ic_person)
                        }
                    }
                }
        }
    }

    private fun setupGroupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        binding.btnVideoCall.visibility = android.view.View.GONE // Tạm ẩn video call cho nhóm
        binding.btnInfo.setImageResource(R.drawable.ic_more_vert) // Đổi icon info thành 3 chấm

        val roomName = intent.getStringExtra("userName") ?: "Nhóm chưa đặt tên"
        binding.chatUserName.text = roomName
        binding.chatUserPhone.text = "Nhóm"

        FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_ROOMS).child(chatRoomId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val name = snapshot.child("name").value?.toString() ?: roomName
                    val imageUrl = snapshot.child("avatarUrl").value?.toString() ?: ""

                    binding.chatUserName.text = name

                    if (imageUrl.isNotEmpty()) {
                        com.bumptech.glide.Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_group)
                            .circleCrop()
                            .into(binding.chatUserAvatar)
                    } else {
                        binding.chatUserAvatar.setImageResource(R.drawable.ic_group)
                    }
                }
            }
            
        // Load group members for mentions
        val usersRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS)
        val roomMembersRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_ROOM_MEMBERS).child(chatRoomId)
        
        roomMembersRef.get().addOnSuccessListener { snapshot ->
            groupMembers.clear()
            for (child in snapshot.children) {
                val uid = child.key ?: continue
                if (uid != Utils.getUserId()) {
                    usersRef.child(uid).get().addOnSuccessListener { userSnapshot ->
                        val user = userSnapshot.getValue(com.giangnam.chat_app.data.model.User::class.java)
                        if (user != null) {
                            groupMembers.add(user)
                        }
                    }
                }
            }
        }
    }

    private fun createGetChatRoom() {
        chatRoomId = getChatRoomId(Utils.getUserId(), user2Id)

        val chatRoomRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_ROOMS).child(chatRoomId)

        chatRoomRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val userIds = ArrayList<String>()
                userIds.add(Utils.getUserId())
                userIds.add(user2Id)

                val chatRoom = ChatRoom(
                    roomId = chatRoomId,
                    userIds = userIds,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                chatRoomRef.setValue(chatRoom)
            }

            val userChatRoomRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USER_INBOX)
            val currentTime = System.currentTimeMillis()
            val inboxData = mapOf(
                "roomId" to chatRoomId,
                "lastActivityAt" to currentTime
            )
            userChatRoomRef.child(Utils.getUserId()).child(chatRoomId).updateChildren(inboxData)
            userChatRoomRef.child(user2Id).child(chatRoomId).updateChildren(inboxData)

            userChatRoomRef.child(Utils.getUserId()).child(chatRoomId).child("clearedAt").get().addOnSuccessListener { inboxSnapshot ->
                val clearedAt = inboxSnapshot.getValue(Long::class.java) ?: 0L
                setupMessageAdapter(clearedAt)
                markMessagesAsRead()
            }
        }
            .addOnFailureListener {
                Toast.makeText(this, "Không thể tạo phòng chat", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getChatRoomId(u1: String, u2: String): String {
        return if (u1.hashCode() < u2.hashCode()) {
            "${u1}_${u2}"
        } else {
            "${u2}_${u1}"
        }
    }

    private fun uploadImageToImgBB(uri: Uri) {
        Toast.makeText(this, "Đang xử lý và tải ảnh lên...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)

                viewModel.uploadImage(base64Image ?: "") { imageUrl ->
                    if (imageUrl != null) {
                        sendImageMessage(imageUrl)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@ChatActivity, "Lỗi upload ảnh!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Lỗi hệ thống: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // [DELETE OLD uploadImageToImgBB]


    private fun sendImageMessage(imageUrl: String) {
        val currentTime = System.currentTimeMillis()
        val messageModel = Message(
            senderId = Utils.getUserId(),
            timestamp = currentTime,
            mediaUrl = imageUrl,
            type = "image"
        )

        viewModel.sendMessage(chatRoomId, messageModel)
        sendNotification("Đã gửi một ảnh")
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.otherUserStatus.collect { (isOnline, lastSeen) ->
                if (isOnline) {
                    binding.toolbar.subtitle = "🟢 Đang hoạt động"
                    binding.chatOnlineIndicator.visibility = android.view.View.VISIBLE
                } else {
                    binding.chatOnlineIndicator.visibility = android.view.View.GONE
                    if (lastSeen > 0L) {
                        val timeDiff = System.currentTimeMillis() - lastSeen
                        val minutes = timeDiff / (60 * 1000)
                        if (minutes < 60) {
                            binding.toolbar.subtitle = "Hoạt động $minutes phút trước"
                        } else {
                            val hours = minutes / 60
                            if (hours < 24) {
                                binding.toolbar.subtitle = "Hoạt động $hours giờ trước"
                            } else {
                                val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                                binding.toolbar.subtitle = "Hoạt động " + sdf.format(java.util.Date(lastSeen))
                            }
                        }
                    } else {
                        binding.toolbar.subtitle = "⚪ Ngoại tuyến"
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.smartReplies.collect { newReplies ->
                if (newReplies.isNotEmpty()) {
                    smartRepliesList.clear()
                    smartRepliesList.addAll(newReplies)
                    smartReplyAdapter?.notifyDataSetChanged()
                    binding.smartReplyRv.visibility = android.view.View.VISIBLE
                    binding.smartReplyRv.scrollToPosition(0)
                }
            }
        }
    }

    private fun listenToUserStatus() {
        viewModel.listenToUserStatus(user2Id)
    }

    private fun callGeminiAPI(userText: String) {
        val botMessageId = com.google.firebase.database.FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOMS)
            .child(chatRoomId).child(Constants.NODE_MESSAGES).push().key ?: return

        val tempMessage = Message(
            messageId = botMessageId,
            text = "⏳ AI đang suy nghĩ...",
            senderId = "AI_BOT_001",
            timestamp = System.currentTimeMillis(),
            type = "text"
        )
        com.google.firebase.database.FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOMS)
            .child(chatRoomId)
            .child(Constants.NODE_MESSAGES)
            .child(botMessageId)
            .setValue(tempMessage)

        viewModel.getGeminiReply(userText) { aiReply ->
            if (!aiReply.isNullOrEmpty()) {
                if (aiReply.startsWith("ERROR:")) {
                    com.google.firebase.database.FirebaseDatabase.getInstance(Constants.DATABASE_URL)
                        .getReference(Constants.NODE_ROOMS)
                        .child(chatRoomId).child(Constants.NODE_MESSAGES).child(botMessageId).removeValue()
                    val errorMsg = aiReply.removePrefix("ERROR:").trim()
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread {
                            Toast.makeText(this@ChatActivity, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val updateMap = mapOf(
                        "text" to aiReply,
                        "timestamp" to System.currentTimeMillis()
                    )
                    com.google.firebase.database.FirebaseDatabase.getInstance(Constants.DATABASE_URL)
                        .getReference(Constants.NODE_ROOMS)
                        .child(chatRoomId).child(Constants.NODE_MESSAGES).child(botMessageId).updateChildren(updateMap)
                }
            } else {
                com.google.firebase.database.FirebaseDatabase.getInstance(Constants.DATABASE_URL)
                    .getReference(Constants.NODE_ROOMS)
                    .child(chatRoomId).child(Constants.NODE_MESSAGES).child(botMessageId).removeValue()
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Không kết nối được AI", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // [DELETE OLD callGeminiAPI]


    private fun saveBotReply(reply: String) {
        val currentTime = System.currentTimeMillis()
        val messageModel = Message(
            text = reply,
            senderId = "AI_BOT_001",
            timestamp = currentTime,
            type = "text"
        )
        viewModel.sendMessage(chatRoomId, messageModel, isAiBot = true)
    }

    private fun getSmartRepliesFromGemini(incomingMessage: String) {
        if (incomingMessage.trim().isEmpty()) return
        viewModel.getSmartReplies(incomingMessage)
    }

    // [DELETE OLD getSmartRepliesFromGemini]



    private fun sendNotification(messageText: String) {
        if (user2Id == "AI_BOT_001") return
        viewModel.sendNotification(user2Id, messageText)
    }

    // [DELETE OLD sendNotification]
    


    private fun showMessageOptions(message: Message) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_message_options, null)
        
        val btnReply = view.findViewById<android.view.View>(R.id.btnReply)
        val btnForward = view.findViewById<android.view.View>(R.id.btnForward)
        val btnEdit = view.findViewById<android.view.View>(R.id.btnEdit)
        val btnDeleteForMe = view.findViewById<android.view.View>(R.id.btnDeleteForMe)
        val btnRevoke = view.findViewById<android.view.View>(R.id.btnRevoke)

        val reactLike = view.findViewById<android.widget.TextView>(R.id.reactLike)
        val reactLove = view.findViewById<android.widget.TextView>(R.id.reactLove)
        val reactHaha = view.findViewById<android.widget.TextView>(R.id.reactHaha)
        val reactWow = view.findViewById<android.widget.TextView>(R.id.reactWow)
        val reactSad = view.findViewById<android.widget.TextView>(R.id.reactSad)
        val reactAngry = view.findViewById<android.widget.TextView>(R.id.reactAngry)
        val btnMoreReaction = view.findViewById<android.widget.ImageView>(R.id.btnMoreReaction)

        val onReact = { emoji: String ->
            viewModel.addReaction(chatRoomId, message.messageId, Utils.getUserId(), emoji)
            bottomSheetDialog.dismiss()
        }
        
        reactLike?.setOnClickListener { onReact("👍") }
        reactLove?.setOnClickListener { onReact("❤️") }
        reactHaha?.setOnClickListener { onReact("😂") }
        reactWow?.setOnClickListener { onReact("😮") }
        reactSad?.setOnClickListener { onReact("😥") }
        reactAngry?.setOnClickListener { onReact("😡") }
        
        btnMoreReaction?.setOnClickListener {
            val grid = android.widget.GridLayout(this).apply {
                columnCount = 5
                setPadding(32, 32, 32, 32)
            }
            val emojis = arrayOf("💯", "💩", "🤡", "☠️", "👻", "👽", "🤖", "🎃", "🙏", "👏", "🔥", "🎉", "💔", "🙄", "🤢", "🤔", "👀", "🧠", "🥶", "🤯")
            val dialog = android.app.AlertDialog.Builder(this)
                .setView(grid)
                .show()
                
            for (e in emojis) {
                val tv = android.widget.TextView(this).apply {
                    text = e
                    textSize = 32f
                    setPadding(24, 24, 24, 24)
                    setOnClickListener {
                        onReact(e)
                        dialog.dismiss()
                    }
                }
                grid.addView(tv)
            }
        }

        val isMyMessage = message.senderId == Utils.getUserId()
        val isTextMessage = message.type == "text"

        if (message.isRevoked) {
            view.findViewById<android.view.View>(R.id.reactionBar).visibility = android.view.View.GONE
            view.findViewById<android.view.View>(R.id.btnReply).visibility = android.view.View.GONE
            view.findViewById<android.view.View>(R.id.btnForward).visibility = android.view.View.GONE
            view.findViewById<android.view.View>(R.id.btnEdit).visibility = android.view.View.GONE
            view.findViewById<android.view.View>(R.id.btnRevoke).visibility = android.view.View.GONE
        } else {
            if (isMyMessage && isTextMessage) {
                btnEdit.visibility = android.view.View.VISIBLE
            }
            if (isMyMessage) {
                btnRevoke.visibility = android.view.View.VISIBLE
            }
        }

        btnReply?.setOnClickListener {
            bottomSheetDialog.dismiss()
            replyingToMessage = message
            binding.replyPreviewLayout.visibility = android.view.View.VISIBLE
            
            // Tìm tên người gửi
            var senderName = "Ai đó"
            if (message.senderId == Utils.getUserId()) {
                senderName = "Bạn"
            } else if (user2Id.isNotEmpty()) {
                senderName = binding.chatUserName.text.toString()
            } else {
                val member = groupMembers.find { it.uid == message.senderId }
                if (member != null) senderName = member.username ?: "Một thành viên"
            }
            
            binding.replyPreviewTitle.text = "Đang trả lời $senderName"
            binding.replyPreviewContent.text = message.text?.takeIf { it.isNotEmpty() } ?: "Đính kèm"
            
            binding.message.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.message, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        btnForward?.setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ForwardActivity::class.java)
            intent.putExtra("messageId", message.messageId)
            intent.putExtra("roomId", chatRoomId)
            startActivity(intent)
        }

        btnEdit?.setOnClickListener {
            bottomSheetDialog.dismiss()
            editingMessage = message
            binding.message.setText(message.text)
            binding.message.setSelection(message.text?.length ?: 0)
            binding.message.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.message, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        btnDeleteForMe?.setOnClickListener {
            bottomSheetDialog.dismiss()
            android.app.AlertDialog.Builder(this)
                .setTitle("Xóa tin nhắn")
                .setMessage("Bạn có chắc chắn muốn xóa tin nhắn này ở phía bạn không?")
                .setPositiveButton("Xóa") { _, _ ->
                    viewModel.deleteMessageForMe(chatRoomId, message.messageId, Utils.getUserId())
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        btnRevoke?.setOnClickListener {
            bottomSheetDialog.dismiss()
            android.app.AlertDialog.Builder(this)
                .setTitle("Thu hồi tin nhắn")
                .setMessage("Thu hồi tin nhắn này với mọi người trong đoạn chat?")
                .setPositiveButton("Thu hồi") { _, _ ->
                    viewModel.revokeMessage(chatRoomId, message.messageId)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun showReactionsInfo(message: Message) {
        val reactions = message.reactions
        if (reactions.isEmpty()) return
        
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Lượt bày tỏ cảm xúc")
            .setMessage("Đang tải...")
            .setPositiveButton("Đóng", null)
            .show()
            
        val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        
        val database = com.google.firebase.database.FirebaseDatabase.getInstance(Constants.DATABASE_URL)
        val usersRef = database.getReference(Constants.NODE_USERS)
        
        val resultList = mutableListOf<String>()
        var count = 0
        
        for ((uid, reaction) in reactions) {
            usersRef.child(uid).get().addOnSuccessListener { snapshot ->
                val userName = snapshot.child("userName").getValue(String::class.java) ?: "Người dùng ẩn danh"
                val finalName = if (uid == Utils.getUserId()) "Bạn" else userName
                resultList.add("${reaction.emoji} - $finalName")
                count++
                if (count == reactions.size) {
                    messageView?.text = resultList.joinToString("\n\n")
                }
            }.addOnFailureListener {
                resultList.add("${reaction.emoji} - Người dùng (lỗi)")
                count++
                if (count == reactions.size) {
                    messageView?.text = resultList.joinToString("\n\n")
                }
            }
        }
    }

    private fun listenToTypingStatus() {
        if (chatRoomId.isEmpty()) return
        val typingRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOM_STATUS).child(chatRoomId).child("typingUsers")
            
        typingRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val typingUsers = mutableListOf<String>()
                for (child in snapshot.children) {
                    val uid = child.key ?: ""
                    if (uid != Utils.getUserId()) {
                        val isTypingUser = child.getValue(Boolean::class.java) ?: false
                        if (isTypingUser) {
                            typingUsers.add(uid)
                        }
                    }
                }
                
                if (typingUsers.isNotEmpty()) {
                    if (user2Id.isNotEmpty()) {
                        binding.tvTypingStatus.text = "Đang soạn tin..."
                    } else {
                        binding.tvTypingStatus.text = "${typingUsers.size} người đang soạn tin..."
                    }
                    binding.tvTypingStatus.visibility = android.view.View.VISIBLE
                    binding.chatUserPhone.visibility = android.view.View.GONE
                } else {
                    binding.tvTypingStatus.visibility = android.view.View.GONE
                    binding.chatUserPhone.visibility = android.view.View.VISIBLE
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun listenToGroupMembership() {
        if (chatRoomId.isEmpty() || user2Id.isNotEmpty()) return
        val myMemberRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOM_MEMBERS).child(chatRoomId).child(Utils.getUserId())
            
        myMemberRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists()) {
                    binding.bottomLayout.visibility = android.view.View.GONE
                    binding.tvRemovedFromGroup.visibility = android.view.View.VISIBLE
                    binding.btnInfo.visibility = android.view.View.GONE
                    binding.btnVideoCall.visibility = android.view.View.GONE
                } else {
                    binding.bottomLayout.visibility = android.view.View.VISIBLE
                    binding.tvRemovedFromGroup.visibility = android.view.View.GONE
                    binding.btnInfo.visibility = android.view.View.VISIBLE
                    binding.btnVideoCall.visibility = android.view.View.VISIBLE
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private var seenListener: com.google.firebase.database.ValueEventListener? = null
    private var messagesRefForSeen: com.google.firebase.database.Query? = null

    private fun markMessagesAsRead() {
        if (chatRoomId.isEmpty()) return
        
        // Reset unreadCount in UserInbox for this room
        com.giangnam.chat_app.data.repository.ChatRepository.getInstance().markMessagesAsRead(chatRoomId)
        
        val messagesRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_MESSAGES)
            .child(chatRoomId)
            
        messagesRef.limitToLast(50).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val currentUserId = Utils.getUserId()
                for (child in snapshot.children) {
                    val message = child.getValue(Message::class.java)
                    if (message != null && message.senderId != currentUserId) {
                        if (!message.readBy.containsKey(currentUserId)) {
                            com.giangnam.chat_app.data.repository.ChatRepository.getInstance().markMessageAsRead(chatRoomId, message.messageId, currentUserId)
                        }
                    }
                }
            }
        }
        
        // Remove previous listener if exists
        seenListener?.let { messagesRefForSeen?.removeEventListener(it) }
        
        messagesRefForSeen = messagesRef.limitToLast(1)
        seenListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val message = child.getValue(Message::class.java)
                        val currentUserId = Utils.getUserId()
                        if (message != null && message.senderId != currentUserId) {
                            if (!message.readBy.containsKey(currentUserId)) {
                                com.giangnam.chat_app.data.repository.ChatRepository.getInstance().markMessageAsRead(chatRoomId, message.messageId, currentUserId)
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        messagesRefForSeen?.addValueEventListener(seenListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        seenListener?.let {
            messagesRefForSeen?.removeEventListener(it)
        }
    }

    private fun fetchGiphyStickersAndShowBottomSheet() {
        val url = "${Constants.GIPHY_TRENDING_URL}?api_key=${BuildConfig.GIPHY_API_KEY}&limit=48&rating=g"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ChatActivity,
                        "Không thể tải Sticker: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return

                    val responseData = it.body?.string() ?: ""
                    val stickerUrls = ArrayList<String>()

                    try {
                        val jsonObject = org.json.JSONObject(responseData)
                        val dataArray = jsonObject.getJSONArray("data")

                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            val gifUrl = item.getJSONObject("images")
                                .getJSONObject("fixed_height")
                                .getString("url")

                            stickerUrls.add(gifUrl)
                        }

                        runOnUiThread {
                            showDynamicStickerBottomSheet(stickerUrls)
                        }

                    } catch (e: Exception) {
                        android.util.Log.e("GiphyError", "Lỗi đọc dữ liệu Giphy", e)
                    }
                }
            }
        })
    }

    private fun showDynamicStickerBottomSheet(dynamicStickers: List<String>) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_stickers, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.stickerRecyclerView)

        val adapter =
            StickerAdapter(dynamicStickers) { selectedUrl ->
                sendStickerMessage(selectedUrl)
                bottomSheetDialog.dismiss()
            }

        recyclerView.adapter = adapter
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun sendStickerMessage(stickerUrl: String) {
        val currentTime = System.currentTimeMillis()
        val messageModel = Message(
            senderId = Utils.getUserId(),
            timestamp = currentTime,
            mediaUrl = stickerUrl,
            type = "sticker",
            isRead = false
        )

        viewModel.sendMessage(chatRoomId, messageModel)
        sendNotification("Đã gửi một nhãn dán")
    }

    private fun startRecording() {
        // Đặt tên file theo thời gian thực để không bị trùng
        val fileName = "voice_record_${System.currentTimeMillis()}.3gp"
        audioFilePath = "${externalCacheDir?.absolutePath}/$fileName"

        mediaRecorder = android.media.MediaRecorder().apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFilePath)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
                start()
                android.widget.Toast.makeText(
                    this@ChatActivity,
                    "Đang ghi âm...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("VoiceRecord", "Lỗi khởi tạo ghi âm: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        try {
            android.util.Log.d("VoiceRecord", "1. Đang tiến hành dừng ghi âm...")
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            android.util.Log.d("VoiceRecord", "2. Đã lưu file, chuẩn bị gọi upload...")
            uploadAudioToCloudinary()

        } catch (e: Exception) {
            android.util.Log.e("VoiceRecord", "LỖI LÚC DỪNG: ${e.message}")
            android.widget.Toast.makeText(this, "Thu âm quá ngắn!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAudioToCloudinary() {
        if (audioFilePath.isEmpty()) return
        val file = File(audioFilePath)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "Lỗi: File ghi âm rỗng", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Đang gửi tin nhắn thoại...", Toast.LENGTH_SHORT).show()
        viewModel.uploadAudio(file) { secureAudioUrl ->
            if (secureAudioUrl != null) {
                runOnUiThread { sendAudioMessage(secureAudioUrl) }
            } else {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Lỗi upload âm thanh!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // [DELETE OLD uploadAudioToCloudinary]


    private fun sendAudioMessage(audioUrl: String) {
        val currentTime = System.currentTimeMillis()
        val userId = Utils.getUserId()

        val messageModel = Message(
            senderId = userId,
            timestamp = currentTime,
            mediaUrl = audioUrl,
            type = "audio",
            isRead = false
        )

        viewModel.sendMessage(chatRoomId, messageModel)
        sendNotification("Đã gửi một tin nhắn thoại")
    }

    fun showImageViewer(imageUrl: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_image_viewer)
        val photoView = dialog.findViewById<android.widget.ImageView>(R.id.fullscreenImageView)
        val closeBtn = dialog.findViewById<android.widget.ImageView>(R.id.closeImageViewBtn)
        
        com.bumptech.glide.Glide.with(this).load(imageUrl).into(photoView)
        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun listenToBlockStatus() {
        val myUid = Utils.getUserId()
        if (user2Id.isEmpty()) return

        com.giangnam.chat_app.data.repository.SocialRepository.getInstance().checkBlockStatus(myUid, user2Id) { amIBlocked, didIBlock ->
            if (amIBlocked || didIBlock) {
                binding.bottomLayout.visibility = android.view.View.GONE
                binding.tvBlockMessage.visibility = android.view.View.VISIBLE
            } else {
                binding.bottomLayout.visibility = android.view.View.VISIBLE
                binding.tvBlockMessage.visibility = android.view.View.GONE
            }
        }
    }
}