# 💬 E-Chat (Android Chat Application)

E-Chat là một ứng dụng nhắn tin thời gian thực (Real-time Chat App) trên nền tảng Android. Ứng dụng được thiết kế với giao diện hiện đại (Material Design), hỗ trợ xác thực người dùng qua số điện thoại (OTP) và quản lý hồ sơ cá nhân hoàn chỉnh.

## ✨ Tính năng nổi bật

*   **Xác thực bảo mật:** Đăng nhập và đăng ký an toàn bằng số điện thoại với mã xác thực OTP.
*   **Giao diện hiện đại (UI/UX):** Sử dụng các thành phần Material Design, hiệu ứng bo góc mượt mà, Splash screen bắt mắt và hỗ trợ Bottom Sheet.
*   **Hồ sơ cá nhân:** Xem và chỉnh sửa thông tin cá nhân (Tên, Tiểu sử). Cập nhật ảnh đại diện mượt mà.
*   **Nhắn tin thời gian thực:** Trò chuyện không độ trễ, lưu trữ và đồng bộ hóa tin nhắn liên tục.
*   **Tìm kiếm:** Tìm kiếm bạn bè và người dùng khác trong hệ thống dễ dàng.

## 🛠 Công nghệ & Thư viện sử dụng

*   **Ngôn ngữ:** Kotlin 
*   **Giao diện:** XML, Material Components for Android, ConstraintLayout
*   **Backend & Database:** Firebase Authentication (Phone Auth), Firebase Realtime Database
*   **Xử lý ảnh:** [Glide](https://github.com/bumptech/glide) (Tải và bo tròn ảnh hiệu suất cao)
*   **API Upload Ảnh:** ImgBB API (Kết hợp cùng OkHttp3 để upload avatar)
*   **Kiến trúc:** Mô hình quản lý trạng thái với ViewModel, Coroutines & LifecycleScope

## 🚀 Cài đặt & Chạy dự án

1. Clone kho lưu trữ này về máy:
   ```bash
   git clone https://github.com/GiangNam2004/Chat_App.git
