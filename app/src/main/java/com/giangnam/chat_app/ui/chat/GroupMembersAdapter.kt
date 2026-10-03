package com.giangnam.chat_app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.RoomMember
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.FirebaseDatabase

class GroupMembersAdapter(
    private var memberList: List<RoomMember>,
    private val currentRole: String, // admin or member
    private val onMoreOptionsClick: (RoomMember, User) -> Unit
) : RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder>() {

    private val usersMap = mutableMapOf<String, User>()

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val memberName: TextView = itemView.findViewById(R.id.memberName)
        val memberRole: TextView = itemView.findViewById(R.id.memberRole)
        val memberAvatar: ImageView = itemView.findViewById(R.id.memberAvatar)
        val btnMoreOptions: ImageView = itemView.findViewById(R.id.btnMoreOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = memberList[position]
        
        holder.memberRole.visibility = if (member.role == "admin") View.VISIBLE else View.GONE

        // Fetch User Info
        if (usersMap.containsKey(member.uid)) {
            bindUser(holder, usersMap[member.uid]!!, member)
        } else {
            FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference(Constants.NODE_USERS)
                .child(member.uid).get().addOnSuccessListener { snapshot ->
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        usersMap[member.uid] = user
                        bindUser(holder, user, member)
                    }
                }
        }
    }

    private fun bindUser(holder: MemberViewHolder, user: User, member: RoomMember) {
        holder.memberName.text = if (user.uid == Utils.getUserId()) "${user.username} (Bạn)" else user.username

        val imageUrl = user.avatarUrl ?: ""
        if (imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.memberAvatar)
        } else {
            holder.memberAvatar.setImageResource(R.drawable.ic_person)
        }

        // Only Admin can see the More Options button, and only for OTHER users
        if (currentRole == "admin" && user.uid != Utils.getUserId()) {
            holder.btnMoreOptions.visibility = View.VISIBLE
            holder.btnMoreOptions.setOnClickListener {
                onMoreOptionsClick(member, user)
            }
        } else {
            holder.btnMoreOptions.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = memberList.size

    fun submitList(newList: List<RoomMember>) {
        memberList = newList
        notifyDataSetChanged()
    }
}
