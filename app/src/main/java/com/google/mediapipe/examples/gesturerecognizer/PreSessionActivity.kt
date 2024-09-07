package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
import android.graphics.Color
import android.graphics.Rect
import androidx.appcompat.app.AlertDialog

class PreSessionActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var takePictureButton: Button
    private lateinit var detectedItemsTextView: TextView
    private lateinit var startSessionButton: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var promptTextView: TextView
    private lateinit var detectedItemsList: LinearLayout
    private lateinit var totalPointsTextView: TextView
    private lateinit var imageView: ImageView
    private var cameraProvider: ProcessCameraProvider? = null
    private var isRetake = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pre_session)

        previewView = findViewById(R.id.previewView)
        takePictureButton = findViewById(R.id.takePictureButton)
        detectedItemsTextView = findViewById(R.id.detectedItemsHeaderTextView)
        startSessionButton = findViewById(R.id.startSessionButton)
        promptTextView = findViewById(R.id.promptTextView)
        detectedItemsList = findViewById(R.id.detectedItemsList)
        totalPointsTextView = findViewById(R.id.totalPointsTextView)
        imageView = findViewById(R.id.imageView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        showInitialPopup() // Show the initial popup explaining the process

        takePictureButton.setOnClickListener {
            if (isRetake) {
                takePictureButton.text = "Take Picture"
                isRetake = false
                resetMaxPoints() // Reset the max points to 0 before taking a new picture
                detectedItemsList.removeAllViews() // Clear the list of detected items
                totalPointsTextView.text = "Total Points: 0" // Reset total points display
                imageView.visibility = View.GONE // Hide the previous image
                startSessionButton.visibility = View.GONE // Hide the Start Session button
                startCamera() // Restart the camera to allow retaking the picture
            } else {
                takePictureButton.text = "Retake"
                isRetake = true
                takePictureButton.visibility = View.GONE // Hide the Take Picture button
                takePhoto()
            }
        }

        startSessionButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showInitialPopup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_layout, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        val okButton = dialogView.findViewById<Button>(R.id.btn_ok)
        okButton.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.d("CameraXApp", "Camera started")
            } catch (exc: Exception) {
                Log.e("CameraXApp", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val photoFile = File(externalMediaDirs.first(), SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraXApp", "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                Log.d("CameraXApp", "Photo capture succeeded: $savedUri")
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                val rotatedBitmap = rotateImageIfRequired(bitmap, savedUri)
                detectTrashItems(rotatedBitmap)

                // Unbind the camera here after image is saved
                cameraProvider?.unbindAll()
                Log.d("CameraXApp", "Camera unbound after taking photo")
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
                    detectedItemsList.removeAllViews()
                    totalPointsTextView.text = "Total Points: 0"
                    promptTextView.visibility = View.VISIBLE
                    startSessionButton.visibility = View.GONE
                    imageView.visibility = View.GONE
                    // Show buttons after processing when no objects are detected
                    takePictureButton.visibility = View.VISIBLE
                }
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                val detectedItems = boundingBoxes.map { it.clsName }
                val detectedItemsCount = detectedItems.groupingBy { it }.eachCount()
                val totalPoints = calculatePoints(detectedItems)

                runOnUiThread {
                    promptTextView.visibility = View.GONE
                    detectedItemsList.removeAllViews()
                    detectedItemsCount.forEach { (item, count) ->
                        val itemPoints = getPointsForClass(item) * count
                        val textView = TextView(this@PreSessionActivity).apply {
                            text = "$item x$count     $itemPoints points"
                            textSize = 16f
                        }
                        detectedItemsList.addView(textView)
                    }

                    totalPointsTextView.text = "Total Points: $totalPoints"

                    detectedItemsTextView.visibility = View.VISIBLE
                    startSessionButton.visibility = View.VISIBLE

                    drawBoundingBoxes(bitmap, boundingBoxes)
                    // Store the sum of all detected points in SharedPreferences
                    val sharedPref = getSharedPreferences("userData", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        Log.d(GestureRecognizerHelper.YOLO123, "max_points: $totalPoints")
                        putInt("max_points", totalPoints)
                        apply()
                    }

                    for (box in boundingBoxes) {
                        Log.d(GestureRecognizerHelper.YOLO123, "Detected object: ${box.clsName} with confidence: ${box.cnf}")
                        val points = getPointsForClass(box.clsName)
                    }
                    // Show buttons after processing when objects are detected
                    takePictureButton.visibility = View.VISIBLE
                    startSessionButton.visibility = View.VISIBLE
                }
            }
        })
        objectDetector.runDetection(bitmap)
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, boundingBoxes: List<BoundingBox>) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Define a map of class names to colors
        val classColors = mapOf(
            "Can" to ContextCompat.getColor(this, android.R.color.holo_blue_light),
            "Glass Bottle" to ContextCompat.getColor(this, android.R.color.holo_orange_light),
            "Paper Cup" to ContextCompat.getColor(this, android.R.color.holo_red_light),
            "Peel" to ContextCompat.getColor(this, android.R.color.holo_purple),
            "Plastic Bag" to ContextCompat.getColor(this, android.R.color.holo_green_light),
            "Plastic Bottle" to ContextCompat.getColor(this, android.R.color.holo_blue_dark),
            "Plastic Cup" to ContextCompat.getColor(this, android.R.color.holo_orange_dark),
            "Snack" to ContextCompat.getColor(this, android.R.color.holo_red_dark),
            "Tissue" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
        )

        for (box in boundingBoxes) {
            // Adjust coordinates to image size
            val left = box.x1 * bitmap.width
            val top = box.y1 * bitmap.height
            val right = box.x2 * bitmap.width
            val bottom = box.y2 * bitmap.height
            val rect = RectF(left, top, right, bottom)

            // Set the paint color based on the class
            val paint = Paint().apply {
                color = classColors[box.clsName] ?: ContextCompat.getColor(this@PreSessionActivity, android.R.color.holo_green_light)
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val backgroundPaint = Paint().apply {
                color = paint.color // Match the background with the bounding box color
                style = Paint.Style.FILL
            }
            val textPaint = Paint().apply {
                color = Color.BLACK // Set text color to black
                textSize = 80f
                typeface = Typeface.DEFAULT // Use the default typeface, not bold
            }

            canvas.drawRect(rect, paint)

            val textBounds = Rect()
            textPaint.getTextBounds(box.clsName, 0, box.clsName.length, textBounds)
            val backgroundRect = RectF(left, top - textBounds.height() - 10, left + textBounds.width() + 20, top)
            canvas.drawRect(backgroundRect, backgroundPaint)
            canvas.drawText(box.clsName, left + 10, top - 5, textPaint)
        }

        imageView.setImageBitmap(mutableBitmap)
        imageView.visibility = View.VISIBLE
    }

    private fun getPointsForClass(clsName: String): Int {
        return when (clsName) {
            "Can" -> 10
            "Glass Bottle" -> 12
            "Paper Cup" -> 5
            "Peel" -> 3
            "Plastic Bag" -> 15
            "Plastic Bottle" -> 10
            "Plastic Cup" -> 8
            "Snack" -> 7
            "Tissue" -> 2
            else -> 0
        }
    }

    private fun calculatePoints(detectedItems: List<String>): Int {
        val pointsMap = mapOf(
            "Can" to 10,
            "Glass Bottle" to 12,
            "Paper Cup" to 5,
            "Peel" to 3,
            "Plastic Bag" to 15,
            "Plastic Bottle" to 10,
            "Plastic Cup" to 8,
            "Snack" to 7,
            "Tissue" to 2
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