package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.utils.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchMessageAdapter(
    private var messages: List<Message>,
    private val onItemClick: (Message) -> Unit
) : RecyclerView.Adapter<SearchMessageAdapter.ViewHolder>() {

    private val userNameCache = mutableMapOf<String, String>()

    fun updateData(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessageContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        val tvSender: TextView = itemView.findViewById(R.id.tvMessageSender)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.tvMessage.text = msg.text
        
        val date = Date(msg.timestamp)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.tvTime.text = format.format(date)
        
        if (msg.senderId == Utils.getUserId()) {
            holder.tvSender.text = "Bạn"
        } else {
            val cachedName = userNameCache[msg.senderId]
            if (cachedName != null) {
                holder.tvSender.text = cachedName
            } else {
                holder.tvSender.text = "Đang tải..."
                com.google.firebase.database.FirebaseDatabase.getInstance(com.giangnam.chat_app.utils.Constants.DATABASE_URL)
                    .getReference(com.giangnam.chat_app.utils.Constants.NODE_USERS)
                    .child(msg.senderId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val name = snapshot.child("userName").getValue(String::class.java) ?: "Người dùng"
                            userNameCache[msg.senderId] = name
                            holder.tvSender.text = name
                        } else {
                            holder.tvSender.text = "Người dùng"
                        }
                    }
            }
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(msg)
        }
    }

    override fun getItemCount(): Int = messages.size
}
