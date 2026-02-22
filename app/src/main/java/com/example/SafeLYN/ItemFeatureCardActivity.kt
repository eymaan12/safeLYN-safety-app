package com.example.SafeLYN

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class ItemFeatureCardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_safe_route) // Uses your Map XML

        val startNav = findViewById<Button>(R.id.startNavigation)
        startNav.setOnClickListener {
            Toast.makeText(this, "Navigation Started", Toast.LENGTH_SHORT).show()
        }
    }
}