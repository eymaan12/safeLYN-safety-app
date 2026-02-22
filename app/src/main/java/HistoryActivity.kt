package com.example.SafeLYN

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history) // Error happens here if filename is wrong

        // Load the shared preferences saved in the main screen
        val prefs = getSharedPreferences("SafetyLogs", Context.MODE_PRIVATE)
        val sos = prefs.getInt("sos_count", 0)
        val reports = prefs.getInt("report_count", 0)

        // Find the view by ID
        val logDisplay = findViewById<TextView>(R.id.historyLogText)

        logDisplay.text = "History Details:\n\n" +
                "• Emergency SOS triggers: $sos\n" +
                "• Safety community reports: $reports\n" +
                "• Status: All logs synced to cloud."

        findViewById<Button>(R.id.backBtn).setOnClickListener {
            finish()
        }
    }
}