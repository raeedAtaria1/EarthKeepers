package com.google.mediapipe.examples.gesturerecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class capturing : AppCompatActivity() {

    private val permissionStorage = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    companion object {
        private const val REQUEST_EXTERNAL_STORAGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request storage permissions if necessary
        verifyStoragePermissions()
    }

    public fun takeScreenshotAndSave() {
        // Create a timestamped file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "screenshot_$timeStamp.jpeg"

        // Get the directory to save the screenshot
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        if (storageDir != null) {
            try {
                // Create the file to save the screenshot
                val file = File(storageDir, fileName)
                val outputStream = FileOutputStream(file)

                // Get the root view and create a bitmap of it
                val rootView = window.decorView.rootView
                rootView.isDrawingCacheEnabled = true
                val bitmap = Bitmap.createBitmap(rootView.drawingCache)
                rootView.isDrawingCacheEnabled = false

                // Compress bitmap to JPEG with quality 80%
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()

                // Notify user that screenshot was saved
                Toast.makeText(
                    this@capturing,
                    "Screenshot saved to ${file.absolutePath}",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@capturing,
                    "Failed to save screenshot",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                this@capturing,
                "External storage not available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun verifyStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            if (permission != PackageManager.PERMISSION_GRANTED) {
                // Request permissions if not granted
                ActivityCompat.requestPermissions(
                    this,
                    permissionStorage,
                    REQUEST_EXTERNAL_STORAGE
                )
            } else {
                // Permission already granted, proceed with taking screenshot
                takeScreenshotAndSave()
            }
        } else {
            // For versions lower than Marshmallow, permission is granted at installation time
            takeScreenshotAndSave()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with taking screenshot
                takeScreenshotAndSave()
            } else {
                // Permission denied, inform the user
                Toast.makeText(
                    this,
                    "Storage permission is required to save screenshots",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
