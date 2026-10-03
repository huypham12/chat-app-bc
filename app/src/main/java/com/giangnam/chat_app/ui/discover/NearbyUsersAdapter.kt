package com.giangnam.chat_app.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.NearbyUserDTO
import com.giangnam.chat_app.databinding.ItemNearbyUserBinding

class NearbyUsersAdapter(
    private val onUserClick: (NearbyUserDTO) -> Unit,
    private val onMessageClick: (NearbyUserDTO) -> Unit
) : RecyclerView.Adapter<NearbyUsersAdapter.ViewHolder>() {

    private val users = mutableListOf<NearbyUserDTO>()

    fun submitList(newUsers: List<NearbyUserDTO>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearbyUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class ViewHolder(private val binding: ItemNearbyUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: NearbyUserDTO) {
            binding.tvDisplayName.text = user.displayName
            
            // Format distance (e.g. 1.23 km)
            binding.tvDistance.text = String.format("%.2f km", user.distance)

            Glide.with(binding.root.context)
                .load(user.avatarUrl)
                .placeholder(R.drawable.ic_person)
                .into(binding.ivAvatar)

            binding.root.setOnClickListener { onUserClick(user) }
            binding.btnMessage.setOnClickListener { onMessageClick(user) }
        }
    }
}
