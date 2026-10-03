package com.evax.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.DemoAssistantEngine
import com.evax.mobile.domain.GatewayAssistantEngine
import com.evax.mobile.domain.SpeechInputFailure
import com.evax.mobile.platform.vision.CameraFaceTracker
import com.evax.mobile.platform.voice.AndroidSpeechInputController
import com.evax.mobile.platform.voice.AndroidTextToSpeechController
import com.evax.mobile.platform.voice.RealAudioMicMonitor
import com.evax.mobile.presentation.ConversationViewModel
import com.evax.mobile.presentation.MessageRole
import com.evax.mobile.ui.ConversationScreen
import com.evax.mobile.ui.theme.EvaXTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val conversationViewModel: ConversationViewModel by viewModels {
        val activeEngine: AssistantEngine = if (isRunningInstrumentationTest()) {
            DemoAssistantEngine()
        } else {
            GatewayAssistantEngine(fallbackEngine = DemoAssistantEngine())
        }
        ConversationViewModelFactory(
            engine = activeEngine,
            onSpeakChunk = { sentence, isFirst ->
                if (::speechOutputController.isInitialized) {
                    speechOutputController.speakChunk(sentence, flush = isFirst)
                }
            },
        )
    }

    private lateinit var speechInputController: AndroidSpeechInputController
    private lateinit var speechOutputController: AndroidTextToSpeechController
    private lateinit var cameraFaceTracker: CameraFaceTracker
    private val realAudioMicMonitor = RealAudioMicMonitor()

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
                var showLiveVoiceSheet by rememberSaveable { mutableStateOf(false) }
                var liveMicLevel by rememberSaveable { mutableFloatStateOf(0.15f) }

                val systemSpeechLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val recognized = result.data
                            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            ?.firstOrNull { it.isNotBlank() }
                            .orEmpty()
                        if (recognized.isNotBlank()) {
                            conversationViewModel.onSpeechResult(recognized)
                        } else {
                            conversationViewModel.onSpeechFailure(SpeechInputFailure.NO_MATCH)
                        }
                    } else {
                        conversationViewModel.onListeningCancelled()
                    }
                }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    if (isGranted) {
                        permissionWasDenied = false
                        startSpeechRecognition(
                            systemSpeechLauncher = systemSpeechLauncher,
                            onUpdateMicLevel = { liveMicLevel = it },
                            onOpenLiveSheetFallback = {
                                showLiveVoiceSheet = true
                                realAudioMicMonitor.start { level -> liveMicLevel = level }
                            },
                        )
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
                                startSpeechRecognition(
                                    systemSpeechLauncher = systemSpeechLauncher,
                                    onUpdateMicLevel = { liveMicLevel = it },
                                    onOpenLiveSheetFallback = {
                                        showLiveVoiceSheet = true
                                        realAudioMicMonitor.start { level -> liveMicLevel = level }
                                    },
                                )

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
                    onCycleSpeechRate = { speechOutputController.cycleSpeechRate() },
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
                    showLiveVoiceSheet = showLiveVoiceSheet,
                    liveMicLevel = liveMicLevel,
                    onDismissLiveVoiceSheet = {
                        showLiveVoiceSheet = false
                        realAudioMicMonitor.stop()
                        conversationViewModel.onListeningCancelled()
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
        realAudioMicMonitor.stop()
        if (::speechInputController.isInitialized) {
            speechInputController.cancel()
            conversationViewModel.onListeningCancelled()
        }
        if (::speechOutputController.isInitialized) speechOutputController.stop()
        if (::cameraFaceTracker.isInitialized) cameraFaceTracker.stop()
        super.onStop()
    }

    override fun onDestroy() {
        realAudioMicMonitor.stop()
        if (::speechInputController.isInitialized) speechInputController.destroy()
        if (::speechOutputController.isInitialized) speechOutputController.shutdown()
        if (::cameraFaceTracker.isInitialized) cameraFaceTracker.stop()
        super.onDestroy()
    }

    private fun startSpeechRecognition(
        systemSpeechLauncher: ActivityResultLauncher<Intent>,
        onUpdateMicLevel: (Float) -> Unit,
        onOpenLiveSheetFallback: () -> Unit,
    ) {
        if (::speechOutputController.isInitialized) {
            speechOutputController.stop()
        }
        conversationViewModel.onListeningStarted()
        speechInputController.start(
            onResult = conversationViewModel::onSpeechResult,
            onPartialResult = { partial ->
                if (partial.isNotBlank()) {
                    conversationViewModel.onDraftChanged(partial)
                }
            },
            onRmsChanged = { rmsdB ->
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                onUpdateMicLevel(normalized)
            },
            onFailure = { failure ->
                if (failure == SpeechInputFailure.SERVICE_UNAVAILABLE || failure == SpeechInputFailure.UNKNOWN) {
                    if (!tryLaunchSystemSpeechDialog(systemSpeechLauncher)) {
                        onOpenLiveSheetFallback()
                    }
                } else {
                    conversationViewModel.onSpeechFailure(failure)
                }
            },
        )
    }

    private fun tryLaunchSystemSpeechDialog(
        launcher: ActivityResultLauncher<Intent>,
    ): Boolean {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag().ifBlank { "zh-CN" })
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请对 EVA-X 说出你的指令…")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            if (intent.resolveActivity(packageManager) != null) {
                launcher.launch(intent)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
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
    private val engine: AssistantEngine,
    private val onSpeakChunk: ((sentence: String, isFirst: Boolean) -> Unit)? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return ConversationViewModel(engine = engine, onSpeakChunk = onSpeakChunk) as T
    }
}
