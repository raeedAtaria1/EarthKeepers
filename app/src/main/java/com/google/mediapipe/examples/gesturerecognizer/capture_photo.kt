package com.google.mediapipe.examples.gesturerecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.gesturerecognizer.fragment.ScreenshotHelper

class capture_photo : AppCompatActivity() {

    private lateinit var click: Button
    private lateinit var screenshotHelper: ScreenshotHelper

    companion object {
        private const val REQUEST_EXTERNAL_STORAGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.capture_photo) // Make sure this layout file exists

        screenshotHelper = ScreenshotHelper(this)

        click = findViewById(R.id.clickme)
        // Adding beep sound

        click.setOnClickListener {
            screenshotHelper.takeScreenshot()

        }
    }



}
