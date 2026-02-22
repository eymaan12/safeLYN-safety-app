package com.example.SafeLYN

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory // Import added
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CommunityBackgroundService : Service() {

    private val db = Firebase.firestore
    private var isFirstLoad = true

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startRealtimeListener()
    }

    private fun startRealtimeListener() {
        // Listen to the 'reports' collection in real-time
        db.collection("reports").addSnapshotListener { snapshots, _ ->
            if (snapshots != null) {
                // Skip the initial data load so you don't get alerts for old reports
                if (isFirstLoad) {
                    isFirstLoad = false
                    return@addSnapshotListener
                }

                for (dc in snapshots.documentChanges) {
                    // Only alert on NEW reports added while the service is running
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val desc = dc.document.data["description"] as? String ?: "New Issue"
                        val loc = dc.document.data["location"] as? String ?: "Nearby"

                        // Send Real Notification
                        sendNotification("Community Alert", "$desc reported at $loc")
                    }
                }
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "CommunityGuardReal"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Real-time Safety", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // ICON FIX: Load App Icon
        val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeLYN Guard")
            .setContentText("Listening for real-time community reports...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(appIcon) // Show full color icon
            .build()

        // Ensure service keeps running
        startForeground(2, notification)
    }

    private fun sendNotification(title: String, message: String) {
        // CRASH FIX: Check Android 13+ Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return // Permission denied; do not crash
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ICON FIX: Load App Icon
        val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(this, "CommunityGuardReal")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher) // Changed from ic_dialog_alert
            .setLargeIcon(appIcon) // Show full color icon
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up notification
            .build()

        // Use current time as ID to allow multiple notifications
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}