package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.gesturerecognizer.Constants.LABELS_PATH
import com.google.mediapipe.examples.gesturerecognizer.Constants.MODEL_PATH
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PreSessionActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var takePictureButton: Button
    private lateinit var detectedItemsTextView: TextView
    private lateinit var startSessionButton: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pre_session)

        previewView = findViewById(R.id.previewView)
        takePictureButton = findViewById(R.id.takePictureButton)
        detectedItemsTextView = findViewById(R.id.detectedItemsTextView)
        startSessionButton = findViewById(R.id.startSessionButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        takePictureButton.setOnClickListener {
            resetMaxPoints() // Reset the max points to 0 before taking a new picture
            takePhoto()
        }

        startSessionButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraXApp", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val photoFile = File(
            externalMediaDirs.first(),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.d("CameraXApp", "Photo capture succeeded: $savedUri")
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val rotatedBitmap = rotateImageIfRequired(bitmap, savedUri)
                    detectTrashItems(rotatedBitmap)
                }
            })
    }

    private fun rotateImageIfRequired(img: Bitmap, selectedImage: Uri): Bitmap {
        val ei = ExifInterface(selectedImage.path!!)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }

    private fun detectTrashItems(bitmap: Bitmap) {
        val objectDetector = ObjectDetector(this, MODEL_PATH, LABELS_PATH, object : ObjectDetector.DetectorListener {
            override fun onEmptyDetect() {
                Log.d(GestureRecognizerHelper.YOLO123, "Detected object nothing")
                runOnUiThread {
                    detectedItemsTextView.text = "No objects detected"
                    detectedItemsTextView.visibility = View.VISIBLE
                    startSessionButton.visibility = View.VISIBLE
                }
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                val detectedItems = boundingBoxes.map { it.clsName }
                val totalPoints = calculatePoints(detectedItems)

                runOnUiThread {
                    detectedItemsTextView.text = "Detected Items: $detectedItems\nTotal Points: $totalPoints"
                    detectedItemsTextView.visibility = View.VISIBLE
                    startSessionButton.visibility = View.VISIBLE

                    // Store the sum of all detected points in SharedPreferences
                    val sharedPref = getSharedPreferences("userData", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        Log.d(GestureRecognizerHelper.YOLO123, "max_points: ${totalPoints}")
                        putInt("max_points", totalPoints)
                        apply()
                    }

                    for (box in boundingBoxes) {
                        Log.d(GestureRecognizerHelper.YOLO123, "Detected object: ${box.clsName} with confidence: ${box.cnf}")
                        val points = getPointsForClass(box.clsName)
                    }
                }
            }
        })
        objectDetector.runDetection(bitmap)
    }

    private fun getPointsForClass(clsName: String): Int {
        return when (clsName) {
            "Battery" -> 10
            "Can" -> 5
            "Glass Bottle" -> 8
            "Paper Cup" -> 3
            "Peel" -> 2
            "Plastic Bag" -> 4
            "Plastic Bottle" -> 6
            "Plastic Cup" -> 3
            "Snack" -> 1
            "Tissue" -> 1
            else -> 0
        }
    }

    private fun calculatePoints(detectedItems: List<String>): Int {
        val pointsMap = mapOf(
            "Battery" to 10,
            "Can" to 5,
            "Glass Bottle" to 8,
            "Paper Cup" to 3,
            "Peel" to 2,
            "Plastic Bag" to 4,
            "Plastic Bottle" to 6,
            "Plastic Cup" to 3,
            "Snack" to 7,
            "Tissue" to 1
        )
        return detectedItems.sumBy { pointsMap[it] ?: 0 }
    }

    private fun resetMaxPoints() {
        val sharedPref = getSharedPreferences("userData", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("max_points", 0)
            apply()
        }
        Log.d(GestureRecognizerHelper.YOLO123, "max_points reset to 0")
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}