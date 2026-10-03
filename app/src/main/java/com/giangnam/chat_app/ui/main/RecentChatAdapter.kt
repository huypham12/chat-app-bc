package com.giangnam.chat_app.ui.main

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.ui.chat.ChatActivity
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.FirebaseDatabase

class RecentChatAdapter(
    private val userNameMap: Map<String, String>,
    val onLongClick: (RecentChatItem) -> Unit
) : ListAdapter<RecentChatItem, RecentChatAdapter.RecentChatViewHolder>(RecentChatDiffCallback()) {

    inner class RecentChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName = itemView.findViewById<TextView>(R.id.user_name)
        val lastMessage = itemView.findViewById<TextView>(R.id.lastText)
        val time = itemView.findViewById<TextView>(R.id.time)
        val unreadBadge = itemView.findViewById<TextView>(R.id.unread_badge)
        val userAvatar = itemView.findViewById<ImageView>(R.id.user_avatar)
        val pinIcon = itemView.findViewById<ImageView>(R.id.pinIcon)
        val muteIcon = itemView.findViewById<ImageView>(R.id.muteIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recent_user_row, parent, false)
        return RecentChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentChatViewHolder, position: Int) {
        val item = getItem(position)
        val room = item.room
        val inbox = item.inbox
        
        // 1. Ẩn nếu isHidden == true
        if (inbox.isHidden) {
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val currentUserId = Utils.getUserId()
        var otherUserId = ""
        var roomName = ""
        
        if (room.type == "group") {
            roomName = room.name ?: "Nhóm chưa đặt tên"
            holder.userName.text = roomName
        } else {
            otherUserId = room.userIds.firstOrNull { it != currentUserId } ?: currentUserId
            if (otherUserId == "AI_BOT_001") {
                roomName = "Trợ lý AI"
            } else {
                roomName = userNameMap[otherUserId] ?: "Unknown User"
            }
            holder.userName.text = roomName
        }

        // 2. Tải ảnh đại diện
        if (room.type == "group") {
            if (!room.avatarUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(room.avatarUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.userAvatar)
            } else {
                holder.userAvatar.setImageResource(R.drawable.ic_group)
            }
        } else {
            if (otherUserId == "AI_BOT_001") {
                Glide.with(holder.itemView.context)
                    .load("https://cdn-icons-png.flaticon.com/512/4712/4712027.png")
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.userAvatar)
            } else {
                FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS).child(otherUserId)
                    .get()
                    .addOnSuccessListener { userSnapshot ->
                        if (userSnapshot.exists()) {
                            val imageUrl = userSnapshot.child("avatarUrl").value as? String ?: ""
                            if (imageUrl.isNotEmpty()) {
                                Glide.with(holder.itemView.context)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(holder.userAvatar)
                            } else {
                                holder.userAvatar.setImageResource(R.drawable.ic_person)
                            }
                        }
                    }
            }
        }

        // 3. Tin nhắn cuối & Unread
        val lastMsg = room.lastMessage
        val isMessageByMe = lastMsg?.senderId == currentUserId

        if (lastMsg != null) {
            val messageText = lastMsg.preview
            
            // Dùng unreadCount từ inbox thay vì isRead chung của nhóm
            val unreadCount = inbox.unreadCount
            
            if (isMessageByMe) {
                holder.lastMessage.text = "Bạn: $messageText"
                holder.lastMessage.setTypeface(null, Typeface.NORMAL)
                holder.lastMessage.setTextColor(Color.GRAY)
                holder.unreadBadge.visibility = View.GONE
            } else {
                holder.lastMessage.text = messageText
                if (unreadCount > 0) {
                    holder.lastMessage.setTypeface(null, Typeface.BOLD)
                    holder.lastMessage.setTextColor(Color.BLACK)
                    holder.unreadBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                    holder.unreadBadge.visibility = View.VISIBLE
                } else {
                    holder.lastMessage.setTypeface(null, Typeface.NORMAL)
                    holder.lastMessage.setTextColor(Color.GRAY)
                    holder.unreadBadge.visibility = View.GONE
                }
            }
        } else {
            holder.lastMessage.text = "Gửi lời chào 👋 !!"
            holder.lastMessage.setTypeface(null, Typeface.NORMAL)
            holder.lastMessage.setTextColor(Color.GRAY)
            holder.unreadBadge.visibility = View.GONE
        }

        // 4. Sự kiện
        holder.userAvatar.setOnClickListener { view ->
            if (room.type != "group") {
                val bundle = android.os.Bundle().apply {
                    putString("userId", otherUserId)
                }
                view.findNavController().navigate(R.id.profileFragment, bundle)
            }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, ChatActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("roomId", room.roomId)
            intent.putExtra("userName", roomName)
            if (room.type != "group") {
                intent.putExtra("userId", otherUserId)
            }
            holder.itemView.context.startActivity(intent)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }

        val timeInMillis = room.updatedAt
        if (timeInMillis > 0L) {
            val date = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            holder.time.text = date.format(java.util.Date(timeInMillis))
        } else {
            holder.time.text = ""
        }
        
        // 5. Hiển thị pinIcon nếu ghim
        holder.pinIcon?.visibility = if (inbox.isPinned) View.VISIBLE else View.GONE
        
        // 6. Hiển thị muteIcon nếu tắt thông báo
        val isCurrentlyMuted = inbox.isMuted || (inbox.mutedUntil == -1L || inbox.mutedUntil > System.currentTimeMillis())
        holder.muteIcon?.visibility = if (isCurrentlyMuted) View.VISIBLE else View.GONE
    }
}

class RecentChatDiffCallback : DiffUtil.ItemCallback<RecentChatItem>() {
    override fun areItemsTheSame(oldItem: RecentChatItem, newItem: RecentChatItem): Boolean {
        return oldItem.room.roomId == newItem.room.roomId
    }

    override fun areContentsTheSame(oldItem: RecentChatItem, newItem: RecentChatItem): Boolean {
        return oldItem.room == newItem.room && oldItem.inbox == newItem.inbox
    }
}