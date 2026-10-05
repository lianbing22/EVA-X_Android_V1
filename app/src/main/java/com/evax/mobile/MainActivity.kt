package com.evax.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.DemoAssistantEngine
import com.evax.mobile.domain.GatewayAssistantEngine
import com.evax.mobile.domain.GatewayConnectionConfig
import com.evax.mobile.domain.GatewayConnectionState
import com.evax.mobile.domain.GatewayConnectionStatus
import com.evax.mobile.domain.GatewayException
import com.evax.mobile.domain.SpeechInputFailure
import com.evax.mobile.domain.TunnelBeaconResolver
import com.evax.mobile.platform.gateway.GatewayConfigStore
import com.evax.mobile.platform.vision.CameraFaceTracker
import com.evax.mobile.platform.voice.AndroidSpeechInputController
import com.evax.mobile.platform.voice.AndroidTextToSpeechController
import com.evax.mobile.presentation.ConversationViewModel
import com.evax.mobile.presentation.MessageRole
import com.evax.mobile.ui.EvaCompanionMode
import com.evax.mobile.ui.ConversationScreen
import com.evax.mobile.ui.theme.EvaXTheme
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val gatewayConfigStore by lazy { GatewayConfigStore(applicationContext) }
    private val gatewayEngine by lazy {
        GatewayAssistantEngine(
            configProvider = gatewayConfigStore::load,
            beaconResolver = { token ->
                if (isRunningInstrumentationTest()) null else TunnelBeaconResolver.resolve(token)
            },
            onEndpointDiscovered = { discoveredEndpoint ->
                val current = runCatching { gatewayConfigStore.load() }.getOrDefault(gatewayConfig)
                if (current.isConfigured && current.endpoint != discoveredEndpoint) {
                    val updated = current.copy(endpoint = discoveredEndpoint)
                    runCatching { gatewayConfigStore.save(updated) }
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (!gatewayStatus.isTesting) {
                            gatewayConfig = updated
                        }
                    }
                }
            },
        )
    }
    private var gatewayConfig by mutableStateOf(GatewayConnectionConfig())
    private var gatewayStatus by mutableStateOf(GatewayConnectionStatus())
    private var gatewayOperationJob: Job? = null
    private var gatewayOperationGeneration = 0L

    private val conversationViewModel: ConversationViewModel by viewModels {
        val activeEngine: AssistantEngine = if (isRunningInstrumentationTest() || isDebugDemoRequested()) {
            DemoAssistantEngine()
        } else {
            gatewayEngine
        }
        ConversationViewModelFactory(
            engine = activeEngine,
            onSpeakChunk = ::speakAssistantSentence,
        )
    }

    private lateinit var speechInputController: AndroidSpeechInputController
    private lateinit var speechOutputController: AndroidTextToSpeechController
    private lateinit var cameraFaceTracker: CameraFaceTracker
    private var cameraTrackingEnabled = false
    private var companionMode = EvaCompanionMode.COMPANION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraTrackingEnabled = savedInstanceState?.getBoolean("cameraTrackingEnabled") ?: false
        companionMode = savedInstanceState?.getString("companionMode")
            ?.let { value -> EvaCompanionMode.entries.firstOrNull { it.name == value } }
            ?: EvaCompanionMode.COMPANION
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (companionMode != EvaCompanionMode.REST) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        speechInputController = AndroidSpeechInputController(applicationContext)
        speechOutputController = AndroidTextToSpeechController(applicationContext)
        cameraFaceTracker = CameraFaceTracker(applicationContext)
        conversationViewModel.setSpeechCallback(::speakAssistantSentence)
        try {
            var loaded = gatewayConfigStore.load()
            if (!loaded.isConfigured && !isRunningInstrumentationTest()) {
                loaded = GatewayConnectionConfig(
                    endpoint = "https://ca9215c26a5023.lhr.life",
                    pairingToken = "YvpyZ4nG0d_AlDqMP4MjWW_oDi7kfMrPF3O2x-PgTnk",
                )
                runCatching { gatewayConfigStore.save(loaded) }
            }
            gatewayConfig = loaded
            gatewayStatus = GatewayConnectionStatus.notChecked(gatewayConfig)
            if (gatewayConfig.isConfigured && !isRunningInstrumentationTest()) {
                testGatewayConnection(gatewayConfig)
            }
        } catch (failure: GatewayException) {
            gatewayStatus = GatewayConnectionStatus(
                state = GatewayConnectionState.ERROR,
                message = failure.userMessage,
                errorCode = failure.code,
            )
        }

        setContent {
            EvaXTheme {
                val uiState by conversationViewModel.uiState.collectAsStateWithLifecycle()
                val faceTrackingState by cameraFaceTracker.state.collectAsStateWithLifecycle()
                var permissionWasDenied by rememberSaveable { mutableStateOf(false) }
                var showLiveVoiceSheet by rememberSaveable { mutableStateOf(false) }
                var liveMicLevel by rememberSaveable { mutableFloatStateOf(0f) }

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
                                liveMicLevel = 0f
                                showLiveVoiceSheet = true
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
                    cameraTrackingEnabled = isGranted
                    if (isGranted && companionMode != EvaCompanionMode.REST) cameraFaceTracker.start()
                }

                LaunchedEffect(Unit) {
                    val hasCam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    cameraFaceTracker.onPermissionChanged(hasCam)
                }

                LaunchedEffect(speechOutputController, lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        speechOutputController.state.collect(conversationViewModel::onVoicePlaybackChanged)
                    }
                }

                ConversationScreen(
                    state = uiState,
                    gatewayConfig = gatewayConfig,
                    gatewayStatus = gatewayStatus,
                    onSaveGatewayConfig = ::saveGatewayConfig,
                    onTestGatewayConnection = ::testGatewayConnection,
                    onDraftChanged = conversationViewModel::onDraftChanged,
                    onSubmit = { prompt ->
                        speechInputController.cancel()
                        liveMicLevel = 0f
                        conversationViewModel.submitPrompt(prompt)
                    },
                    onMicTap = {
                        when {
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                                startSpeechRecognition(
                                    systemSpeechLauncher = systemSpeechLauncher,
                                    onUpdateMicLevel = { liveMicLevel = it },
                                    onOpenLiveSheetFallback = {
                                        liveMicLevel = 0f
                                        showLiveVoiceSheet = true
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

                            cameraTrackingEnabled -> {
                                cameraTrackingEnabled = false
                                cameraFaceTracker.stop()
                            }

                            else -> {
                                cameraTrackingEnabled = true
                                cameraFaceTracker.start()
                            }
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
                        liveMicLevel = 0f
                        conversationViewModel.onListeningCancelled()
                    },
                    onCancelTask = {
                        conversationViewModel.cancelProcessing()
                        speechOutputController.stop()
                    },
                    onStopListening = {
                        speechInputController.cancel()
                        showLiveVoiceSheet = false
                        liveMicLevel = 0f
                        conversationViewModel.onListeningCancelled()
                    },
                    onClearNotice = conversationViewModel::clearNotice,
                    onCompanionModeChanged = { mode ->
                        val previousMode = companionMode
                        companionMode = mode
                        if (mode != EvaCompanionMode.COMPANION) speechOutputController.stop()
                        if (mode == EvaCompanionMode.REST) {
                            speechInputController.cancel()
                            conversationViewModel.onListeningCancelled()
                            if (uiState.isProcessing) conversationViewModel.cancelProcessing()
                            liveMicLevel = 0f
                            showLiveVoiceSheet = false
                            cameraFaceTracker.stop()
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            if (previousMode == EvaCompanionMode.REST && cameraTrackingEnabled &&
                                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraFaceTracker.start()
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::cameraFaceTracker.isInitialized && cameraTrackingEnabled && companionMode != EvaCompanionMode.REST &&
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("cameraTrackingEnabled", cameraTrackingEnabled)
        outState.putString("companionMode", companionMode.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        conversationViewModel.setSpeechCallback(null)
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
        onUpdateMicLevel(0f)
        conversationViewModel.onListeningStarted()
        speechInputController.start(
            onResult = { text -> onUpdateMicLevel(0f); conversationViewModel.onSpeechResult(text) },
            onPartialResult = { partial ->
                if (partial.isNotBlank()) {
                    conversationViewModel.onDraftChanged(partial)
                }
            },
            onRmsChanged = { rmsdB ->
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                onUpdateMicLevel(normalized)
            },
            onFailure = { failure ->
                onUpdateMicLevel(0f)
                if (failure == SpeechInputFailure.SERVICE_UNAVAILABLE || failure == SpeechInputFailure.UNKNOWN) {
                    if (!tryLaunchSystemSpeechDialog(systemSpeechLauncher)) {
                        conversationViewModel.onListeningCancelled()
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

    private fun speakAssistantSentence(sentence: String, isFirst: Boolean) {
        if (::speechOutputController.isInitialized && companionMode == EvaCompanionMode.COMPANION &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            speechOutputController.speakChunk(sentence, flush = isFirst)
        }
    }

    private fun saveGatewayConfig(config: GatewayConnectionConfig) {
        if (conversationViewModel.uiState.value.isProcessing || gatewayStatus.isTesting) return
        val operation = ++gatewayOperationGeneration
        gatewayOperationJob?.cancel()
        gatewayStatus = GatewayConnectionStatus(state = GatewayConnectionState.CHECKING, message = "正在安全保存连接配置…")
        gatewayOperationJob = lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    config.validated().also(gatewayConfigStore::save)
                }
                if (operation == gatewayOperationGeneration) {
                    gatewayConfig = saved
                    gatewayStatus = GatewayConnectionStatus.notChecked(saved)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: GatewayException) {
                if (operation == gatewayOperationGeneration) {
                    gatewayStatus = GatewayConnectionStatus(
                        state = GatewayConnectionState.ERROR,
                        message = failure.userMessage,
                        errorCode = failure.code,
                    )
                }
            }
        }
    }

    private fun testGatewayConnection(config: GatewayConnectionConfig) {
        if (conversationViewModel.uiState.value.isProcessing || gatewayStatus.isTesting) return
        val operation = ++gatewayOperationGeneration
        gatewayOperationJob?.cancel()
        gatewayStatus = GatewayConnectionStatus(state = GatewayConnectionState.CHECKING, message = "正在检查桥接、授权与助理在线状态…")
        gatewayOperationJob = lifecycleScope.launch {
            val checked = gatewayEngine.testConnection(config)
            if (operation == gatewayOperationGeneration) {
                val resolvedEndpoint = gatewayEngine.lastResolvedEndpoint
                if (checked.state == GatewayConnectionState.READY &&
                    !resolvedEndpoint.isNullOrBlank() &&
                    resolvedEndpoint != gatewayConfig.endpoint
                ) {
                    val updated = config.copy(endpoint = resolvedEndpoint)
                    runCatching { gatewayConfigStore.save(updated) }
                    gatewayConfig = updated
                }
                gatewayStatus = checked
            }
        }
    }

    // Local QA may opt into deterministic demo content in debug builds only.
    private fun isDebugDemoRequested(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("demo_mode", false)

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
