package com.google.mediapipe.examples.gesturerecognizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SessionSummaryActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private fun checkAndUpdateBestScore(totalPoints: Int) {
        val sharedPref = getSharedPreferences("userData", MODE_PRIVATE)
        val currentBestScore = sharedPref.getInt("bestScoreOfPoints", 0)

        if (totalPoints > currentBestScore) {
            val userId = auth.currentUser?.uid ?: return

            // Update shared preferences
            with(sharedPref.edit()) {
                putInt("bestScoreOfPoints", totalPoints)
                apply()
            }

            // Update Firestore
            val userDocRef = firestore.collection("users").document(userId)
            userDocRef.update("bestScoreOfPoints", totalPoints)
                .addOnSuccessListener {
                    Toast.makeText(this, "New best score updated!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to update best score: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    private fun updatePoints(totalPoints: Int) {
        val sharedPref = getSharedPreferences("userData", MODE_PRIVATE)
        val currentPoints = sharedPref.getInt("points", 0)
        val newPoints = currentPoints + totalPoints

        val userId = auth.currentUser?.uid ?: return

        // Update shared preferences
        with(sharedPref.edit()) {
            putInt("points", newPoints)
            apply()
        } // Update Firestore
        val userDocRef = firestore.collection("users").document(userId)
        userDocRef.update("points", newPoints)
            .addOnSuccessListener {
                Toast.makeText(this, "Points updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to update points: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_summary)

        val classCountMap = intent.getSerializableExtra("classCountMap") as? Map<String, Int>
        val totalPoints = intent.getIntExtra("totalPoints", 0)
        val maxPoints = intent.getIntExtra("maxPoints", 0) // Receive maxPoints from the intent
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        checkAndUpdateBestScore(totalPoints)

        // Log the received data for debugging
        Log.d("SessionSummaryActivity", "Received Class Count Map: $classCountMap")
        Log.d("SessionSummaryActivity", "Received Total Points: $totalPoints")
        Log.d("SessionSummaryActivity", "Received Max Points: $maxPoints") // Log the maxPoints
        updatePoints(totalPoints)

        val summaryTextView: TextView = findViewById(R.id.summaryTextView)
        val pointsTextView: TextView = findViewById(R.id.pointsTextView)
        val maxPointsTextView: TextView = findViewById(R.id.maxPointsTextView) // Add TextView for maxPoints
        val backToHomeButton: Button = findViewById(R.id.backToHomeButton)

        summaryTextView.text = buildSummaryString(classCountMap)
        pointsTextView.text = "Total Points: $totalPoints"
        maxPointsTextView.text = "Max Points in a Session: $maxPoints" // Set text for maxPoints

        backToHomeButton.setOnClickListener {
            Log.d("SessionSummaryActivity", "Back to Home button clicked")
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish() // Finish the SessionSummaryActivity
        }
    }

    private fun buildSummaryString(classCountMap: Map<String, Int>?): String {
        if (classCountMap == null) return "No items collected"

        val stringBuilder = StringBuilder()
        classCountMap.forEach { (clsName, count) ->
            stringBuilder.append("$clsName: $count items\n")
        }
        return stringBuilder.toString()
    }
}
