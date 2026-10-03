package com.giangnam.chat_app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.ChatRoom
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CreateGroupActivity : AppCompatActivity() {

    private val chatRepository = ChatRepository.getInstance()
    private val selectedUserIds = mutableListOf<String>()
    
    private lateinit var rvUsers: RecyclerView
    private lateinit var etSearchUser: EditText
    private lateinit var btnCreateGroup: Button
    private lateinit var edtGroupName: EditText
    private lateinit var btnBack: ImageView
    
    private lateinit var selectUserAdapter: SelectUserAdapter
    private val allUsersList = mutableListOf<User>()

    private var isAddMemberMode = false
    private var existingRoomId = ""
    private val existingMemberIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_group)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        isAddMemberMode = intent.getBooleanExtra("isAddMemberMode", false)
        existingRoomId = intent.getStringExtra("roomId") ?: ""

        val prefillUserId = intent.getStringExtra("prefillUserId")
        if (!prefillUserId.isNullOrEmpty()) {
            selectedUserIds.add(prefillUserId)
        }

        initViews()
        setupRecyclerView()
        if (isAddMemberMode && existingRoomId.isNotEmpty()) {
            fetchExistingMembersAndLoadUsers()
        } else {
            loadUsers()
        }
        setupListeners()
    }

    private fun fetchExistingMembersAndLoadUsers() {
        FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOM_MEMBERS).child(existingRoomId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        existingMemberIds.add(child.key ?: "")
                    }
                }
                loadUsers()
            }
            .addOnFailureListener {
                loadUsers()
            }
    }

    private fun initViews() {
        rvUsers = findViewById(R.id.rvUsers)
        etSearchUser = findViewById(R.id.etSearchUser)
        btnCreateGroup = findViewById(R.id.btnCreateGroup)
        edtGroupName = findViewById(R.id.edtGroupName)
        btnBack = findViewById(R.id.btnBack)
        
        if (isAddMemberMode) {
            edtGroupName.visibility = View.GONE
            btnCreateGroup.text = "Thêm"
            findViewById<android.widget.TextView>(R.id.tvTitle)?.text = "Thêm thành viên"
        }

        updateCreateButtonState()
    }
    
    private fun setupRecyclerView() {
        selectUserAdapter = SelectUserAdapter(allUsersList, selectedUserIds) {
            updateCreateButtonState()
        }
        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = selectUserAdapter
    }

    private fun loadUsers() {
        val currentUserId = Utils.getUserId()
        val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
        val friendsRef = db.getReference(Constants.NODE_RELATIONSHIPS).child(currentUserId).child("friends")
        val usersRef = db.getReference(Constants.NODE_USERS)
        
        friendsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(friendsSnapshot: DataSnapshot) {
                if (!friendsSnapshot.exists()) {
                    allUsersList.clear()
                    selectUserAdapter.submitList(allUsersList)
                    return
                }
                
                val friendIds = friendsSnapshot.children.mapNotNull { it.key }
                
                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        allUsersList.clear()
                        if (snapshot.exists()) {
                            for (userSnapshot in snapshot.children) {
                                val user = userSnapshot.getValue(User::class.java)
                                if (user != null && friendIds.contains(user.uid)) {
                                    if (!isAddMemberMode || !existingMemberIds.contains(user.uid)) {
                                        allUsersList.add(user)
                                    }
                                }
                            }
                            selectUserAdapter.submitList(allUsersList)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CreateGroupActivity, "Lỗi tải danh sách bạn bè", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s?.toString()?.trim()?.lowercase() ?: ""
                if (searchText.isEmpty()) {
                    selectUserAdapter.submitList(allUsersList)
                } else {
                    val filteredList = allUsersList.filter {
                        (it.username?.lowercase()?.contains(searchText) == true) ||
                        (it.phoneNumber?.contains(searchText) == true)
                    }
                    selectUserAdapter.submitList(filteredList)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        btnCreateGroup.setOnClickListener {
            if (isAddMemberMode) {
                if (selectedUserIds.isEmpty()) return@setOnClickListener
                btnCreateGroup.isEnabled = false
                btnCreateGroup.text = "Đang thêm..."
                
                var successCount = 0
                val totalToAdd = selectedUserIds.size
                
                for (userId in selectedUserIds) {
                    val userAdded = allUsersList.find { it.uid == userId }
                    chatRepository.addMemberToGroup(existingRoomId, userId) { success ->
                        if (success && userAdded != null) {
                            chatRepository.sendSystemMessage(existingRoomId, "${userAdded.username} đã được thêm vào nhóm.", userAdded.uid, "đã được thêm vào nhóm.")
                        }
                        successCount++
                        if (successCount == totalToAdd) {
                            Toast.makeText(this, "Đã thêm thành viên", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            } else {
                val groupName = edtGroupName.text.toString().trim()
                if (groupName.isEmpty()) {
                    Toast.makeText(this, "Vui lòng nhập tên nhóm", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (selectedUserIds.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn ít nhất 1 thành viên khác", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val roomId = "group_${System.currentTimeMillis()}"
                val userIds = ArrayList<String>()
                userIds.add(Utils.getUserId())
                userIds.addAll(selectedUserIds)

                val chatRoom = ChatRoom(
                    roomId = roomId,
                    type = "group",
                    userIds = userIds,
                    name = groupName,
                    avatarUrl = "", // Có thể mở rộng cho upload avatar
                    createdBy = Utils.getUserId(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Disable button during creation
                btnCreateGroup.isEnabled = false
                btnCreateGroup.text = "Đang tạo..."

                chatRepository.createChatRoom(chatRoom) { success ->
                    if (success) {
                        Toast.makeText(this, "Tạo nhóm thành công", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, ChatActivity::class.java)
                        intent.putExtra("roomId", roomId)
                        intent.putExtra("userName", groupName)
                        intent.putExtra("isGroup", true)
                        startActivity(intent)
                        finish()
                    } else {
                        btnCreateGroup.isEnabled = true
                        btnCreateGroup.text = "Tạo"
                        Toast.makeText(this, "Lỗi tạo nhóm", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun updateCreateButtonState() {
        btnCreateGroup.isEnabled = selectedUserIds.isNotEmpty()
    }
}
