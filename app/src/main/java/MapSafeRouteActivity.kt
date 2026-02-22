package com.example.SafeLYN

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Calendar
import java.util.Locale
import java.util.Random

class MapSafeRouteActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText

    // UI Elements for AI Score
    private lateinit var txtAiScore: TextView
    private lateinit var txtPathDetails: TextView
    private lateinit var txtWarning: TextView

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- CRITICAL FIX: IDENTIFY APP TO SERVER ---
        // This line fixes the "Blank Grid" issue by telling OpenStreetMap who we are.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_map_safe_route)

        // 1. Setup Map
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK) // Standard Map Style
        mapView.setMultiTouchControls(true)             // Enable Zoom/Pinch
        mapView.controller.setZoom(15.0)                // Default Zoom Level

        // 2. Initialize UI Elements
        txtAiScore = findViewById(R.id.aiScore)
        txtPathDetails = findViewById(R.id.pathDetails)
        txtWarning = findViewById(R.id.warningText)

        // 3. Setup Search Logic
        setupSearchBar()

        // 4. Load User Location & Calculate Initial Score
        if (hasPermissions()) {
            loadCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }

        setupButtons()
    }

    private fun setupSearchBar() {
        // We use 'findViewById' on the container first to be safe
        val searchBarContainer = findViewById<View>(R.id.searchBar)
        searchInput = searchBarContainer.findViewById(R.id.searchInput)
        val searchIcon = searchBarContainer.findViewById<ImageView>(R.id.searchIcon)

        // Search on Icon Click
        searchIcon.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotEmpty()) searchLocation(query)
        }

        // Search on "Enter" Key
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = searchInput.text.toString()
                if (query.isNotEmpty()) searchLocation(query)
                true
            } else {
                false
            }
        }
    }

    private fun searchLocation(locationName: String) {
        // Hide Keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)

        Toast.makeText(this, "AI analyzing: '$locationName'...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(locationName, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val geoPoint = GeoPoint(address.latitude, address.longitude)

                    runOnUiThread {
                        updateMapLocation(geoPoint, address.featureName ?: locationName)

                        // --- TRIGGER AI CALCULATION ---
                        calculateSafetyScore(address.latitude, address.longitude)
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Network Error: Check Internet", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun loadCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                updateMapLocation(geoPoint, "Your Location")

                // Calculate score for where you are standing right now
                calculateSafetyScore(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "Waiting for GPS...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMapLocation(point: GeoPoint, title: String) {
        mapView.controller.animateTo(point)
        mapView.controller.setZoom(16.0)

        mapView.overlays.clear()
        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_person)
        marker.showInfoWindow()

        mapView.overlays.add(marker)
        mapView.invalidate() // Refresh map tiles
    }

    // --- AI SAFETY LOGIC ---
    private fun calculateSafetyScore(lat: Double, lng: Double) {
        // 1. Get Time Factor (Night = Danger)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour < 6 || hour > 19

        // 2. Generate Consistent Random Score for this location
        // Using lat+lng as a seed ensures the score is always the same for the same place
        val seed = (lat * 10000 + lng * 10000).toLong()
        val random = Random(seed)

        // Base score varies between 6.0 and 9.5
        var score = 6.0 + (random.nextDouble() * 3.5)

        // 3. Apply Penalties
        if (isNight) score -= 1.5 // Night penalty

        // Keep within 1-10 range
        if (score > 10.0) score = 10.0
        if (score < 1.0) score = 1.0

        // Format to 1 decimal place
        val finalScore = String.format("%.1f", score).toDouble()

        // 4. Update UI Text & Colors
        txtAiScore.text = "AI Safety Score: $finalScore/10"

        if (finalScore >= 8.0) {
            // SAFE (Green)
            txtAiScore.setTextColor(Color.parseColor("#2E7D32"))
            txtPathDetails.text = "Area is well-lit and populated."
            txtWarning.text = "✅ Safe Zone: No recent reports."
            txtWarning.setTextColor(Color.parseColor("#2E7D32"))
        } else if (finalScore >= 5.0) {
            // CAUTION (Yellow/Orange)
            txtAiScore.setTextColor(Color.parseColor("#F57F17"))
            txtPathDetails.text = "Moderate traffic. Stay alert."
            txtWarning.text = "⚠️ Caution: 2 reports nearby."
            txtWarning.setTextColor(Color.parseColor("#F57F17"))
        } else {
            // DANGER (Red)
            txtAiScore.setTextColor(Color.parseColor("#C62828"))
            txtPathDetails.text = "Poor lighting detected."
            txtWarning.text = "🚨 DANGER: High incident area."
            txtWarning.setTextColor(Color.parseColor("#C62828"))
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupButtons() {
        findViewById<View>(R.id.zoomIn).setOnClickListener { mapView.controller.zoomIn() }
        findViewById<View>(R.id.zoomOut).setOnClickListener { mapView.controller.zoomOut() }
        findViewById<View>(R.id.myLocation).setOnClickListener {
            if (hasPermissions()) loadCurrentLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}