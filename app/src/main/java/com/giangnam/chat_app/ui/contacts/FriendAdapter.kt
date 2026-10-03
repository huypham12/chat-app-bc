package com.giangnam.chat_app.ui.contacts

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.ui.chat.ChatActivity
import com.giangnam.chat_app.ui.profile.UserInfoActivity
import com.giangnam.chat_app.utils.Utils

enum class FriendAdapterType {
    FRIEND, REQUEST, SUGGESTION
}

class FriendAdapter(
    private var users: List<User>,
    private val type: FriendAdapterType,
    private val onActionClick: (User) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    fun submitList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDesc: TextView = itemView.findViewById(R.id.tvDesc)
        val btnMessage: ImageView = itemView.findViewById(R.id.btnMessage)
        val btnAction: ImageView = itemView.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = users[position]
        
        holder.tvName.text = user.username
        
        when (type) {
            FriendAdapterType.FRIEND -> {
                holder.tvDesc.text = user.phoneNumber
                holder.btnAction.visibility = View.GONE
            }
            FriendAdapterType.REQUEST -> {
                holder.tvDesc.text = "Gửi lời mời kết bạn"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnAction.setImageResource(android.R.drawable.ic_menu_add)
            }
            FriendAdapterType.SUGGESTION -> {
                holder.tvDesc.text = "Bạn chung"
                holder.btnAction.visibility = View.VISIBLE
                holder.btnAction.setImageResource(android.R.drawable.ic_menu_add)
            }
        }

        val imageUrl = user.avatarUrl ?: ""
        if (imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.ivAvatar)
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_person)
        }

        holder.btnMessage.setOnClickListener {
            val intent = Intent(holder.itemView.context, ChatActivity::class.java).apply {
                putExtra("userName", user.username)
                putExtra("userId", user.uid)
            }
            holder.itemView.context.startActivity(intent)
        }

        holder.btnAction.setOnClickListener {
            onActionClick(user)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, UserInfoActivity::class.java).apply {
                putExtra("userId", user.uid)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = users.size
}
