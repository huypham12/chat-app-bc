package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.User

class SelectUserAdapter(
    private var userList: List<User>,
    private val selectedUserIds: MutableList<String>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<SelectUserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.user_name)
        val userNumber: TextView = itemView.findViewById(R.id.user_number)
        val userAvatar: ImageView = itemView.findViewById(R.id.user_avatar)
        val checkboxSelect: CheckBox = itemView.findViewById(R.id.checkbox_select)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_select_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        
        holder.userName.text = user.username
        holder.userNumber.text = "+84" + user.phoneNumber

        val imageUrl = user.avatarUrl ?: ""
        if (imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.userAvatar)
        } else {
            holder.userAvatar.setImageResource(R.drawable.ic_person)
        }

        holder.checkboxSelect.isChecked = selectedUserIds.contains(user.uid)

        holder.itemView.setOnClickListener {
            if (selectedUserIds.contains(user.uid)) {
                selectedUserIds.remove(user.uid)
                holder.checkboxSelect.isChecked = false
            } else {
                selectedUserIds.add(user.uid)
                holder.checkboxSelect.isChecked = true
            }
            onSelectionChanged()
        }
    }

    override fun getItemCount(): Int = userList.size

    fun submitList(newList: List<User>) {
        userList = newList
        notifyDataSetChanged()
    }
}
