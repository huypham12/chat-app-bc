package com.giangnam.chat_app.ui.chat

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.RoomMember
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class GroupMembersActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private var currentUserId = Utils.getUserId()
    private var currentUserRole = "member"

    private lateinit var rvMembers: RecyclerView
    private lateinit var btnAddMember: ImageView
    private lateinit var adapter: GroupMembersAdapter

    private val membersList = mutableListOf<RoomMember>()
    private val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private var membersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_group_members)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        roomId = intent.getStringExtra("roomId") ?: ""
        if (roomId.isEmpty()) {
            finish()
            return
        }

        initViews()
        fetchMyRoleAndLoadMembers()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvMembers = findViewById(R.id.rvMembers)
        btnAddMember = findViewById(R.id.btnAddMember)

        btnAddMember.setOnClickListener {
            val intent = android.content.Intent(this, CreateGroupActivity::class.java)
            intent.putExtra("isAddMemberMode", true)
            intent.putExtra("roomId", roomId)
            startActivity(intent)
        }
    }

    private fun fetchMyRoleAndLoadMembers() {
        db.getReference(Constants.NODE_ROOM_MEMBERS).child(roomId).child(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val myMember = snapshot.getValue(RoomMember::class.java)
                    currentUserRole = myMember?.role ?: "member"
                } else {
                    currentUserRole = "member"
                }
                
                // Cho phép bất kỳ ai cũng có thể thêm thành viên
                btnAddMember.visibility = android.view.View.VISIBLE

                setupRecyclerView()
                listenToMembers()
            }
            .addOnFailureListener {
                currentUserRole = "member"
                btnAddMember.visibility = android.view.View.VISIBLE
                setupRecyclerView()
                listenToMembers()
            }
    }

    private fun setupRecyclerView() {
        adapter = GroupMembersAdapter(membersList, currentUserRole) { member, user ->
            showMemberOptionsDialog(member, user)
        }
        rvMembers.layoutManager = LinearLayoutManager(this)
        rvMembers.adapter = adapter
    }

    private fun listenToMembers() {
        val membersRef = db.getReference(Constants.NODE_ROOM_MEMBERS).child(roomId)
        membersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                membersList.clear()
                var adminFound = false
                
                for (child in snapshot.children) {
                    val member = child.getValue(RoomMember::class.java)
                    if (member != null) {
                        membersList.add(member)
                        if (member.uid == currentUserId && member.role == "admin") {
                            adminFound = true
                        }
                    }
                }
                
                // Sort so admins are on top
                membersList.sortByDescending { it.role == "admin" }
                adapter.submitList(membersList)

                // Update my role dynamically if it changed
                if (currentUserRole == "admin" && !adminFound) {
                    currentUserRole = "member"
                    setupRecyclerView() // Re-setup adapter with new role
                } else if (currentUserRole == "member" && adminFound) {
                    currentUserRole = "admin"
                    setupRecyclerView()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        membersRef.addValueEventListener(membersListener!!)
    }

    private fun showMemberOptionsDialog(member: RoomMember, user: User) {
        if (member.uid == currentUserId) return

        val options = mutableListOf<String>()
        
        if (currentUserRole == "admin") {
            options.add("Chuyển quyền Quản trị viên")
            options.add("Xóa khỏi nhóm")
        }

        if (options.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(user.username)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Chuyển quyền Quản trị viên" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Chuyển quyền Quản trị viên")
                            .setMessage("Bạn sẽ mất quyền Quản trị viên và trở thành thành viên bình thường. Bạn có chắc chắn?")
                            .setPositiveButton("Đồng ý") { _, _ ->
                                ChatRepository.getInstance().transferAdminRole(roomId, currentUserId, member.uid) {
                                    if (it) {
                                        ChatRepository.getInstance().sendSystemMessage(roomId, "${user.username} đã trở thành Quản trị viên.", member.uid, "đã trở thành Quản trị viên.")
                                        Toast.makeText(this, "Đã chuyển quyền", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                    "Xóa khỏi nhóm" -> {
                        AlertDialog.Builder(this)
                            .setMessage("Bạn chắc chắn muốn xóa ${user.username} khỏi nhóm?")
                            .setPositiveButton("Xóa") { _, _ ->
                                ChatRepository.getInstance().removeMemberFromGroup(roomId, member.uid) {
                                    if (it) {
                                        ChatRepository.getInstance().sendSystemMessage(roomId, "${user.username} đã bị xóa khỏi nhóm.", member.uid, "đã bị xóa khỏi nhóm.")
                                        Toast.makeText(this, "Đã xóa thành viên", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        membersListener?.let {
            db.getReference(Constants.NODE_ROOM_MEMBERS).child(roomId).removeEventListener(it)
        }
    }
}
