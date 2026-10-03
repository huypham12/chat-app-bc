package com.giangnam.chat_app.ui.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.giangnam.chat_app.R
import com.giangnam.chat_app.ui.auth.AuthActivity
import com.giangnam.chat_app.ui.chat.ChatActivity
import com.giangnam.chat_app.utils.Utils
import com.giangnam.chat_app.data.model.User
import com.giangnam.chat_app.databinding.DialogEditProfileBinding
import com.giangnam.chat_app.databinding.FragmentProfileBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private val viewModel: ProfileViewModel by viewModels()
    private val currentUserId = Utils.getUserId()

    private var targetUserId: String = ""
    private var isMyProfile: Boolean = true
    private var isUploadingAvatar: Boolean = true
    private lateinit var audioFile: File

    // Image Picker Launcher
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageUri = result.data?.data
                imageUri?.let { startCrop(it) }
            }
        }

    // Image Cropper Launcher
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            uriContent?.let { uploadImageToImgBB(it) }
        } else {
            val exception = result.error
            exception?.printStackTrace()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(layoutInflater)

        targetUserId = arguments?.getString("userId") ?: currentUserId
        isMyProfile = (targetUserId == currentUserId)
        audioFile = File(requireContext().cacheDir, "voice_enroll_update.wav")

        setupUI()
        fetchUserDetails()
        observeViewModel()

        return binding.root
    }

    private fun setupUI() {
        if (isMyProfile) {
            binding.btnChangeName.visibility = View.VISIBLE
            binding.btnBlockedUsers.visibility = View.VISIBLE
            binding.btnPrivacySettings.visibility = View.VISIBLE
            binding.logout.visibility = View.VISIBLE
            binding.btnMessage.visibility = View.GONE
            binding.btnEditAvatar.visibility = View.VISIBLE
            binding.btnEditCover.visibility = View.VISIBLE
            binding.onlineIndicator.visibility = View.VISIBLE
            binding.layoutVoiceAuth.visibility = View.VISIBLE

            binding.btnManageVoice.setOnClickListener {
                showManageVoiceDialog()
            }

            binding.btnEditAvatar.setOnClickListener {
                isUploadingAvatar = true
                openGallery()
            }

            binding.btnEditCover.setOnClickListener {
                isUploadingAvatar = false
                openGallery()
            }

            binding.btnChangeName.setOnClickListener {
                showEditProfileDialog()
            }
            
            binding.btnBlockedUsers.setOnClickListener {
                startActivity(Intent(requireContext(), BlockedUsersActivity::class.java))
            }
            
            binding.btnPrivacySettings.setOnClickListener {
                startActivity(Intent(requireContext(), PrivacySettingsActivity::class.java))
            }
        } else {
            binding.btnChangeName.visibility = View.GONE
            binding.btnBlockedUsers.visibility = View.GONE
            binding.btnPrivacySettings.visibility = View.GONE
            binding.logout.visibility = View.GONE
            binding.btnMessage.visibility = View.VISIBLE
            binding.btnEditAvatar.visibility = View.GONE
            binding.btnEditCover.visibility = View.GONE
            binding.layoutVoiceAuth.visibility = View.GONE
        }

        // Image Preview
        binding.profileImage.setOnClickListener {
            val user = viewModel.userProfile.value
            if (user?.avatarUrl?.isNotEmpty() == true) {
                showImagePreview(user.avatarUrl!!, true)
            }
        }
        binding.profileCover.setOnClickListener {
            val user = viewModel.userProfile.value
            if (user?.coverUrl?.isNotEmpty() == true) {
                showImagePreview(user.coverUrl!!, false)
            }
        }

        binding.btnMessage.setOnClickListener {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("userId", targetUserId)
            intent.putExtra("userName", binding.userName.text.toString())
            startActivity(intent)
        }

        binding.logout.setOnClickListener {
            // Tắt service vị trí ngầm và vô hiệu hoá vị trí trên Firebase
            requireContext().stopService(Intent(requireContext(), com.giangnam.chat_app.service.LocationTrackingService::class.java))
            com.giangnam.chat_app.data.repository.NearbyRepository.getInstance().disableNearby(currentUserId) {
                Utils.getFirebaseAuthInstance().signOut()
                startActivity(Intent(requireContext(), AuthActivity::class.java))
                requireActivity().finishAffinity()
            }
        }
    }

    private fun showEditProfileDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val user = viewModel.userProfile.value
        user?.let {
            dialogBinding.etDisplayName.setText(if (it.displayName.isNotEmpty()) it.displayName else it.username)
            dialogBinding.etUsername.setText(it.username)
            dialogBinding.etPhone.setText(it.phoneNumber)
            dialogBinding.etDob.setText(it.dateOfBirth)
            dialogBinding.etBio.setText(it.bio)

            when (it.gender?.lowercase()) {
                "male" -> dialogBinding.rbMale.isChecked = true
                "female" -> dialogBinding.rbFemale.isChecked = true
                else -> dialogBinding.rbOther.isChecked = true
            }
        }

        dialogBinding.etDob.setOnClickListener {
            showDatePicker(dialogBinding.etDob)
        }

        dialogBinding.btnSave.setOnClickListener {
            val newUsername = dialogBinding.etUsername.text.toString().trim()
            val currentUsername = user?.username ?: ""

            if (newUsername.isEmpty()) {
                dialogBinding.etUsername.error = "Username cannot be empty"
                return@setOnClickListener
            }

            if (newUsername != currentUsername) {
                viewModel.checkUsernameAvailability(newUsername, currentUserId) { available ->
                    if (available) {
                        performSave(dialog, dialogBinding)
                    } else {
                        activity?.runOnUiThread {
                            dialogBinding.etUsername.error = "Username already taken"
                        }
                    }
                }
            } else {
                performSave(dialog, dialogBinding)
            }
        }

        dialog.show()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun showManageVoiceDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val bottomSheetView = layoutInflater.inflate(R.layout.layout_bottom_sheet_voice, null)
        dialog.setContentView(bottomSheetView)

        val btnRecord = bottomSheetView.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnBottomSheetRecord)
        val tvStatus = bottomSheetView.findViewById<android.widget.TextView>(R.id.tvBottomSheetStatus)
        val btnClose = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBottomSheetClose)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.performClick()
                    tvStatus.text = "Đang thu âm..."
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        viewModel.startRecording(audioFile)
                    } else {
                        androidx.core.app.ActivityCompat.requestPermissions(requireActivity(), arrayOf(android.Manifest.permission.RECORD_AUDIO), 111)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    tvStatus.text = "Đang trích xuất vector AI..."
                    viewModel.stopRecording()
                    
                    val currentUser = viewModel.userProfile.value
                    if (currentUser != null) {
                        viewModel.enrollVoice(audioFile, currentUser.phoneNumber) { voiceprint ->
                            activity?.runOnUiThread {
                                if (voiceprint != null) {
                                    Toast.makeText(requireContext(), "Cập nhật giọng nói thành công!", Toast.LENGTH_SHORT).show()
                                    
                                    val updatedUser = currentUser.copy(voiceprint = voiceprint, isVoiceAuthEnabled = true)
                                    viewModel.updateUserProfile(updatedUser) { success ->
                                        activity?.runOnUiThread {
                                            if (success) {
                                                com.giangnam.chat_app.data.repository.AuthRepository.getInstance()
                                                    .registerPhoneNumber(currentUser.phoneNumber, currentUserId, voiceprint) {}
                                            }
                                        }
                                    }
                                    dialog.dismiss()
                                } else {
                                    tvStatus.text = "Lỗi AI, hãy thử lại"
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        dialog.show()
    }

    private fun performSave(dialog: BottomSheetDialog, dialogBinding: DialogEditProfileBinding) {
        val selectedGenderId = dialogBinding.rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) {
            dialogBinding.root.findViewById<RadioButton>(selectedGenderId).text.toString()
        } else "Other"

        val updatedUser = viewModel.userProfile.value?.copy(
            displayName = dialogBinding.etDisplayName.text.toString().trim(),
            username = dialogBinding.etUsername.text.toString().trim(),
            phoneNumber = dialogBinding.etPhone.text.toString().trim(),
            gender = gender,
            dateOfBirth = dialogBinding.etDob.text.toString().trim(),
            bio = dialogBinding.etBio.text.toString().trim()
        ) ?: return

        viewModel.updateUserProfile(updatedUser) { success ->
            activity?.runOnUiThread {
                if (success) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Lỗi cập nhật", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showImagePreview(imageUrl: String, isAvatar: Boolean) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_preview)
        
        val imageView = dialog.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.preview_image)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_preview)
        
        if (isAvatar) {
            // Force square/circle for avatar in preview
            imageView.layoutParams.width = (resources.displayMetrics.widthPixels * 0.8).toInt()
            imageView.layoutParams.height = imageView.layoutParams.width
            imageView.shapeAppearanceModel = imageView.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(com.google.android.material.shape.RelativeCornerSize(0.5f))
                .build()
        } else {
            // Cover: match screen width, auto height via adjustViewBounds
            imageView.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            imageView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            imageView.shapeAppearanceModel = imageView.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(0f)
                .build()
        }
        
        Glide.with(this).load(imageUrl).into(imageView)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDatePicker(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        
        // Parse current date if exists
        val currentText = editText.text.toString()
        if (currentText.isNotEmpty()) {
            try {
                val parts = currentText.split("/")
                if (parts.size == 3) {
                    calendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                    calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(Calendar.YEAR, parts[2].toInt())
                }
            } catch (e: Exception) {}
        }

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = "${dayOfMonth}/${month + 1}/${year}"
                editText.setText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // Constraint: Must be before today
        datePicker.datePicker.maxDate = System.currentTimeMillis() - 86400000 // Yesterday
        
        datePicker.show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun startCrop(uri: Uri) {
        val options = CropImageOptions().apply {
            guidelines = CropImageView.Guidelines.ON
            cropShape = if (isUploadingAvatar) CropImageView.CropShape.OVAL else CropImageView.CropShape.RECTANGLE
            fixAspectRatio = true
            aspectRatioX = if (isUploadingAvatar) 1 else 16
            aspectRatioY = if (isUploadingAvatar) 1 else 9
            showProgressBar = true
            activityTitle = if (isUploadingAvatar) "Cắt ảnh đại diện" else "Cắt ảnh bìa"
            activityMenuIconColor = android.graphics.Color.WHITE
            autoZoomEnabled = true
        }
        cropImage.launch(CropImageContractOptions(uri, options))
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.userProfile.collect { user ->
                user?.let {
                    binding.userName.text = if (it.displayName.isNotEmpty()) it.displayName else it.username
                    binding.userHandle.text = "@${it.username}"
                    binding.phNumber.text = if (it.phoneNumber.isNotEmpty()) it.phoneNumber else "Not set"
                    binding.tvEmail.text = it.email ?: "Not set"
                    binding.tvGender.text = it.gender ?: "Not set"
                    binding.tvDob.text = it.dateOfBirth ?: "Not set"
                    binding.userBio.text = it.bio

                    if (isMyProfile) {
                        if (it.voiceprint.isNotEmpty()) {
                            binding.tvVoiceStatus.text = "Đã đăng ký"
                            binding.tvVoiceStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        } else {
                            binding.tvVoiceStatus.text = "Chưa đăng ký"
                            binding.tvVoiceStatus.setTextColor(android.graphics.Color.parseColor("#EF5350"))
                        }
                    }

                    if (!isMyProfile) {
                        if (it.isOnline) {
                            binding.onlineIndicator.visibility = View.VISIBLE
                            binding.tvLastSeen.visibility = View.GONE
                        } else {
                            binding.onlineIndicator.visibility = View.GONE
                            binding.tvLastSeen.visibility = View.VISIBLE
                            binding.tvLastSeen.text = "Last seen: ${formatLastSeen(it.lastSeen)}"
                        }
                    }

                    if (isAdded) {
                        Glide.with(this@ProfileFragment)
                            .load(it.avatarUrl)
                            .placeholder(R.drawable.ic_person)
                            .into(binding.profileImage)

                        if (it.coverUrl?.isNotEmpty() == true) {
                            Glide.with(this@ProfileFragment)
                                .load(it.coverUrl)
                                .into(binding.profileCover)
                        }
                    }
                }
            }
        }
    }

    private fun formatLastSeen(timestamp: Long): String {
        if (timestamp == 0L) return "Never"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} mins ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun fetchUserDetails() {
        if (targetUserId.isEmpty()) return
        viewModel.fetchUserProfile(targetUserId)
    }

    private fun uploadImageToImgBB(uri: Uri) {
        val message = if (isUploadingAvatar) "Đang cập nhật ảnh đại diện..." else "Đang cập nhật ảnh bìa..."
        
        // Show Loading Overlay
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.tvLoadingMessage.text = message

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)

                viewModel.uploadProfileImage(base64Image ?: "") { imageUrl ->
                    activity?.runOnUiThread {
                        if (imageUrl != null) {
                            val currentUser = viewModel.userProfile.value ?: return@runOnUiThread
                            val updatedUser = if (isUploadingAvatar) {
                                currentUser.copy(avatarUrl = imageUrl)
                            } else {
                                currentUser.copy(coverUrl = imageUrl)
                            }
                            
                            viewModel.updateUserProfile(updatedUser) { success ->
                                activity?.runOnUiThread {
                                    binding.loadingOverlay.visibility = View.GONE
                                    if (success) {
                                        Toast.makeText(requireContext(), "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(requireContext(), "Lỗi cập nhật dữ liệu!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            binding.loadingOverlay.visibility = View.GONE
                            Toast.makeText(requireContext(), "Lỗi upload ảnh!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), "Lỗi hệ thống: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
