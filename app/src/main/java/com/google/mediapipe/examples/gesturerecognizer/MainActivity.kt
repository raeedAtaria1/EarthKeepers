// MainActivity.kt
package com.google.mediapipe.examples.gesturerecognizer

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.gesturerecognizer.databinding.ActivityMainBinding
import android.widget.ImageView
import android.content.Intent
import android.util.Log
import com.google.mediapipe.examples.gesturerecognizer.fragment.CameraFragment

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        // Find the finish_button ImageView
        val finishButton: ImageView = findViewById(R.id.finish_button)

        // Set OnClickListener to navigate to SessionSummaryActivity when finish_button is pressed
        finishButton.setOnClickListener {
            Log.d("MainActivity", "Finish button clicked")

            // Retrieve the current fragment from the NavHostFragment
            val currentFragment = navHostFragment.childFragmentManager.primaryNavigationFragment

            if (currentFragment is CameraFragment) {
                Log.d("MainActivity", "CameraFragment found")
                currentFragment.stopBackgroundMusic()  // Stop the background music
                currentFragment.stopCameraAndShowSummary()
                finish()
            } else {
                Log.e("MainActivity", "CameraFragment not found")
            }
        }
    }

    override fun onBackPressed() {
        finish()
    }
}
