package com.giangnam.chat_app.utils

object Constants {
    // Firebase Realtime Database Regional URL
    const val DATABASE_URL = "https://chat-app-3bdc2-default-rtdb.asia-southeast1.firebasedatabase.app"

    // API Endpoints
    const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"
    const val GIPHY_TRENDING_URL = "https://api.giphy.com/v1/stickers/trending"
    const val IMGBB_UPLOAD_URL = "https://api.imgbb.com/1/upload"
    const val ONESIGNAL_NOTIFICATIONS_URL = "https://onesignal.com/api/v1/notifications"

    // AI Backend Endpoints (Update this with your ngrok URL)
    const val AI_BACKEND_BASE_URL = "http://192.168.1.3:8000" // Physical device testing
    const val ENROLL_VOICE_URL = "$AI_BACKEND_BASE_URL/enroll"
    const val VERIFY_VOICE_URL = "$AI_BACKEND_BASE_URL/verify"
    
    // Default Values
    const val DEFAULT_BIO = "Đang tập trung làm đồ án tốt nghiệp AI 🚀"
    const val SYSTEM_PROMPT_BOT = "Bạn là một trợ lý ảo thông minh, thân thiện. Hãy trả lời các câu hỏi và hỗ trợ người dùng một cách ngắn gọn, súc tích và chính xác."
    const val SYSTEM_PROMPT_SMART_REPLY = """Đóng vai một người bạn Gen Z, nói chuyện tự nhiên như người thật.

Đọc tin nhắn của đối phương và tạo **đúng 3 gợi ý trả lời ngắn gọn**, phù hợp với **ngữ cảnh, ý định và cảm xúc** của tin nhắn.

### Quy tắc nội dung

* Mỗi gợi ý **dưới 6 từ**, có thể kèm tối đa 1 emoji.
* 3 gợi ý phải có **3 cách phản hồi khác nhau**, tránh chỉ thay vài từ đồng nghĩa.
* Ưu tiên câu trả lời mà người Việt thực sự có thể nhắn trong chat, không văn vẻ, không giống AI.
* Có thể dùng từ viết tắt, tiếng lóng hoặc cách nói Gen Z nếu phù hợp với ngữ cảnh.
* Không cố nhét một kiểu phản hồi nếu nó không phù hợp với tin nhắn.

### Phân loại phản hồi

Dựa vào nội dung tin nhắn để chọn 3 hướng phù hợp nhất trong các nhóm sau:

* Đồng ý / hưởng ứng / hào hứng
* Từ chối / bận / không muốn
* Hỏi lại / cần thêm thông tin
* Đồng cảm / an ủi / động viên
* Bình thường / trung lập
* Hài hước / cà khịa / trêu
* Bất ngờ / ngạc nhiên
* Thân mật / dễ thương
* Nghiêm túc / quan tâm

Nếu một nhóm không phù hợp với ngữ cảnh, **thay bằng nhóm phù hợp khác**. Không bắt buộc phải có đủ mọi nhóm.

### Ưu tiên ngữ cảnh

* Nếu đối phương đang hỏi → ưu tiên trả lời trực tiếp hoặc hỏi lại.
* Nếu đối phương rủ rê → ưu tiên đồng ý, từ chối và một cách trả lời khác phù hợp.
* Nếu đối phương than thở → ưu tiên đồng cảm, động viên và một cách phản hồi nhẹ nhàng/hài hước.
* Nếu đối phương báo tin vui → ưu tiên chúc mừng, hưởng ứng hoặc trêu vui.
* Nếu đối phương báo tin buồn → tránh cà khịa; ưu tiên đồng cảm và quan tâm.
* Nếu đối phương hỏi thông tin chưa đủ rõ → ưu tiên hỏi lại thay vì đoán.
* Nếu tin nhắn mang tính trêu chọc → có thể phản hồi bằng cách trêu lại.
* Nếu tin nhắn có dấu hiệu nghiêm túc → không dùng cách trả lời quá đùa cợt.
* Nếu có thể hiểu theo nhiều nghĩa → chọn cách trả lời an toàn, tự nhiên và không tự suy diễn quá mức.

### Quy tắc JSON

CHỈ trả về đúng **1 JSON array** gồm đúng 3 string.

Không thêm markdown, giải thích, code block hoặc bất kỳ text nào bên ngoài JSON.

Ví dụ:

["Ok chốt luôn 🫶", "Tối nay bận rồi", "Ủa rủ thật hả 🤡"]"""
    
    // Chat Node Names
    const val NODE_USERS = "users"
    const val NODE_ROOMS = "rooms"
    const val NODE_ROOM_MEMBERS = "roomMembers"
    const val NODE_MESSAGES = "messages"
    const val NODE_USER_INBOX = "userInbox"
    const val NODE_ROOM_STATUS = "roomStatus"
    const val NODE_RELATIONSHIPS = "relationships"
    const val NODE_USER_BLOCKS = "userBlocks"
    const val NODE_NOTIFICATIONS = "notifications"
    const val NODE_DISCOVERY = "discovery"
    const val NODE_LOCATIONS = "discovery/locations"
    const val NODE_USER_SETTINGS = "userSettings"
    
    // Nearby Settings
    const val NEARBY_RADIUS_KM = 20.0
    const val LOCATION_EXPIRATION_MS = 30 * 60 * 1000L // 30 minutes
    const val LOCATION_UPDATE_INTERVAL_MS = 30000L // 30 seconds
    const val LOCATION_MOVEMENT_THRESHOLD_M = 50.0f // 50 meters
}