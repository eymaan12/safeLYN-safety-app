package com.example.SafeLYN

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

class ItemFeatureCardReportActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val PICK_IMAGE_REQUEST = 101
    private lateinit var selectedImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.report_safety_issue_screen)

        val submitBtn = findViewById<Button>(R.id.submitBtn)
        val chooseFileBtn = findViewById<Button>(R.id.chooseFileBtn)
        val locationInput = findViewById<EditText>(R.id.locationInput)
        val reportDescription = findViewById<EditText>(R.id.reportDescription)
        val viewHistoryBtn = findViewById<Button>(R.id.viewHistoryBtn)
        selectedImageView = findViewById(R.id.selectedImageView)

        refreshStats()

        viewHistoryBtn.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        chooseFileBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        submitBtn.setOnClickListener {
            val location = locationInput.text.toString().trim()
            val description = reportDescription.text.toString().trim()

            if (location.isNotEmpty()) {
                // FIX: Upload in the format the Feed expects
                uploadReportToCloud(location, description)

                // Update Local Count
                val sharedPrefs = getSharedPreferences("SafetyLogs", Context.MODE_PRIVATE)
                val currentReports = sharedPrefs.getInt("report_count", 0)
                sharedPrefs.edit().putInt("report_count", currentReports + 1).apply()

                refreshStats()
                Toast.makeText(this, "Report Live on Community Feed!", Toast.LENGTH_SHORT).show()

                locationInput.text.clear()
                reportDescription.text.clear()
                selectedImageView.visibility = View.GONE
            } else {
                Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        val sharedPrefs = getSharedPreferences("SafetyLogs", Context.MODE_PRIVATE)
        val sosCountText = findViewById<TextView>(R.id.sosCountText)
        val reportsCountText = findViewById<TextView>(R.id.reportsCountText)
        sosCountText.text = sharedPrefs.getInt("sos_count", 0).toString()
        reportsCountText.text = sharedPrefs.getInt("report_count", 0).toString()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data
            selectedImageView.visibility = View.VISIBLE
            selectedImageView.setImageURI(imageUri)
        }
    }

    private fun uploadReportToCloud(location: String, description: String) {
        // FIX: Match the 'ReportModel' schema so it shows up in the Feed!
        val report = hashMapOf(
            "title" to "General Safety Report", // Default title for personal reports
            "description" to if (description.isEmpty()) "Safety Issue Reported" else description,
            "location" to location,
            "riskLevel" to "Medium Risk", // Default risk
            "resolved" to false,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("reports").add(report)
    }
}