package com.example.SafeLYN

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ItemFeatureCardFeedActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var db: FirebaseFirestore
    private lateinit var feedContainer: LinearLayout

    // Sensor & Location
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var currentLux: Float = 1000f
    private var currentLocationString = "Unknown Location"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_feed)

        db = FirebaseFirestore.getInstance()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Find the Container where we will add new cards
        feedContainer = findViewById(R.id.feedContainer)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Setup hardcoded buttons (so they work too)
        setupHardcodedCards()

        findViewById<FloatingActionButton>(R.id.fabNewReport).setOnClickListener {
            showAddReportDialog()
        }

        fetchRealLocation()
        startRealtimeUpdates()
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            currentLux = event.values[0]
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun fetchRealLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val street = addresses[0].thoroughfare ?: addresses[0].locality
                            currentLocationString = street ?: "Nearby"
                        }
                    } catch (e: Exception) { currentLocationString = "GPS Error" }
                }
            }
        }
    }

    private fun setupHardcodedCards() {
        try {
            findViewById<Button>(R.id.btnHighResolved)?.setOnClickListener { disableButton(it as Button) }
            findViewById<Button>(R.id.btnMediumResolved)?.setOnClickListener { disableButton(it as Button) }
            findViewById<Button>(R.id.btnLowResolved)?.setOnClickListener { disableButton(it as Button) }
        } catch (e: Exception) { }
    }

    private fun disableButton(btn: Button) {
        btn.isEnabled = false
        btn.alpha = 0.5f
        btn.text = "Resolved"
    }

    private fun startRealtimeUpdates() {
        db.collection("reports")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                if (snapshots != null) {
                    for (doc in snapshots.documentChanges) {
                        if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val report = doc.document.toObject(ReportModel::class.java)
                            report.id = doc.document.id
                            addNewCardToFeed(report)
                        }
                    }
                }
            }
    }

    private fun showAddReportDialog() {
        // This requires 'dialog_add_report.xml' to exist
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_report, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = builder.create()

        val etTitle = dialogView.findViewById<EditText>(R.id.etReportTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etReportDesc)
        val spinnerRisk = dialogView.findViewById<Spinner>(R.id.spinnerRisk)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitReport)

        val risks = arrayOf("Low Risk", "Medium Risk", "High Risk", "Poor Lighting")
        spinnerRisk.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, risks)

        if (currentLux < 10) {
            spinnerRisk.setSelection(3)
            etTitle.setText("Dark Area Detected")
            etDesc.setText("Sensor read $currentLux lux.")
        }

        btnSubmit.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val risk = risks[spinnerRisk.selectedItemPosition]

            if (title.isNotEmpty()) {
                val report = hashMapOf(
                    "title" to title,
                    "description" to desc,
                    "riskLevel" to risk,
                    "timestamp" to System.currentTimeMillis(),
                    "resolved" to false,
                    "location" to currentLocationString
                )
                db.collection("reports").add(report).addOnSuccessListener {
                    Toast.makeText(this, "Report Added!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun addNewCardToFeed(report: ReportModel) {
        // Inflate the card template
        val view = LayoutInflater.from(this).inflate(R.layout.item_community_report, feedContainer, false)

        // Find Views
        val title = view.findViewById<TextView>(R.id.txtTitle)
        val desc = view.findViewById<TextView>(R.id.txtDesc)
        val risk = view.findViewById<TextView>(R.id.txtRisk)
        val time = view.findViewById<TextView>(R.id.txtTime)
        val bg = view.findViewById<FrameLayout>(R.id.riskBackground)
        val icon = view.findViewById<ImageView>(R.id.imgRiskIcon)
        val btn = view.findViewById<Button>(R.id.btnResolve)

        // REMOVED THE BAD LINE HERE (val container = ...)

        // Bind Data
        title.text = report.title
        desc.text = "${report.description} \n📍 ${report.location}"
        risk.text = report.riskLevel

        val date = Date(report.timestamp)
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        time.text = format.format(date)

        // Click to Open Maps (Click the whole card)
        view.setOnClickListener {
            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(report.location)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            try { startActivity(mapIntent) } catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
        }

        // Apply Colors
        when (report.riskLevel) {
            "High Risk" -> {
                bg.setBackgroundColor(Color.parseColor("#FF3B30"))
                risk.setTextColor(Color.parseColor("#C62828"))
                risk.setBackgroundColor(Color.parseColor("#FFF2F2"))
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF3B30"))
                icon.setImageResource(android.R.drawable.ic_dialog_alert)
            }
            "Medium Risk" -> {
                bg.setBackgroundColor(Color.parseColor("#FF9500"))
                risk.setTextColor(Color.parseColor("#BF360C"))
                risk.setBackgroundColor(Color.parseColor("#FFF7E5"))
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9500"))
                icon.setImageResource(android.R.drawable.ic_dialog_info)
            }
            else -> {
                bg.setBackgroundColor(Color.parseColor("#007AFF"))
                risk.setTextColor(Color.parseColor("#0D47A1"))
                risk.setBackgroundColor(Color.parseColor("#E5F0FF"))
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#007AFF"))
                icon.setImageResource(android.R.drawable.ic_dialog_email)
            }
        }

        btn.setOnClickListener {
            db.collection("reports").document(report.id).update("resolved", true)
            disableButton(btn)
        }

        if (report.resolved) {
            disableButton(btn)
        }

        // Add new reports to the TOP of the feed
        feedContainer.addView(view, 0)
    }
}

// Data Model
data class ReportModel(
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val riskLevel: String = "",
    val timestamp: Long = 0,
    val resolved: Boolean = false,
    val location: String = ""
) {
    constructor() : this("", "", "", "", 0, false, "")
}