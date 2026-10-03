package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.User

class MentionAdapter(
    private var usersList: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<MentionAdapter.MentionViewHolder>() {

    inner class MentionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mention, parent, false)
        return MentionViewHolder(view)
    }

    override fun onBindViewHolder(holder: MentionViewHolder, position: Int) {
        val user = usersList[position]
        holder.tvName.text = user.username
        if (!user.avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context).load(user.avatarUrl).circleCrop().into(holder.imgAvatar)
        } else {
            holder.imgAvatar.setImageResource(R.drawable.ic_person)
        }

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }
    }

    override fun getItemCount(): Int = usersList.size

    fun submitList(newList: List<User>) {
        usersList = newList
        notifyDataSetChanged()
    }
}
