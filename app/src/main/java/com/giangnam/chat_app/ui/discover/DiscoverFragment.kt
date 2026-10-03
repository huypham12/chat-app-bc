package com.giangnam.chat_app.ui.discover

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.giangnam.chat_app.R
import com.giangnam.chat_app.data.model.NearbyUserDTO
import com.giangnam.chat_app.databinding.FragmentDiscoverBinding
import com.giangnam.chat_app.ui.chat.ChatActivity
import com.giangnam.chat_app.ui.profile.PrivacySettingsActivity
import com.giangnam.chat_app.ui.profile.UserInfoActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DiscoverViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val userMarkerMap = mutableMapOf<Marker, NearbyUserDTO>()
    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            setupMap()
            getCurrentLocationAndFetchUsers()
        } else {
            showEmptyState("Cần quyền vị trí.", showSettingsBtn = true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = requireContext().packageName
        
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        // Sử dụng Tile Source lấy trực tiếp giao diện của Google Maps thông qua OSMDroid (Không cần API Key)
        val googleMapsSource = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
            "GoogleMaps",
            0, 19, 256, "",
            arrayOf(
                "https://mt0.google.com/vt/lyrs=m&hl=vi&x=",
                "https://mt1.google.com/vt/lyrs=m&hl=vi&x=",
                "https://mt2.google.com/vt/lyrs=m&hl=vi&x=",
                "https://mt3.google.com/vt/lyrs=m&hl=vi&x="
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl + org.osmdroid.util.MapTileIndex.getX(pMapTileIndex) +
                        "&y=" + org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) +
                        "&z=" + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            }
        }
        
        binding.map.setTileSource(googleMapsSource)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(15.0)
        
        binding.btnGoToSettings.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacySettingsActivity::class.java))
        }

        binding.cardUserInfo.setOnClickListener {
            // Can add logic to go to user profile here
        }

        checkPermissionsAndFetch()
    }

    private fun setupMap() {
        // Chỉ khởi tạo map config cơ bản, marker cá nhân sẽ được custom
        binding.map.setMultiTouchControls(true)
        observeViewModel()
    }

    private fun getBitmapFromVectorDrawable(drawableId: Int, colorTint: Int): android.graphics.Bitmap {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId)!!
        drawable.setTint(colorTint)
        // Set size a bit larger for visibility
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth * 2 else 120
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight * 2 else 120
        
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun checkPermissionsAndFetch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setupMap()
            getCurrentLocationAndFetchUsers()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndFetchUsers() {
        binding.progressBar.visibility = View.VISIBLE
        
        // Sử dụng getCurrentLocation thay vì lastLocation để có vị trí chính xác và mới nhất
        fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                if (location != null) {
                    val currentGeoPoint = GeoPoint(location.latitude, location.longitude)
                    binding.map.controller.setZoom(16.0)
                    binding.map.controller.animateTo(currentGeoPoint)
                    
                    // Cập nhật vị trí lên Firebase ngay để người khác có thể thấy
                    val userId = com.giangnam.chat_app.utils.Utils.getUserId()
                    com.giangnam.chat_app.data.repository.NearbyRepository.getInstance()
                        .updateLocation(userId, location.latitude, location.longitude) {}
                    
                    // Vẽ marker cho người dùng hiện tại
                    drawMyMarker(location)
                    
                    viewModel.startObservingNearbyUsers(location)
                } else {
                    binding.progressBar.visibility = View.GONE
                    showEmptyState("Không thể lấy vị trí hiện tại. Vui lòng bật GPS.", showSettingsBtn = false)
                }
            }.addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Lỗi lấy vị trí", Toast.LENGTH_SHORT).show()
            }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.nearbyUsers.collect { users ->
                // Xóa markers cũ (trừ my location)
                binding.map.overlays.removeAll(userMarkerMap.keys)
                userMarkerMap.clear()
                
                if (users.isEmpty()) {
                    showEmptyState("Không tìm thấy ai xung quanh hoặc bạn chưa bật tính năng Nearby.", showSettingsBtn = true)
                } else {
                    hideEmptyState()
                    users.forEach { user ->
                        val marker = Marker(binding.map)
                        marker.position = GeoPoint(user.approxLat, user.approxLng)
                        marker.title = user.displayName
                        
                        // Set anchor to center bottom so text is below the coordinate
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        
                        // Load Avatar and convert to custom Bitmap Drawable
                        loadAvatarMarker(user, marker)
                        
                        marker.setOnMarkerClickListener { m, _ ->
                            // Bấm vào avatar để vào thẳng trang cá nhân
                            val intent = Intent(requireContext(), UserInfoActivity::class.java)
                            intent.putExtra("userId", user.userId)
                            startActivity(intent)
                            true
                        }
                        binding.map.overlays.add(marker)
                        userMarkerMap[marker] = user
                    }
                }
                binding.map.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private var myMarker: Marker? = null

    private fun drawMyMarker(location: Location) {
        val userId = com.giangnam.chat_app.utils.Utils.getUserId()
        val db = com.google.firebase.database.FirebaseDatabase.getInstance(com.giangnam.chat_app.utils.Constants.DATABASE_URL)
        
        // Check if user has nearby enabled first
        db.getReference(com.giangnam.chat_app.utils.Constants.NODE_USER_SETTINGS)
            .child(userId).child("nearbyEnabled").get().addOnSuccessListener { settingSnapshot ->
                val isEnabled = settingSnapshot.getValue(Boolean::class.java) ?: false
                
                if (!isEnabled) {
                    if (myMarker != null) {
                        binding.map.overlays.remove(myMarker)
                        myMarker = null
                        binding.map.invalidate()
                    }
                    return@addOnSuccessListener
                }
                
                // If enabled, proceed to draw
                db.getReference(com.giangnam.chat_app.utils.Constants.NODE_USERS)
                    .child(userId).get().addOnSuccessListener { snapshot ->
                        if (_binding == null || !isAdded) return@addOnSuccessListener
                        val user = snapshot.getValue(com.giangnam.chat_app.data.model.User::class.java)
                        if (user != null) {
                    if (myMarker == null) {
                        myMarker = Marker(binding.map)
                        binding.map.overlays.add(myMarker)
                    }
                    myMarker?.position = GeoPoint(location.latitude, location.longitude)
                    myMarker?.title = "You"
                    myMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    
                    val nearbyUserDto = NearbyUserDTO(
                        userId = user.uid,
                        displayName = "You",
                        avatarUrl = user.avatarUrl ?: "",
                        approxLat = location.latitude,
                        approxLng = location.longitude,
                        distance = 0.0
                    )
                    loadAvatarMarker(nearbyUserDto, myMarker!!, isCurrentUser = true)
                    
                    myMarker?.setOnMarkerClickListener { _, _ ->
                        requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav_view)
                            ?.selectedItemId = R.id.profileFragment
                        true
                    }
                    binding.map.invalidate()
                }
            }
        }
    }

    private fun loadAvatarMarker(user: NearbyUserDTO, marker: Marker, isCurrentUser: Boolean = false) {
        Glide.with(this)
            .asBitmap()
            .load(user.avatarUrl)
            .placeholder(R.drawable.ic_person)
            .circleCrop()
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                override fun onResourceReady(
                    resource: android.graphics.Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?
                ) {
                    val compositeBitmap = createMarkerWithText(resource, user.displayName, isCurrentUser)
                    if (isAdded && context != null) {
                        marker.icon = android.graphics.drawable.BitmapDrawable(resources, compositeBitmap)
                        binding.map.invalidate()
                    }
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    if (isAdded && context != null) {
                        val colorStr = if (isCurrentUser) "#3B82F6" else "#8B5CF6"
                        val fallbackBitmap = getBitmapFromVectorDrawable(R.drawable.ic_person, android.graphics.Color.parseColor(colorStr))
                        val compositeBitmap = createMarkerWithText(fallbackBitmap, user.displayName, isCurrentUser)
                        marker.icon = android.graphics.drawable.BitmapDrawable(resources, compositeBitmap)
                        binding.map.invalidate()
                    }
                }
            })
    }

    private fun createMarkerWithText(avatar: android.graphics.Bitmap, text: String, isCurrentUser: Boolean): android.graphics.Bitmap {
        val avatarSize = 120
        val borderSize = 6f
        val textSize = 32f

        val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(5f, 0f, 2f, android.graphics.Color.WHITE)
        }
        
        val scaledAvatar = android.graphics.Bitmap.createScaledBitmap(avatar, avatarSize, avatarSize, true)
        
        val totalWidth = 300 // Max width for text
        val totalHeight = avatarSize + 50 // Space for text below avatar

        val bitmap = android.graphics.Bitmap.createBitmap(totalWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Draw Border
        val paintBorder = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCurrentUser) android.graphics.Color.parseColor("#3B82F6") else android.graphics.Color.parseColor("#8B5CF6")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(totalWidth / 2f, avatarSize / 2f, (avatarSize / 2f) + borderSize, paintBorder)

        // Draw circular Avatar
        val paintAvatar = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val bitmapShader = android.graphics.BitmapShader(scaledAvatar, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        paintAvatar.shader = bitmapShader
        canvas.save()
        canvas.translate((totalWidth - avatarSize) / 2f, 0f)
        canvas.drawCircle(avatarSize / 2f, avatarSize / 2f, avatarSize / 2f, paintAvatar)
        canvas.restore()

        // Draw Text
        canvas.drawText(text, totalWidth / 2f, avatarSize + 35f, paintText)

        return bitmap
    }

    private fun showUserInfoCard(user: NearbyUserDTO) {
        binding.cardUserInfo.visibility = View.VISIBLE
        binding.tvUserName.text = user.displayName
        binding.tvUserDistance.text = String.format("Cách đây %.2f km", user.distance)

        Glide.with(this)
            .load(user.avatarUrl)
            .placeholder(R.drawable.ic_person)
            .into(binding.ivUserAvatar)

        binding.btnChat.setOnClickListener {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("userId", user.userId)
            intent.putExtra("userName", user.displayName)
            startActivity(intent)
        }
        
        binding.ivUserAvatar.setOnClickListener {
            val intent = Intent(requireContext(), UserInfoActivity::class.java)
            intent.putExtra("userId", user.userId)
            startActivity(intent)
        }
    }

    private fun showEmptyState(message: String, showSettingsBtn: Boolean) {
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = message
        binding.btnGoToSettings.visibility = if (showSettingsBtn) View.VISIBLE else View.GONE
    }

    private fun hideEmptyState() {
        binding.layoutEmptyState.visibility = View.GONE
    }
    
    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        refreshMyMarkerVisibility()
    }

    private fun refreshMyMarkerVisibility() {
        val userId = com.giangnam.chat_app.utils.Utils.getUserId()
        com.google.firebase.database.FirebaseDatabase.getInstance(com.giangnam.chat_app.utils.Constants.DATABASE_URL)
            .getReference(com.giangnam.chat_app.utils.Constants.NODE_USER_SETTINGS)
            .child(userId).child("nearbyEnabled").get().addOnSuccessListener { settingSnapshot ->
                val isEnabled = settingSnapshot.getValue(Boolean::class.java) ?: false
                
                if (!isEnabled) {
                    if (myMarker != null) {
                        binding.map.overlays.remove(myMarker)
                        myMarker = null
                        binding.map.invalidate()
                    }
                } else if (myMarker == null) {
                    // Nếu bật lại mà chưa có marker, hãy gọi fetch lại location
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        getCurrentLocationAndFetchUsers()
                    }
                }
            }
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
