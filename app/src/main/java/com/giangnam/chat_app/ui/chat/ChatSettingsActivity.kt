package com.giangnam.chat_app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.UserInbox
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatSettingsActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private lateinit var otherUserId: String
    private lateinit var otherUserName: String
    
    private val currentUserId = Utils.getUserId()
    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private var inboxListener: ValueEventListener? = null

    private val pickImageLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                val imageUri = result.data?.data
                imageUri?.let { uploadImageAndUpdateGroup(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_settings)

        roomId = intent.getStringExtra("roomId") ?: ""
        otherUserId = intent.getStringExtra("userId") ?: ""
        otherUserName = intent.getStringExtra("userName") ?: "Người dùng"

        if (roomId.isEmpty()) {
            finish()
            return
        }

        setupUI()
        loadOtherUserAvatar()
        listenToInboxSettings()
        listenToGroupMembership()
    }

    private fun listenToGroupMembership() {
        if (otherUserId.isNotEmpty()) return // Only for groups
        val myMemberRef = db.getReference(Constants.NODE_ROOM_MEMBERS).child(roomId).child(currentUserId)
        myMemberRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.settingsName).text = otherUserName

        // Options
        val switchPin = findViewById<SwitchMaterial>(R.id.switchPin)
        val switchMute = findViewById<SwitchMaterial>(R.id.switchMute)
        val switchHide = findViewById<SwitchMaterial>(R.id.switchHide)

        switchPin.setOnCheckedChangeListener { _, isChecked ->
            ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isPinned", isChecked)
        }
        
        switchMute.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                showMuteDurationDialog(buttonView as SwitchMaterial)
            } else {
                ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isMuted", false)
                ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "mutedUntil", 0L)
            }
        }

        switchHide.setOnCheckedChangeListener { _, isChecked ->
            ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isHidden", isChecked)
        }

        // Danger zone
        // Removed btnClearHistory logic as it's merged with Delete Chat

        findViewById<TextView>(R.id.btnDeleteChat).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Xóa hội thoại")
                .setMessage("Hội thoại sẽ bị xóa khỏi danh sách của bạn.")
                .setPositiveButton("Xóa") { _, _ ->
                    ChatRepository.getInstance().deleteConversation(currentUserId, roomId)
                    Toast.makeText(this, "Đã xóa hội thoại", Toast.LENGTH_SHORT).show()
                    finish() // Close settings
                    // TODO: Also need to broadcast or finish ChatActivity
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Quick Actions
        findViewById<LinearLayout>(R.id.btnProfile).setOnClickListener {
            val intent = Intent(this, com.giangnam.chat_app.ui.profile.UserInfoActivity::class.java)
            intent.putExtra("userId", otherUserId)
            startActivity(intent)
        }
        
        findViewById<LinearLayout>(R.id.btnSearch).setOnClickListener {
            val intent = Intent(this, com.giangnam.chat_app.ui.chat.SearchMessageActivity::class.java)
            intent.putExtra("roomId", roomId)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.btnMedia).setOnClickListener {
            val intent = android.content.Intent(this, SharedMediaActivity::class.java)
            intent.putExtra("roomId", roomId)
            startActivity(intent)
        }

        // Group specific settings
        if (otherUserId.isEmpty()) {
            findViewById<LinearLayout>(R.id.btnProfile).visibility = android.view.View.GONE
            findViewById<TextView>(R.id.tvChangeAvatarHint).visibility = android.view.View.VISIBLE

            findViewById<TextView>(R.id.btnLeaveGroup).visibility = android.view.View.VISIBLE
            findViewById<android.view.View>(R.id.dividerLeaveGroup).visibility = android.view.View.VISIBLE
            findViewById<TextView>(R.id.btnLeaveGroup).setOnClickListener {
                db.getReference(Constants.NODE_ROOM_MEMBERS).child(roomId).get().addOnSuccessListener { snapshot ->
                    var isAdmin = false
                    var otherMembersCount = 0
                    for (child in snapshot.children) {
                        if (child.key == currentUserId) {
                            if (child.child("role").value == "admin") isAdmin = true
                        } else {
                            otherMembersCount++
                        }
                    }
                    if (isAdmin && otherMembersCount > 0) {
                        Toast.makeText(this, "Bạn phải trao quyền quản trị viên cho người khác trước khi rời nhóm.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Rời nhóm")
                        .setMessage("Bạn có chắc chắn muốn rời nhóm này?")
                        .setPositiveButton("Rời") { _, _ ->
                            ChatRepository.getInstance().leaveGroup(roomId, currentUserId) { success ->
                                if (success) {
                                    db.getReference(Constants.NODE_USERS).child(currentUserId).get().addOnSuccessListener { userSnap ->
                                        val userName = userSnap.child("userName").value?.toString() ?: "Một thành viên"
                                        ChatRepository.getInstance().sendSystemMessage(roomId, "$userName đã rời khỏi nhóm.", currentUserId, "đã rời khỏi nhóm.")
                                        Toast.makeText(this, "Đã rời nhóm", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }.addOnFailureListener {
                                        ChatRepository.getInstance().sendSystemMessage(roomId, "Một thành viên đã rời khỏi nhóm.", currentUserId, "đã rời khỏi nhóm.")
                                        Toast.makeText(this, "Đã rời nhóm", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Hủy", null)
                        .show()
                }
            }
            
            findViewById<TextView>(R.id.btnGroupMembers).visibility = android.view.View.VISIBLE
            findViewById<android.view.View>(R.id.dividerGroupMembers).visibility = android.view.View.VISIBLE
            findViewById<TextView>(R.id.btnGroupMembers).setOnClickListener {
                val intent = Intent(this, GroupMembersActivity::class.java)
                intent.putExtra("roomId", roomId)
                startActivity(intent)
            }
            
            findViewById<TextView>(R.id.btnEditGroup).visibility = android.view.View.VISIBLE
            findViewById<android.view.View>(R.id.dividerEditGroup).visibility = android.view.View.VISIBLE
            findViewById<TextView>(R.id.btnEditGroup).setOnClickListener {
                showEditGroupDialog()
            }
            
            val settingsAvatar = findViewById<ImageView>(R.id.settingsAvatar)
            settingsAvatar.setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                pickImageLauncher.launch(intent)
            }
            
            findViewById<TextView>(R.id.btnCreateGroup).visibility = android.view.View.GONE
            findViewById<android.view.View>(R.id.dividerCreateGroup).visibility = android.view.View.GONE
        } else {
            if (otherUserId == "AI_BOT_001") {
                findViewById<LinearLayout>(R.id.btnProfile).visibility = android.view.View.GONE
                findViewById<TextView>(R.id.btnCreateGroup).visibility = android.view.View.GONE
                findViewById<android.view.View>(R.id.dividerCreateGroup).visibility = android.view.View.GONE
            } else {
                findViewById<TextView>(R.id.btnCreateGroup).visibility = android.view.View.VISIBLE
                findViewById<android.view.View>(R.id.dividerCreateGroup).visibility = android.view.View.VISIBLE
                findViewById<TextView>(R.id.btnCreateGroup).setOnClickListener {
                    val intent = Intent(this, CreateGroupActivity::class.java)
                    intent.putExtra("prefillUserId", otherUserId)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun showEditGroupDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_edit_group, null)
        val edtName = view.findViewById<android.widget.EditText>(R.id.edtName)
        edtName.hint = "Nhập tên nhóm mới"
        edtName.setText(findViewById<TextView>(R.id.settingsName).text)
        
        view.findViewById<android.widget.TextView>(R.id.tvTitle).text = "Đổi tên nhóm"
        
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = edtName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    ChatRepository.getInstance().updateGroupInfo(roomId, newName, null) { success ->
                        if (success) {
                            findViewById<TextView>(R.id.settingsName).text = newName
                            Toast.makeText(this, "Đổi tên nhóm thành công", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun loadOtherUserAvatar() {
        val settingsAvatar = findViewById<ImageView>(R.id.settingsAvatar)
        if (otherUserId.isNotEmpty()) {
            if (otherUserId == "AI_BOT_001") {
                Glide.with(this).load("https://cdn-icons-png.flaticon.com/512/4712/4712027.png").placeholder(R.drawable.ic_person).into(settingsAvatar)
            } else {
                db.getReference(Constants.NODE_USERS).child(otherUserId).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java)
                        if (!avatarUrl.isNullOrEmpty()) {
                            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_person).into(settingsAvatar)
                        }
                    }
                }
            }
        } else {
            db.getReference(Constants.NODE_ROOMS).child(roomId).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java)
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_person).into(settingsAvatar)
                    } else {
                        settingsAvatar.setImageResource(R.drawable.ic_person)
                    }
                }
            }
        }
    }

    private fun listenToInboxSettings() {
        val inboxRef = db.getReference(Constants.NODE_USER_INBOX).child(currentUserId).child(roomId)
        inboxListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val inbox = snapshot.getValue(UserInbox::class.java)
                    if (inbox != null) {
                        findViewById<SwitchMaterial>(R.id.switchPin).apply {
                            setOnCheckedChangeListener(null)
                            isChecked = inbox.isPinned
                            setOnCheckedChangeListener { _, isChecked ->
                                ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isPinned", isChecked)
                            }
                        }

                        findViewById<SwitchMaterial>(R.id.switchMute).apply {
                            setOnCheckedChangeListener(null)
                            val isCurrentlyMuted = inbox.isMuted || (inbox.mutedUntil == -1L || inbox.mutedUntil > System.currentTimeMillis())
                            isChecked = isCurrentlyMuted
                            setOnCheckedChangeListener { buttonView, isChecked ->
                                if (isChecked) {
                                    showMuteDurationDialog(buttonView as SwitchMaterial)
                                } else {
                                    ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isMuted", false)
                                    ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "mutedUntil", 0L)
                                }
                            }
                        }

                        findViewById<SwitchMaterial>(R.id.switchHide).apply {
                            setOnCheckedChangeListener(null)
                            isChecked = inbox.isHidden
                            setOnCheckedChangeListener { _, isChecked ->
                                ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isHidden", isChecked)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        inboxRef.addValueEventListener(inboxListener!!)
    }

    private fun showMuteDurationDialog(switchView: SwitchMaterial) {
        val options = arrayOf("15 phút", "1 giờ", "8 giờ", "24 giờ", "Cho đến khi tôi bật lại")
        var selectedIndex = 0

        AlertDialog.Builder(this)
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
                ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "isMuted", true)
                ChatRepository.getInstance().updateInboxProperty(currentUserId, roomId, "mutedUntil", mutedUntil)
                Toast.makeText(this, "Đã tắt thông báo", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy") { _, _ ->
                switchView.isChecked = false
            }
            .setOnCancelListener {
                switchView.isChecked = false
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        inboxListener?.let {
            db.getReference(Constants.NODE_USER_INBOX).child(currentUserId).child(roomId).removeEventListener(it)
        }
    }

    private fun uploadImageAndUpdateGroup(uri: android.net.Uri) {
        Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)

                ChatRepository.getInstance().uploadImageToImgBB(base64Image ?: "") { imageUrl ->
                    if (imageUrl != null) {
                        ChatRepository.getInstance().updateGroupInfo(roomId, null, imageUrl) { success ->
                            runOnUiThread {
                                if (success) {
                                    Toast.makeText(this@ChatSettingsActivity, "Cập nhật ảnh nhóm thành công", Toast.LENGTH_SHORT).show()
                                    // Cập nhật lại UI
                                    val avatar = findViewById<ImageView>(R.id.settingsAvatar)
                                    Glide.with(this@ChatSettingsActivity).load(imageUrl).circleCrop().into(avatar)
                                } else {
                                    Toast.makeText(this@ChatSettingsActivity, "Lỗi cập nhật ảnh nhóm", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@ChatSettingsActivity, "Lỗi upload ảnh!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ChatSettingsActivity, "Lỗi hệ thống: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
