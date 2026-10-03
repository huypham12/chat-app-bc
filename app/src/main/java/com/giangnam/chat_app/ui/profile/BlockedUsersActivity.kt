package com.giangnam.chat_app.ui.profile

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.data.repository.SocialRepository
import com.giangnam.chat_app.data.repository.UserRepository
import com.giangnam.chat_app.databinding.ActivityBlockedUsersBinding
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

class BlockedUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedUsersBinding
    private lateinit var adapter: BlockedUserAdapter
    private val userRepository = UserRepository.getInstance()
    private val socialRepository = SocialRepository.getInstance()
    private val myUid = Utils.getUserId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = BlockedUserAdapter(emptyList()) { user ->
            socialRepository.unblockUser(myUid, user.uid!!) {}
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        if (myUid.isEmpty()) return
        
        FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_USER_BLOCKS)
            .child(myUid)
            .child("blockedUsers")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val blockedIds = snapshot.children.mapNotNull { it.key }
                    if (blockedIds.isEmpty()) {
                        adapter.submitList(emptyList())
                        binding.tvEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        val result = mutableListOf<User>()
                        var count = 0
                        blockedIds.forEach { uid ->
                            userRepository.getUserById(uid) { user ->
                                if (user != null) result.add(user)
                                count++
                                if (count == blockedIds.size) {
                                    adapter.submitList(result)
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
