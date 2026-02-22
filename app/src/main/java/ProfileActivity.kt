package com.example.SafeLYN

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.InputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var txtName: TextView
    private lateinit var txtEmail: TextView
    private lateinit var switchNotif: Switch

    // Image Picker
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("USER_IMAGE_PATH", uri.toString()).apply()

            // Fix: Use helper to make it circular immediately
            loadCircularImage(uri)
            Toast.makeText(this, "Photo Updated!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_activity)

        imgProfile = findViewById(R.id.profileImage)
        txtName = findViewById(R.id.profileName)
        txtEmail = findViewById(R.id.profileEmail)
        switchNotif = findViewById(R.id.switchNotif)

        loadUserData()
        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)
        txtName.text = prefs.getString("USER_NAME", "Guest User")
        txtEmail.text = prefs.getString("USER_EMAIL", "No email set")
        switchNotif.isChecked = prefs.getBoolean("NOTIFICATIONS_ENABLED", true)

        val imagePath = prefs.getString("USER_IMAGE_PATH", null)
        if (imagePath != null) {
            try {
                loadCircularImage(Uri.parse(imagePath))
            } catch (e: Exception) {
                imgProfile.setImageResource(R.drawable.ic_person)
            }
        } else {
            // Default image
            imgProfile.setImageResource(R.drawable.ic_person)
        }
    }

    // FIX: Helper function to crop any image into a perfect circle
    private fun loadCircularImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Create a Circular Drawable from the Bitmap
            val circularBitmapDrawable = RoundedBitmapDrawableFactory.create(resources, bitmap)
            circularBitmapDrawable.isCircular = true

            imgProfile.setImageDrawable(circularBitmapDrawable)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if loading fails
            imgProfile.setImageResource(R.drawable.ic_person)
        }
    }

    private fun setupClicks() {
        findViewById<ImageView>(R.id.backBtn).setOnClickListener { finish() }

        // 1. Single Click: Change Photo
        imgProfile.setOnClickListener {
            Toast.makeText(this, "Select a new profile picture", Toast.LENGTH_SHORT).show()
            imagePicker.launch(arrayOf("image/*"))
        }

        // 2. FIX: Long Click: Remove Photo
        imgProfile.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove Photo?")
                .setMessage("Do you want to remove your profile picture?")
                .setPositiveButton("Remove") { _, _ ->
                    val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("USER_IMAGE_PATH").apply()
                    imgProfile.setImageResource(R.drawable.ic_person)
                    Toast.makeText(this, "Photo Removed", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        findViewById<LinearLayout>(R.id.rowProfile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowGuardian).setOnClickListener {
            startActivity(Intent(this, ItemFeatureCardGuardianActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowVoice).setOnClickListener {
            startActivity(Intent(this, ItemFeatureCardVoiceActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowPrivacy).setOnClickListener {
            showInfoDialog("Privacy Settings", "Your location is only shared when SOS is triggered. \n\nWe do not store your audio recordings.")
        }

        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("SafeLYN_Prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("NOTIFICATIONS_ENABLED", isChecked).apply()
            val status = if (isChecked) "Enabled" else "Disabled"
            Toast.makeText(this, "Notifications $status", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.rowAbout).setOnClickListener {
            showInfoDialog("About SafeLYN", "SafeLYN v1.0\n\nDeveloped for Community Safety.")
        }

        findViewById<LinearLayout>(R.id.rowLogout).setOnClickListener {
            Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
            finishAffinity()
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}