package com.example.SafeLYN

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ItemFeatureCardVoiceActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private val REQUEST_RECORD_AUDIO = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sos_keyword_activity)

        prefsManager = PrefsManager(this)

        // --- FIXED: IDs now match sos_keyword_activity.xml ---
        val inputKeyword = findViewById<EditText>(R.id.secretPhrase)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val txtStatus = findViewById<TextView>(R.id.listeningText)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        // 1. Load existing keyword
        val savedWord = prefsManager.getVoiceKeyword()
        inputKeyword.setText(savedWord)
        txtStatus.text = "Current Keyword: $savedWord"

        // 2. Back Button Logic
        btnBack.setOnClickListener { finish() }

        // 3. Save Button Logic
        btnSave.setOnClickListener {
            val word = inputKeyword.text.toString().trim()
            if (word.isNotEmpty()) {
                prefsManager.saveVoiceKeyword(word)
                txtStatus.text = "Current Keyword: $word"
                Toast.makeText(this, "Keyword Saved!", Toast.LENGTH_SHORT).show()

                // Optional: Restart service automatically when saving
                checkPermissionsAndStartService()
            } else {
                Toast.makeText(this, "Keyword cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Test/Start Button Logic
        btnTest.setOnClickListener {
            checkPermissionsAndStartService()
        }
    }

    private fun checkPermissionsAndStartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        } else {
            startVoiceService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceService()
        } else {
            Toast.makeText(this, "Microphone permission needed for Voice SOS", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, SosBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        val txtStatus = findViewById<TextView>(R.id.listeningText)
        txtStatus.text = "Status: ACTIVE (Listening for '${prefsManager.getVoiceKeyword()}')"
        Toast.makeText(this, "Voice Service Active", Toast.LENGTH_SHORT).show()
    }
}