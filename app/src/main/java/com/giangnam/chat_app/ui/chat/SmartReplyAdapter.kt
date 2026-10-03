package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.giangnam.chat_app.R

class SmartReplyAdapter(
    private val replies: List<String>,
    private val onReplyClick: (String) -> Unit
) : RecyclerView.Adapter<SmartReplyAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val replyChipTv: TextView = itemView.findViewById(R.id.replyChipTv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_smart_reply, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reply = replies[position]
        holder.replyChipTv.text = reply
        holder.itemView.setOnClickListener {
            onReplyClick(reply)
        }
    }

    override fun getItemCount(): Int = replies.size
}