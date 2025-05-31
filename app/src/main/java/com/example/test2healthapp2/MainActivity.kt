package com.example.test2healthapp2

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.delay
import com.example.test2healthapp2.ml.LiteModelMovenetSingleposeLightningTfliteFloat1641
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

@Serializable
data class ResultsResponse(
    val average_angle: Float = 0f,
    val max_angle: Float = 0f,
    val min_angle: Float = 0f,
    val success_rate: Float = 0f,
    val total_sessions: Int = 0,
    val sessions: List<Float> = emptyList()
)

object Config {
    //    const val BASE_URL = "http://192.168.1.204/posefit_backend/"
    const val BASE_URL = "https://archontis.sites.sch.gr/posefit/"
}

class MainActivity : ComponentActivity() {

    val paint = Paint().apply {
        color = android.graphics.Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.FILL
    }
    val textPaint = Paint().apply {
        color = android.graphics.Color.YELLOW
        textSize = 30f
        strokeWidth = 2f
    }
    lateinit var imageProcessor: ImageProcessor


    lateinit var model: LiteModelMovenetSingleposeLightningTfliteFloat1641


    lateinit var cameraManager: CameraManager
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var isCameraRunning = false
    private var lastCalculatedAngle: Float by mutableStateOf(Float.NaN)
    private var processedBitmap: Bitmap? by mutableStateOf(null)

    private val angleHistory = mutableListOf<Float>()
    private var exerciseDurationSeconds = 10
    private var currentExerciseSecond = 0

    enum class ExerciseState {
        READY, COUNTDOWN, TRACKING, DONE
    }

    private var currentExerciseState by mutableStateOf(ExerciseState.READY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AppLifecycle", "MainActivity created, requesting permissions")
        get_permissions()

        imageProcessor =
            ImageProcessor.Builder().add(ResizeOp(192, 192, ResizeOp.ResizeMethod.BILINEAR)).build()
        try {
            model = LiteModelMovenetSingleposeLightningTfliteFloat1641.newInstance(this)
            Log.d("Model", "MoveNet model loaded successfully")
        } catch (e: Exception) {
            Log.e("Model", "Failed to load MoveNet model: ${e.message}")
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        setContent {
            PoseFitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppScaffold(navController)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        close_cameraForExercise()
        model.close()
        handlerThread.quitSafely()
    }

    private fun handleStartButtonClick(navController: NavHostController) {
        if (currentExerciseState == ExerciseState.READY) {
            Log.d(
                "Permissions",
                "Camera permission granted: ${checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}"
            )
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                isCameraRunning = true
                currentExerciseState = ExerciseState.COUNTDOWN
                Log.d("currentExerciseState", "currentExerciseState: $currentExerciseState")
                startCountdown(navController)
            } else {
                get_permissions()
            }
        } else if (currentExerciseState == ExerciseState.TRACKING || currentExerciseState == ExerciseState.COUNTDOWN) {
            stopExercise()
            Log.d("currentExerciseState", "currentExerciseState: $currentExerciseState")
            currentExerciseState = ExerciseState.READY
        }
    }

    private fun startCountdown(navController: NavHostController) {
        Log.d("Countdown", "Starting countdown")
        CoroutineScope(Dispatchers.Main).launch {
            var count = 3
            while (count > 0 && currentExerciseState == ExerciseState.COUNTDOWN) {
                Log.d("Countdown", "Displaying: $count")
                currentExerciseState = ExerciseState.COUNTDOWN
                delay(1000)
                count--
            }
            if (currentExerciseState == ExerciseState.COUNTDOWN) {
                Log.d("Countdown", "Displaying: Go!")
                currentExerciseState = ExerciseState.TRACKING
                delay(1000)
                if (currentExerciseState == ExerciseState.TRACKING) {
                    Log.d("Countdown", "Starting exercise tracking")
                    startExerciseTracking(navController)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun open_cameraForExercise(textureView: TextureView) {
        Log.d("Camera", "Attempting to open camera")
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing =
                    characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)

                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                    Log.d("Camera", "Opening front camera ID: $cameraId")
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            Log.d("Camera", "Camera opened successfully")
                            cameraDevice = camera
                            startPreviewForExercise(textureView)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            Log.i("Camera", "Camera disconnected")
                            camera.close()
                            cameraDevice = null
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            Log.e("Camera", "Camera error occurred: $error")
                            camera.close()
                            cameraDevice = null
                            runOnUiThread {
                                currentExerciseState = ExerciseState.READY
                            }
                        }
                    }, handler)
                    return
                }
            }
            if (cameraManager.cameraIdList.isNotEmpty()) {
                val firstCameraId = cameraManager.cameraIdList[0]
                Log.d("Camera", "Front camera not found, opening default camera ID: $firstCameraId")
                cameraManager.openCamera(firstCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Log.d("Camera", "Camera opened successfully")
                        cameraDevice = camera
                        startPreviewForExercise(textureView)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.i("Camera", "Camera disconnected")
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e("Camera", "Camera error occurred: $error")
                        camera.close()
                        cameraDevice = null
                        runOnUiThread {
                            currentExerciseState = ExerciseState.READY
                        }
                    }
                }, handler)
            } else {
                Log.e("Camera", "No cameras found on the device")
                runOnUiThread {
                    currentExerciseState = ExerciseState.READY
                }
            }
        } catch (e: Exception) {
            Log.e("Camera", "Error opening camera: ${e.localizedMessage}")
            runOnUiThread {
                currentExerciseState = ExerciseState.READY
            }
        }
    }

    private fun startPreviewForExercise(textureView: TextureView) {
        cameraDevice?.apply {
            try {
                Log.d("Camera", "Starting camera preview")
                val surfaceTexture = textureView.surfaceTexture
                surfaceTexture?.setDefaultBufferSize(1920, 1080)
                val surface = Surface(surfaceTexture)
                val captureRequestBuilder =
                    createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }

                createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Log.d("Camera", "Capture session configured")
                            captureSession = session
                            try {
                                captureSession?.setRepeatingRequest(
                                    captureRequestBuilder.build(),
                                    null,
                                    handler
                                )
                                Log.d("Camera", "Camera preview started")
                            } catch (e: Exception) {
                                Log.e(
                                    "Camera",
                                    "Error starting repeating request: ${e.localizedMessage}"
                                )
                                runOnUiThread {
                                    currentExerciseState = ExerciseState.READY
                                }
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("Camera", "Capture session configuration failed")
                            runOnUiThread {
                                currentExerciseState = ExerciseState.READY
                            }
                        }
                    },
                    handler
                )
            } catch (e: Exception) {
                Log.e("Camera", "Error starting preview: ${e.localizedMessage}")
                runOnUiThread {
                    currentExerciseState = ExerciseState.READY
                }
            }
        } ?: Log.e("Camera", "Camera device is null")
    }

    private fun startExerciseTracking(navController: NavHostController) {
        Log.d("Tracking", "Starting exercise tracking")
        angleHistory.clear()
        currentExerciseSecond = 0

        CoroutineScope(Dispatchers.Main).launch {
            while (currentExerciseSecond < exerciseDurationSeconds && currentExerciseState == ExerciseState.TRACKING) {
                if (!lastCalculatedAngle.isNaN()) {
                    angleHistory.add(lastCalculatedAngle)
                    Log.d("Tracking", "Angle recorded: $lastCalculatedAngle")
                }
                currentExerciseSecond++
                delay(1000)
            }
            if (currentExerciseState == ExerciseState.TRACKING) {
                currentExerciseState = ExerciseState.DONE
                stopExercise()
                val sessionId = withContext(Dispatchers.IO) { saveToBackend(angleHistory) }
                val angleHistoryJson = Json.encodeToString(angleHistory)
                Log.d("Serialization", "Encoded angleHistory: $angleHistoryJson")
                navController.navigate("results/$angleHistoryJson?sessionId=$sessionId")
            }
        }
    }

    @Serializable
    data class BackendResponse(val success: Boolean, val session_id: String? = null)

    private suspend fun saveToBackend(angles: List<Float>): String {
        val url = "${Config.BASE_URL}save_exercise.php"
        val json = Json.encodeToString(mapOf("angles" to angles))
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.d("BackendResponse", "Raw response from backend: $responseBody")
                    try {
                        val backendResponse = Json.decodeFromString<BackendResponse>(responseBody)
                        Log.d("BackendResponse", "Decoded response: $backendResponse")
                        if (backendResponse.success) {
                            backendResponse.session_id ?: ""
                        } else {
                            Log.e("Backend", "Backend reported failure")
                            ""
                        }
                    } catch (e: Exception) {
                        Log.e("Backend", "Error decoding JSON response: ${e.message}, Raw response: $responseBody")
                        ""
                    }
                } else {
                    Log.e(
                        "Backend",
                        "Failed to save data: ${response.code}, Body: ${response.body?.string()}"
                    )
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e("Backend", "Error saving to backend: ${e.message}")
            ""
        }
    }

    private fun stopExercise() {
        Log.d("Tracking", "Stopping exercise")
        close_cameraForExercise()
        currentExerciseState = ExerciseState.DONE
    }

    private fun close_cameraForExercise() {
        Log.d("Camera", "Closing camera")
        captureSession?.let {
            it.close()
            captureSession = null
        }
        cameraDevice?.let {
            it.close()
            cameraDevice = null
        }
        isCameraRunning = false
    }

    private fun displayResults(angleHistory: List<Float>): String {
        val resultsStringBuilder = StringBuilder("Exercise Results:\n")
        angleHistory.forEachIndexed { index, angle ->
            resultsStringBuilder.append(
                "Second ${index + 1}: ${
                    String.format(
                        "%.2f",
                        angle
                    )
                } degrees\n"
            )
        }

        val averageAngle = if (angleHistory.isNotEmpty()) {
            angleHistory.average()
        } else {
            Double.NaN
        }
        resultsStringBuilder.append(
            "\nAverage Angle: ${
                if (averageAngle.isNaN()) "No valid data" else String.format(
                    "%.2f",
                    averageAngle
                )
            } degrees\n"
        )
        return resultsStringBuilder.toString()
    }

    private fun processPoseEstimation(currentBitmap: Bitmap?) {
        if (currentExerciseState == ExerciseState.TRACKING) {
            currentBitmap?.let { bitmap ->
                var tensorImage = TensorImage(DataType.UINT8)
                tensorImage.load(bitmap)
                tensorImage = imageProcessor.process(tensorImage)

                val inputFeature0 =
                    TensorBuffer.createFixedSize(intArrayOf(1, 192, 192, 3), DataType.UINT8)
                inputFeature0.loadBuffer(tensorImage.buffer)

                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

                val keypointNames = listOf(
                    "nose", "left_eye", "right_eye", "left_ear", "right_ear",
                    "right_shoulder", "left_shoulder", "left_elbow", "right_elbow",
                    "right_wrist", "left_wrist", "left_hip", "right_hip",
                    "left_knee", "right_knee", "left_ankle", "right_ankle"
                )

                val keypointData = keypointNames.mapIndexed { i, name ->
                    name to Triple(
                        outputFeature0[i * 3 + 1],
                        outputFeature0[i * 3],
                        outputFeature0[i * 3 + 2]
                    )
                }.toMap()

                var mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                var canvas = Canvas(mutableBitmap)

                val rightShoulderData = keypointData["right_shoulder"]
                val rightWristData = keypointData["right_wrist"]

                var rsX = 0f
                var rsY = 0f
                var rwX = 0f
                var rwY = 0f

                if (rightShoulderData != null && rightWristData != null &&
                    rightShoulderData.third > 0.45 && rightWristData.third > 0.45
                ) {
                    rsX = rightShoulderData.first * bitmap.width
                    rsY = rightShoulderData.second * bitmap.height
                    rwX = rightWristData.first * bitmap.width
                    rwY = rightWristData.second * bitmap.height

                    val deltaX = rwX - rsX
                    val deltaY = rwY - rsY
                    val angleRadians = Math.atan2(deltaX.toDouble(), (-deltaY).toDouble())
                    var angleDegrees = Math.toDegrees(angleRadians).toFloat()
                    angleDegrees = Math.abs(angleDegrees)
                    if (angleDegrees < 180f) {
                        angleDegrees = 180f - angleDegrees
                    }

                    lastCalculatedAngle = angleDegrees
                    Log.d("PoseEstimation", "Calculated angle: $angleDegrees") // Debug log

                    val ninetyDegreeThresholdMin = 70f
                    val ninetyDegreeThresholdMax = 110f

                    if (angleDegrees in ninetyDegreeThresholdMin..ninetyDegreeThresholdMax) {
                        paint.color = android.graphics.Color.GREEN
                    } else if (angleDegrees < ninetyDegreeThresholdMin) {
                        paint.color = android.graphics.Color.YELLOW
                    } else if (angleDegrees > ninetyDegreeThresholdMax) {
                        paint.color = android.graphics.Color.RED
                    } else {
                        paint.color = android.graphics.Color.YELLOW
                    }

                    canvas.drawCircle(rsX, rsY, 20f, paint)
                    canvas.drawCircle(rwX, rwY, 20f, paint)
                    processedBitmap = mutableBitmap
                    Log.d(
                        "PoseEstimation",
                        "Keypoints drawn - Shoulder: ($rsX, $rsY), Wrist: ($rwX, $rwY), Angle: $angleDegrees"
                    )
                } else {
                    lastCalculatedAngle = Float.NaN
                    Log.d(
                        "PoseEstimation",
                        "Keypoints not detected - Shoulder: ${rightShoulderData?.third}, Wrist: ${rightWristData?.third}"
                    )
                    if (rightShoulderData?.third ?: 0f > 0.45) {
                        canvas.drawCircle(rsX, rsY, 20f, paint)
                        Log.d("PoseEstimation", "Shoulder drawn: ($rsX, $rsY)")
                    }
                    if (rightWristData?.third ?: 0f > 0.45) {
                        canvas.drawCircle(rwX, rwY, 20f, paint)
                        Log.d("PoseEstimation", "Wrist drawn: ($rwX, $rwY)")
                    }
                    processedBitmap = mutableBitmap
                }
            } ?: run {
                Log.w("BitmapError", "TextureView bitmap is null")
                lastCalculatedAngle = Float.NaN
                processedBitmap = null
            }
        }
    }

    private fun get_permissions() {
        Log.d(
            "Permissions",
            "Requesting camera permission, granted: ${checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}"
        )
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(
            "Permissions",
            "Permission result: requestCode=$requestCode, granted=${grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED}"
        )
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (currentExerciseState == ExerciseState.COUNTDOWN) {
                isCameraRunning = true
            }
        } else {
            get_permissions()
        }
    }

    @Composable
    fun PoseFitTheme(content: @Composable () -> Unit) {
        val colors = lightColorScheme(
            primary = Color(0xFF1E3A8A), // Dark Blue
            secondary = Color(0xFF60A5FA), // Light Blue
            background = Color(0xFFF8FAFC), // Soft White
            surface = Color(0xFFFFFFFF), // White
            onPrimary = Color(0xFFFFFFFF), // White text on buttons
            onSecondary = Color(0xFF111827), // Dark Gray text
            onBackground = Color(0xFF111827), // Dark Gray text
            onSurface = Color(0xFF111827), // Dark Gray text
            error = Color(0xFFB91C1C), // Red for errors
            primaryContainer = Color(0xFFE0E7FF), // Light Indigo
            secondaryContainer = Color(0xFFE0F2FE) // Light Blue
        )

        val typography = Typography(
            headlineLarge = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            headlineMedium = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp
            ),
            titleMedium = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
            labelLarge = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
        )

        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            content = content
        )
    }

    @Composable
    fun AppScaffold(navController: NavHostController) {
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Home", "Exercises", "Results")

        Scaffold(
            topBar = {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "tab",
                modifier = Modifier.padding(padding)
            ) {
                composable("tab") {
                    when (selectedTab) {
                        0 -> HomeScreen()
                        1 -> ExercisesScreen(
                            onExerciseClick = { exercise ->
                                if (exercise.isActive) {
                                    navController.navigate("instruction")
                                }
                            }
                        )

                        2 -> GeneralResultsScreen(navController)
                    }
                }
                composable("instruction") {
                    InstructionScreen(
                        onStartClick = { navController.navigate("main") },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable("main") {
                    MainScreen(
                        navController = navController,
                        currentExerciseState = currentExerciseState,
                        onStartButtonClick = { handleStartButtonClick(navController) },
                        lastCalculatedAngle = lastCalculatedAngle,
                        angleHistory = angleHistory,
                        processedBitmap = processedBitmap,
                        onSurfaceTextureAvailable = { textureView ->
                            if (isCameraRunning) {
                                open_cameraForExercise(textureView)
                            }
                        },
                        onSurfaceTextureUpdated = { textureView ->
                            processPoseEstimation(textureView.bitmap)
                        },
                        onSurfaceTextureDestroyed = {
                            close_cameraForExercise()
                            true
                        }
                    )
                }
                composable(
                    route = "results/{angleHistory}?sessionId={sessionId}",
                    arguments = listOf(
                        navArgument("angleHistory") { type = NavType.StringType },
                        navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val angleHistoryJson =
                        backStackEntry.arguments?.getString("angleHistory") ?: "[]"
                    val angleHistory: List<Float> = Json.decodeFromString(angleHistoryJson)
                    ResultsScreen(
                        angleHistory = angleHistory,
                        onBackClick = {
                            currentExerciseState = ExerciseState.READY
                            this@MainActivity.angleHistory.clear()
                            currentExerciseSecond = 0
                            navController.popBackStack("tab", inclusive = false)
                            selectedTab = 0 // Return to Home tab
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun HomeScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(1000))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome to PoseFit Beta",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Thank you for testing my thesis app! Try the Arm Lift to check your shoulder health.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ExercisesScreen(
        onExerciseClick: (Exercise) -> Unit
    ) {
        val exercises = listOf(
            Exercise("Arm Lift", isActive = true),
            Exercise("Knee Bend", isActive = false),
            Exercise("Neck Stretch", isActive = false),
            Exercise("Hip Flex", isActive = false)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(exercises) { exercise ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = exercise.isActive) { onExerciseClick(exercise) }
                            .shadow(2.dp, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (exercise.isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (exercise.isActive)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                            if (!exercise.isActive) {
                                Text(
                                    text = "Coming Soon",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    data class Exercise(val name: String, val isActive: Boolean)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InstructionScreen(
        onStartClick: () -> Unit,
        onBackClick: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Arm Lift", style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(1000))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Arm Lift Exercise",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Position yourself in 3 seconds, then lift your arm for 10 seconds to check shoulder health.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        InstructionItem(
                            icon = Icons.Default.Person,
                            title = "Standing Position",
                            description = "Stand upright facing the camera."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        InstructionItem(
                            icon = Icons.Default.BackHand,
                            title = "Hand Placement",
                            description = "Extend your right arm forward, palm up."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        InstructionItem(
                            icon = Icons.Default.Camera,
                            title = "Camera Distance",
                            description = "Stand 1-2 meters from the camera."
                        )
                    }
                }
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Start Exercise",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    @Composable
    fun InstructionItem(icon: ImageVector, title: String, description: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(
        navController: NavHostController,
        currentExerciseState: ExerciseState,
        onStartButtonClick: () -> Unit,
        lastCalculatedAngle: Float,
        angleHistory: List<Float>,
        processedBitmap: Bitmap?,
        onSurfaceTextureAvailable: (TextureView) -> Unit,
        onSurfaceTextureUpdated: (TextureView) -> Unit,
        onSurfaceTextureDestroyed: () -> Boolean
    ) {
        var feedbackText by remember { mutableStateOf("Ready") }
        var feedbackColor by remember { mutableStateOf(Color.Black) }
        var isCameraVisible by remember { mutableStateOf(false) }
        var isImageVisible by remember { mutableStateOf(false) }
        var isButtonEnabled by remember { mutableStateOf(true) }
        var buttonText by remember { mutableStateOf("Start Exercise") }
        var progress by remember { mutableStateOf(0f) }

        LaunchedEffect(currentExerciseState) {
            Log.d("UIUpdate", "State changed: $currentExerciseState")
            when (currentExerciseState) {
                ExerciseState.READY -> {
                    isCameraVisible = false
                    isImageVisible = false
                    feedbackText = "Ready"
                    feedbackColor = Color.Black
                    isButtonEnabled = true
                    buttonText = "Start Exercise"
                    progress = 0f
                }

                ExerciseState.COUNTDOWN -> {
                    isCameraVisible = true
                    isImageVisible = true
                    feedbackText = "Get Ready..."
                    feedbackColor = Color.Yellow
                    isButtonEnabled = true
                    buttonText = "Stop Exercise"
                    progress = 0f
                }

                ExerciseState.TRACKING -> {
                    isCameraVisible = true
                    isImageVisible = true
                    feedbackText = "Tracking..."
                    feedbackColor = Color.Cyan
                    isButtonEnabled = true
                    buttonText = "Stop Exercise"
                    // Simulate progress over 10 seconds
                    for (i in 1..100) {
                        if (currentExerciseState != ExerciseState.TRACKING) break
                        delay(100L)
                        progress = i / 100f
                    }
                }

                ExerciseState.DONE -> {
                    isCameraVisible = false
                    isImageVisible = false
                    feedbackText = "Exercise Done!"
                    feedbackColor = Color.Green
                    isButtonEnabled = true
                    buttonText = "Start Exercise"
                    progress = 0f
                }
            }
        }

        LaunchedEffect(lastCalculatedAngle) {
            if (currentExerciseState == ExerciseState.TRACKING) {
                if (!lastCalculatedAngle.isNaN()) {
                    val angleDegrees = lastCalculatedAngle
                    feedbackText = String.format("%.2f", angleDegrees)
                    val ninetyDegreeThresholdMin = 70f
                    val ninetyDegreeThresholdMax = 110f
                    feedbackColor = when {
                        angleDegrees in ninetyDegreeThresholdMin..ninetyDegreeThresholdMax -> Color.Green
                        angleDegrees < ninetyDegreeThresholdMin -> Color.Red
                        angleDegrees > ninetyDegreeThresholdMax -> Color.Red
                        else -> Color.Yellow
                    }
                    Log.d("UIUpdate", "Angle updated: $angleDegrees, Color: $feedbackColor")
                } else {
                    feedbackText = "Shoulder or wrist not detected"
                    feedbackColor = Color.Red
                    Log.d("UIUpdate", "Angle not detected")
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Arm Lift", style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isCameraVisible) {
                    // Framed Camera Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(700.dp)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .align(Alignment.TopCenter)
                    ) {
                        AndroidView(
                            factory = { context ->
                                TextureView(context).apply {
                                    surfaceTextureListener =
                                        object : TextureView.SurfaceTextureListener {
                                            override fun onSurfaceTextureAvailable(
                                                surface: SurfaceTexture,
                                                width: Int,
                                                height: Int
                                            ) {
                                                Log.d("TextureView", "Surface available")
                                                onSurfaceTextureAvailable(this@apply)
                                            }

                                            override fun onSurfaceTextureSizeChanged(
                                                surface: SurfaceTexture,
                                                width: Int,
                                                height: Int
                                            ) {
                                            }

                                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                                Log.d("TextureView", "Surface destroyed")
                                                return onSurfaceTextureDestroyed()
                                            }

                                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                                onSurfaceTextureUpdated(this@apply)
                                            }
                                        }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (isImageVisible && processedBitmap != null) {
                    Image(
                        bitmap = processedBitmap.asImageBitmap(),
                        contentDescription = "Pose Estimation Output",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(700.dp)
                            .align(Alignment.TopCenter)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 320.dp, bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = feedbackText,
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(feedbackColor)
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentExerciseState == ExerciseState.TRACKING) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Exercise Progress: ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Button(
                    onClick = {
                        onStartButtonClick()
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ResultsScreen(
        angleHistory: List<Float>,
        onBackClick: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Results", style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exercise Results",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )

                    Text(
                        text = displayResults(angleHistory),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(16.dp)
                    )

                    Button(
                        onClick = onBackClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Back to Home",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GeneralResultsScreen(navController: NavHostController) {
        var averageAngle by remember { mutableStateOf(0f) }
        var maxAngle by remember { mutableStateOf(0f) }
        var minAngle by remember { mutableStateOf(0f) }
        var successRate by remember { mutableStateOf(0f) }
        var totalSessions by remember { mutableStateOf(0) }
        var sessionAverages by remember { mutableStateOf<List<Float>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val url = "${Config.BASE_URL}get_results.php"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                try {
                    if (responseBody != null) {
                        Log.d("Backend", "Raw Response: $responseBody")
                        val json = Json { ignoreUnknownKeys = true }
                        val result = json.decodeFromString<ResultsResponse>(responseBody)

                        averageAngle = result.average_angle
                        maxAngle = result.max_angle
                        minAngle = result.min_angle
                        successRate = result.success_rate
                        totalSessions = result.total_sessions
                        sessionAverages = result.sessions
                    } else {
                        errorMessage = "No response from server"
                    }
                } catch (e: Exception) {
                    Log.e(
                        "Backend",
                        "Error fetching results: ${e.message}, Response: $responseBody"
                    )
                    errorMessage = e.message
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "General Results",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "General Results",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (totalSessions == 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "No exercise sessions recorded yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // Metrics in Cards
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Average Angle: ${String.format("%.2f", averageAngle)}°",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Max Angle: ${String.format("%.2f", maxAngle)}°",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Min Angle: ${String.format("%.2f", minAngle)}°",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Success Rate: ${String.format("%.2f", successRate)}%",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Total Sessions: $totalSessions",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Session Average Angles",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (sessionAverages.isNotEmpty()) {
                        val labels = sessionAverages.indices.map { "Session ${it + 1}" }
                        // Placeholder for chart (chartjs block removed due to compilation issues)
                        Text(
                            text = "Chart Placeholder: Session Averages - $sessionAverages",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        Text(text = "No session data available")
                    }
                }

            }
        }
    }
}