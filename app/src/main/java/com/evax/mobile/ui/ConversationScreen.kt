package com.evax.mobile.ui

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evax.mobile.platform.vision.FaceTrackingState
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.ConversationMessage
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.presentation.MessageRole

private val HudGlassBg = Color(0xFF0B0E17)
private val HudTileBg = Color(0xFF141926)
private val EvaMint = Color(0xFF4AF5A8)
private val HudCyan = Color(0xFF00F5D4)
private val HudViolet = Color(0xFFB9A9FF)
private val HudEmerald = Color(0xFF5CE6B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMicTap: () -> Unit,
    onSpeakLatest: () -> Unit,
    onStopSpeaking: () -> Unit,
    faceTrackingState: FaceTrackingState = FaceTrackingState(),
    onToggleCameraTracking: () -> Unit = {},
    onToggleOrientation: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val latestAssistant = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
    val conversationListState = rememberLazyListState()
    var noticeDismissed by remember(state.notice) { mutableStateOf(false) }
    var showVoiceDisclosure by rememberSaveable { mutableStateOf(false) }
    var voiceDisclosureAccepted by rememberSaveable { mutableStateOf(false) }

    // Pure full-screen eyes mode vs HUD split/deck mode
    var pureEyesMode by rememberSaveable { mutableStateOf(false) }
    var eyeStyleOrdinal by rememberSaveable { mutableStateOf(EvaEyeStyle.EVA_MINT.ordinal) }
    var companionModeOrdinal by rememberSaveable { mutableStateOf(EvaCompanionMode.COMPANION.ordinal) }

    val currentEyeStyle = EvaEyeStyle.entries[eyeStyleOrdinal % EvaEyeStyle.entries.size]
    val currentCompanionMode = EvaCompanionMode.entries[companionModeOrdinal % EvaCompanionMode.entries.size]

    val activeFaceOffset = if (faceTrackingState.isCameraActive && faceTrackingState.faceDetected) {
        Offset(faceTrackingState.faceX, faceTrackingState.faceY)
    } else {
        null
    }

    val hudGradientBorder = remember {
        Brush.linearGradient(
            colors = listOf(
                EvaMint.copy(alpha = 0.65f),
                HudCyan.copy(alpha = 0.50f),
                HudViolet.copy(alpha = 0.45f),
            ),
        )
    }

    // Edge glow pulse (DingTalk QwenNote Eva: "屏幕四周亮起，代表Eva正在听你说话")
    val edgeTransition = rememberInfiniteTransition(label = "edge-aura")
    val edgePulse by edgeTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "edgePulse",
    )

    val edgeGlowActive = state.phase == AssistantPhase.LISTENING ||
        state.phase == AssistantPhase.THINKING ||
        state.phase == AssistantPhase.EXECUTING ||
        faceTrackingState.faceDetected

    val edgeColor = when {
        state.phase == AssistantPhase.LISTENING -> EvaMint
        state.phase == AssistantPhase.THINKING || state.phase == AssistantPhase.EXECUTING -> HudViolet
        faceTrackingState.faceDetected -> EvaMint.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            conversationListState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = Color(0xFF030406),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030406))
                .drawWithContent {
                    drawContent()
                    if (edgeGlowActive) {
                        val strokeW = if (state.phase == AssistantPhase.LISTENING) {
                            6.dp.toPx() * edgePulse
                        } else {
                            3.dp.toPx() * (0.5f + 0.5f * edgePulse)
                        }
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    edgeColor.copy(alpha = 0.85f * edgePulse),
                                    HudCyan.copy(alpha = 0.65f * edgePulse),
                                    edgeColor.copy(alpha = 0.85f * edgePulse),
                                ),
                            ),
                            topLeft = Offset(strokeW / 2f, strokeW / 2f),
                            size = Size(size.width - strokeW, size.height - strokeW),
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            style = Stroke(width = strokeW),
                        )
                    }
                }
                .imePadding()
                .padding(scaffoldPadding),
        ) {
            if (isLandscape) {
                DingTalkEvaLandscapeLayout(
                    state = state,
                    latestAssistant = latestAssistant,
                    conversationListState = conversationListState,
                    pureEyesMode = pureEyesMode,
                    onTogglePureMode = { pureEyesMode = !pureEyesMode },
                    eyeStyle = currentEyeStyle,
                    onCycleEyeStyle = {
                        eyeStyleOrdinal = (eyeStyleOrdinal + 1) % EvaEyeStyle.entries.size
                    },
                    companionMode = currentCompanionMode,
                    onCycleCompanionMode = {
                        companionModeOrdinal = (companionModeOrdinal + 1) % EvaCompanionMode.entries.size
                    },
                    faceTrackingState = faceTrackingState,
                    activeFaceOffset = activeFaceOffset,
                    onToggleCameraTracking = onToggleCameraTracking,
                    onToggleOrientation = onToggleOrientation,
                    hudGradientBorder = hudGradientBorder,
                    noticeDismissed = noticeDismissed,
                    onDismissNotice = { noticeDismissed = true },
                    onDraftChanged = onDraftChanged,
                    onSubmit = onSubmit,
                    onMicClick = {
                        if (voiceDisclosureAccepted) onMicTap() else showVoiceDisclosure = true
                    },
                    onSpeakLatest = onSpeakLatest,
                    onStopSpeaking = onStopSpeaking,
                )
            } else {
                PortraitHudLayout(
                    state = state,
                    latestAssistant = latestAssistant,
                    conversationListState = conversationListState,
                    pureEyesMode = pureEyesMode,
                    onTogglePureMode = { pureEyesMode = !pureEyesMode },
                    eyeStyle = currentEyeStyle,
                    onCycleEyeStyle = {
                        eyeStyleOrdinal = (eyeStyleOrdinal + 1) % EvaEyeStyle.entries.size
                    },
                    companionMode = currentCompanionMode,
                    onCycleCompanionMode = {
                        companionModeOrdinal = (companionModeOrdinal + 1) % EvaCompanionMode.entries.size
                    },
                    faceTrackingState = faceTrackingState,
                    activeFaceOffset = activeFaceOffset,
                    onToggleCameraTracking = onToggleCameraTracking,
                    onToggleOrientation = onToggleOrientation,
                    hudGradientBorder = hudGradientBorder,
                    noticeDismissed = noticeDismissed,
                    onDismissNotice = { noticeDismissed = true },
                    onDraftChanged = onDraftChanged,
                    onSubmit = onSubmit,
                    onMicClick = {
                        if (voiceDisclosureAccepted) onMicTap() else showVoiceDisclosure = true
                    },
                    onSpeakLatest = onSpeakLatest,
                    onStopSpeaking = onStopSpeaking,
                )
            }
        }
    }

    if (showVoiceDisclosure) {
        AlertDialog(
            onDismissRequest = { showVoiceDisclosure = false },
            containerColor = Color(0xFF101522),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB8C4D9),
            title = { Text("语音识别说明") },
            text = {
                Text(
                    "语音识别由设备上安装的 Android 语音服务处理。该服务可能按照自己的行为对音频进行处理或传输；EVA-X 演示版不会把音频发送到 EVA-X 服务器。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        voiceDisclosureAccepted = true
                        showVoiceDisclosure = false
                        onMicTap()
                    },
                ) { Text("继续", color = EvaMint) }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceDisclosure = false }) {
                    Text("暂不", color = Color(0xFF9BA6BC))
                }
            },
        )
    }
}

/**
 * DingTalk / QwenNote Eva Horizontal (Landscape) Mode:
 * - Supports Full-Screen Giant Mint Eyes mode (replicates eva1.jpg / eva2.jpg / eva6.jpg / eva7.jpg)
 * - Supports Split-Screen Office Delivery HUD mode (replicates eva4.jpg bottom-left meeting/task split view)
 */
@Composable
private fun DingTalkEvaLandscapeLayout(
    state: ConversationUiState,
    latestAssistant: ConversationMessage?,
    conversationListState: androidx.compose.foundation.lazy.LazyListState,
    pureEyesMode: Boolean,
    onTogglePureMode: () -> Unit,
    eyeStyle: EvaEyeStyle,
    onCycleEyeStyle: () -> Unit,
    companionMode: EvaCompanionMode,
    onCycleCompanionMode: () -> Unit,
    faceTrackingState: FaceTrackingState,
    activeFaceOffset: Offset?,
    onToggleCameraTracking: () -> Unit,
    onToggleOrientation: () -> Unit,
    hudGradientBorder: Brush,
    noticeDismissed: Boolean,
    onDismissNotice: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMicClick: () -> Unit,
    onSpeakLatest: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Top status & mode switch bar (DingTalk Eva style)
        EvaControlHeaderBar(
            phase = state.phase,
            pureEyesMode = pureEyesMode,
            onTogglePureMode = onTogglePureMode,
            eyeStyle = eyeStyle,
            onCycleEyeStyle = onCycleEyeStyle,
            companionMode = companionMode,
            onCycleCompanionMode = onCycleCompanionMode,
            faceTrackingState = faceTrackingState,
            onToggleCameraTracking = onToggleCameraTracking,
            isLandscape = true,
            onToggleOrientation = onToggleOrientation,
        )

        Spacer(Modifier.height(6.dp))

        if (pureEyesMode) {
            // Full-Screen Giant Eyes Companion Mode (like eva1.jpg, eva2.jpg, eva6.jpg)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AssistantAvatar(
                    phase = state.phase,
                    diameter = 220.dp,
                    isSpeaking = state.voicePlayback.isSpeaking,
                    faceOffset = activeFaceOffset,
                    eyeStyle = eyeStyle,
                    companionMode = companionMode,
                    isLandscape = true,
                )
            }

            // Subtle floating subtitle pill at bottom center (exact match to eva2.jpg: "「主人，放心，一切包在我身上！」")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudWaveformStatusBanner(
                    phase = state.phase,
                    currentStep = state.currentStep,
                    isSpeaking = state.voicePlayback.isSpeaking,
                    compact = true,
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0D141C),
                    border = BorderStroke(1.dp, EvaMint.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    val subtitleText = when {
                        companionMode != EvaCompanionMode.COMPANION ->
                            "「${companionMode.title}模式：${companionMode.subtitle}」"
                        latestAssistant != null -> "「${latestAssistant.text}」"
                        else -> "「主人，放心，一切包在我身上！」"
                    }
                    Text(
                        text = subtitleText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD9FBEA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onMicClick,
                        enabled = !state.isProcessing,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, EvaMint.copy(alpha = 0.6f)),
                    ) {
                        Text("语音输入", color = EvaMint, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onTogglePureMode,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, Color(0xFF304057)),
                    ) {
                        Text("展开工作台", color = Color(0xFFD0DCF0), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            // Split-Screen DingTalk Eva Mode: Left = Expressive Robot Face + Voice Action; Right = Office Delivery Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left Pane (42%): Eva Giant Mint Eyes + Status Waveform + Quick Voice Controls (replicates eva4.jpg left side)
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AssistantAvatar(
                            phase = state.phase,
                            diameter = 168.dp,
                            isSpeaking = state.voicePlayback.isSpeaking,
                            faceOffset = activeFaceOffset,
                            eyeStyle = eyeStyle,
                            companionMode = companionMode,
                            isLandscape = true,
                        )
                    }

                    HudWaveformStatusBanner(
                        phase = state.phase,
                        currentStep = state.currentStep,
                        isSpeaking = state.voicePlayback.isSpeaking,
                        compact = true,
                    )

                    Spacer(Modifier.height(6.dp))

                    // Subtitle caption under eyes
                    Text(
                        text = if (faceTrackingState.faceDetected) {
                            "已锁定人脸 (${(faceTrackingState.faceX * 100).toInt()}%, ${(faceTrackingState.faceY * 100).toInt()}%) · 视线实时跟随中"
                        } else {
                            companionMode.subtitle
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (faceTrackingState.faceDetected) EvaMint else Color(0xFF8C9AB0),
                        maxLines = 1,
                    )

                    Spacer(Modifier.height(6.dp))

                    // Mint-emerald action buttons (like eva4.jpg's green capsule button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onMicClick,
                            enabled = !state.isProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EvaMint,
                                contentColor = Color(0xFF04140C),
                            ),
                        ) {
                            Text(
                                text = "语音输入",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        if (state.voicePlayback.isSpeaking) {
                            OutlinedButton(
                                onClick = onStopSpeaking,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                border = BorderStroke(1.dp, EvaMint),
                            ) {
                                Text("停止播报", color = EvaMint, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSpeakLatest,
                                enabled = state.voicePlayback.isReady && latestAssistant != null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                border = BorderStroke(1.dp, Color(0xFF28364A)),
                            ) {
                                Text("朗读回复", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Right Pane (56%): DingTalk Office Collaboration Card + Quick Prompt Chips + Composer
                Column(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Quick horizontal office scenario chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LandscapeQuickChip("查今天的安排", enabled = !state.isProcessing) {
                            onSubmit("查今天的安排")
                        }
                        LandscapeQuickChip("整理会议纪要", enabled = !state.isProcessing) {
                            onSubmit("整理会议纪要")
                        }
                        LandscapeQuickChip("截取电脑屏幕", enabled = !state.isProcessing) {
                            onSubmit("截取电脑屏幕")
                        }
                        LandscapeQuickChip("电脑 Agent 状态", enabled = !state.isProcessing) {
                            onSubmit("电脑 Agent 状态")
                        }
                    }

                    // Main dark rounded card for task progress & structured results (replicates eva4.jpg right pane)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0E131D))
                            .border(1.dp, hudGradientBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.progressSteps.isNotEmpty()) {
                                HudProgressSection(state.progressSteps)
                                Spacer(Modifier.height(6.dp))
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                state = conversationListState,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (state.messages.isEmpty()) {
                                    item(key = "greeting") {
                                        HudGreetingContent()
                                    }
                                } else {
                                    items(state.messages, key = ConversationMessage::id) { message ->
                                        HudMessageCard(message)
                                    }
                                }
                            }
                        }
                    }

                    state.notice?.takeIf { !noticeDismissed }?.let { notice ->
                        NoticeBanner(notice = notice, onDismiss = onDismissNotice)
                    }

                    // Compact input bar for landscape right pane
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = onDraftChanged,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = "一句话交给 Eva 执行（如：整理会议纪要）…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7D899E),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EvaMint.copy(alpha = 0.7f),
                                unfocusedBorderColor = Color(0xFF202A3C),
                                focusedContainerColor = Color(0xFF090C12),
                                unfocusedContainerColor = Color(0xFF090C12),
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSubmit(state.draft) }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onSubmit(state.draft) },
                            enabled = state.draft.isNotBlank() && !state.isProcessing,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EvaMint,
                                contentColor = Color(0xFF04140C),
                            ),
                        ) {
                            Text("发送", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeQuickChip(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Color(0xFF131A28),
        border = BorderStroke(1.dp, EvaMint.copy(alpha = 0.32f)),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Color(0xFFEAFBF3) else Color(0xFF6C768A),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PortraitHudLayout(
    state: ConversationUiState,
    latestAssistant: ConversationMessage?,
    conversationListState: androidx.compose.foundation.lazy.LazyListState,
    pureEyesMode: Boolean,
    onTogglePureMode: () -> Unit,
    eyeStyle: EvaEyeStyle,
    onCycleEyeStyle: () -> Unit,
    companionMode: EvaCompanionMode,
    onCycleCompanionMode: () -> Unit,
    faceTrackingState: FaceTrackingState,
    activeFaceOffset: Offset?,
    onToggleCameraTracking: () -> Unit,
    onToggleOrientation: () -> Unit,
    hudGradientBorder: Brush,
    noticeDismissed: Boolean,
    onDismissNotice: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMicClick: () -> Unit,
    onSpeakLatest: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EvaControlHeaderBar(
            phase = state.phase,
            pureEyesMode = pureEyesMode,
            onTogglePureMode = onTogglePureMode,
            eyeStyle = eyeStyle,
            onCycleEyeStyle = onCycleEyeStyle,
            companionMode = companionMode,
            onCycleCompanionMode = onCycleCompanionMode,
            faceTrackingState = faceTrackingState,
            onToggleCameraTracking = onToggleCameraTracking,
            isLandscape = false,
            onToggleOrientation = onToggleOrientation,
        )

        Spacer(Modifier.height(6.dp))

        AssistantAvatar(
            phase = state.phase,
            diameter = if (pureEyesMode) 260.dp else 172.dp,
            isSpeaking = state.voicePlayback.isSpeaking,
            faceOffset = activeFaceOffset,
            eyeStyle = eyeStyle,
            companionMode = companionMode,
            isLandscape = false,
        )

        Spacer(Modifier.height(4.dp))

        HudWaveformStatusBanner(
            phase = state.phase,
            currentStep = state.currentStep,
            isSpeaking = state.voicePlayback.isSpeaking,
            compact = false,
        )

        Spacer(Modifier.height(8.dp))

        if (!pureEyesMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(HudGlassBg)
                    .border(1.2.dp, hudGradientBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.progressSteps.isNotEmpty()) {
                        HudProgressSection(state.progressSteps)
                        Spacer(Modifier.height(8.dp))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        state = conversationListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.messages.isEmpty()) {
                            item(key = "greeting") {
                                HudGreetingContent()
                            }
                        } else {
                            items(state.messages, key = ConversationMessage::id) { message ->
                                HudMessageCard(message)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            PcControlDeck(
                enabled = !state.isProcessing,
                compact = state.messages.isNotEmpty(),
                borderBrush = hudGradientBorder,
                onSubmit = onSubmit,
            )

            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }

        state.notice?.takeIf { !noticeDismissed }?.let { notice ->
            NoticeBanner(notice = notice, onDismiss = onDismissNotice)
        }

        HudBottomComposerBar(
            draft = state.draft,
            isProcessing = state.isProcessing,
            isSpeaking = state.voicePlayback.isSpeaking,
            canSpeak = state.voicePlayback.isReady && latestAssistant != null,
            onDraftChanged = onDraftChanged,
            onSubmit = { onSubmit(state.draft) },
            onMicClick = onMicClick,
            onSpeakLatest = onSpeakLatest,
            onStopSpeaking = onStopSpeaking,
        )
    }
}

@Composable
private fun EvaControlHeaderBar(
    phase: AssistantPhase,
    pureEyesMode: Boolean,
    onTogglePureMode: () -> Unit,
    eyeStyle: EvaEyeStyle,
    onCycleEyeStyle: () -> Unit,
    companionMode: EvaCompanionMode,
    onCycleCompanionMode: () -> Unit,
    faceTrackingState: FaceTrackingState,
    onToggleCameraTracking: () -> Unit,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
) {
    val dotColor = phaseAccentColor(phase)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left pill: Pure Eyes toggle
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onTogglePureMode),
                shape = RoundedCornerShape(50),
                color = Color(0xFF0E131F),
                border = BorderStroke(1.dp, Color(0xFF233044)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (pureEyesMode) "EVA-X · 纯享大眼" else "EVA-X · 桌面伴侣",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE6EDF8),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Right pill: Camera Face Tracking status & radar + Demo Mode label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FaceTrackingStatusPill(
                    faceTrackingState = faceTrackingState,
                    onClick = onToggleCameraTracking,
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0E131F),
                    border = BorderStroke(1.dp, Color(0xFF233044)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "演示模式",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudViolet,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Second mini bar: DingTalk Eva 3 States + 3 Eye Styles + Landscape/Portrait switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniSwitchPill(
                label = "状态: ${companionMode.title}",
                accent = EvaMint,
                onClick = onCycleCompanionMode,
            )
            MiniSwitchPill(
                label = "眼神: ${eyeStyle.label}",
                accent = HudCyan,
                onClick = onCycleEyeStyle,
            )
            MiniSwitchPill(
                label = if (isLandscape) "切换竖屏" else "横屏 Eva 模式",
                accent = HudViolet,
                onClick = onToggleOrientation,
            )
            MiniSwitchPill(
                label = if (pureEyesMode) "显示控制台" else "纯享大眼模式",
                accent = Color(0xFF9BA6BC),
                onClick = onTogglePureMode,
            )
        }
    }
}

@Composable
private fun FaceTrackingStatusPill(
    faceTrackingState: FaceTrackingState,
    onClick: () -> Unit,
) {
    val borderColor = when {
        faceTrackingState.faceDetected -> EvaMint
        faceTrackingState.isCameraActive -> HudCyan.copy(alpha = 0.6f)
        else -> Color(0xFF34435E)
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = Color(0xFF0E1520),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Live 2D mini radar showing detected face coordinates
            Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(
                    color = Color(0xFF1A2636),
                    radius = size.minDimension / 2f,
                )
                drawCircle(
                    color = borderColor.copy(alpha = 0.5f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 1.dp.toPx()),
                )
                val dotX = (size.width / 2f) + (faceTrackingState.faceX * size.width * 0.35f)
                val dotY = (size.height / 2f) + (faceTrackingState.faceY * size.height * 0.35f)
                drawCircle(
                    color = if (faceTrackingState.faceDetected) EvaMint else Color(0xFF7D899E),
                    radius = if (faceTrackingState.faceDetected) 3.dp.toPx() else 2.dp.toPx(),
                    center = Offset(dotX, dotY),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    faceTrackingState.faceDetected -> "人脸跟随中"
                    faceTrackingState.isCameraActive -> "视觉寻找人脸"
                    else -> "开启人脸跟随"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (faceTrackingState.faceDetected) EvaMint else Color(0xFFD6E2F5),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MiniSwitchPill(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = Color(0xFF0D131E),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NoticeBanner(
    notice: String,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1620)),
        border = BorderStroke(1.dp, Color(0xFFFF7A95).copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice,
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFCED5),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text("知道了", color = EvaMint)
            }
        }
    }
}

@Composable
private fun HudWaveformStatusBanner(
    phase: AssistantPhase,
    currentStep: String?,
    isSpeaking: Boolean,
    compact: Boolean,
) {
    val accent = phaseAccentColor(phase)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            SymmetricAudioWaveform(
                phase = phase,
                isSpeaking = isSpeaking,
            )
            Spacer(Modifier.width(10.dp))
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF0E1520),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = phaseLabel(phase),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                if (currentStep != null) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                    Text(
                        text = currentStep,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
            }
        }
        if (!compact) {
            Spacer(Modifier.width(10.dp))
            SymmetricAudioWaveform(
                phase = phase,
                isSpeaking = isSpeaking,
            )
        }
    }
}

@Composable
private fun HudProgressSection(steps: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121826))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val totalBars = 3
            for (i in 0 until totalBars) {
                val active = i < steps.size
                val barColor = when {
                    !active -> Color(0xFF252D3D)
                    i == 0 -> EvaMint
                    i == 1 -> HudCyan
                    else -> HudViolet
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = EvaMint,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD5DFEE),
                    )
                }
            }
        }
    }
}

@Composable
private fun HudGreetingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = EvaMint.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EvaMint.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "EVA-X",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = EvaMint,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "千问/钉钉 Eva 具身桌面伴侣",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8C9AB0),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "你好，我是 EVA-X（支持横屏底座模式 & 视觉人脸跟随）",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "把手机横放或点击顶部「横屏 Eva 模式」即可切换千问 Eva 同款翡翠灵眸横屏形态；面对前置镜头移动头部，眼睛会实时看向你。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9BA6BC),
        )
    }
}

@Composable
private fun HudMessageCard(message: ConversationMessage) {
    val isUser = message.role == MessageRole.USER
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isUser) Color(0xFF11221E) else Color(0xFF111622),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUser) EvaMint.copy(alpha = 0.35f) else HudViolet.copy(alpha = 0.25f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isUser) EvaMint.copy(alpha = 0.15f) else HudViolet.copy(alpha = 0.15f),
                    border = BorderStroke(
                        0.8.dp,
                        if (isUser) EvaMint.copy(alpha = 0.5f) else HudViolet.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = if (isUser) "你" else "EVA-X",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) EvaMint else HudViolet,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (message.isSample) {
                    Text(
                        text = "演示数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = EvaMint,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF2F6FC),
            )
            message.followUps.forEach { followUp ->
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvaMint,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = followUp,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8C4D9),
                    )
                }
            }
        }
    }
}

@Composable
private fun PcControlDeck(
    enabled: Boolean,
    compact: Boolean,
    borderBrush: Brush,
    onSubmit: (String) -> Unit,
) {
    val verticalPad = if (compact) 8.dp else 10.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HudGlassBg)
            .border(1.2.dp, borderBrush, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = verticalPad),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeckActionTile(
                    title = "查今天的安排",
                    iconType = DeckIconType.CALENDAR,
                    enabled = enabled,
                    compact = compact,
                    onClick = { onSubmit("查今天的安排") },
                    modifier = Modifier.weight(1f),
                )
                DeckActionTile(
                    title = "整理会议纪要",
                    iconType = DeckIconType.EDIT,
                    enabled = enabled,
                    compact = compact,
                    onClick = { onSubmit("整理会议纪要") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeckActionTile(
                    title = "截取电脑屏幕",
                    iconType = DeckIconType.MONITOR,
                    enabled = enabled,
                    compact = compact,
                    onClick = { onSubmit("截取电脑屏幕") },
                    modifier = Modifier.weight(1f),
                )
                DeckActionTile(
                    title = "电脑 Agent 状态",
                    iconType = DeckIconType.BOLT,
                    enabled = enabled,
                    compact = compact,
                    onClick = { onSubmit("电脑 Agent 状态") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class DeckIconType {
    CALENDAR,
    EDIT,
    MONITOR,
    BOLT,
}

@Composable
private fun DeckActionTile(
    title: String,
    iconType: DeckIconType,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = HudTileBg,
        border = BorderStroke(1.dp, Color(0xFF252F42)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 28.dp else 32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF1E2638)),
                contentAlignment = Alignment.Center,
            ) {
                DeckTileVectorIcon(iconType)
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = if (enabled) Color(0xFFF0F4FA) else Color(0xFF6C768A),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeckTileVectorIcon(type: DeckIconType) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 1.6.dp.toPx()
        val color = Color(0xFFB8C7E0)
        when (type) {
            DeckIconType.CALENDAR -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.2f),
                    size = Size(size.width * 0.76f, size.height * 0.68f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.12f, size.height * 0.42f),
                    end = Offset(size.width * 0.88f, size.height * 0.42f),
                    strokeWidth = stroke,
                )
            }

            DeckIconType.EDIT -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.2f, size.height * 0.8f),
                    end = Offset(size.width * 0.8f, size.height * 0.2f),
                    strokeWidth = stroke * 1.2f,
                    cap = StrokeCap.Round,
                )
            }

            DeckIconType.MONITOR -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.1f, size.height * 0.16f),
                    size = Size(size.width * 0.8f, size.height * 0.54f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.3f, size.height * 0.84f),
                    end = Offset(size.width * 0.7f, size.height * 0.84f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            DeckIconType.BOLT -> {
                val path = Path().apply {
                    moveTo(size.width * 0.56f, size.height * 0.10f)
                    lineTo(size.width * 0.24f, size.height * 0.54f)
                    lineTo(size.width * 0.50f, size.height * 0.54f)
                    lineTo(size.width * 0.42f, size.height * 0.90f)
                    lineTo(size.width * 0.76f, size.height * 0.44f)
                    lineTo(size.width * 0.50f, size.height * 0.44f)
                    close()
                }
                drawPath(path, color = EvaMint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun HudBottomComposerBar(
    draft: String,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    canSpeak: Boolean,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onMicClick: () -> Unit,
    onSpeakLatest: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0E131D),
        border = BorderStroke(1.dp, Color(0xFF253146)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "向 EVA-X 发送指令…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7D899E),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EvaMint.copy(alpha = 0.65f),
                        unfocusedBorderColor = Color(0xFF202A3C),
                        focusedContainerColor = Color(0xFF090C12),
                        unfocusedContainerColor = Color(0xFF090C12),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSubmit,
                    enabled = draft.isNotBlank() && !isProcessing,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EvaMint,
                        contentColor = Color(0xFF041014),
                        disabledContainerColor = Color(0xFF1A2232),
                        disabledContentColor = Color(0xFF5D687D),
                    ),
                ) {
                    Text("发送", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onMicClick,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1.35f),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF131D2B),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(EvaMint, HudCyan),
                        ),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EvaMint),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "语音输入",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = " · 点我说话",
                            style = MaterialTheme.typography.labelSmall,
                            color = EvaMint,
                        )
                    }
                }

                if (isSpeaking) {
                    OutlinedButton(
                        onClick = onStopSpeaking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, EvaMint.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text(
                            text = "停止播报",
                            style = MaterialTheme.typography.labelMedium,
                            color = EvaMint,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onSpeakLatest,
                        enabled = canSpeak,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFF263247)),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text(
                            text = "朗读回复",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
