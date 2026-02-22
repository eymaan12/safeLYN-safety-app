package com.example.SafeLYN

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlertSuccessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_success)

        val btnEndAlert = findViewById<Button>(R.id.btnEndAlert)
        val btnCallPolice = findViewById<Button>(R.id.btnCallPolice)
        val guardianContainer = findViewById<LinearLayout>(R.id.guardianListContainer)
        val txtLocation = findViewById<TextView>(R.id.txtCurrentLocation)

        txtLocation.text = "Live Tracking Active"

        // 1. Fetch fresh data
        val prefsManager = PrefsManager(this)
        val namesList = prefsManager.getGuardianNamesList()

        // 2. Clear old views to fix the "stale data" bug
        guardianContainer.removeAllViews()

        // 3. Populate fresh list
        if (namesList.isNotEmpty()) {
            for (name in namesList) {
                val nameView = TextView(this).apply {
                    text = "• ${name.trim()}"
                    textSize = 18f
                    setTextColor(Color.BLACK)
                    setPadding(16, 8, 16, 8)
                }
                guardianContainer.addView(nameView)
            }
        }

        btnEndAlert.setOnClickListener {
            val intent = Intent(this, FrontPageActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        btnCallPolice.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:15")))
        }
    }
}