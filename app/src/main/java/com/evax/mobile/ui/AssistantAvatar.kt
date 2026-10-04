package com.evax.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evax.mobile.presentation.AssistantPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Existing saved style values stay valid; each palette uses the same quiet Cozmo silhouette. */
enum class EvaEyeStyle(val label: String) {
    EVA_MINT("薄荷微光"),
    CYBER_COZMO("青绿灵眸"),
    GOLDEN_MECHA("暖琥珀"),
}

enum class EvaCompanionMode(val title: String, val subtitle: String) {
    COMPANION("陪伴", "我在呢，随时陪你聊"),
    DND("勿扰", "暂停自动播报，仍可主动提问"),
    REST("静息", "轻触唤醒"),
}

@Composable
fun AssistantAvatar(
    phase: AssistantPhase,
    modifier: Modifier = Modifier,
    diameter: Dp = 188.dp,
    isSpeaking: Boolean = false,
    faceOffset: Offset? = null,
    eyeStyle: EvaEyeStyle = EvaEyeStyle.EVA_MINT,
    companionMode: EvaCompanionMode = EvaCompanionMode.COMPANION,
    isLandscape: Boolean = false,
    micLevel: Float = 0f,
    reduceMotion: Boolean = false,
    onBackgroundTap: (() -> Unit)? = null,
) {
    val blink = remember { Animatable(1f) }
    val wink = remember { Animatable(1f) }
    val errorShake = remember { Animatable(0f) }
    val scan = remember { Animatable(0f) }
    var idleGaze by remember { mutableStateOf(Offset.Zero) }
    var idleTilt by remember { mutableStateOf(0f) }
    var touchGaze by remember { mutableStateOf<Offset?>(null) }
    var tapSerial by remember { mutableIntStateOf(0) }
    var winkSerial by remember { mutableIntStateOf(0) }
    var handledWinkSerial by remember { mutableIntStateOf(0) }
    var completionSmile by remember { mutableStateOf(false) }
    val companionActive = companionMode == EvaCompanionMode.COMPANION
    val idle = phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED
    val taskExpressionActive = companionMode != EvaCompanionMode.REST &&
        (companionActive || phase != AssistantPhase.IDLE)

    // Pauses between gestures prevent the perpetual rocking of a looping demo animation.
    LaunchedEffect(phase, companionMode, reduceMotion) {
        idleGaze = Offset.Zero
        idleTilt = 0f
        if (!companionActive || !idle || reduceMotion) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(1_800L, 5_001L))
            idleGaze = if (Random.nextFloat() < 0.35f) {
                Offset.Zero
            } else {
                Offset(Random.nextFloat() * 0.72f - 0.36f, Random.nextFloat() * 0.24f - 0.12f)
            }
            idleTilt = if (Random.nextFloat() < 0.18f) Random.nextFloat() * 5f - 2.5f else 0f
        }
    }

    LaunchedEffect(phase, companionMode, reduceMotion) {
        blink.snapTo(1f)
        if (!companionActive || reduceMotion) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(2_500L, 6_501L))
            blink.animateTo(0.06f, tween(75, easing = FastOutSlowInEasing))
            blink.animateTo(1f, tween(135, easing = FastOutSlowInEasing))
            if (Random.nextFloat() < 0.1f) {
                delay(120)
                blink.animateTo(0.06f, tween(70))
                blink.animateTo(1f, tween(130))
            }
        }
    }

    LaunchedEffect(tapSerial, companionMode) {
        if (!companionActive) touchGaze = null
        if (tapSerial > 0 && touchGaze != null) {
            delay(2_000)
            touchGaze = null
        }
    }

    LaunchedEffect(winkSerial, companionMode, reduceMotion) {
        val newWink = winkSerial != handledWinkSerial
        handledWinkSerial = winkSerial
        wink.snapTo(1f)
        if (!newWink || !companionActive || reduceMotion) return@LaunchedEffect
        wink.animateTo(0.06f, tween(110))
        delay(250)
        wink.animateTo(1f, tween(180))
    }

    LaunchedEffect(phase, companionMode) {
        completionSmile = false
        if (phase == AssistantPhase.COMPLETED && taskExpressionActive) {
            completionSmile = true
            delay(1_600)
            completionSmile = false
        }
    }

    LaunchedEffect(phase, companionMode, reduceMotion) {
        errorShake.snapTo(0f)
        if (phase != AssistantPhase.ERROR || !taskExpressionActive || reduceMotion) return@LaunchedEffect
        for (position in listOf(-1f, 1f, -0.7f, 0.5f, 0f)) {
            errorShake.animateTo(position, tween(55))
        }
    }

    LaunchedEffect(phase, companionMode, reduceMotion) {
        scan.snapTo(0f)
        if (phase != AssistantPhase.EXECUTING || !taskExpressionActive || reduceMotion) return@LaunchedEffect
        while (isActive) {
            scan.snapTo(0f)
            scan.animateTo(1f, tween(2_100, easing = LinearEasing))
            delay(500)
        }
    }

    val cameraGaze = faceOffset?.takeIf { it.x.isFinite() && it.y.isFinite() }
    val stateGaze = when (phase) {
        AssistantPhase.THINKING -> Offset(0.16f, -0.25f)
        AssistantPhase.LISTENING, AssistantPhase.EXECUTING, AssistantPhase.ERROR -> Offset.Zero
        else -> idleGaze
    }
    val requestedGaze = when {
        !taskExpressionActive -> Offset.Zero
        touchGaze != null -> touchGaze!!
        cameraGaze != null -> {
            // Camera tracking steers attention without cancelling the state expression.
            val weight = if (idle) 1f else 0.45f
            Offset(
                stateGaze.x + cameraGaze.x.coerceIn(-0.65f, 0.65f) * weight,
                stateGaze.y + cameraGaze.y.coerceIn(-0.45f, 0.45f) * weight,
            )
        }
        else -> stateGaze
    }
    val attentionDuration = if (reduceMotion) 0 else 420
    val gazeX by animateFloatAsState(requestedGaze.x.coerceIn(-0.65f, 0.65f), tween(attentionDuration), label = "eye-gaze-x")
    val gazeY by animateFloatAsState(requestedGaze.y.coerceIn(-0.45f, 0.45f), tween(attentionDuration), label = "eye-gaze-y")
    val tiltTarget = when {
        !taskExpressionActive || reduceMotion -> 0f
        phase == AssistantPhase.THINKING -> -2f
        idle && cameraGaze == null && touchGaze == null -> idleTilt
        else -> 0f
    }
    val headTilt by animateFloatAsState(tiltTarget, tween(if (reduceMotion) 0 else 650), label = "eye-head-tilt")
    val opennessTarget = when {
        companionMode == EvaCompanionMode.DND && idle && !completionSmile -> 0.48f
        phase == AssistantPhase.THINKING -> 0.68f
        phase == AssistantPhase.EXECUTING -> 0.78f
        phase == AssistantPhase.LISTENING -> 1.08f
        phase == AssistantPhase.ERROR -> 0.76f
        else -> 1f
    }
    val openness by animateFloatAsState(opennessTarget, tween(if (reduceMotion) 0 else 260), label = "eye-openness")
    val smile by animateFloatAsState(if (completionSmile) 1f else 0f, tween(if (reduceMotion) 0 else 200), label = "eye-smile")
    val sadness by animateFloatAsState(if (phase == AssistantPhase.ERROR && taskExpressionActive) 1f else 0f, tween(if (reduceMotion) 0 else 260), label = "eye-sadness")
    val audioLevel = micLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val exposure by animateFloatAsState(
        targetValue = if (isSpeaking || phase == AssistantPhase.LISTENING) audioLevel else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 90),
        label = "eye-audio-exposure",
    )
    val accent = when (eyeStyle) {
        EvaEyeStyle.GOLDEN_MECHA -> Color(0xFFE5BC68)
        EvaEyeStyle.EVA_MINT, EvaEyeStyle.CYBER_COZMO -> Color(0xFF45E5CC)
    }
    val resting = companionMode == EvaCompanionMode.REST
    val semanticLabel = if (taskExpressionActive) phaseLabel(phase) else companionMode.title
    val currentBackgroundTap by rememberUpdatedState(onBackgroundTap)
    val currentEyeGaze by rememberUpdatedState(Offset(gazeX, gazeY))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = diameter)
            .pointerInput(companionMode, isLandscape) {
                fun touchesEye(position: Offset): Boolean {
                    val width = min(size.width * if (isLandscape) 0.255f else 0.27f, size.height * 0.72f)
                    val height = min(size.width * 0.31f, size.height * 0.70f)
                    val center = Offset(size.width / 2f, size.height / 2f) + Offset(
                        currentEyeGaze.x * width * 0.28f,
                        currentEyeGaze.y * height * 0.22f,
                    )
                    val distanceX = abs(position.x - center.x)
                    return abs(distanceX - width * 0.72f) <= width * 0.58f &&
                        abs(position.y - center.y) <= height * 0.60f
                }
                detectTapGestures(
                    onTap = { position ->
                        if (companionMode != EvaCompanionMode.COMPANION || !touchesEye(position)) {
                            currentBackgroundTap?.invoke()
                        } else {
                            touchGaze = Offset(
                                ((position.x / size.width) - 0.5f).coerceIn(-0.5f, 0.5f) * 1.3f,
                                ((position.y / size.height) - 0.5f).coerceIn(-0.5f, 0.5f) * 0.8f,
                            )
                            tapSerial++
                        }
                    },
                    onDoubleTap = { position ->
                        if (companionMode == EvaCompanionMode.COMPANION && touchesEye(position)) {
                            touchGaze = null
                            winkSerial++
                        } else {
                            currentBackgroundTap?.invoke()
                        }
                    },
                )
            }
            .semantics { contentDescription = "EVA 双眼，$semanticLabel" },
    ) {
        val eyeWidth = min(size.width * if (isLandscape) 0.255f else 0.27f, size.height * 0.72f)
        val eyeHeight = min(size.width * 0.31f, size.height * 0.70f)
        val spacing = eyeWidth * 0.72f
        val sceneCenter = Offset(size.width / 2f, size.height / 2f)
        val eyeCenter = sceneCenter + Offset(
            gazeX * eyeWidth * 0.28f + (if (taskExpressionActive) errorShake.value else 0f) * eyeWidth * 0.02f,
            gazeY * eyeHeight * 0.22f,
        )
        val intensity = when (companionMode) {
            EvaCompanionMode.REST -> 0.36f
            EvaCompanionMode.DND -> if (!idle || completionSmile) 0.91f + exposure * 0.09f else 0.60f
            EvaCompanionMode.COMPANION -> 0.91f + exposure * 0.09f
        }
        withTransform({ rotate(headTilt, sceneCenter) }) {
            for (index in 0..1) {
                val left = index == 0
                val perspective = (1f + gazeX * if (left) -0.09f else 0.09f).coerceIn(0.94f, 1.06f)
                val center = eyeCenter + Offset(if (left) -spacing else spacing, 0f)
                val eyeBlink = if (companionActive && !reduceMotion) {
                    blink.value * if (left) 1f else wink.value
                } else {
                    1f
                }
                val width = eyeWidth * perspective
                val height = eyeHeight * perspective * openness * eyeBlink
                val localTilt = sadness * if (left) -10f else 10f
                withTransform({ rotate(localTilt, center) }) {
                    if (resting) {
                        drawClosedEye(center, width * 0.90f, eyeHeight * 0.15f, accent, intensity, happy = false)
                    } else if (smile > 0.02f) {
                        drawMinimalEye(center, width, height, accent, intensity * (1f - smile), scan.value, phase == AssistantPhase.EXECUTING)
                        drawClosedEye(center, width * 0.90f, eyeHeight * 0.28f, accent, intensity * smile, happy = true)
                    } else {
                        drawMinimalEye(center, width, height, accent, intensity, scan.value, phase == AssistantPhase.EXECUTING && taskExpressionActive)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMinimalEye(
    center: Offset,
    width: Float,
    height: Float,
    accent: Color,
    intensity: Float,
    scan: Float,
    executing: Boolean,
) {
    if (intensity <= 0f) return
    val safeHeight = height.coerceAtLeast(width * 0.045f)
    val rect = Rect(center - Offset(width / 2f, safeHeight / 2f), Size(width, safeHeight))
    val corner = min(width * 0.29f, safeHeight / 2f)
    val outline = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner, corner))) }
    // The glow stays close to each silhouette so the surrounding OLED scene stays black.
    for ((expansion, alpha) in listOf(0.08f to 0.028f, 0.04f to 0.055f, 0.015f to 0.10f)) {
        val padding = width * expansion
        drawRoundRect(
            color = accent.copy(alpha = alpha * intensity),
            topLeft = rect.topLeft - Offset(padding, padding),
            size = Size(width + padding * 2f, safeHeight + padding * 2f),
            cornerRadius = CornerRadius(corner + padding, corner + padding),
        )
    }
    drawPath(
        path = outline,
        brush = Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = intensity), accent.copy(alpha = intensity * 0.83f)),
            startY = rect.top,
            endY = rect.bottom,
        ),
    )
    if (executing) {
        clipPath(outline) {
            val scanCenter = Offset(rect.left + width * scan, rect.center.y)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f * intensity), Color.Transparent),
                    center = scanCenter,
                    radius = width * 0.40f,
                ),
                radius = width * 0.40f,
                center = scanCenter,
            )
        }
    }
}

private fun DrawScope.drawClosedEye(
    center: Offset,
    width: Float,
    archHeight: Float,
    accent: Color,
    intensity: Float,
    happy: Boolean,
) {
    if (intensity <= 0f) return
    val direction = if (happy) -1f else 1f
    val path = Path().apply {
        moveTo(center.x - width / 2f, center.y + if (happy) archHeight * 0.24f else -archHeight * 0.24f)
        quadraticTo(center.x, center.y + direction * archHeight * 1.4f, center.x + width / 2f, center.y + if (happy) archHeight * 0.24f else -archHeight * 0.24f)
    }
    drawPath(path, accent.copy(alpha = intensity * 0.10f), style = Stroke(width * 0.16f, cap = StrokeCap.Round))
    drawPath(path, accent.copy(alpha = intensity), style = Stroke(width * 0.085f, cap = StrokeCap.Round))
}

/** The waveform receives measured audio levels; task phases never create fake microphone activity. */
@Composable
fun SymmetricAudioWaveform(
    phase: AssistantPhase,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    micLevel: Float = 0f,
    reduceMotion: Boolean = false,
) {
    val audioActive = phase == AssistantPhase.LISTENING || isSpeaking
    val measuredLevel = micLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val level by animateFloatAsState(
        targetValue = if (audioActive) measuredLevel else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 90),
        label = "measured-audio-level",
    )
    Canvas(modifier = modifier.width(84.dp).height(24.dp)) {
        val barCount = 11
        val spacing = size.width / (barCount + 1)
        val centerIndex = (barCount - 1) / 2f
        val accent = phaseAccentColor(phase)
        for (index in 0 until barCount) {
            val envelope = 1f - abs(index - centerIndex) / (centerIndex + 1f)
            val shape = 0.62f + 0.38f * abs(sin(index * 1.8f))
            val barHeight = maxOf(2.dp.toPx(), size.height * level * envelope * shape)
            val x = spacing * (index + 1)
            drawLine(
                color = accent.copy(alpha = if (audioActive) 0.42f + level * 0.45f else 0.20f),
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

internal fun phaseAccentColor(phase: AssistantPhase): Color = when (phase) {
    AssistantPhase.ERROR -> Color(0xFFF0A893)
    else -> Color(0xFF45E5CC)
}

internal fun phaseLabel(phase: AssistantPhase): String = when (phase) {
    AssistantPhase.IDLE -> "准备就绪"
    AssistantPhase.LISTENING -> "正在聆听"
    AssistantPhase.THINKING -> "正在思考"
    AssistantPhase.EXECUTING -> "正在执行"
    AssistantPhase.COMPLETED -> "已完成"
    AssistantPhase.ERROR -> "遇到一点问题"
}
