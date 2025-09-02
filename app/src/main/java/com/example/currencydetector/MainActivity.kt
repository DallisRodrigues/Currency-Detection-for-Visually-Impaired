package com.example.currencydetector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.currencydetector.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TFLiteHelper.DetectionListener, TextToSpeech.OnInitListener {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding
    private lateinit var tfLiteHelper: TFLiteHelper
    private lateinit var tts: TextToSpeech

    private var isDetecting = false
    private var lastSpokenTime: Long = 0
    private val cooldownMillis: Long = 5000 // 5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tfLiteHelper = TFLiteHelper(this, this)
        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.fab.setOnClickListener {
            isDetecting = !isDetecting
            if (isDetecting) {
                binding.fab.setImageResource(android.R.drawable.ic_media_pause)
                tts.speak("Detection started", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                binding.overlay.setResults(emptyList(), 0, 0) // Clear boxes when stopping
                binding.fab.setImageResource(android.R.drawable.ic_media_play)
                tts.speak("Detection stopped", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isDetecting) {
                            tfLiteHelper.detect(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(results: List<DetectionResult>?, imageWidth: Int, imageHeight: Int) {
        runOnUiThread {
            // Update the overlay with the new detection results and image dimensions
            binding.overlay.setResults(
                results ?: emptyList(),
                imageWidth,
                imageHeight
            )
        }

        if (!results.isNullOrEmpty()) {
            val topResultText = results[0].text.substringBefore(" (")
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastSpokenTime > cooldownMillis) {
                if (topResultText.contains("None").not()) {
                    tts.speak("$topResultText Rupees", TextToSpeech.QUEUE_FLUSH, null, null)
                    lastSpokenTime = currentTime
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("en", "IN")) // Set Indian English
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "The Language specified is not supported!")
            }
        } else {
            Log.e(TAG, "TTS Initialization Failed!")
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tfLiteHelper.close()
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "CurrencyDetector"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}