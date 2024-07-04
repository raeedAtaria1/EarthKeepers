package com.google.mediapipe.examples.gesturerecognizer.fragment
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotHelper(private val activity: Activity) {

    private val TAG = "ScreenshotHelper"

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    fun takeScreenshot() {
        // Initialize MediaProjectionManager
        mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Start a new MediaProjection
        activity.startActivityForResult(mediaProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE_SCREENSHOT)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCREENSHOT) {
            if (resultCode == Activity.RESULT_OK) {
                // MediaProjection is granted
                mediaProjection = mediaProjectionManager!!.getMediaProjection(resultCode, data!!)

                // Prepare ImageReader
                val metrics = DisplayMetrics()
                val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.defaultDisplay.getMetrics(metrics)
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels

                imageReader = ImageReader.newInstance(screenWidth, screenHeight, android.graphics.PixelFormat.RGBA_8888, 2)


                // Register ImageAvailableListener
                imageReader!!.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    val bitmap = imageToBitmap(image)

                    // Save bitmap to file
                    saveBitmap(bitmap)

                    // Release resources
                    image.close()
                    imageReader!!.setOnImageAvailableListener(null, null)
                    mediaProjection!!.stop()
                }, null)
            } else {
                Log.e(TAG, "MediaProjection permission denied: resultCode = $resultCode")
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride: Int = planes[0].pixelStride
        val rowStride: Int = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val cw = ContextWrapper(activity.applicationContext)
        val directory = cw.getDir("screenshots", Context.MODE_PRIVATE)
        val file = File(directory, "screenshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png")

        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot: ${e.message}")
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing FileOutputStream: ${e.message}")
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_SCREENSHOT = 100
    }
}
