package com.example.SafeLYN

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SosConfirmationActivity : AppCompatActivity() {

    private lateinit var timer: CountDownTimer
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private lateinit var prefsManager: PrefsManager
    private var isCancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // FIX 1: Force Screen Wake-Up Logic (Ensures it pops up over Lock Screen)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_confirmation)

        prefsManager = PrefsManager(this) // Wired to V2 Storage
        startAlarm()

        val txtCountdown = findViewById<TextView>(R.id.txtCountdown)
        timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millis: Long) { txtCountdown.text = (millis / 1000).toString() }
            override fun onFinish() { if (!isCancelled) { stopAlarm(); sendRealSOS() } }
        }.start()

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            isCancelled = true
            timer.cancel()
            stopAlarm()
            finish()
        }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            timer.cancel()
            stopAlarm()
            sendRealSOS()
        }
    }

    private fun sendRealSOS() {
        val numbers = prefsManager.getGuardianNumbers()
        if (numbers.isEmpty()) {
            Toast.makeText(this, "No Guardians Found!", Toast.LENGTH_SHORT).show()
            return
        }

        // FIX 2: Increment SOS Count so History/Report Activity updates correctly
        val logPrefs = getSharedPreferences("SafetyLogs", Context.MODE_PRIVATE)
        val currentCount = logPrefs.getInt("sos_count", 0)
        logPrefs.edit().putInt("sos_count", currentCount + 1).apply()

        // 1. STOP the listener immediately so the MIC is free for the SOS recording
        stopService(Intent(this, SosBackgroundService::class.java))

        // 2. Trigger the sequence (SMS -> WhatsApp Text -> 15s Recording)
        SOSUtils.startEmergencySequence(this, numbers)

        // 3. RESTART the listener after the 15-second recording + 1s finalization delay
        // We use a 17-second delay to be safe (15s recording + 2s buffer)
        Handler(Looper.getMainLooper()).postDelayed({
            val serviceIntent = Intent(this, SosBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }, 17000)

        startActivity(Intent(this, AlertSuccessActivity::class.java))
        finish()
    }

    private fun startAlarm() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
        } catch (e: Exception) {}
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        timer.cancel()
    }
}