package com.giangnam.chat_app.ui.search

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.giangnam.chat_app.R
import com.giangnam.chat_app.utils.Utils
import com.giangnam.chat_app.ui.chat.ChatActivity
import com.giangnam.chat_app.data.model.User

class SearchUserAdapter(options: FirebaseRecyclerOptions<User>) :
    FirebaseRecyclerAdapter<User, SearchUserAdapter.UserViewHolder>(options) {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.user_name)
        val userNumber: TextView = itemView.findViewById(R.id.user_number)
        // KẾT NỐI VỚI ID ẢNH TRONG XML
        val userAvatar: ImageView = itemView.findViewById(R.id.user_avatar)
        val btnMessage: ImageView = itemView.findViewById(R.id.btnMessage)
        val btnFriend: ImageView = itemView.findViewById(R.id.btnFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.search_user_row, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int, model: User) {
        // Gán dữ liệu mặc định an toàn
        holder.userNumber.text = "+84" + model.phoneNumber

        val myUid = Utils.getUserId()
        
        // SỬ DỤNG TOÁN TỬ == ĐỂ CHỐNG CRASH BỞI NULLPOINTEREXCEPTION
        if (model.uid == myUid) {
            holder.userName.text = "${model.username} (Bạn)"
            holder.btnFriend.visibility = View.GONE
            holder.btnMessage.visibility = View.GONE
        } else {
            holder.userName.text = model.username
            holder.btnFriend.visibility = View.VISIBLE
            holder.btnMessage.visibility = View.VISIBLE
        }

        // DÙNG GLIDE ĐỂ TẢI VÀ BO TRÒN ẢNH ĐẠI DIỆN
        val imageUrl = model.avatarUrl ?: ""
        if (imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop() // Lệnh tự động cắt tròn
                .into(holder.userAvatar)
        } else {
            holder.userAvatar.setImageResource(R.drawable.ic_person)
        }

        // Lắng nghe trạng thái bạn bè
        if (model.uid != myUid) {
            com.giangnam.chat_app.data.repository.SocialRepository.getInstance().listenToRelationshipStatus(myUid, model.uid!!) { status ->
                when (status) {
                    com.giangnam.chat_app.data.repository.FriendStatus.NONE -> {
                        holder.btnFriend.setImageResource(android.R.drawable.ic_menu_add)
                        holder.btnFriend.setOnClickListener {
                            com.giangnam.chat_app.data.repository.SocialRepository.getInstance().sendFriendRequest(myUid, model.uid!!) {}
                        }
                    }
                    com.giangnam.chat_app.data.repository.FriendStatus.REQUEST_SENT -> {
                        holder.btnFriend.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                        holder.btnFriend.setOnClickListener {
                            AlertDialog.Builder(holder.itemView.context)
                                .setTitle("Hủy lời mời")
                                .setMessage("Bạn có chắc chắn muốn hủy lời mời kết bạn này không?")
                                .setPositiveButton("Hủy lời mời") { _, _ ->
                                    com.giangnam.chat_app.data.repository.SocialRepository.getInstance().rejectOrCancelFriendRequest(myUid, model.uid!!) {}
                                }
                                .setNegativeButton("Đóng", null)
                                .show()
                        }
                    }
                    com.giangnam.chat_app.data.repository.FriendStatus.REQUEST_RECEIVED -> {
                        holder.btnFriend.setImageResource(android.R.drawable.checkbox_on_background)
                        holder.btnFriend.setOnClickListener {
                            com.giangnam.chat_app.data.repository.SocialRepository.getInstance().acceptFriendRequest(myUid, model.uid!!) {}
                        }
                    }
                    com.giangnam.chat_app.data.repository.FriendStatus.FRIEND -> {
                        holder.btnFriend.setImageResource(R.drawable.ic_person) // or an icon indicating friends
                        holder.btnFriend.setOnClickListener {
                            // Mở trang cá nhân để xoá bạn bè nếu muốn
                            val intent = Intent(holder.itemView.context, com.giangnam.chat_app.ui.profile.UserInfoActivity::class.java).apply {
                                putExtra("userId", model.uid)
                            }
                            holder.itemView.context.startActivity(intent)
                        }
                    }
                }
            }
        }

        // Click vào nút Nhắn tin -> Mở Chat
        holder.btnMessage.setOnClickListener {
            val intent = Intent(holder.itemView.context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("userName", model.username)
                putExtra("userId", model.uid)
            }
            holder.itemView.context.startActivity(intent)
        }

        // Click vào avatar -> Mở Profile
        holder.userAvatar.setOnClickListener {
            val intent = Intent(holder.itemView.context, com.giangnam.chat_app.ui.profile.UserInfoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("userId", model.uid)
            }
            holder.itemView.context.startActivity(intent)
        }
        
        // Click vào toàn bộ dòng -> Mở Profile thay vì Chat
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, com.giangnam.chat_app.ui.profile.UserInfoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("userId", model.uid)
            }
            holder.itemView.context.startActivity(intent)
        }
    }
}