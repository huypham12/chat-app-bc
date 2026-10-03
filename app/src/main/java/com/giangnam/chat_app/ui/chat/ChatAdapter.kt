package com.giangnam.chat_app.ui.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.giangnam.chat_app.R
import com.giangnam.chat_app.utils.Utils
import com.giangnam.chat_app.data.model.Message

class ChatAdapter(
    options: FirebaseRecyclerOptions<Message>,
    private val friendName: String,
    private val onMessageLongClick: ((Message) -> Unit)? = null,
    private val onReactionClick: ((Message) -> Unit)? = null
) : FirebaseRecyclerAdapter<Message, ChatAdapter.ChatViewHolder>(options) {

    companion object {
        var mediaPlayer: android.media.MediaPlayer? = null
        var currentlyPlayingUrl: String? = null
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val leftHolder: MaterialCardView = itemView.findViewById(R.id.leftHolder)
        val rightHolder: MaterialCardView = itemView.findViewById(R.id.rightHolder)
        val leftSenderName: TextView = itemView.findViewById(R.id.leftSenderName)
        val leftMessage: TextView = itemView.findViewById(R.id.leftChat)
        val rightMessage: TextView = itemView.findViewById(R.id.rightChat)
        val leftEditedLabel: TextView = itemView.findViewById(R.id.leftEditedLabel)
        val rightEditedLabel: TextView = itemView.findViewById(R.id.rightEditedLabel)
        val leftReactions: TextView = itemView.findViewById(R.id.leftReactions)
        val rightReactions: TextView = itemView.findViewById(R.id.rightReactions)
        val leftImage: ImageView = itemView.findViewById(R.id.leftImage)
        val rightImage: ImageView = itemView.findViewById(R.id.rightImage)
        val leftContentLayout: LinearLayout = itemView.findViewById(R.id.leftContentLayout)
        val rightContentLayout: LinearLayout = itemView.findViewById(R.id.rightContentLayout)

        val leftAudioLayout: LinearLayout = itemView.findViewById(R.id.leftAudioLayout)
        val rightAudioLayout: LinearLayout = itemView.findViewById(R.id.rightAudioLayout)
        val leftPlayPauseBtn: ImageView = itemView.findViewById(R.id.leftPlayPauseBtn)
        val rightPlayPauseBtn: ImageView = itemView.findViewById(R.id.rightPlayPauseBtn)
        
        val leftAudioSeekBar: android.widget.SeekBar = itemView.findViewById(R.id.leftAudioSeekBar)
        val leftAudioTime: TextView = itemView.findViewById(R.id.leftAudioTime)
        val rightAudioSeekBar: android.widget.SeekBar = itemView.findViewById(R.id.rightAudioSeekBar)
        val rightAudioTime: TextView = itemView.findViewById(R.id.rightAudioTime)
        
        val rightReadStatus: TextView = itemView.findViewById(R.id.rightReadStatus)
        val systemMessage: TextView = itemView.findViewById(R.id.systemMessage)
        
        val leftReplyBox: LinearLayout = itemView.findViewById(R.id.leftReplyBox)
        val rightReplyBox: LinearLayout = itemView.findViewById(R.id.rightReplyBox)
        val leftReplyName: TextView = itemView.findViewById(R.id.leftReplyName)
        val rightReplyName: TextView = itemView.findViewById(R.id.rightReplyName)
        val leftReplyContent: TextView = itemView.findViewById(R.id.leftReplyContent)
        val rightReplyContent: TextView = itemView.findViewById(R.id.rightReplyContent)
        
        val leftForwardTag: TextView = itemView.findViewById(R.id.leftForwardTag)
        val rightForwardTag: TextView = itemView.findViewById(R.id.rightForwardTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_row, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int, model: Message) {
        val myUid = Utils.getUserId()
        if (model.deletedFor[myUid] == true) {
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val longClickListener = View.OnLongClickListener {
            onMessageLongClick?.invoke(model)
            true
        }
        holder.itemView.setOnLongClickListener(longClickListener)
        holder.leftImage.setOnLongClickListener(longClickListener)
        holder.rightImage.setOnLongClickListener(longClickListener)

        val isMyMessage = model.senderId == myUid
        val context = holder.itemView.context
        val scale = holder.itemView.resources.displayMetrics.density
        val stickerSizePx = (120 * scale + 0.5f).toInt()
        val padding12 = (12 * scale + 0.5f).toInt()
        
        val displayText: CharSequence = if (model.isRevoked) {
            if (isMyMessage) "Bạn đã thu hồi một tin nhắn" else "$friendName đã thu hồi một tin nhắn"
        } else {
            val text = model.text ?: ""
            if (model.mentionedNames != null && model.mentionedNames.isNotEmpty()) {
                val spannable = android.text.SpannableStringBuilder(text)
                for (name in model.mentionedNames) {
                    val mentionTag = "@$name"
                    var startIndex = text.indexOf(mentionTag)
                    while (startIndex != -1) {
                        val endIndex = startIndex + mentionTag.length
                        spannable.setSpan(
                            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                            startIndex, endIndex,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        val color = if (isMyMessage) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#03A9F4")
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(color),
                            startIndex, endIndex,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        if (isMyMessage) {
                            spannable.setSpan(
                                android.text.style.UnderlineSpan(),
                                startIndex, endIndex,
                                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        startIndex = text.indexOf(mentionTag, endIndex)
                    }
                }
                spannable
            } else {
                if (model.senderId == "AI_BOT_001" && (text.contains("**") || text.contains("*"))) {
                    var htmlText = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
                    htmlText = htmlText.replace("\n", "<br>")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        @Suppress("DEPRECATION")
                        android.text.Html.fromHtml(htmlText)
                    }
                } else {
                    text
                }
            }
        }

        if (model.type == "system") {
            holder.leftHolder.visibility = View.GONE
            holder.rightHolder.visibility = View.GONE
            holder.leftReactions.visibility = View.GONE
            holder.rightReactions.visibility = View.GONE
            
            holder.systemMessage.visibility = View.VISIBLE
            if (model.systemSubjectId == myUid && model.systemAction != null) {
                holder.systemMessage.text = "Bạn ${model.systemAction}"
            } else {
                holder.systemMessage.text = model.text
            }
            return
        } else {
            holder.systemMessage.visibility = View.GONE
        }

        if (isMyMessage) {
            holder.leftHolder.visibility = View.GONE
            holder.rightHolder.visibility = View.VISIBLE

            holder.rightMessage.visibility = View.GONE
            holder.rightImage.visibility = View.GONE
            holder.rightAudioLayout.visibility = View.GONE
            
            holder.rightReadStatus.text = if (model.isRead || model.readBy.isNotEmpty()) "Đã xem" else "Đã gửi"
            holder.rightReadStatus.visibility = if (model.type == "image" || model.type == "sticker") View.GONE else View.VISIBLE
            
            if (model.forwardedFrom != null) {
                holder.rightForwardTag.visibility = View.VISIBLE
            } else {
                holder.rightForwardTag.visibility = View.GONE
            }

            if (model.replyTo != null) {
                holder.rightReplyBox.visibility = View.VISIBLE
                holder.rightReplyContent.text = model.replyTo!!.preview
                if (model.replyTo!!.senderId == myUid) {
                    holder.rightReplyName.text = "Đã trả lời Bạn"
                } else {
                    holder.rightReplyName.text = "Đã trả lời Ai đó" // Placeholder
                    com.google.firebase.database.FirebaseDatabase.getInstance(com.giangnam.chat_app.utils.Constants.DATABASE_URL)
                        .getReference(com.giangnam.chat_app.utils.Constants.NODE_USERS)
                        .child(model.replyTo!!.senderId)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val name = snapshot.child("userName").value?.toString() ?: "Người dùng"
                            holder.rightReplyName.text = "Đã trả lời $name"
                        }
                }
            } else {
                holder.rightReplyBox.visibility = View.GONE
            }

            when (model.type) {
                "sticker" -> {
                    holder.rightContentLayout.setPadding(0, 0, 0, 0)
                    holder.rightImage.visibility = View.VISIBLE
                    holder.rightHolder.setCardBackgroundColor(Color.TRANSPARENT)
                    holder.rightHolder.cardElevation = 0f
                    holder.rightHolder.radius = 0f
                    holder.rightImage.scaleType = ImageView.ScaleType.FIT_CENTER
                    holder.rightImage.layoutParams.width = stickerSizePx
                    holder.rightImage.layoutParams.height = stickerSizePx
                    Glide.with(context).load(model.mediaUrl).into(holder.rightImage)
                    holder.rightImage.setOnClickListener {
                        (context as? ChatActivity)?.showImageViewer(model.mediaUrl ?: "")
                    }
                }
                "image" -> {
                    holder.rightContentLayout.setPadding(0, 0, 0, 0)
                    holder.rightImage.visibility = View.VISIBLE
                    holder.rightHolder.radius = 10 * scale
                    holder.rightHolder.cardElevation = 2 * scale
                    holder.rightImage.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context).load(model.mediaUrl).into(holder.rightImage)
                    holder.rightHolder.setCardBackgroundColor(Color.parseColor("#03A9F4"))
                    holder.rightImage.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    holder.rightImage.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    holder.rightImage.setOnClickListener {
                        (context as? ChatActivity)?.showImageViewer(model.mediaUrl ?: "")
                    }
                }
                "audio" -> {
                    holder.rightContentLayout.setPadding(padding12, padding12, padding12, padding12)
                    holder.rightAudioLayout.visibility = View.VISIBLE
                    holder.rightHolder.radius = 10 * scale
                    holder.rightHolder.cardElevation = 2 * scale
                    holder.rightHolder.setCardBackgroundColor(Color.parseColor("#03A9F4"))
                    setupAudioPlayer(holder.rightPlayPauseBtn, holder.rightAudioSeekBar, holder.rightAudioTime, model.mediaUrl ?: "")
                }
                else -> {
                    holder.rightContentLayout.setPadding(padding12, padding12, padding12, padding12)
                    holder.rightMessage.visibility = View.VISIBLE
                    holder.rightMessage.text = displayText
                    holder.rightEditedLabel.visibility = if (model.isEdited && !model.isRevoked) View.VISIBLE else View.GONE

                    holder.rightHolder.radius = 10 * scale
                    holder.rightHolder.cardElevation = 2 * scale

                    if (model.isRevoked) {
                        holder.rightMessage.setTypeface(null, android.graphics.Typeface.ITALIC)
                        holder.rightMessage.setTextColor(Color.DKGRAY)
                        holder.rightHolder.setCardBackgroundColor(Color.TRANSPARENT)
                        holder.rightHolder.strokeWidth = 2
                        holder.rightHolder.strokeColor = Color.parseColor("#94A3B8")
                    } else {
                        holder.rightMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                        holder.rightMessage.setTextColor(Color.WHITE)
                        holder.rightHolder.setCardBackgroundColor(Color.parseColor("#03A9F4"))
                        holder.rightHolder.strokeWidth = 0
                    }
                }
            }
            holder.rightImage.requestLayout()

            // Reactions
            if (model.reactions.isNotEmpty() && !model.isRevoked) {
                holder.rightReactions.visibility = View.VISIBLE
                val emojis = model.reactions.values.map { it.emoji }.distinct().joinToString(" ")
                holder.rightReactions.text = if (model.reactions.size > 1) "$emojis ${model.reactions.size}" else emojis
                holder.rightReactions.setOnClickListener { onReactionClick?.invoke(model) }
            } else {
                holder.rightReactions.visibility = View.GONE
            }

        } else {
            holder.leftHolder.visibility = View.VISIBLE
            holder.rightHolder.visibility = View.GONE

            holder.leftMessage.visibility = View.GONE
            holder.leftImage.visibility = View.GONE
            holder.leftAudioLayout.visibility = View.GONE
            holder.leftSenderName.visibility = View.GONE

            if (model.forwardedFrom != null) {
                holder.leftForwardTag.visibility = View.VISIBLE
            } else {
                holder.leftForwardTag.visibility = View.GONE
            }

            if (model.replyTo != null) {
                holder.leftReplyBox.visibility = View.VISIBLE
                holder.leftReplyContent.text = model.replyTo!!.preview
                if (model.replyTo!!.senderId == myUid) {
                    holder.leftReplyName.text = "Đã trả lời Bạn"
                } else {
                    holder.leftReplyName.text = "Đã trả lời Ai đó" // Placeholder
                    com.google.firebase.database.FirebaseDatabase.getInstance(com.giangnam.chat_app.utils.Constants.DATABASE_URL)
                        .getReference(com.giangnam.chat_app.utils.Constants.NODE_USERS)
                        .child(model.replyTo!!.senderId)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val name = snapshot.child("userName").value?.toString() ?: "Người dùng"
                            holder.leftReplyName.text = "Đã trả lời $name"
                        }
                }
            } else {
                holder.leftReplyBox.visibility = View.GONE
            }

            // Fetch and display sender name for left messages (useful for groups)
            if (model.senderId != myUid && model.senderId != "AI_BOT_001") {
                com.google.firebase.database.FirebaseDatabase.getInstance(com.giangnam.chat_app.utils.Constants.DATABASE_URL)
                    .getReference(com.giangnam.chat_app.utils.Constants.NODE_USERS)
                    .child(model.senderId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val name = snapshot.child("userName").value?.toString() ?: "Người dùng"
                        holder.leftSenderName.text = name
                        holder.leftSenderName.visibility = View.VISIBLE
                    }
            }

            when (model.type) {
                "sticker" -> {
                    holder.leftContentLayout.setPadding(0, 0, 0, 0)
                    holder.leftImage.visibility = View.VISIBLE
                    holder.leftHolder.setCardBackgroundColor(Color.TRANSPARENT)
                    holder.leftHolder.cardElevation = 0f
                    holder.leftHolder.radius = 0f
                    holder.leftImage.scaleType = ImageView.ScaleType.FIT_CENTER
                    holder.leftImage.layoutParams.width = stickerSizePx
                    holder.leftImage.layoutParams.height = stickerSizePx
                    Glide.with(context).load(model.mediaUrl).into(holder.leftImage)
                    holder.leftImage.setOnClickListener {
                        (context as? ChatActivity)?.showImageViewer(model.mediaUrl ?: "")
                    }
                }
                "image" -> {
                    holder.leftContentLayout.setPadding(0, 0, 0, 0)
                    holder.leftImage.visibility = View.VISIBLE
                    holder.leftHolder.radius = 10 * scale
                    holder.leftHolder.cardElevation = 2 * scale
                    holder.leftImage.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context).load(model.mediaUrl).into(holder.leftImage)
                    holder.leftHolder.setCardBackgroundColor(Color.WHITE)
                    holder.leftImage.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    holder.leftImage.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    holder.leftImage.setOnClickListener {
                        (context as? ChatActivity)?.showImageViewer(model.mediaUrl ?: "")
                    }
                }
                "audio" -> {
                    holder.leftContentLayout.setPadding(padding12, padding12, padding12, padding12)
                    holder.leftAudioLayout.visibility = View.VISIBLE
                    holder.leftHolder.radius = 10 * scale
                    holder.leftHolder.cardElevation = 2 * scale
                    holder.leftHolder.setCardBackgroundColor(Color.WHITE)
                    setupAudioPlayer(holder.leftPlayPauseBtn, holder.leftAudioSeekBar, holder.leftAudioTime, model.mediaUrl ?: "")
                }
                else -> {
                    holder.leftContentLayout.setPadding(padding12, padding12, padding12, padding12)
                    holder.leftMessage.visibility = View.VISIBLE
                    holder.leftMessage.text = displayText
                    holder.leftEditedLabel.visibility = if (model.isEdited && !model.isRevoked) View.VISIBLE else View.GONE

                    holder.leftHolder.radius = 10 * scale
                    holder.leftHolder.cardElevation = 2 * scale

                    if (model.isRevoked) {
                        holder.leftMessage.setTypeface(null, android.graphics.Typeface.ITALIC)
                        holder.leftMessage.setTextColor(Color.GRAY)
                        holder.leftHolder.setCardBackgroundColor(Color.TRANSPARENT)
                        holder.leftHolder.strokeWidth = 2
                        holder.leftHolder.strokeColor = Color.parseColor("#E2E8F0")
                    } else {
                        holder.leftMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                        holder.leftMessage.setTextColor(Color.BLACK)
                        holder.leftHolder.setCardBackgroundColor(Color.WHITE)
                        holder.leftHolder.strokeWidth = 2
                        holder.leftHolder.strokeColor = Color.parseColor("#E2E8F0")
                    }
                }
            }
            holder.leftImage.requestLayout()

            // Reactions
            if (model.reactions.isNotEmpty() && !model.isRevoked) {
                holder.leftReactions.visibility = View.VISIBLE
                val emojis = model.reactions.values.map { it.emoji }.distinct().joinToString(" ")
                holder.leftReactions.text = if (model.reactions.size > 1) "$emojis ${model.reactions.size}" else emojis
                holder.leftReactions.setOnClickListener { onReactionClick?.invoke(model) }
            } else {
                holder.leftReactions.visibility = View.GONE
            }
        }
    }

    private fun setupAudioPlayer(
        playBtn: ImageView, 
        seekBar: android.widget.SeekBar, 
        timeTextView: TextView, 
        audioUrl: String
    ) {
        // Reset state
        seekBar.progress = 0
        timeTextView.text = "00:00"

        val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                if (currentlyPlayingUrl == audioUrl && mediaPlayer?.isPlaying == true) {
                    val currentPos = mediaPlayer?.currentPosition ?: 0
                    seekBar.progress = currentPos
                    
                    val seconds = (currentPos / 1000) % 60
                    val minutes = (currentPos / 1000) / 60
                    timeTextView.text = String.format("%02d:%02d", minutes, seconds)
                    
                    updateHandler.postDelayed(this, 100)
                }
            }
        }

        if (currentlyPlayingUrl == audioUrl && mediaPlayer?.isPlaying == true) {
            playBtn.setImageResource(R.drawable.ic_pause)
            seekBar.max = mediaPlayer?.duration ?: 0
            updateHandler.post(updateRunnable)
        } else {
            playBtn.setImageResource(R.drawable.ic_play)
        }

        playBtn.setOnClickListener {
            if (currentlyPlayingUrl == audioUrl && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                playBtn.setImageResource(R.drawable.ic_play)
                updateHandler.removeCallbacks(updateRunnable)
            } else {
                mediaPlayer?.release()
                mediaPlayer = android.media.MediaPlayer().apply {
                    try {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(audioUrl)
                        prepareAsync()
                        setOnPreparedListener {
                            start()
                            playBtn.setImageResource(R.drawable.ic_pause)
                            seekBar.max = duration
                            updateHandler.post(updateRunnable)
                        }
                        setOnCompletionListener {
                            playBtn.setImageResource(R.drawable.ic_play)
                            currentlyPlayingUrl = null
                            seekBar.progress = 0
                            val dur = duration
                            val s = (dur / 1000) % 60
                            val m = (dur / 1000) / 60
                            timeTextView.text = String.format("%02d:%02d", m, s)
                            updateHandler.removeCallbacks(updateRunnable)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                currentlyPlayingUrl = audioUrl
            }
        }

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && currentlyPlayingUrl == audioUrl && mediaPlayer != null) {
                    val seconds = (progress / 1000) % 60
                    val minutes = (progress / 1000) / 60
                    timeTextView.text = String.format("%02d:%02d", minutes, seconds)
                }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                if (currentlyPlayingUrl == audioUrl && mediaPlayer != null) {
                    mediaPlayer?.seekTo(sb?.progress ?: 0)
                }
            }
        })
    }
}