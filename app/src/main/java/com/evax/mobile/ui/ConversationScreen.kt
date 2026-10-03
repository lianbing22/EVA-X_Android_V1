package com.evax.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.ConversationMessage
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.presentation.MessageRole

private val HudGlassBg = Color(0xFF0B0E17)
private val HudTileBg = Color(0xFF141926)
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
) {
    val latestAssistant = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
    val conversationListState = rememberLazyListState()
    var noticeDismissed by remember(state.notice) { mutableStateOf(false) }
    var showVoiceDisclosure by rememberSaveable { mutableStateOf(false) }
    var voiceDisclosureAccepted by rememberSaveable { mutableStateOf(false) }
    var pureEyesMode by rememberSaveable { mutableStateOf(false) }

    val hudGradientBorder = remember {
        Brush.linearGradient(
            colors = listOf(
                HudCyan.copy(alpha = 0.65f),
                HudViolet.copy(alpha = 0.55f),
                HudCyan.copy(alpha = 0.45f),
            ),
        )
    }

    Scaffold(
        containerColor = Color(0xFF050608),
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050608))
                .imePadding()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HudTopBar(
                phase = state.phase,
                pureEyesMode = pureEyesMode,
                onTogglePureMode = { pureEyesMode = !pureEyesMode },
            )

            Spacer(Modifier.height(6.dp))

            AssistantAvatar(
                phase = state.phase,
                diameter = if (pureEyesMode) 260.dp else 172.dp,
                isSpeaking = state.voicePlayback.isSpeaking,
            )

            Spacer(Modifier.height(4.dp))

            HudWaveformStatusBanner(
                phase = state.phase,
                currentStep = state.currentStep,
                isSpeaking = state.voicePlayback.isSpeaking,
            )

            Spacer(Modifier.height(8.dp))

            LaunchedEffect(state.messages.size) {
                if (state.messages.isNotEmpty()) {
                    conversationListState.animateScrollToItem(state.messages.lastIndex)
                }
            }

            if (!pureEyesMode) {
                // Card 1: Floating AI Subtitle, Progress & Conversation HUD Card
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

                // Card 2: 2x2 PC AI Control Deck (matches Image 1)
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1620)),
                    border = BorderStroke(1.dp, Color(0xFFFF7A95).copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = notice,
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFFFCED5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { noticeDismissed = true }) {
                            Text("知道了", color = HudCyan)
                        }
                    }
                }
            }

            HudBottomComposerBar(
                draft = state.draft,
                isProcessing = state.isProcessing,
                isSpeaking = state.voicePlayback.isSpeaking,
                canSpeak = state.voicePlayback.isReady && latestAssistant != null,
                onDraftChanged = onDraftChanged,
                onSubmit = { onSubmit(state.draft) },
                onMicClick = {
                    if (voiceDisclosureAccepted) onMicTap() else showVoiceDisclosure = true
                },
                onSpeakLatest = onSpeakLatest,
                onStopSpeaking = onStopSpeaking,
            )
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
                ) { Text("继续", color = HudCyan) }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceDisclosure = false }) {
                    Text("暂不", color = Color(0xFF9BA6BC))
                }
            },
        )
    }
}

@Composable
private fun HudTopBar(
    phase: AssistantPhase,
    pureEyesMode: Boolean,
    onTogglePureMode: () -> Unit,
) {
    val dotColor = phaseAccentColor(phase)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onTogglePureMode),
            shape = RoundedCornerShape(50),
            color = Color(0xFF0E131F),
            border = BorderStroke(1.dp, Color(0xFF233044)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (pureEyesMode) "EVA-X · 纯享模式" else "EVA-X · 桌面伴侣 HUD",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE6EDF8),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF0E131F),
            border = BorderStroke(1.dp, Color(0xFF233044)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PC 已连接 · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9BA6BC),
                )
                Text(
                    text = "演示模式",
                    style = MaterialTheme.typography.labelMedium,
                    color = HudViolet,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HudWaveformStatusBanner(
    phase: AssistantPhase,
    currentStep: String?,
    isSpeaking: Boolean,
) {
    val accent = phaseAccentColor(phase)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymmetricAudioWaveform(
            phase = phase,
            isSpeaking = isSpeaking,
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF0E1520),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = phaseLabel(phase),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                if (currentStep != null) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Text(
                        text = currentStep,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        SymmetricAudioWaveform(
            phase = phase,
            isSpeaking = isSpeaking,
        )
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
        // Segmented glowing progress bars (matches Image 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val totalBars = 3
            for (i in 0 until totalBars) {
                val active = i < steps.size
                val barColor = when {
                    !active -> Color(0xFF252D3D)
                    i == 0 -> HudCyan
                    i == 1 -> HudViolet
                    else -> HudEmerald
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
                        color = HudCyan,
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
                color = HudCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "EVA-X",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "桌面具身智能协同终端",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8C9AB0),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "你好，我是 EVA-X",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "点击下方电脑协同磁贴或使用语音输入，实时感知屏幕上下文并操控桌面 AI 任务。",
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
        color = if (isUser) Color(0xFF132229) else Color(0xFF111622),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUser) HudCyan.copy(alpha = 0.30f) else HudViolet.copy(alpha = 0.25f),
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
                    color = if (isUser) HudCyan.copy(alpha = 0.15f) else HudViolet.copy(alpha = 0.15f),
                    border = BorderStroke(
                        0.8.dp,
                        if (isUser) HudCyan.copy(alpha = 0.5f) else HudViolet.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = if (isUser) "你" else "EVA-X",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) HudCyan else HudViolet,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (message.isSample) {
                    Text(
                        text = "演示数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = HudCyan,
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
                        color = HudCyan,
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
                drawPath(path, color = HudCyan, style = Stroke(width = stroke, join = StrokeJoin.Round))
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
                        focusedBorderColor = HudCyan.copy(alpha = 0.65f),
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudCyan,
                        contentColor = Color(0xFF04221F),
                        disabledContainerColor = Color(0xFF1B2333),
                        disabledContentColor = Color(0xFF5D687D),
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("发送", fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onMicClick,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudCyan,
                        contentColor = Color(0xFF03201D),
                        disabledContainerColor = Color(0xFF1A2530),
                        disabledContentColor = Color(0xFF637285),
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Text("语音输入", fontWeight = FontWeight.Bold)
                }

                if (isSpeaking) {
                    OutlinedButton(
                        onClick = onStopSpeaking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFFFF7A95)),
                    ) {
                        Text("停止播报", color = Color(0xFFFF9AAB), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSpeakLatest,
                        enabled = canSpeak,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (canSpeak) HudViolet.copy(alpha = 0.7f) else Color(0xFF252E3E),
                        ),
                    ) {
                        Text(
                            text = "朗读回复",
                            color = if (canSpeak) Color(0xFFE2DCFF) else Color(0xFF5C667A),
                        )
                    }
                }
            }
        }
    }
}
