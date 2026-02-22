package com.example.SafeLYN

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashScreenActivity : AppCompatActivity() {
    private val SPLASH_TIME_OUT: Long = 2500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splashscreen)

        val logoContainer: View = findViewById(R.id.logoContainer)
        val glowRing: View = findViewById(R.id.glowRing)
        val tvAppName: TextView = findViewById(R.id.tvAppName)
        val tvTagline: View = findViewById(R.id.safetyLineTextView)

        // Init State
        logoContainer.alpha = 0f
        logoContainer.scaleX = 0.5f
        logoContainer.scaleY = 0.5f

        glowRing.alpha = 0f
        glowRing.scaleX = 0.5f

        tvAppName.alpha = 0f
        tvAppName.translationY = 50f

        tvTagline.alpha = 0f

        // Animate Logo Pop
        logoContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(800).start()

        // Animate Glow Ring Expansion
        glowRing.animate().alpha(1f).scaleX(1f).setDuration(1000).setStartDelay(200).start()

        // Animate Text Slide Up
        tvAppName.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(500).start()
        tvTagline.animate().alpha(1f).setDuration(600).setStartDelay(700).start()

        Handler(Looper.getMainLooper()).postDelayed({
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                startActivity(Intent(this, FrontPageActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, SPLASH_TIME_OUT)
    }
}