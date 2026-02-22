package com.example.SafeLYN

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

class SosBackgroundService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var prefsManager: PrefsManager
    private lateinit var speechIntent: Intent
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false

    private val CHANNEL_ID_SERVICE = "SafeLynVoiceChannel"
    private val CHANNEL_ID_ALERT = "SafeLynAlertChannel"

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // FIX 1: ACQUIRE WAKELOCK WITHOUT TIMEOUT
        // This ensures the CPU stays awake indefinitely while the service is running.
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeLYN::VoiceLock")
        wakeLock?.acquire()

        createNotificationChannels()
        startForegroundService()
        initSpeechRecognizer()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel 1: Silent foreground service notification
            val serviceChannel = NotificationChannel(CHANNEL_ID_SERVICE, "Voice Guard Service", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(serviceChannel)

            // Channel 2: High Priority Alarm for SOS triggering
            val alertChannel = NotificationChannel(CHANNEL_ID_ALERT, "SOS ALERTS", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                description = "Triggers when SOS keyword is heard"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun startForegroundService() {
        val currentKeyword = prefsManager.getVoiceKeyword()
        val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        // FIX 2: PendingIntent makes the notification clickable, preventing system kills
        val intent = Intent(this, FrontPageActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("SafeLYN Active")
            .setContentText("Listening for: \"$currentKeyword\"")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(appIcon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    restartListening()
                }

                override fun onError(error: Int) {
                    isListening = false
                    // FIX 3: SMART RESTART LOGIC
                    // Error 7 (No Match) & 6 (Timeout) are normal -> Soft Restart
                    // Other errors (like 2 or 5) mean the mic is disconnected -> Hard Reset
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        handler.postDelayed({ startListeningSafe() }, 500)
                    } else {
                        handler.postDelayed({ resetSpeechRecognizer() }, 1000)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkKeywords(matches)
                    startListeningSafe()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkKeywords(matches)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            startListeningSafe()
        }
    }

    // Helper to completely destroy and recreate the recognizer if it freezes
    private fun resetSpeechRecognizer() {
        handler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                initSpeechRecognizer()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startListeningSafe() {
        handler.post {
            try {
                if (speechRecognizer == null) initSpeechRecognizer()
                speechRecognizer?.startListening(speechIntent)
                isListening = true
            } catch (e: Exception) {
                Log.e("VOICE", "Failed to start listening: ${e.message}")
                handler.postDelayed({ resetSpeechRecognizer() }, 2000)
            }
        }
    }

    private fun restartListening() {
        handler.post {
            try {
                speechRecognizer?.stopListening()
                startListeningSafe()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun checkKeywords(matches: ArrayList<String>?) {
        val triggerWord = prefsManager.getVoiceKeyword().lowercase().trim()
        matches?.forEach { sentence ->
            if (sentence.lowercase().contains(triggerWord)) {
                triggerSOS()
                return@forEach
            }
        }
    }

    private fun triggerSOS() {
        // Stop listening so Mic is free for recording in the next activity
        try {
            speechRecognizer?.stopListening()
            isListening = false
        } catch (e: Exception) {}

        val intent = Intent(this, SosConfirmationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            putExtra("TRIGGER_SOURCE", "Voice Command")
        }

        // FIX 4: Full Screen Intent to wake up the screen from background
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(appIcon)
            .setContentTitle("SOS Triggered!")
            .setContentText("Voice command detected.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // This line is CRITICAL for popping up over the lock screen on Android 10+
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, notification)

        // Try standard start as well for older Android versions
        try {
            startActivity(intent)
        } catch (e: Exception) { Log.e("SOS", "Launch failed: ${e.message}") }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { wakeLock?.release() } catch (e: Exception) {}
        speechRecognizer?.destroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        if (!isListening) startListeningSafe()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}