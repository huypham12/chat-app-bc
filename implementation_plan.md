# Kế hoạch triển khai tính năng Group Chat

Dựa trên việc phân tích codebase hiện tại của Chat_App, dưới đây là đánh giá cấu trúc và kế hoạch triển khai chi tiết cho cụm tính năng Group Chat.

## 1. Đánh giá Data Model / Cấu trúc hiện tại

Hệ thống Data Model hiện tại đã có sự chuẩn bị khá tốt cho Group Chat, tuy nhiên cần một số điều chỉnh nhỏ để tính năng hoạt động chính xác:

*   **`ChatRoom.kt` & `RoomMember.kt`**: `ChatRoom` đã có trường `type` ("private" | "group") và `userIds`. Class `RoomMember` (với `role` "admin" / "member") đã được định nghĩa nhưng **chưa được sử dụng** trong `ChatRepository` khi tạo phòng.
    *   *Đề xuất*: Khi tạo/cập nhật group, cần ghi dữ liệu vào node `Constants.NODE_ROOM_MEMBERS` để quản lý quyền Admin và thời gian join nhóm.
*   **`RoomStatus.kt`**: Đã định nghĩa sẵn map `typing: Map<String, Boolean>`. Thiết kế này rất chuẩn xác để làm tính năng Typing indicator cho cả 1-1 và Group.
*   **`Message.kt`**: Hiện tại chỉ có `isRead: Boolean = false`. Điều này gây lỗi logic cho Group Chat (nếu 1 người đọc, hàm `markMessagesAsRead` hiện tại sẽ đánh dấu là đã đọc cho tất cả mọi người).
    *   *Đề xuất*: Thêm trường `readBy: Map<String, Long> = emptyMap()` vào `Message.kt` để lưu trữ UID của những người đã đọc và timestamp, thay vì chỉ dùng `isRead`. Hàm `markMessagesAsRead` cần được sửa lại để hỗ trợ phân nhánh logic cho "private" và "group".

## 2. Các file cần tạo mới hoặc sửa đổi

### 📂 Files tạo mới:
1.  **`app/src/main/java/com/giangnam/chat_app/ui/chat/CreateGroupActivity.kt`** (hoặc Fragment)
    *   *Mục đích*: UI để chọn nhiều thành viên từ danh sách bạn bè và nhập tên nhóm/ảnh nhóm để tạo Group mới.
2.  **`app/src/main/java/com/giangnam/chat_app/ui/chat/GroupMembersActivity.kt`** (Tuỳ chọn - có thể gom vào `ChatSettingsActivity` nhưng tách ra sẽ dễ maintain hơn)
    *   *Mục đích*: UI hiển thị danh sách thành viên, thêm/xóa thành viên, và menu popup để cấp/thu hồi/chuyển quyền admin.

### 📂 Files cần sửa đổi:
1.  **`Message.kt`**: Thêm `var readBy: Map<String, Long> = emptyMap()`.
2.  **`ChatRepository.kt`**:
    *   Sửa logic `createChatRoom` để hỗ trợ lưu `RoomMember`.
    *   Thêm các hàm quản lý nhóm: `updateGroupInfo`, `addMember`, `removeMember`, `leaveGroup`, `updateMemberRole`.
    *   Sửa logic `markMessagesAsRead` để hỗ trợ Group.
    *   Thêm hàm `setTypingStatus` và `listenToTypingStatus`.
3.  **`ChatViewModel.kt`**: Wrap các hàm mới từ `ChatRepository` để UI gọi.
4.  **`ChatActivity.kt` & `ChatAdapter.kt`**:
    *   Xử lý hiển thị tên nhóm, ảnh nhóm thay vì thông tin User 1-1.
    *   Hiển thị tên người gửi trên mỗi tin nhắn nếu là Group chat.
    *   Lắng nghe và hiển thị trạng thái "Typing...".
    *   Cập nhật UI Read Receipt dựa vào `readBy` (VD: hiển thị avatar nhỏ của những người đã đọc dưới tin nhắn).
5.  **`ChatSettingsActivity.kt`**:
    *   Thêm logic ẩn/hiện các tính năng tùy theo `room.type`. Mở rộng để cho phép đổi tên nhóm, đổi avatar nhóm và rời nhóm.

---

## 3. Lộ trình triển khai (Phases)

Được chia thành 4 Phase theo thứ tự dependency.

### 🚀 Phase 1: Core Group & Khởi tạo nhóm
*   **Mục tiêu**: Người dùng có thể tạo một nhóm mới và gửi tin nhắn vào nhóm đó.
*   **Các file sửa/tạo**: `CreateGroupActivity.kt` (New), `ChatRepository.kt`, `ChatActivity.kt`, `ChatAdapter.kt`.
*   **Công việc cụ thể**:
    1.  Tạo màn hình `CreateGroupActivity` cho phép tick chọn user.
    2.  Sửa `ChatRepository.createChatRoom` để ghi thêm dữ liệu vào node `roomMembers/{roomId}`.
    3.  Sửa `ChatActivity` và `ChatAdapter`: Nếu `room.type == "group"`, bind Tên nhóm, Ảnh nhóm; tin nhắn đến phải hiện tên người gửi.
*   **Điều kiện hoàn thành**: Tạo được nhóm >2 người, chat trong nhóm hoạt động bình thường, hiển thị đúng UI cơ bản của nhóm.

### 🚀 Phase 2: Cài đặt nhóm cơ bản (Group Settings)
*   **Mục tiêu**: Đổi tên, đổi ảnh, rời nhóm.
*   **Dependency**: Đã hoàn thành Phase 1.
*   **Các file sửa/tạo**: `ChatSettingsActivity.kt`, `ChatRepository.kt`.
*   **Công việc cụ thể**:
    1.  Thêm các hàm `updateGroupInfo` (name, avatar), `leaveGroup` vào Repository.
    2.  Bổ sung UI cho `ChatSettingsActivity`: Hiện nút đổi tên, đổi ảnh (nếu là nhóm) và nút "Rời nhóm".
    3.  Xử lý logic rời nhóm: Xóa UID khỏi `ChatRoom.userIds`, xóa khỏi `roomMembers`, xóa `UserInbox`.
*   **Điều kiện hoàn thành**: Cập nhật thành công tên/ảnh nhóm, thành viên rời nhóm không còn thấy phòng chat trong màn hình chính.

### 🚀 Phase 3: Quản lý thành viên & Quyền Admin
*   **Mục tiêu**: Xem danh sách thành viên, thêm/xóa thành viên, phân quyền Admin.
*   **Dependency**: Đã hoàn thành Phase 1.
*   **Các file sửa/tạo**: `GroupMembersActivity.kt` (New), `ChatRepository.kt`.
*   **Công việc cụ thể**:
    1.  Tạo màn hình hiển thị danh sách `RoomMember`.
    2.  Thêm UI/Logic mời người mới vào nhóm.
    3.  Check Role: Chỉ Admin mới có quyền Xóa thành viên, Cấp quyền Admin, Thu hồi quyền Admin.
    4.  Logic Chuyển quyền Admin: Chuyển quyền cho người khác thì bản thân tự hạ xuống Member.
*   **Điều kiện hoàn thành**: Phân quyền hoạt động chính xác, Admin thao tác được các hành động quản trị, Member bị block các hành động này.

### 🚀 Phase 4: Typing Indicator & Group Read Receipts
*   **Mục tiêu**: Bổ sung trạng thái đang gõ và đã xem tin nhắn (hỗ trợ cho cả 1-1 và Group).
*   **Dependency**: Không phụ thuộc chặt chẽ vào Phase 2, 3 nhưng cần Core chat hoạt động.
*   **Các file sửa/tạo**: `Message.kt`, `ChatRepository.kt`, `ChatViewModel.kt`, `ChatActivity.kt`, `ChatAdapter.kt`.
*   **Công việc cụ thể**:
    1.  **Typing**: Lắng nghe TextWatcher trong `ChatActivity`, ghi `true/false` vào `Constants.NODE_ROOM_STATUS + "/{roomId}/typing/{userId}"`.
    2.  **Typing UI**: Observe node status, hiển thị "[Tên] đang soạn tin..." (hoặc lặp lại avatar nhỏ nếu có).
    3.  **Read Receipt Data**: Thêm trường `readBy: Map<String, Long>` vào `Message.kt`. Cập nhật lại `markMessagesAsRead` trong Repo để patch uid vào map `readBy` của từng tin nhắn.
    4.  **Read Receipt UI**: Trong `ChatAdapter`, render các avatar thu nhỏ của những UID có trong `readBy` dưới tin nhắn cuối cùng họ đọc.
*   **Điều kiện hoàn thành**: Hiển thị realtime người đang gõ; Thấy được icon của những người đã xem tin nhắn trong Group.

## 💬 Mời User phản hồi
Bạn có đồng ý với đề xuất thay đổi Model (cụ thể là cách giải quyết Group Read Receipt bằng cách thêm `readBy` vào `Message` và sử dụng `RoomMember` model như đã nêu) và cấu trúc các Phase không? Nếu bạn ok, tôi sẽ bắt đầu lên cấu trúc Task để theo dõi.
