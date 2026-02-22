package com.example.SafeLYN

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    // 1. Declare Firebase
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // 2. Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // 3. Find Views (Matching the IDs from your updated XML)
        val emailInput = findViewById<EditText>(R.id.etEmail)
        val resetButton = findViewById<Button>(R.id.btnReset)
        val backButton = findViewById<ImageView>(R.id.btnBack)

        // 4. Back Button Logic (Go back to Login)
        backButton.setOnClickListener {
            finish()
        }

        // 5. Reset Button Logic
        resetButton.setOnClickListener {
            val email = emailInput.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your registered email", Toast.LENGTH_SHORT).show()
            } else {
                // --- FIREBASE RESET LOGIC ---
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Success: Firebase sent the email
                            Toast.makeText(this, "Reset link sent! Check your email.", Toast.LENGTH_LONG).show()
                            finish() // Close screen so they can login
                        } else {
                            // Failure: Email not found or bad internet
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }
}