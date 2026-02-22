package com.example.SafeLYN

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class EditProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile) // Ensure this XML exists!

        val editName = findViewById<TextInputEditText>(R.id.editName)
        val editEmail = findViewById<TextInputEditText>(R.id.editEmail)
        val editPhone = findViewById<TextInputEditText>(R.id.editPhone)
        val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)

        // Load
        editName.setText(prefs.getString("USER_NAME", ""))
        editEmail.setText(prefs.getString("USER_EMAIL", ""))
        editPhone.setText(prefs.getString("USER_PHONE", ""))

        // Save
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            prefs.edit().apply {
                putString("USER_NAME", editName.text.toString())
                putString("USER_EMAIL", editEmail.text.toString())
                putString("USER_PHONE", editPhone.text.toString())
                apply()
            }
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }
}