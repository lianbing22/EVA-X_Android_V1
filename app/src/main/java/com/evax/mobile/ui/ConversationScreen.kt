package com.evax.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evax.mobile.domain.AssistantSource
import com.evax.mobile.domain.GatewayConnectionConfig
import com.evax.mobile.domain.GatewayConnectionState
import com.evax.mobile.domain.GatewayConnectionStatus
import com.evax.mobile.platform.vision.FaceTrackingState
import com.evax.mobile.presentation.*
import kotlinx.coroutines.delay

private val CompanionAccent = androidx.compose.ui.graphics.Color(0xFF45E5CC)
private val CompanionMuted = androidx.compose.ui.graphics.Color(0xFF9AA9B5)
private val CompanionSurface = androidx.compose.ui.graphics.Color(0xEE10151A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMicTap: () -> Unit,
    onSpeakLatest: () -> Unit,
    onStopSpeaking: () -> Unit,
    onCycleSpeechRate: () -> Unit = {},
    faceTrackingState: FaceTrackingState = FaceTrackingState(),
    onToggleCameraTracking: () -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    showLiveVoiceSheet: Boolean = false,
    liveMicLevel: Float = 0f,
    onDismissLiveVoiceSheet: () -> Unit = {},
    onCancelTask: () -> Unit = {},
    onStopListening: () -> Unit = {},
    onClearNotice: () -> Unit = {},
    onCompanionModeChanged: (EvaCompanionMode) -> Unit = {},
    gatewayConfig: GatewayConnectionConfig = GatewayConnectionConfig(),
    gatewayStatus: GatewayConnectionStatus = GatewayConnectionStatus(),
    onSaveGatewayConfig: (GatewayConnectionConfig) -> Unit = {},
    onTestGatewayConnection: (GatewayConnectionConfig) -> Unit = {},
) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var modeOrdinal by rememberSaveable { mutableIntStateOf(EvaCompanionMode.COMPANION.ordinal) }
    var reduceMotion by rememberSaveable { mutableStateOf(false) }
    var voiceDisclosureAccepted by rememberSaveable { mutableStateOf(false) }
    var showVoiceDisclosure by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var gatewayEditedAfterCheck by remember(gatewayConfig) { mutableStateOf(false) }
    var lastTestedGatewayConfig by remember(gatewayConfig) { mutableStateOf<GatewayConnectionConfig?>(null) }
    val savedGatewayStatus = if (!gatewayStatus.isTesting && (gatewayEditedAfterCheck ||
            lastTestedGatewayConfig?.let { it != gatewayConfig } == true)) {
        GatewayConnectionStatus(
            state = if (gatewayConfig.endpoint.isNotBlank() && gatewayConfig.pairingToken.isNotBlank()) GatewayConnectionState.NOT_CHECKED else GatewayConnectionState.UNCONFIGURED,
            message = "当前保存的连接尚未验证",
            configured = gatewayConfig.endpoint.isNotBlank() && gatewayConfig.pairingToken.isNotBlank(),
        )
    } else gatewayStatus
    val testGateway = { config: GatewayConnectionConfig ->
        gatewayEditedAfterCheck = false
        lastTestedGatewayConfig = config
        onTestGatewayConnection(config)
    }
    val mode = EvaCompanionMode.entries[modeOrdinal]
    val latest = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
    val latestUser = state.messages.lastOrNull { it.role == MessageRole.USER }
    val faceOffset = if (faceTrackingState.isCameraActive && faceTrackingState.faceDetected) {
        Offset(faceTrackingState.faceX, faceTrackingState.faceY)
    } else null

    LaunchedEffect(mode) { onCompanionModeChanged(mode) }
    LaunchedEffect(latest?.id, state.voicePlayback.isSpeaking, state.phase) {
        showSubtitle = latest != null
        if (!state.voicePlayback.isSpeaking && !state.isProcessing) {
            delay(6_000)
            showSubtitle = false
        }
    }

    val requestMic = {
        if (voiceDisclosureAccepted) onMicTap() else showVoiceDisclosure = true
    }
    val openTools = {
        if (mode == EvaCompanionMode.REST) modeOrdinal = EvaCompanionMode.COMPANION.ordinal
        else panel = "tools"
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
    ) {
        val landscape = maxWidth > maxHeight
        val compactInput = landscape && WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val sceneHeight by animateDpAsState(
            targetValue = if (state.isProcessing || panel != null) maxHeight * 0.64f else maxHeight * 0.84f,
            animationSpec = tween(if (reduceMotion) 0 else 280),
            label = "companion-scene-height",
        )
        val panelHeight = maxHeight * if (landscape) 0.94f else 0.86f
        var dragDistance by remember { mutableFloatStateOf(0f) }

        Box(
            Modifier.fillMaxSize().pointerInput(mode) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        if (dragDistance < -64.dp.toPx()) {
                            if (mode == EvaCompanionMode.REST) modeOrdinal = EvaCompanionMode.COMPANION.ordinal
                            else panel = "conversation"
                        }
                    },
                )
            },
        ) {
            Text(
                text = if (mode == EvaCompanionMode.COMPANION) "EVA-X" else mode.title,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(24.dp, 16.dp),
                color = CompanionMuted.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
            if (faceTrackingState.isCameraActive && mode != EvaCompanionMode.REST) {
                Text(
                    "视觉已开启",
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(24.dp, 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = CompanionMuted,
                )
            }

            AssistantAvatar(
                phase = state.phase,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(sceneHeight),
                diameter = 160.dp,
                isSpeaking = state.voicePlayback.isSpeaking,
                faceOffset = faceOffset,
                eyeStyle = EvaEyeStyle.CYBER_COZMO,
                companionMode = mode,
                isLandscape = landscape,
                micLevel = if (state.phase == AssistantPhase.LISTENING) liveMicLevel else 0f,
                reduceMotion = reduceMotion,
                onBackgroundTap = openTools,
            )

            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = if (landscape) 44.dp else 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (mode != EvaCompanionMode.REST) {
                    when {
                        state.isProcessing -> TaskPeek(state, onCancelTask)
                        state.phase == AssistantPhase.LISTENING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SymmetricAudioWaveform(AssistantPhase.LISTENING, isSpeaking = false, modifier = Modifier.width(112.dp).height(24.dp), micLevel = liveMicLevel, reduceMotion = reduceMotion)
                                Spacer(Modifier.width(12.dp))
                                Text("正在聆听", color = CompanionAccent)
                                TextButton(onClick = onStopListening) { Text("停止") }
                            }
                        }
                        state.notice != null -> Surface(
                            color = CompanionSurface,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(state.notice, style = MaterialTheme.typography.bodyMedium)
                                if (state.phase == AssistantPhase.ERROR && !state.canRetryTask &&
                                    !state.notice.contains("电脑端确认")) {
                                    Text("请先在电脑端确认状态", color = CompanionMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    if (state.phase == AssistantPhase.ERROR && latestUser != null && state.canRetryTask) {
                                        TextButton(onClick = { onSubmit(latestUser.text) }) { Text("重试") }
                                    } else if (state.phase == AssistantPhase.ERROR && !state.canRetryTask) {
                                        TextButton(
                                            onClick = { panel = "connection"; testGateway(gatewayConfig) },
                                            enabled = !gatewayStatus.isTesting,
                                        ) { Text("查看电脑状态") }
                                    }
                                    TextButton(onClick = { panel = "conversation" }) { Text("文字输入") }
                                    TextButton(onClick = { panel = "connection" }) { Text("电脑连接") }
                                    TextButton(onClick = onClearNotice) { Text("知道了") }
                                }
                            }
                        }
                        else -> AnimatedVisibility(showSubtitle, enter = fadeIn(), exit = fadeOut()) {
                            Column(
                                Modifier.fillMaxWidth().clickable { panel = "conversation" }
                                    .semantics { contentDescription = "查看回复详情" },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (latest?.isSample == true) {
                                    Text("演示数据", style = MaterialTheme.typography.labelSmall, color = CompanionMuted)
                                }
                                Text(
                                    latest?.text.orEmpty(), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                if (state.voicePlayback.isSpeaking) {
                                    Text("正在播报", style = MaterialTheme.typography.labelSmall, color = CompanionAccent)
                                }
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode == EvaCompanionMode.REST) {
                        OutlinedButton(onClick = { modeOrdinal = EvaCompanionMode.COMPANION.ordinal }) { Text("唤醒") }
                    } else {
                        OutlinedButton(
                            onClick = if (state.phase == AssistantPhase.LISTENING) onStopListening else requestMic,
                            enabled = !state.isProcessing,
                            border = BorderStroke(1.dp, CompanionAccent.copy(alpha = 0.38f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CompanionAccent),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(if (state.phase == AssistantPhase.LISTENING) "停止聆听" else "语音输入") }
                        TextButton(onClick = { panel = "tools" }, Modifier.heightIn(min = 48.dp)) { Text("工具", color = CompanionMuted) }
                    }
                }
            }
        }

        if (panel != null) {
            ModalBottomSheet(
                onDismissRequest = { panel = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
            ) {
                ImmersiveSheetWindow()
                Column(Modifier.fillMaxWidth().heightIn(max = panelHeight).imePadding()) {
                    if (!((panel == "conversation" || panel == "connection") && compactInput)) Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (panel) { "conversation" -> "对话详情"; "settings" -> "设置"; "connection" -> "电脑连接"; else -> "快捷工具" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = { panel = null }) { Text("收起") }
                    }
                    when (panel) {
                        "conversation" -> ConversationDetails(state, onDraftChanged, onSubmit, onSpeakLatest, onStopSpeaking, onCancelTask, compactInput)
                        "settings" -> SettingsPanel(
                            state, faceTrackingState, landscape, mode, reduceMotion,
                            onToggleCameraTracking, onToggleOrientation, onCycleSpeechRate,
                            onModeChanged = {
                                modeOrdinal = it.ordinal
                                if (it == EvaCompanionMode.REST) panel = null
                            },
                            onReduceMotionChanged = { reduceMotion = it },
                            gatewayStatus = savedGatewayStatus,
                            onConnection = { panel = "connection" },
                        )
                        "connection" -> GatewayConnectionPanel(
                            config = gatewayConfig,
                            status = gatewayStatus,
                            isProcessing = state.isProcessing,
                            onSave = {
                                gatewayEditedAfterCheck = true
                                lastTestedGatewayConfig = null
                                onSaveGatewayConfig(it)
                            },
                            onTest = testGateway,
                            onBack = { panel = "settings" },
                            hasUnverifiedEdits = gatewayEditedAfterCheck,
                            verifiedInput = lastTestedGatewayConfig,
                            onInputEdited = { gatewayEditedAfterCheck = true },
                        )
                        else -> ToolsPanel(
                            state,
                            onAction = { prompt -> panel = null; onSubmit(prompt) },
                            onConversation = { panel = "conversation" },
                            onSettings = { panel = "settings" },
                            onConnectionCheck = { panel = "connection"; testGateway(gatewayConfig) },
                            isTestingConnection = gatewayStatus.isTesting,
                        )
                    }
                }
            }
        }
    }

    if (showVoiceDisclosure) {
        AlertDialog(
            onDismissRequest = { showVoiceDisclosure = false },
            title = { Text("开始语音输入") },
            text = { Text("语音由设备上的 Android 语音服务识别，可能由该服务处理或传输音频。识别后的文字会交给当前助手处理。") },
            confirmButton = {
                TextButton(onClick = { voiceDisclosureAccepted = true; showVoiceDisclosure = false; onMicTap() }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showVoiceDisclosure = false }) { Text("取消") } },
        )
    }
    if (showLiveVoiceSheet) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = onDismissLiveVoiceSheet,
            title = { Text("改用文字输入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("此设备暂时没有可用的语音识别服务。你可以输入指令，也可以使用键盘自带的语音输入。")
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = onDraftChanged,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text("你的指令") },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { val text = state.draft; onDismissLiveVoiceSheet(); if (text.isNotBlank()) onSubmit(text) },
                    enabled = state.draft.isNotBlank(),
                ) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = onDismissLiveVoiceSheet) { Text("取消") } },
        )
    }
}

@Composable
private fun TaskPeek(state: ConversationUiState, onCancel: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(), color = CompanionSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CompanionAccent.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.phase == AssistantPhase.THINKING) "正在理解" else "正在执行", style = MaterialTheme.typography.labelLarge, color = CompanionAccent)
                TextButton(onClick = onCancel) { Text("停止接收") }
            }
            Text(state.currentStep ?: "正在准备，请稍候…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { (state.completedProgressSteps.size.toFloat() / state.progressTotal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = CompanionAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text("${state.progressIndex} / ${state.progressTotal}", color = CompanionMuted, style = MaterialTheme.typography.labelSmall)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = CompanionAccent, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun ToolsPanel(
    state: ConversationUiState,
    onAction: (String) -> Unit,
    onConversation: () -> Unit,
    onSettings: () -> Unit,
    onConnectionCheck: () -> Unit,
    isTestingConnection: Boolean,
) {
    val actions = listOf(
        "查今天的安排" to "查今天的安排",
        "整理会议纪要" to "整理会议纪要",
        "截屏分析" to "分析电脑当前窗口截图",
        "整理当前工作" to "请基于电脑上当前工作内容，梳理今天最重要的三项待办，并说明依据；缺少上下文时先询问我",
        "起草回复" to "请为我正在处理的消息起草回复；先确认消息内容和接收方，只生成草稿，不发送",
    )
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceLabel(state)
        actions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, prompt) ->
                    OutlinedButton(
                        onClick = { onAction(prompt) }, enabled = !state.isProcessing,
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) { Text(label, maxLines = 2, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        OutlinedButton(
            onClick = onConnectionCheck,
            enabled = !state.isProcessing && !isTestingConnection,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (isTestingConnection) "正在检查连接…" else "检查电脑连接") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onConversation) { Text("对话详情") }
            TextButton(onClick = onSettings) { Text("设置") }
        }
    }
}

@Composable
private fun SourceLabel(state: ConversationUiState) {
    Text(
        when (state.source) {
            AssistantSource.PC_GATEWAY -> "WorkBuddy · 电脑任务通道"
            AssistantSource.LOCAL_DEMO -> "本地演示 · 示例内容"
            AssistantSource.UNCONFIRMED -> "电脑连接尚未确认"
        },
        style = MaterialTheme.typography.labelMedium, color = CompanionMuted,
    )
}

@Composable
private fun ConversationDetails(
    state: ConversationUiState,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSpeakLatest: () -> Unit,
    onStopSpeaking: () -> Unit,
    onCancel: () -> Unit,
    compactInput: Boolean,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { text: String -> keyboard?.hide(); onSubmit(text) }
    val listState = rememberLazyListState()
    val latest = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
    LaunchedEffect(state.messages.size, state.streamingReply, state.progressSteps.size) {
        if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
        if (!compactInput) SourceLabel(state)
        if (!compactInput) LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).heightIn(min = 80.dp).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.messages.isEmpty()) item { Text("有什么想一起处理的？", color = CompanionMuted, modifier = Modifier.padding(vertical = 24.dp)) }
            items(state.messages, key = { it.id }) { message ->
                Surface(
                    color = if (message.role == MessageRole.USER) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (message.role == MessageRole.USER) "你" else "EVA-X", style = MaterialTheme.typography.labelMedium, color = CompanionAccent)
                        if (message.isSample) Text("演示数据", style = MaterialTheme.typography.labelSmall, color = CompanionMuted)
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        message.followUps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = CompanionMuted) }
                    }
                }
            }
            if (state.progressSteps.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("任务步骤", style = MaterialTheme.typography.labelLarge, color = CompanionMuted)
                    state.progressSteps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Canvas(Modifier.size(6.dp)) {
                                drawCircle(if (step in state.completedProgressSteps) CompanionAccent else CompanionMuted)
                            }
                            Text(step)
                            if (step == state.currentStep) Text("执行中", style = MaterialTheme.typography.labelSmall, color = CompanionAccent)
                        }
                    }
                }
            }
            state.streamingReply?.takeIf { it.isNotBlank() }?.let { reply -> item { Text(reply, style = MaterialTheme.typography.bodyLarge) } }
        }
        if (state.isProcessing) TextButton(onClick = onCancel) { Text("停止接收") }
        if (latest != null && !compactInput) {
            TextButton(
                onClick = if (state.voicePlayback.isSpeaking) onStopSpeaking else onSpeakLatest,
                enabled = state.voicePlayback.isReady,
            ) { Text(if (state.voicePlayback.isSpeaking) "停止播报" else "朗读回复") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.draft, onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入你的指令…") },
                enabled = !state.isProcessing, maxLines = if (compactInput) 2 else 3,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (state.draft.isNotBlank()) submit(state.draft) }),
            )
            Button(onClick = { submit(state.draft) }, enabled = state.draft.isNotBlank() && !state.isProcessing) { Text("发送") }
        }
    }
}

@Composable
private fun SettingsPanel(
    state: ConversationUiState,
    face: FaceTrackingState,
    landscape: Boolean,
    mode: EvaCompanionMode,
    reduceMotion: Boolean,
    onCamera: () -> Unit,
    onOrientation: () -> Unit,
    onSpeechRate: () -> Unit,
    onModeChanged: (EvaCompanionMode) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit,
    gatewayStatus: GatewayConnectionStatus,
    onConnection: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceLabel(state)
        Surface(
            onClick = onConnection,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("电脑连接", style = MaterialTheme.typography.titleMedium, color = CompanionAccent)
                Text(gatewayConnectionStateTitle(gatewayStatus), color = CompanionMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("交互", style = MaterialTheme.typography.titleMedium)
        SettingsAction("人脸跟随", if (face.isCameraActive) "已开启" else "已关闭", onCamera)
        SettingsAction("屏幕方向", if (landscape) "切换竖屏" else "切换横屏", onOrientation)
        SettingsAction("播报语速", "${state.voicePlayback.speechRate}x", onSpeechRate)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("减少动态效果")
            Switch(checked = reduceMotion, onCheckedChange = onReduceMotionChanged)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("陪伴模式", style = MaterialTheme.typography.titleMedium)
        EvaCompanionMode.entries.forEach { candidate ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onModeChanged(candidate) },
                color = if (mode == candidate) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(candidate.title, color = if (mode == candidate) CompanionAccent else MaterialTheme.colorScheme.onSurface)
                    Text(candidate.subtitle, color = CompanionMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsAction(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun ImmersiveSheetWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}
