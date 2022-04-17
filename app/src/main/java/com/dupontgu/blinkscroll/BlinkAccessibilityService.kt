package com.dupontgu.blinkscroll

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ALL OF THE ACTUAL FACE DETECTION STUFF WAS ADAPTED FROM GOOGLE'S TUTORIAL HERE:
 * https://developers.google.com/ml-kit/vision/face-detection/android
 */

// min amount of time to pass between virtual swipes
const val SWIPE_THROTTLE_MS = 500

/**
 * When it detects a face, MLKit gives us confidence ratings for each eye being OPEN
 * 0 == very likely closed eye, 1 == very likely open eye
 * Since we are detecting blinks (closed eyes), we pick a value closer to 0
 */
const val EYE_OPEN_CONFIDENCE_THRESHOLD = 0.3f

val REQUIRED_PERMISSIONS
    get() = arrayOf(Manifest.permission.CAMERA)

fun Context.allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(
        this.applicationContext, it
    ) == PackageManager.PERMISSION_GRANTED
}

class BlinkAccessibilityService : AccessibilityService(), LifecycleOwner {
    private var coroutineScope = CoroutineScope(Job())
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    // Google does not recommend these settings for real time camera analysis,
    // but they work really well for me on a Pixel 6. The phone is definitely working hard.
    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()


    val detector = FaceDetection.getClient(highAccuracyOpts)

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var timeOfLastSwipe = 0L

    private fun timeNow() = System.currentTimeMillis()

    private inner class FaceDetectAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                detector.process(image)
                    .addOnSuccessListener {
                        onFacesDetected(it)
                        imageProxy.close()
                    }
                    .addOnFailureListener { e ->
                        println("Face detect failure\n${e.stackTraceToString()}")
                        imageProxy.close()
                    }
            }
        }
    }

    private fun onFacesDetected(faces: Collection<Face>) {
        // if the last swipe happened too recently, don't do anything
        if ((timeNow() - timeOfLastSwipe) < SWIPE_THROTTLE_MS) return
        val blinkDetected = faces.any { face ->
            (face.leftEyeOpenProbability ?: 1f) < EYE_OPEN_CONFIDENCE_THRESHOLD ||
                    (face.rightEyeOpenProbability ?: 1f) < EYE_OPEN_CONFIDENCE_THRESHOLD
        }
        if (blinkDetected) {
            timeOfLastSwipe = timeNow()
            swipeGesture()
        }
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onServiceConnected() {
        super.onServiceConnected()
        with(coroutineScope) {
            launch {
                // lazily just wait for the user to grant permissions
                while (!allPermissionsGranted()) {
                    println("Waiting for permissions...")
                    delay(5000)
                }
                if (isActive) {
                    println("Starting camera...")
                    startCamera()
                    launch(Dispatchers.Main) {
                        lifecycleRegistry.currentState = Lifecycle.State.STARTED
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also { it.setAnalyzer(cameraExecutor, FaceDetectAnalyzer()) }

            val imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, imageAnalyzer)

            } catch (exc: Exception) {
                println("Failed to bind camera\n${exc.stackTraceToString()}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun swipeGesture() {
        val displayMetrics = resources.displayMetrics

        // swipe from middle of screen, 3/4 of the way down
        // up to middle of screen, 1/4 of the way from the top
        val middleX = (displayMetrics.widthPixels / 2).toFloat()
        val bottom = (displayMetrics.heightPixels * 0.75).toFloat()
        val top = (displayMetrics.heightPixels * 0.25).toFloat()
        val path = Path().apply {
            moveTo(middleX, bottom)
            lineTo(middleX, top)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(StrokeDescription(path, 100, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }
        }, null)
    }

    // Didn't have to deal with this in limited testing
    override fun onInterrupt() = Unit

    // We're not paying attention to actual a11y events unfortunately
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        coroutineScope.cancel()
        cameraExecutor.shutdown()
    }
}
