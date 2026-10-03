package com.giangnam.chat_app.ui.profile

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

class BlockedUserAdapter(
    private var users: List<User>,
    private val onUnblockClick: (User) -> Unit
) : RecyclerView.Adapter<BlockedUserAdapter.BlockedViewHolder>() {

    fun submitList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }

    inner class BlockedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDesc: TextView = itemView.findViewById(R.id.tvDesc)
        val btnMessage: ImageView = itemView.findViewById(R.id.btnMessage)
        val btnAction: ImageView = itemView.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return BlockedViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlockedViewHolder, position: Int) {
        val user = users[position]
        
        holder.tvName.text = user.username
        holder.tvDesc.text = "Bị chặn"
        
        // Hide message button
        holder.btnMessage.visibility = View.GONE
        
        // Show unblock action
        holder.btnAction.visibility = View.VISIBLE
        holder.btnAction.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Example icon for unblock
        holder.btnAction.setColorFilter(android.graphics.Color.parseColor("#EF4444"))

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

        holder.btnAction.setOnClickListener {
            onUnblockClick(user)
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
