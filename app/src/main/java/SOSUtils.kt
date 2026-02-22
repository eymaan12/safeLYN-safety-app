package com.example.SafeLYN

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object SOSUtils {

    // ⚠️ Ensure these match your Green API Console exactly
    private const val INSTANCE_ID = "7103514142"
    private const val API_TOKEN = "e1c7a1bc665c43109405d0012e0076222a74cc7c6ac9419293"

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startEmergencySequence(context: Context, phoneNumbers: List<String>) {
        // 1. Send Immediate SMS
        sendSOSViaSMS(context, phoneNumbers)

        // 2. Send Immediate WhatsApp Text
        sendSOSViaGreenApi(context, phoneNumbers, null)

        // 3. Start 15-Second Audio Recording
        startAudioRecording(context) { recordedFile ->
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                // FIXED: 1s delay ensures the file is released by the OS before upload
                Handler(Looper.getMainLooper()).postDelayed({
                    sendSOSViaGreenApi(context, phoneNumbers, recordedFile)
                }, 1000)
            } else {
                Log.e("SOS_AUDIO", "Recording failed or file is empty")
            }
        }
    }

    private fun startAudioRecording(context: Context, onRecordingFinished: (File?) -> Unit) {
        // 1. Check for Microphone Permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onRecordingFinished(null)
            return
        }

        try {
            // 2. Create a unique file in the cache directory
            val fileName = "sos_voice_${System.currentTimeMillis()}.m4a"
            audioFile = File(context.cacheDir, fileName)

            // 3. Initialize MediaRecorder based on Android Version
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                // FIXED: Use MPEG_4 and AAC for WhatsApp compatibility
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100) // Standard high-quality audio
                setAudioEncodingBitRate(96000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            // 4. Record for 15 seconds then trigger completion
            Handler(Looper.getMainLooper()).postDelayed({
                stopRecording() // This must call recorder.stop() and recorder.release()
                onRecordingFinished(audioFile)
            }, 15000)

        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            onRecordingFinished(null)
        }
    }
    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) { e.printStackTrace() }
        finally { mediaRecorder = null }
    }

    private fun sendSOSViaGreenApi(context: Context, targetPhoneNumbers: List<String>, audioFile: File?) {
        getCurrentLocation(context) { lat, lng ->
            val messageText = getMessage(lat, lng)

            Thread {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                for (rawNumber in targetPhoneNumbers) {
                    try {
                        val cleanNumber = formatForWhatsApp(rawNumber)
                        val chatId = "$cleanNumber@c.us"

                        val request = if (audioFile != null) {
                            // API call for File Upload
                            val requestBody = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("chatId", chatId)
                                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull()))
                                .build()
                            Request.Builder()
                                .url("https://api.green-api.com/waInstance$INSTANCE_ID/sendFileByUpload/$API_TOKEN")
                                .post(requestBody).build()
                        } else {
                            // API call for Text Message
                            val jsonBody = JSONObject().apply {
                                put("chatId", chatId)
                                put("message", messageText)
                            }
                            val mediaType = "application/json; charset=utf-8".toMediaType()
                            Request.Builder()
                                .url("https://api.green-api.com/waInstance$INSTANCE_ID/sendMessage/$API_TOKEN")
                                .post(jsonBody.toString().toRequestBody(mediaType)).build()
                        }

                        val response = client.newCall(request).execute()
                        response.close()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }.start()
        }
    }

    private fun formatForWhatsApp(number: String): String {
        var clean = number.replace(Regex("[^0-9]"), "")
        if (clean.startsWith("03")) clean = "92" + clean.substring(1) // Pakistan Country Code
        return clean
    }

    private fun getMessage(lat: Double, lng: Double): String =
        "🚨 *SOS ALERT* \nI need help! \n📍 Location: https://maps.google.com/?q=$lat,$lng?q=$lat,$lng"

    private fun sendSOSViaSMS(context: Context, phoneNumbers: List<String>) {
        getCurrentLocation(context) { lat, lng ->
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val message = getMessage(lat, lng)
            val parts = smsManager.divideMessage(message)
            for (number in phoneNumbers) {
                try { smsManager.sendMultipartTextMessage(number, null, parts, null, null) } catch (e: Exception) {}
            }
        }
    }

    private fun getCurrentLocation(context: Context, callback: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(0.0, 0.0)
            return
        }
        LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) callback(loc.latitude, loc.longitude) else callback(0.0, 0.0)
            }
    }
}