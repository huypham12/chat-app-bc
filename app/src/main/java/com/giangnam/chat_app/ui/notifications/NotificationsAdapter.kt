package com.giangnam.chat_app.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.Notification
import com.giangnam.chat_app.databinding.ItemNotificationBinding
import com.giangnam.chat_app.utils.Utils

class NotificationsAdapter(
    private var usersMap: Map<String, com.giangnam.chat_app.data.model.User>,
    private val onClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationsAdapter.NotificationViewHolder>(DiffCallback) {

    fun updateUsersMap(newMap: Map<String, com.giangnam.chat_app.data.model.User>) {
        this.usersMap = newMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            binding.root.setOnClickListener { onClick(notification) }
            
            // Unread indicator
            binding.vUnreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE
            
            // Time
            binding.tvTime.text = Utils.formatTimestamp(notification.updatedAt)

            // Get Actor Info
            val actorUser = usersMap[notification.actorId]
            val nameStr = actorUser?.username ?: "Ai đó"
            
            var actionIcon = R.drawable.ic_person
            
            val contentText = if (notification.title != null && notification.body != null) {
                "${notification.title}\n${notification.body}"
            } else {
                when (notification.type) {
                    "follow" -> {
                        actionIcon = R.drawable.ic_person
                        if (notification.count > 1) "$nameStr và ${notification.count - 1} người khác đã theo dõi bạn."
                        else "$nameStr đã theo dõi bạn."
                    }
                    "mention" -> {
                        actionIcon = R.drawable.ic_chat
                        if (notification.count > 1) "$nameStr và ${notification.count - 1} người khác đã nhắc đến bạn."
                        else "$nameStr đã nhắc đến bạn."
                    }
                    "group_added" -> {
                        actionIcon = android.R.drawable.ic_menu_myplaces
                        "$nameStr đã thêm bạn vào một nhóm."
                    }
                    "friend_request" -> {
                        actionIcon = R.drawable.ic_person
                        "$nameStr đã gửi cho bạn lời mời kết bạn."
                    }
                    "friend_request_accepted" -> {
                        actionIcon = android.R.drawable.checkbox_on_background
                        "$nameStr đã chấp nhận lời mời kết bạn của bạn."
                    }
                    else -> "Bạn có thông báo mới."
                }
            }
            
            binding.tvContent.text = contentText
            binding.ivActionIcon.setImageResource(actionIcon)
            
            // Load actor avatar
            val avatarUrl = actorUser?.avatarUrl ?: ""
            if (avatarUrl.isNotEmpty()) {
                Glide.with(binding.root)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_person)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Notification>() {
            override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
                return oldItem.notificationId == newItem.notificationId
            }

            override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
                return oldItem == newItem
            }
        }
    }
}
