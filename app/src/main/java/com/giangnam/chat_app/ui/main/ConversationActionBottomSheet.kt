package com.giangnam.chat_app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.giangnam.chat_app.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ConversationActionBottomSheet(
    private val isPinned: Boolean,
    private val isMuted: Boolean,
    private val onActionSelected: (Action) -> Unit
) : BottomSheetDialogFragment() {

    enum class Action {
        PIN_UNPIN, MUTE_UNMUTE, DELETE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_conversation_action, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnPin = view.findViewById<LinearLayout>(R.id.btnPin)
        val textPin = view.findViewById<TextView>(R.id.textPin)
        
        val btnMute = view.findViewById<LinearLayout>(R.id.btnMute)
        val textMute = view.findViewById<TextView>(R.id.textMute)
        
        val btnDelete = view.findViewById<LinearLayout>(R.id.btnDelete)

        textPin.text = if (isPinned) "Bỏ ghim hội thoại" else "Ghim hội thoại"
        textMute.text = if (isMuted) "Bật thông báo" else "Tắt thông báo"

        btnPin.setOnClickListener {
            onActionSelected(Action.PIN_UNPIN)
            dismiss()
        }

        btnMute.setOnClickListener {
            onActionSelected(Action.MUTE_UNMUTE)
            dismiss()
        }

        btnDelete.setOnClickListener {
            onActionSelected(Action.DELETE)
            dismiss()
        }
    }
}
