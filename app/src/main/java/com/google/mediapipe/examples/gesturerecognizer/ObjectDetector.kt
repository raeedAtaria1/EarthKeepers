package com.google.mediapipe.examples.gesturerecognizer
import android.graphics.Bitmap
import android.content.Context


class ObjectDetector(
    private val context: Context, // Changed to Context type
    private val modelPath: String,
    private val labelsPath: String,
    private val listener: DetectorListener
) : Detector.DetectorListener {

    private lateinit var detector: Detector

    init {
        detector = Detector(context, modelPath, labelsPath, this)
        detector.setup()
    }

    fun runDetection(bitmap: Bitmap) {
        detector.detect(bitmap)
    }

    fun clear() {
        detector.clear()
    }

    override fun onEmptyDetect() {
        listener.onEmptyDetect()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        listener.onDetect(boundingBoxes, inferenceTime)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }
}
