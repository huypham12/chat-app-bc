package com.giangnam.chat_app.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.giangnam.chat_app.databinding.FragmentMutualFriendsBinding
import kotlinx.coroutines.launch

class MutualFriendsFragment : Fragment() {

    private var _binding: FragmentMutualFriendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ContactsViewModel by activityViewModels()
    private lateinit var adapter: FriendAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMutualFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FriendAdapter(emptyList(), FriendAdapterType.SUGGESTION) { user ->
            viewModel.sendFriendRequest(user.uid)
            // Lạc quan: xóa khỏi list ngay
            val currentList = viewModel.mutualFriends.value.toMutableList()
            currentList.remove(user)
            adapter.submitList(currentList)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mutualFriends.collect { list ->
                adapter.submitList(list)
                if (list.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "Không có gợi ý nào"
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
