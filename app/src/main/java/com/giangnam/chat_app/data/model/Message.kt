package com.giangnam.chat_app.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class Message(
    var messageId: String = "",
    var senderId: String = "",
    var timestamp: Long = 0L,
    
    var type: String = "text", // text, image, video, voice, sticker, file
    var text: String? = "",
    
    var mediaUrl: String? = "",
    var thumbnailUrl: String? = "",
    var fileName: String? = "",
    var fileSize: Long? = 0L,
    
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,
    
    var readBy: Map<String, Long> = emptyMap(), // uid -> timestamp
    
    var replyTo: ReplyPreview? = null,
    var reactions: Map<String, Reaction> = emptyMap(), // uid -> Reaction
    var mentions: Map<String, Boolean> = emptyMap(), // uid -> true
    var mentionedNames: List<String> = emptyList(), // Store exact names to bold in UI
    var forwardedFrom: ForwardedFrom? = null,
    @get:PropertyName("isEdited")
    @set:PropertyName("isEdited")
    var isEdited: Boolean = false,
    
    @get:PropertyName("isRevoked")
    @set:PropertyName("isRevoked")
    var isRevoked: Boolean = false,
    
    var deletedFor: Map<String, Boolean> = emptyMap(), // uid -> true
    
    // System Messages specific
    var systemSubjectId: String? = null,
    var systemAction: String? = null
)

data class ReplyPreview(
    var messageId: String = "",
    var senderId: String = "",
    var preview: String = ""
)

data class Reaction(
    var emoji: String = "",
    var createdAt: Long = 0L
)

data class ForwardedFrom(
    var roomId: String = "",
    var messageId: String = "",
    var senderId: String = ""
)