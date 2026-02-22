package com.example.SafeLYN

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Auto-login check
        if (auth.currentUser != null) {
            startActivity(Intent(this, FrontPageActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tabSignup = findViewById<TextView>(R.id.tabSignup)
        val tvForgot = findViewById<TextView>(R.id.tvForgot)
        val btnSkip = findViewById<TextView>(R.id.btnSkip)

        // 1. Tab Switching: Click "Sign Up" -> Go to SignupActivity
        tabSignup.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            // Remove animation to make it look like a tab switch
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            finish()
        }

        // 2. Login Logic
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Welcome Back!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, FrontPageActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Forgot Password
        tvForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // 4. Skip Button
        btnSkip.setOnClickListener {
            startActivity(Intent(this, FrontPageActivity::class.java))
            finish()
        }
    }
}