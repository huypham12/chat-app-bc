package com.giangnam.chat_app

import android.app.Application
import com.giangnam.chat_app.BuildConfig
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Bật log để dễ dàng kiểm tra lỗi trên Logcat
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        // Khởi tạo OneSignal SDK
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)

        // Yêu cầu quyền hiển thị thông báo (Bắt buộc cho Android 13+)
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }
}