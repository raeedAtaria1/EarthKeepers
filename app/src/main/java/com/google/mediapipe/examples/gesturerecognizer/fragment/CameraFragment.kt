package com.google.mediapipe.examples.gesturerecognizer.fragment
import android.content.SharedPreferences
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerHelper
import com.google.mediapipe.examples.gesturerecognizer.MainViewModel
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.gesturerecognizer.util.TextToSpeechHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.Date
import java.text.SimpleDateFormat
import androidx.core.content.FileProvider
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.BitmapFactory
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.FileInputStream
import android.content.pm.PackageManager
import com.google.mediapipe.examples.gesturerecognizer.SessionSummaryActivity

import java.util.*

class CameraFragment : Fragment(), GestureRecognizerHelper.GestureRecognizerListener {
    private lateinit var mediaPlayer: MediaPlayer
    public var  prefferedHand="Left"
    private var  currentHand=""
    private var prevHand=""
    private var waitDelay=0
    private var falgTrashWasgrapped=false
    private lateinit var backgroundMusicPlayer: MediaPlayer
    private lateinit var textToSpeechHelper: TextToSpeechHelper

    private val permissionStorage = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    companion object {
        private const val TAG = "Hand gesture recognizer"
        private const val REQUEST_EXTERNAL_STORAGE = 1
        var objectWasgrabbed: Boolean = false


    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null

    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var defaultNumResults = 1

    private fun determine_hand_prefrence(){
        val sharedPref = activity?.getSharedPreferences("userData", Context.MODE_PRIVATE)
        val handPreference = sharedPref?.getString("handPreference", null)
        if(handPreference=="Left-Handed"){
            prefferedHand="Right"
        }
        else{
            prefferedHand="Left"
        }
    }
    private val gestureRecognizerResultAdapter: GestureRecognizerResultsAdapter by lazy {
        GestureRecognizerResultsAdapter().apply {
            updateAdapterSize(defaultNumResults)
        }
    }
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK


    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    fun getSessionSummary(): Map<String, Int> {
        return gestureRecognizerHelper.getSessionSummary()
    }

    fun getTotalPoints(): Int {
        return gestureRecognizerHelper.getTotalPoints()
    }

    fun stopCameraAndShowSummary() {
        // Stop the camera
        cameraProvider?.unbindAll()

        // Collect session data
        val classCountMap = gestureRecognizerHelper.getSessionSummary()
        val totalPoints = gestureRecognizerHelper.getTotalPoints()

        // Create an intent to start the SessionSummaryActivity
        Log.d("hhhhhhh", "before")
        val sessionSummaryIntent = Intent(requireContext(), SessionSummaryActivity::class.java)
        sessionSummaryIntent.putExtra("classCountMap", HashMap(classCountMap))
        sessionSummaryIntent.putExtra("totalPoints", totalPoints)
        startActivity(sessionSummaryIntent)
        Log.d("hhhhhhh", "after")

        // Finish the activity
        activity?.finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize an instance of capture_photo
        // Call takeScreenshotAndSave() whenever needed
    }
    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }
        if (this::backgroundMusicPlayer.isInitialized && !backgroundMusicPlayer.isPlaying) {
            backgroundMusicPlayer.start()
        }
        backgroundExecutor.execute {
            if (gestureRecognizerHelper.isClosed()) {
                gestureRecognizerHelper.setupGestureRecognizer()
            }
        }


    }

    override fun onPause() {
        super.onPause()
        if (this::gestureRecognizerHelper.isInitialized) {
            viewModel.setMinHandDetectionConfidence(gestureRecognizerHelper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(gestureRecognizerHelper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(gestureRecognizerHelper.minHandPresenceConfidence)
            viewModel.setDelegate(gestureRecognizerHelper.currentDelegate)
            backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
        }
        if (this::backgroundMusicPlayer.isInitialized && backgroundMusicPlayer.isPlaying) {
            backgroundMusicPlayer.pause()
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        textToSpeechHelper = TextToSpeechHelper(requireContext()) {
            Log.d(TAG, "TextToSpeech initialized successfully")
        }

        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.beep)
        // Initialize the background music player
        backgroundMusicPlayer = MediaPlayer.create(requireContext(), R.raw.background_music)
        backgroundMusicPlayer.isLooping = true // Optional: make the music loop


        super.onViewCreated(view, savedInstanceState)
        with(fragmentCameraBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = gestureRecognizerResultAdapter
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()

        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        backgroundExecutor.execute {
            gestureRecognizerHelper = GestureRecognizerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                gestureRecognizerListener = this
            )
        }

        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinHandDetectionConfidence)
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinHandTrackingConfidence)
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinHandPresenceConfidence)

        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (gestureRecognizerHelper.minHandDetectionConfidence >= 0.2) {
                gestureRecognizerHelper.minHandDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (gestureRecognizerHelper.minHandDetectionConfidence <= 0.8) {
                gestureRecognizerHelper.minHandDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (gestureRecognizerHelper.minHandTrackingConfidence >= 0.2) {
                gestureRecognizerHelper.minHandTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (gestureRecognizerHelper.minHandTrackingConfidence <= 0.8) {
                gestureRecognizerHelper.minHandTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (gestureRecognizerHelper.minHandPresenceConfidence >= 0.2) {
                gestureRecognizerHelper.minHandPresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (gestureRecognizerHelper.minHandPresenceConfidence <= 0.8) {
                gestureRecognizerHelper.minHandPresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(viewModel.currentDelegate, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    try {
                        gestureRecognizerHelper.currentDelegate = p2
                        updateControlsUi()
                    } catch (e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "GestureRecognizerHelper has not been initialized yet.")
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
    }

    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", gestureRecognizerHelper.minHandDetectionConfidence)
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", gestureRecognizerHelper.minHandTrackingConfidence)
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", gestureRecognizerHelper.minHandPresenceConfidence)

        backgroundExecutor.execute {
            gestureRecognizerHelper.clearGestureRecognizer()
            gestureRecognizerHelper.setupGestureRecognizer()
        }
        fragmentCameraBinding.overlay.clear()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { image -> recognizeHand(image) }
            }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            fragmentCameraBinding.viewFinder.scaleX = -1f

            // Set initial zoom level to 0.6
            camera?.cameraControl?.setZoomRatio(0.6f)

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        gestureRecognizerHelper.recognizeLiveStream(imageProxy = imageProxy)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }
    private fun captureScreenshot(): Bitmap? {
        val view = fragmentCameraBinding.viewFinder
        // Ensure the view has been laid out before capturing the screenshot
        if (!view.isLaidOut) {
            Log.e(TAG, "View is not laid out yet")
            return null
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }



    // Update UI after a hand gesture has been recognized. Extracts original
// image height/width to scale and place the landmarks properly through
// OverlayView. Only one result is expected at a time. If two or more
// hands are seen in the camera frame, only one will be processed.
    override fun onResults(
        resultBundle: GestureRecognizerHelper.ResultBundle
    ) {
        determine_hand_prefrence()
        activity?.runOnUiThread {


            if (_fragmentCameraBinding != null) {
                if(waitDelay>0){
                    waitDelay++
                    if(waitDelay==7){
                        textToSpeechHelper.speak("")
                        //textToSpeechHelper.speak("five points")//Edited
                        objectWasgrabbed=true
                        waitDelay=0}


                }
                // Show result of recognized gesture
                val gestureCategories = resultBundle.results.first().gestures()
                if (gestureCategories.isNotEmpty()) {
                    if(!backgroundMusicPlayer.isPlaying()){
                        backgroundMusicPlayer.start()
                    }
                    var flag=0

                    if(waitDelay==0)
                        for(i in 0 until gestureCategories.size){
                            if(resultBundle.results.first().handedness().get(i).get(0).displayName()==prefferedHand) {

                                flag=1
                                var temp=currentHand
                                currentHand=resultBundle.results.first().gestures().get(i).get(0).categoryName()
                                if(prevHand!=currentHand)
                                    prevHand=temp
                                gestureRecognizerResultAdapter.updateResults(
                                    gestureCategories.get(i)
                                )


                                if(currentHand=="closed_palm" && prevHand=="open_palm" && waitDelay==0)
                                {
                                    //                                mediaPlayer.start()
                                    //                                waitDelay++
                                    waitDelay=1
//                                    textToSpeechHelper.speak("five points")
//                                    objectWasgrabbed=true


                                    //add taking an screen shoot
                                    //val screenshot = takeScreenshot()
                                    // Save screenshot to gallery

                                }


                                break
                            }
                        }
                    if(flag==0){
                        gestureRecognizerResultAdapter.updateResults(emptyList())
                        backgroundMusicPlayer.pause()
                    }


                } else {
                    gestureRecognizerResultAdapter.updateResults(emptyList())
                    backgroundMusicPlayer.pause()
                }

                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                val overlayView = fragmentCameraBinding.overlay
                overlayView.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // If the camera feed is flipped horizontally, adjust the drawing accordingly
                if (fragmentCameraBinding.viewFinder.scaleX < 0) {
                    overlayView.scaleX = -1f
                } else {
                    overlayView.scaleX = 1f
                }

                // Force a redraw
                overlayView.invalidate()
            }
        }
    }


    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            gestureRecognizerResultAdapter.updateResults(emptyList())




            if (errorCode == GestureRecognizerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    GestureRecognizerHelper.DELEGATE_CPU, false
                )
            }
        }
    }
}
