package com.example.SafeLYN

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.InputStream
import java.util.Locale

class FrontPageActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var txtWelcome: TextView
    private lateinit var txtLocation: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var notificationIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_front_page)

        profileImage = findViewById(R.id.profileImage)
        txtWelcome = findViewById(R.id.txtWelcome)
        txtLocation = findViewById(R.id.txtLocation)
        bottomNav = findViewById(R.id.bottomNavigationView)
        notificationIcon = findViewById(R.id.notificationIcon)

        setupClicks()
        setupBottomNav()
        fetchCurrentLocation()
        checkPermissionsAndStartService()
        askToDisableDozeMode()
    }

    override fun onResume() {
        super.onResume()
        updateHeader() // Refreshes image every time you come back
        if (bottomNav.selectedItemId != R.id.nav_home) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun updateHeader() {
        val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("USER_NAME", "Safety Hero")
        txtWelcome.text = "Hi, $name"

        val imagePath = prefs.getString("USER_IMAGE_PATH", null)
        if (imagePath != null) {
            try {
                // FIX: Load as Circle
                loadCircularImage(Uri.parse(imagePath))
            } catch (e: Exception) {
                profileImage.setImageResource(R.drawable.ic_person)
            }
        } else {
            profileImage.setImageResource(R.drawable.ic_person)
        }
    }

    private fun loadCircularImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val circularDrawable = RoundedBitmapDrawableFactory.create(resources, bitmap)
            circularDrawable.isCircular = true
            profileImage.setImageDrawable(circularDrawable)
        } catch (e: Exception) {
            profileImage.setImageResource(R.drawable.ic_person)
        }
    }

    private fun setupClicks() {
        profileImage.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        notificationIcon.setOnClickListener { startActivity(Intent(this, ItemFeatureCardFeedActivity::class.java)) }

        findViewById<LinearLayout>(R.id.cardNavigate).setOnClickListener { startActivity(Intent(this, MapSafeRouteActivity::class.java)) }
        findViewById<LinearLayout>(R.id.cardSOS).setOnClickListener { startActivity(Intent(this, ItemFeatureCardSosActivity::class.java)) }
        findViewById<LinearLayout>(R.id.cardReport).setOnClickListener { startActivity(Intent(this, ItemFeatureCardReportActivity::class.java)) }
        findViewById<LinearLayout>(R.id.cardVoice).setOnClickListener { startActivity(Intent(this, ItemFeatureCardVoiceActivity::class.java)) }
        findViewById<LinearLayout>(R.id.cardGuardian).setOnClickListener { startActivity(Intent(this, ItemFeatureCardGuardianActivity::class.java)) }
        findViewById<LinearLayout>(R.id.cardFeed).setOnClickListener { startActivity(Intent(this, ItemFeatureCardFeedActivity::class.java)) }
    }

    // ... (Keep existing methods: setupBottomNav, checkPermissionsAndStartService, fetchCurrentLocation, askToDisableDozeMode)
    // Make sure to include them from previous steps or your existing file.
    private fun askToDisableDozeMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("HAS_ASKED_BATTERY", false)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                prefs.edit().putBoolean("HAS_ASKED_BATTERY", true).apply()
            }
        }
    }

    // Inside FrontPageActivity.kt, update your checkPermissionsAndStartService function
    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)

        if(permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            // Start/Restart service to ensure it picks up any new keyword changes
            val serviceIntent = Intent(this, SosBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_map -> { startActivity(Intent(this, MapSafeRouteActivity::class.java)); true }
                R.id.nav_sos -> { startActivity(Intent(this, ItemFeatureCardSosActivity::class.java)); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
                else -> false
            }
        }
    }
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            txtLocation.text = "${addresses[0].locality}, ${addresses[0].countryName}"
                        }
                    } catch (e: Exception) { txtLocation.text = "Offline" }
                }
            }
        }
    }
}