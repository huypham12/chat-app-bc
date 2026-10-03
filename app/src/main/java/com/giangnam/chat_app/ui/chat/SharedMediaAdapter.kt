package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.Message

class SharedMediaAdapter(
    private val mediaMessages: List<Message>,
    private val onMediaClick: (Message) -> Unit
) : RecyclerView.Adapter<SharedMediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mediaImageView: ImageView = itemView.findViewById(R.id.mediaImageView)
        val videoPlayIcon: ImageView = itemView.findViewById(R.id.videoPlayIcon)

        fun bind(message: Message) {
            // Hiển thị ảnh / video thumbnail
            Glide.with(itemView.context)
                .load(message.mediaUrl)
                .centerCrop()
                .into(mediaImageView)

            // Hiển thị icon play nếu là video hoặc audio (tùy định nghĩa)
            if (message.type == "video") {
                videoPlayIcon.visibility = View.VISIBLE
            } else {
                videoPlayIcon.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onMediaClick(message)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_grid, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaMessages[position])
    }

    override fun getItemCount(): Int = mediaMessages.size
}
