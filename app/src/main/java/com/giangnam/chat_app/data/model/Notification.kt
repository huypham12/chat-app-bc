package com.giangnam.chat_app.data.model

data class Notification(
    var notificationId: String = "",
    var type: String = "", // follow, react, mention, group_added, friend_request, etc.
    var actorId: String = "", // Người thực hiện hành động gần nhất
    var receiverId: String = "", // Người nhận thông báo
    
    // Điều hướng (Navigation context)
    var roomId: String? = null,
    var messageId: String? = null,
    
    // Hiển thị tuỳ chọn (System notification / Fallback text)
    var title: String? = null, 
    var body: String? = null,  
    
    // Gộp thông báo (Smart Grouping)
    var count: Int = 1, // Đếm số lượng hành động. VD: count = 3 -> "A và 2 người khác..."
    
    // Dữ liệu mở rộng (Payload linh hoạt)
    var extraData: Map<String, String>? = null, 
    
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    
    @get:com.google.firebase.database.PropertyName("isRead")
    @set:com.google.firebase.database.PropertyName("isRead")
    var isRead: Boolean = false
)