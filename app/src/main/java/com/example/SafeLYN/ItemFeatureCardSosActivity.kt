package com.example.SafeLYN

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.Executors

class ItemFeatureCardSosActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private var gestureRecognizer: GestureRecognizer? = null
    private var gestureStartTime: Long = 0
    private val GESTURE_HOLD_TIME = 400L

    private var selectedGesture = "Closed_Fist"
    private lateinit var txtGestureHint: TextView

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var frameCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_emergency)

        // Initializing PrefsManager and fetching the user-selected gesture
        prefsManager = PrefsManager(this)
        selectedGesture = prefsManager.getSelectedGesture()
        txtGestureHint = findViewById(R.id.tvGestureHint)

        setupUI()

        if (allPermissionsGranted()) {
            startCameraAndAI()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS),
                101
            )
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun setupUI() {
        findViewById<Button>(R.id.btnSOS).setOnClickListener { launchConfirmationScreen("Manual Button") }
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { finish() }

        val spinner = findViewById<Spinner>(R.id.gestureSpinner)
        val gestures = arrayOf("Closed_Fist", "Open_Palm", "Thumb_Up", "Thumb_Down")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gestures)
        spinner.adapter = adapter

        val position = gestures.indexOf(selectedGesture)
        if (position >= 0) spinner.setSelection(position)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedGesture = gestures[position]
                // Saving the new selection to PrefsManager
                prefsManager.saveSelectedGesture(selectedGesture)
                txtGestureHint.text = "Show $selectedGesture to Trigger"
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun startCameraAndAI() {
        backgroundExecutor.execute {
            setupGestureRecognizer()
            runOnUiThread { startCamera() }
        }
    }

    private fun setupGestureRecognizer() {
        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build()
            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.4f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> processGesture(result) }
                .build()
            gestureRecognizer = GestureRecognizer.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e("SOS_AI", "Error loading model", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val targetResolution = Size(480, 360)

            val preview = Preview.Builder().setTargetResolution(targetResolution).build()
            val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
            preview.setSurfaceProvider(viewFinder.surfaceProvider)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(backgroundExecutor) { imageProxy ->
                        recognizeHand(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("SOS_CAM", "Camera Fail", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        // Frame skipping logic to maintain performance
        if (frameCounter++ % 10 != 0) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            gestureRecognizer?.recognizeAsync(mpImage, imageProcessingOptions, System.currentTimeMillis())
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun processGesture(result: GestureRecognizerResult) {
        val gestures = result.gestures()
        if (gestures.isNotEmpty()) {
            val topGesture = gestures.first().first()
            val detectedName = topGesture.categoryName()
            val confidence = topGesture.score()

            runOnUiThread {
                txtGestureHint.text = "Saw: $detectedName (${(confidence * 100).toInt()}%)"

                if (detectedName == selectedGesture && confidence > 0.4f) {
                    txtGestureHint.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
                    if (gestureStartTime == 0L) {
                        gestureStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - gestureStartTime > GESTURE_HOLD_TIME) {
                        gestureStartTime = 0L
                        launchConfirmationScreen("Gesture: $detectedName")
                    }
                } else {
                    gestureStartTime = 0L
                    txtGestureHint.setTextColor(resources.getColor(android.R.color.white, null))
                }
            }
        }
    }

    private fun launchConfirmationScreen(source: String) {
        val intent = Intent(this, SosConfirmationActivity::class.java)
        intent.putExtra("TRIGGER_SOURCE", source)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { gestureRecognizer?.close() } catch (e: Exception) {}
        backgroundExecutor.shutdownNow()
    }
}