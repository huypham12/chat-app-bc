package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R

class StickerAdapter(
    private val stickerUrls: List<String>,
    private val onStickerClick: (String) -> Unit
) : RecyclerView.Adapter<StickerAdapter.StickerViewHolder>() {

    inner class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgSticker: ImageView = itemView.findViewById(R.id.imgSticker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
        return StickerViewHolder(view)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        val url = stickerUrls[position]

        // Load ảnh từ URL vào ImageView
        Glide.with(holder.itemView.context)
            .load(url)
            .into(holder.imgSticker)

        // Bắt sự kiện bấm vào sticker
        holder.itemView.setOnClickListener {
            onStickerClick(url)
        }
    }

    override fun getItemCount(): Int = stickerUrls.size
}