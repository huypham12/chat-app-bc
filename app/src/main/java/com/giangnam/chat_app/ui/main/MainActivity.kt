package com.giangnam.chat_app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.giangnam.chat_app.BuildConfig
import com.giangnam.chat_app.R
import com.giangnam.chat_app.utils.Utils
import com.giangnam.chat_app.databinding.ActivityMainBinding
import com.giangnam.chat_app.ui.auth.AuthActivity
import com.giangnam.chat_app.ui.search.SearchUserActivity
import com.google.firebase.auth.FirebaseAuth
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            val intent = Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        val userId = Utils.getUserId()
        if (userId.isNotEmpty()) {
            try {
                com.onesignal.OneSignal.login(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Add padding to handle Edge-to-Edge and avoid overlap with system bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavView.setupWithNavController(navController)

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.search -> {
                    startActivity(Intent(this, SearchUserActivity::class.java))
                    true
                }
                else -> false
            }
        }

        setupPresenceSystem()
        initVideoCallService()
        viewModel.fetchAllUserNames()
        viewModel.startListeningToRecentChats()
        
        checkNotificationPermission()
        fetchFcmToken()
        
        viewModel.observeUnreadNotifications()
        viewModel.observePendingFriendRequests()
        
        lifecycleScope.launch {
            viewModel.unreadNotificationCount.collect { count ->
                val badge = binding.bottomNavView.getOrCreateBadge(R.id.notificationsFragment)
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count
                } else {
                    badge.isVisible = false
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.totalUnreadChatCount.collect { count ->
                val badge = binding.bottomNavView.getOrCreateBadge(R.id.chatsFragment)
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count
                } else {
                    badge.isVisible = false
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.pendingFriendRequestsCount.collect { count ->
                val badge = binding.bottomNavView.getOrCreateBadge(R.id.contactsFragment)
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count
                } else {
                    badge.isVisible = false
                }
            }
        }
    }

    private fun initVideoCallService() {
        val appID: Long = BuildConfig.ZEGO_APP_ID.toLongOrNull() ?: 0L
        val appSign = BuildConfig.ZEGO_APP_SIGN
        val userID = Utils.getUserId()

        if (appID == 0L || appSign.isEmpty() || userID.isEmpty()) {
            return
        }

        try {
            val callInvitationConfig = ZegoUIKitPrebuiltCallInvitationConfig()
            ZegoUIKitPrebuiltCallInvitationService.init(
                application,
                appID,
                appSign,
                userID,
                userID,
                callInvitationConfig
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ZegoUIKitPrebuiltCallInvitationService.unInit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupPresenceSystem() {
        val myUserId = Utils.getUserId()
        if (myUserId.isEmpty()) return
        viewModel.setupUserPresence(myUserId)
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun fetchFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result
            val userId = Utils.getUserId()
            if (userId.isNotEmpty() && token != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .update("fcmToken", token)
            }
        }
    }

    private val notificationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val title = intent?.getStringExtra("title") ?: "New Notification"
            val body = intent?.getStringExtra("body") ?: ""
            val type = intent?.getStringExtra("type") ?: ""
            
            // Show Heads-up Banner using Alerter
            com.tapadoo.alerter.Alerter.create(this@MainActivity)
                .setTitle(title)
                .setText(body)
                .setBackgroundColorRes(R.color.black)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setDuration(3000)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .registerReceiver(notificationReceiver, android.content.IntentFilter("IN_APP_NOTIFICATION"))
    }

    override fun onPause() {
        super.onPause()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(notificationReceiver)
    }
}
