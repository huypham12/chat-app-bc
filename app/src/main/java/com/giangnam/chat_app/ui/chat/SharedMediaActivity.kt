package com.giangnam.chat_app.ui.chat

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.utils.Constants
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SharedMediaActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private lateinit var mediaRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var adapter: SharedMediaAdapter
    private val allMediaList = ArrayList<Message>()
    private val displayedMediaList = ArrayList<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_media)

        roomId = intent.getStringExtra("roomId") ?: ""

        if (roomId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy phòng chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadMedia()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        mediaRecyclerView = findViewById(R.id.mediaRecyclerView)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        mediaRecyclerView.layoutManager = GridLayoutManager(this, 3) // 3 cột
        adapter = SharedMediaAdapter(displayedMediaList) { message ->
            if (message.type == "image") {
                // Tận dụng dialog xem ảnh đã có hoặc tự làm
                val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.setContentView(R.layout.dialog_image_viewer)
                val photoView = dialog.findViewById<android.widget.ImageView>(R.id.fullscreenImageView)
                val closeBtn = dialog.findViewById<android.widget.ImageView>(R.id.closeImageViewBtn)
                
                com.bumptech.glide.Glide.with(this).load(message.mediaUrl).into(photoView)
                closeBtn.setOnClickListener { dialog.dismiss() }
                dialog.show()
            } else {
                Toast.makeText(this, "Chỉ hỗ trợ xem ảnh hiện tại", Toast.LENGTH_SHORT).show()
            }
        }
        mediaRecyclerView.adapter = adapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterMedia(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun filterMedia(tabPosition: Int) {
        displayedMediaList.clear()
        when (tabPosition) {
            0 -> displayedMediaList.addAll(allMediaList) // Tất cả
            1 -> displayedMediaList.addAll(allMediaList.filter { it.type == "image" }) // Ảnh
            2 -> displayedMediaList.addAll(allMediaList.filter { it.type == "video" }) // Video
        }
        adapter.notifyDataSetChanged()

        if (displayedMediaList.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            mediaRecyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            mediaRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun loadMedia() {
        val messagesRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOMS)
            .child(roomId)
            .child(Constants.NODE_MESSAGES)

        messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allMediaList.clear()
                for (child in snapshot.children) {
                    val message = child.getValue(Message::class.java)
                    if (message != null && (message.type == "image" || message.type == "video")) {
                        allMediaList.add(message)
                    }
                }
                
                // Hiển thị mới nhất trước
                allMediaList.sortByDescending { it.timestamp }
                
                // Lọc mặc định tab đầu tiên (Tất cả)
                filterMedia(0)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SharedMediaActivity, "Lỗi tải ảnh", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
