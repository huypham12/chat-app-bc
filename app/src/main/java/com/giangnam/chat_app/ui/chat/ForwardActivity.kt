package com.giangnam.chat_app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.ChatRoom
import com.giangnam.chat_app.data.model.ForwardedFrom
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.data.repository.ChatRepository
import com.giangnam.chat_app.databinding.ActivityForwardBinding
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ForwardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForwardBinding
    private val inboxList = mutableListOf<InboxItem>()
    private val filteredList = mutableListOf<InboxItem>()
    private val selectedRoomIds = mutableSetOf<String>()
    
    private var originalMessage: Message? = null
    private var originalRoomId: String? = null

    data class InboxItem(
        val roomId: String,
        val displayName: String,
        val photoUrl: String,
        val isGroup: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForwardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val messageId = intent.getStringExtra("messageId")
        originalRoomId = intent.getStringExtra("roomId")

        if (messageId == null || originalRoomId == null) {
            Toast.makeText(this, "Lỗi dữ liệu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.rvRecentChats.layoutManager = LinearLayoutManager(this)
        val adapter = ForwardAdapter()
        binding.rvRecentChats.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString(), adapter)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSendForward.setOnClickListener {
            forwardMessageToSelectedRooms()
        }

        loadOriginalMessage(messageId)
        loadUserInbox(adapter)
    }

    private fun loadOriginalMessage(messageId: String) {
        val msgRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOMS)
            .child(originalRoomId!!)
            .child(Constants.NODE_MESSAGES)
            .child(messageId)
            
        msgRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                originalMessage = snapshot.getValue(Message::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadUserInbox(adapter: ForwardAdapter) {
        val myUid = Utils.getUserId()
        val inboxRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_USER_INBOX)
            .child(myUid)

        binding.progressBar.visibility = View.VISIBLE

        inboxRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val db = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
                CoroutineScope(Dispatchers.IO).launch {
                    val tempList = mutableListOf<InboxItem>()
                    for (child in snapshot.children) {
                        val roomId = child.key ?: continue
                        val roomSnapshot = db.getReference(Constants.NODE_ROOMS).child(roomId).get().await()
                        val chatRoom = roomSnapshot.getValue(ChatRoom::class.java) ?: continue
                        
                        if (chatRoom.type == "group" && chatRoom.name != null) {
                            // Group - only add if user is still a member
                            if (chatRoom.userIds.contains(myUid)) {
                                tempList.add(InboxItem(roomId, chatRoom.name ?: "", chatRoom.avatarUrl ?: "", true))
                            }
                        } else {
                            // 1-1 chat
                            val otherUid = chatRoom.userIds.find { it != myUid } ?: continue
                            val userSnapshot = db.getReference(Constants.NODE_USERS).child(otherUid).get().await()
                            val user = userSnapshot.getValue(User::class.java)
                            if (user != null) {
                                tempList.add(InboxItem(roomId, user.username ?: "Unknown", user.avatarUrl ?: "", false))
                            }
                        }
                    }
                    runOnUiThread {
                        inboxList.clear()
                        inboxList.addAll(tempList)
                        filteredList.clear()
                        filteredList.addAll(tempList)
                        adapter.notifyDataSetChanged()
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
            }
        })
    }

    private fun filterList(query: String, adapter: ForwardAdapter) {
        filteredList.clear()
        if (query.isEmpty()) {
            filteredList.addAll(inboxList)
        } else {
            val lowerCaseQuery = query.lowercase()
            for (item in inboxList) {
                if (item.displayName.lowercase().contains(lowerCaseQuery)) {
                    filteredList.add(item)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateSendButton() {
        val count = selectedRoomIds.size
        binding.btnSendForward.text = "Gửi ($count)"
        binding.btnSendForward.isEnabled = count > 0
    }

    private fun forwardMessageToSelectedRooms() {
        val messageToForward = originalMessage
        if (messageToForward == null) {
            Toast.makeText(this, "Tin nhắn gốc chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSendForward.isEnabled = false

        val forwardedFrom = ForwardedFrom(
            roomId = originalRoomId!!,
            messageId = messageToForward.messageId,
            senderId = messageToForward.senderId
        )

        val myUid = Utils.getUserId()
        val repo = ChatRepository.getInstance()

        CoroutineScope(Dispatchers.IO).launch {
            for (roomId in selectedRoomIds) {
                val newMsg = Message(
                    text = messageToForward.text,
                    senderId = myUid, // Sent by me now
                    type = messageToForward.type,
                    mediaUrl = messageToForward.mediaUrl,
                    timestamp = System.currentTimeMillis(),
                    forwardedFrom = forwardedFrom
                )
                repo.sendMessage(roomId, newMsg) {}
            }
            runOnUiThread {
                Toast.makeText(this@ForwardActivity, "Đã chuyển tiếp tin nhắn", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    inner class ForwardAdapter : RecyclerView.Adapter<ForwardAdapter.ViewHolder>() {
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val userAvatar: ImageView = itemView.findViewById(R.id.userAvatar)
            val userName: TextView = itemView.findViewById(R.id.userName)
            val checkboxSelect: CheckBox = itemView.findViewById(R.id.checkboxSelect)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_forward_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = filteredList[position]
            holder.userName.text = item.displayName
            
            val placeholder = if (item.isGroup) R.drawable.ic_person else R.drawable.ic_person
            Glide.with(this@ForwardActivity).load(item.photoUrl).placeholder(placeholder).into(holder.userAvatar)

            holder.checkboxSelect.isChecked = selectedRoomIds.contains(item.roomId)

            holder.itemView.setOnClickListener {
                if (selectedRoomIds.contains(item.roomId)) {
                    selectedRoomIds.remove(item.roomId)
                } else {
                    selectedRoomIds.add(item.roomId)
                }
                notifyItemChanged(position)
                updateSendButton()
            }
        }

        override fun getItemCount(): Int = filteredList.size
    }
}
