package com.giangnam.chat_app.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.giangnam.chat_app.R
import androidx.activity.viewModels
import com.giangnam.chat_app.ui.main.MainActivity
import com.giangnam.chat_app.databinding.ActivitySearchUserBinding
import com.giangnam.chat_app.data.model.User

class SearchUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchUserBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: SearchUserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ĐÃ SỬA: Thay thế toolbar bằng btnBack theo giao diện mới
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Real-time search as user types
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s.toString().trim()
                if (searchText.length >= 1) { // Start searching from 1 character
                    setupSearchRecyclerView(searchText)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.searchBtn.setOnClickListener {
            val searchText = binding.etSearch.text.toString().trim()
            if (searchText.isNotEmpty()) {
                setupSearchRecyclerView(searchText)
            }
        }
    }

    private fun setupSearchRecyclerView(searchText: String) {
        val query = viewModel.getUsersQuery(searchText)

        val option = FirebaseRecyclerOptions.Builder<User>()
            .setQuery(query, User::class.java)
            .build()

        // If adapter is already initialized and it's the same query (or same structure), 
        // updating options might be better, but recreating for clarity in this refactor
        adapter = SearchUserAdapter(option)
        binding.userList.layoutManager = LinearLayoutManager(this)
        binding.userList.adapter = adapter
        adapter.startListening()
    }

    override fun onStart() {
        super.onStart()
        if (::adapter.isInitialized) {
            adapter.startListening()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::adapter.isInitialized) {
            adapter.stopListening()
        }
    }
}