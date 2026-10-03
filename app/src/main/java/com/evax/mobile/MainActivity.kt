package com.evax.mobile

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.evax.mobile.domain.DemoAssistantEngine
import com.evax.mobile.domain.SpeechInputFailure
import com.evax.mobile.platform.vision.CameraFaceTracker
import com.evax.mobile.platform.voice.AndroidSpeechInputController
import com.evax.mobile.platform.voice.AndroidTextToSpeechController
import com.evax.mobile.presentation.ConversationViewModel
import com.evax.mobile.presentation.MessageRole
import com.evax.mobile.ui.ConversationScreen
import com.evax.mobile.ui.theme.EvaXTheme

class MainActivity : ComponentActivity() {
    private val conversationViewModel: ConversationViewModel by viewModels {
        ConversationViewModelFactory(DemoAssistantEngine())
    }

    private lateinit var speechInputController: AndroidSpeechInputController
    private lateinit var speechOutputController: AndroidTextToSpeechController
    private lateinit var cameraFaceTracker: CameraFaceTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speechInputController = AndroidSpeechInputController(applicationContext)
        speechOutputController = AndroidTextToSpeechController(applicationContext)
        cameraFaceTracker = CameraFaceTracker(applicationContext)

        setContent {
            EvaXTheme {
                val uiState by conversationViewModel.uiState.collectAsStateWithLifecycle()
                val faceTrackingState by cameraFaceTracker.state.collectAsStateWithLifecycle()
                var permissionWasDenied by rememberSaveable { mutableStateOf(false) }
                var cameraPromptedOnLaunch by rememberSaveable { mutableStateOf(false) }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    if (isGranted) {
                        permissionWasDenied = false
                        startSpeechRecognition()
                    } else {
                        permissionWasDenied = true
                        conversationViewModel.onSpeechFailure(SpeechInputFailure.PERMISSION_DENIED)
                    }
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    cameraFaceTracker.onPermissionChanged(isGranted)
                    if (isGranted) {
                        cameraFaceTracker.start()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasCam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    cameraFaceTracker.onPermissionChanged(hasCam)
                    if (hasCam) {
                        cameraFaceTracker.start()
                    } else if (!cameraPromptedOnLaunch && !isRunningInstrumentationTest()) {
                        cameraPromptedOnLaunch = true
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                LaunchedEffect(speechOutputController, lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        speechOutputController.state.collect(conversationViewModel::onVoicePlaybackChanged)
                    }
                }

                ConversationScreen(
                    state = uiState,
                    onDraftChanged = conversationViewModel::onDraftChanged,
                    onSubmit = { prompt -> conversationViewModel.submitPrompt(prompt) },
                    onMicTap = {
                        when {
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                                startSpeechRecognition()

                            permissionWasDenied ->
                                conversationViewModel.onSpeechFailure(SpeechInputFailure.PERMISSION_DENIED)

                            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSpeakLatest = {
                        uiState.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                            ?.let { speechOutputController.speak(it.text) }
                    },
                    onStopSpeaking = speechOutputController::stop,
                    faceTrackingState = faceTrackingState,
                    onToggleCameraTracking = {
                        when {
                            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ->
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

                            faceTrackingState.isCameraActive ->
                                cameraFaceTracker.stop()

                            else ->
                                cameraFaceTracker.start()
                        }
                    },
                    onToggleOrientation = {
                        val isCurrentlyLandscape =
                            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        requestedOrientation = if (isCurrentlyLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::cameraFaceTracker.isInitialized &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraFaceTracker.start()
        }
    }

    override fun onStop() {
        if (::speechInputController.isInitialized) {
            speechInputController.cancel()
            conversationViewModel.onListeningCancelled()
        }
        if (::speechOutputController.isInitialized) speechOutputController.stop()
        if (::cameraFaceTracker.isInitialized) cameraFaceTracker.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::speechInputController.isInitialized) speechInputController.destroy()
        if (::speechOutputController.isInitialized) speechOutputController.shutdown()
        if (::cameraFaceTracker.isInitialized) cameraFaceTracker.stop()
        super.onDestroy()
    }

    private fun startSpeechRecognition() {
        conversationViewModel.onListeningStarted()
        speechInputController.start(
            onResult = conversationViewModel::onSpeechResult,
            onFailure = conversationViewModel::onSpeechFailure,
        )
    }

    private fun isRunningInstrumentationTest(): Boolean {
        return try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: Throwable) {
            false
        }
    }
}

private class ConversationViewModelFactory(
    private val engine: DemoAssistantEngine,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return ConversationViewModel(engine) as T
    }
}
