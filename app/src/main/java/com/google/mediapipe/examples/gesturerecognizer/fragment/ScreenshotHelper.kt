
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

public class ScreenshotHelper(private val context: Context) {

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val permissionsStorage = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    fun takeScreenshot(view: View, activity: Activity) {
        verifyStoragePermissions(activity)

        Toast.makeText(
            context,
            "You just captured a screenshot. Open Gallery/File Storage to view your captured screenshot.",
            Toast.LENGTH_SHORT
        ).show()

        val screenshotFile = screenshot(view, "result")
        screenshotFile?.let {
            Toast.makeText(context, "Screenshot saved to ${it.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun screenshot(view: View, filename: String): File? {
        val date = Date()
        val format = android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", date)
        return try {
            val dirPath = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()
            val file = File(dirPath)
            if (!file.exists()) {
                file.mkdir()
            }
            val path = "$dirPath/$filename-$format.jpeg"
            view.isDrawingCacheEnabled = true
            val bitmap = Bitmap.createBitmap(view.drawingCache)
            view.isDrawingCacheEnabled = false
            val imageUrl = File(path)
            val outputStream = FileOutputStream(imageUrl)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            outputStream.flush()
            outputStream.close()
            imageUrl
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun verifyStoragePermissions(activity: Activity) {
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, permissionsStorage, REQUEST_EXTERNAL_STORAGE)
        }
    }
}