package com.giangnam.chat_app.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.Message
import com.giangnam.chat_app.utils.Constants
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SearchMessageActivity : AppCompatActivity() {

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnBack: ImageView
    
    private var roomId: String = ""
    private val allMessages = mutableListOf<Message>()
    private lateinit var adapter: SearchMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_message)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        roomId = intent.getStringExtra("roomId") ?: ""

        rvSearchResults = findViewById(R.id.rvSearchResults)
        etSearch = findViewById(R.id.etSearch)
        btnBack = findViewById(R.id.btnBack)

        adapter = SearchMessageAdapter(emptyList()) { message ->
            // In the future, this could return the messageId to scroll to it
            // For now, just show a toast or do nothing since the user can read it
            android.widget.Toast.makeText(this, "Bạn đã chọn tin nhắn lúc " + 
                java.text.SimpleDateFormat("HH:mm dd/MM").format(java.util.Date(message.timestamp)), 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvSearchResults.adapter = adapter

        btnBack.setOnClickListener {
            finish()
        }

        if (roomId.isNotEmpty()) {
            loadAllMessages()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMessages(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAllMessages() {
        val messagesRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
            .getReference(Constants.NODE_ROOMS)
            .child(roomId)
            .child("messages")

        messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allMessages.clear()
                for (child in snapshot.children) {
                    val message = child.getValue(Message::class.java)
                    if (message != null && (message.type == "text" || message.type.isNullOrEmpty())) {
                        allMessages.add(message)
                    }
                }
                // Sort by newest first
                allMessages.sortByDescending { it.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                android.widget.Toast.makeText(this@SearchMessageActivity, "Lỗi tải tin nhắn", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun filterMessages(query: String) {
        if (query.isEmpty()) {
            adapter.updateData(emptyList())
            return
        }
        
        val lowercaseQuery = query.lowercase()
        val filtered = allMessages.filter { 
            it.text != null && it.text!!.lowercase().contains(lowercaseQuery) 
        }
        adapter.updateData(filtered)
    }
}
