package com.giangnam.chat_app.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.giangnam.chat_app.data.model.UserSettings
import com.giangnam.chat_app.data.repository.NearbyRepository
import com.giangnam.chat_app.databinding.ActivityPrivacySettingsBinding
import com.giangnam.chat_app.service.LocationTrackingService
import com.giangnam.chat_app.utils.Constants
import com.giangnam.chat_app.utils.Utils
import com.google.firebase.database.FirebaseDatabase

class PrivacySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySettingsBinding
    private val currentUserId = Utils.getUserId()
    private val settingsRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
        .getReference(Constants.NODE_USER_SETTINGS)
    private val nearbyRepository = NearbyRepository.getInstance()

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadSettings()

        binding.switchNearby.setOnCheckedChangeListener { _, isChecked ->
            updateSetting("nearbyEnabled", isChecked)
            if (!isChecked) {
                nearbyRepository.disableNearby(currentUserId) {}
                stopLocationService()
            } else {
                checkAndStartLocationService()
            }
        }

        binding.switchLocationSharing.setOnCheckedChangeListener { _, isChecked ->
            updateSetting("locationSharingEnabled", isChecked)
            if (isChecked && binding.switchNearby.isChecked) {
                checkAndStartLocationService()
            } else if (!isChecked) {
                stopLocationService()
            }
        }
    }

    private fun loadSettings() {
        settingsRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val settings = snapshot.getValue(UserSettings::class.java) ?: UserSettings()
            binding.switchNearby.isChecked = settings.nearbyEnabled
            binding.switchLocationSharing.isChecked = settings.locationSharingEnabled
        }
    }

    private fun updateSetting(key: String, value: Boolean) {
        settingsRef.child(currentUserId).child(key).setValue(value)
    }

    private fun checkAndStartLocationService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        startService(serviceIntent)
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        stopService(serviceIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService()
            } else {
                Toast.makeText(this, "Location permission is required for Nearby", Toast.LENGTH_SHORT).show()
                binding.switchLocationSharing.isChecked = false
            }
        }
    }
}
