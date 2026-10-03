package com.evax.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evax.mobile.presentation.AssistantPhase
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3 switchable eye styles inspired by DingTalk / QwenNote Eva:
 * - [EVA_MINT]: Iconic glowing mint-emerald jelly squircle eyes with wink & gold sparkle star.
 * - [CYBER_COZMO]: Neon cyan/violet visor eyes with digital scanlines and ring pupils.
 * - [GOLDEN_MECHA]: Amber-gold ring pupils with side mecha HUD brackets.
 */
enum class EvaEyeStyle(val label: String) {
    EVA_MINT("翡翠灵眸"),
    CYBER_COZMO("赛博脉冲"),
    GOLDEN_MECHA("琥珀机甲"),
}

/**
 * 3 companion modes inspired by DingTalk / QwenNote Eva:
 * - [COMPANION]: Active companion ("我在呢，随时陪你聊~")
 * - [DND]: Do-Not-Disturb diving goggles with live digital clock ("潜水中，我听不到你说话~")
 * - [REST]: Sleeping crescent eyes with floating musical notes ("打个盹，有事再叫我~")
 */
enum class EvaCompanionMode(val title: String, val subtitle: String) {
    COMPANION("陪伴", "我在呢，随时陪你聊~"),
    DND("勿扰", "潜水中，我听不到你说话~"),
    REST("静息", "打个盹，有事再叫我~"),
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
) {
    val transition = rememberInfiniteTransition(label = "eva-avatar-transition")
    val textMeasurer = rememberTextMeasurer()

    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )

    val fastPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fastPulse",
    )

    val blinkTicker by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blinkTicker",
    )

    var touchGaze by remember { mutableStateOf<Offset?>(null) }
    var winkTriggered by remember { mutableStateOf(false) }
    var showCompletionSmile by remember { mutableStateOf(false) }

    // Automatically clear touchGaze after 2.2s so camera face tracking is never permanently blocked
    LaunchedEffect(touchGaze) {
        if (touchGaze != null) {
            delay(2_200L)
            touchGaze = null
        }
    }

    // Show happy completion smile (^ ^) for 2.4s when task finishes, then return to expressive tracking eyes
    LaunchedEffect(phase) {
        if (phase == AssistantPhase.COMPLETED) {
            showCompletionSmile = true
            delay(2_400L)
            showCompletionSmile = false
        } else {
            showCompletionSmile = false
        }
    }

    val primaryAccent by animateColorAsState(
        targetValue = when {
            companionMode == EvaCompanionMode.DND -> Color(0xFF4AD8FF)
            eyeStyle == EvaEyeStyle.EVA_MINT && (phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED) -> Color(0xFF4AF5A8)
            eyeStyle == EvaEyeStyle.EVA_MINT && phase == AssistantPhase.LISTENING -> Color(0xFF3BF8B5)
            eyeStyle == EvaEyeStyle.GOLDEN_MECHA && (phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED) -> Color(0xFFFFC847)
            else -> phaseAccentColor(phase)
        },
        animationSpec = tween(durationMillis = 360),
        label = "primaryAccent",
    )

    val secondaryAccent by animateColorAsState(
        targetValue = when {
            companionMode == EvaCompanionMode.DND -> Color(0xFF0096C7)
            eyeStyle == EvaEyeStyle.EVA_MINT -> Color(0xFF12C97D)
            eyeStyle == EvaEyeStyle.GOLDEN_MECHA -> Color(0xFFFF8811)
            phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> Color(0xFF7B61FF)
            phase == AssistantPhase.ERROR -> Color(0xFFFF477E)
            else -> Color(0xFF00B4D8)
        },
        animationSpec = tween(durationMillis = 360),
        label = "secondaryAccent",
    )

    // Always follow face in ALL modes and ALL phases!
    val activeFaceGaze = touchGaze ?: faceOffset

    val targetGazeX = when {
        activeFaceGaze != null -> activeFaceGaze.x.coerceIn(-0.85f, 0.85f)
        phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> 0.30f * sin(cycle * 2f)
        phase == AssistantPhase.LISTENING -> 0.12f * sin(cycle * 2f)
        else -> -0.18f + 0.34f * sin(cycle)
    }
    val targetGazeY = when {
        activeFaceGaze != null -> activeFaceGaze.y.coerceIn(-0.68f, 0.68f)
        phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> -0.22f
        else -> -0.04f + 0.12f * cos(cycle)
    }
    val targetTiltDeg = when {
        activeFaceGaze != null -> activeFaceGaze.x * -8.5f
        phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED -> -3.2f + 2.5f * sin(cycle)
        phase == AssistantPhase.LISTENING -> 1.8f * sin(cycle * 2f)
        phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> -2.8f
        phase == AssistantPhase.ERROR -> 3.5f * sin(cycle * 6f)
        else -> 0f
    }

    val gazeX by animateFloatAsState(
        targetValue = targetGazeX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gazeX",
    )
    val gazeY by animateFloatAsState(
        targetValue = targetGazeY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gazeY",
    )
    val headTilt by animateFloatAsState(
        targetValue = targetTiltDeg,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headTilt",
    )

    val autoBlinkScale = if (blinkTicker in 92f..96.5f) {
        val dist = abs(blinkTicker - 94.25f) / 2.25f
        (0.08f + 0.92f * dist).coerceIn(0.08f, 1f)
    } else {
        1f
    }

    val isPlayfulWink = winkTriggered ||
        ((phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED) && blinkTicker in 44f..54f && activeFaceGaze == null)

    val timeText = remember(blinkTicker.toInt() / 20) {
        SimpleDateFormat("HH : mm", Locale.getDefault()).format(Date())
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(diameter)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        touchGaze = null
                        winkTriggered = !winkTriggered
                    },
                    onTap = { tapOffset ->
                        val normX = ((tapOffset.x / size.width) - 0.5f) * 1.5f
                        val normY = ((tapOffset.y / size.height) - 0.5f) * 1.3f
                        touchGaze = Offset(
                            normX.coerceIn(-0.78f, 0.78f),
                            normY.coerceIn(-0.58f, 0.58f),
                        )
                    },
                )
            }
            .semantics { contentDescription = phaseLabel(phase) },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val visorWidth = (size.width * if (isLandscape) 0.84f else 0.88f)
            .coerceAtMost(size.height * if (isLandscape) 2.25f else 1.85f)
        val visorHeight = size.height * 0.90f

        val baseEyeW = visorWidth * if (isLandscape) 0.31f else 0.34f
        val baseEyeH = visorHeight * if (isLandscape) 0.68f else 0.64f
        val eyeSpacing = visorWidth * 0.22f

        // 3D perspective scaling when looking left/right toward user's face
        val leftPerspective = (1f - gazeX * 0.22f).coerceIn(0.78f, 1.22f)
        val rightPerspective = (1f + gazeX * 0.22f).coerceIn(0.78f, 1.22f)

        val gazeShiftX = gazeX * baseEyeW * 0.46f
        val gazeShiftY = gazeY * baseEyeH * 0.38f

        // 1. Ambient outer glow (shifts slightly with gaze)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryAccent.copy(alpha = if (isLandscape) 0.16f else 0.22f + 0.05f * fastPulse),
                    secondaryAccent.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                center = center + Offset(gazeShiftX * 0.5f, gazeShiftY * 0.5f),
                radius = visorWidth * 0.62f,
            ),
            radius = visorWidth * 0.62f,
            center = center + Offset(gazeShiftX * 0.5f, gazeShiftY * 0.5f),
        )

        // 2. Optional visor shell in Cyber Cozmo portrait mode
        if (!isLandscape && eyeStyle == EvaEyeStyle.CYBER_COZMO) {
            val visorRect = Rect(
                left = center.x - visorWidth / 2f,
                top = center.y - visorHeight / 2f,
                right = center.x + visorWidth / 2f,
                bottom = center.y + visorHeight / 2f,
            )
            val visorCorner = CornerRadius(visorHeight * 0.34f, visorHeight * 0.34f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0E16), Color(0xFF05070B)),
                    startY = visorRect.top,
                    endY = visorRect.bottom,
                ),
                topLeft = visorRect.topLeft,
                size = visorRect.size,
                cornerRadius = visorCorner,
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = 0.45f),
                        secondaryAccent.copy(alpha = 0.20f),
                        primaryAccent.copy(alpha = 0.40f),
                    ),
                    start = visorRect.topLeft,
                    end = visorRect.bottomRight,
                ),
                topLeft = visorRect.topLeft,
                size = visorRect.size,
                cornerRadius = visorCorner,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        if (eyeStyle == EvaEyeStyle.GOLDEN_MECHA) {
            drawMechaSideBrackets(
                center = center + Offset(gazeShiftX * 0.3f, gazeShiftY * 0.3f),
                visorWidth = visorWidth,
                visorHeight = visorHeight,
                accent = Color(0xFF7FA6C9),
            )
        }

        withTransform({
            rotate(degrees = headTilt, pivot = center)
        }) {
            val shiftedCenter = Offset(center.x + gazeShiftX, center.y + gazeShiftY)
            val leftCenter = Offset(
                x = center.x - eyeSpacing + gazeShiftX,
                y = center.y + gazeShiftY,
            )
            val rightCenter = Offset(
                x = center.x + eyeSpacing + gazeShiftX,
                y = center.y + gazeShiftY,
            )

            // Handle 3 Companion Modes inside the gaze-tracking transform so ALL modes follow user's face!
            when (companionMode) {
                EvaCompanionMode.DND -> {
                    drawDndDivingGogglesClock(
                        center = shiftedCenter,
                        visorWidth = visorWidth,
                        visorHeight = visorHeight,
                        timeText = timeText,
                        textMeasurer = textMeasurer,
                        pulse = fastPulse,
                        cycle = cycle,
                    )
                    return@withTransform
                }

                EvaCompanionMode.REST -> {
                    drawRestSleepingFace(
                        leftCenter = leftCenter,
                        rightCenter = rightCenter,
                        eyeWidth = baseEyeW * 0.82f,
                        eyeHeight = baseEyeH * 0.38f,
                        leftScale = leftPerspective,
                        rightScale = rightPerspective,
                        accent = Color(0xFF4AF5A8),
                        cycle = cycle,
                        textMeasurer = textMeasurer,
                    )
                    return@withTransform
                }

                EvaCompanionMode.COMPANION -> Unit
            }

            if ((phase == AssistantPhase.COMPLETED && showCompletionSmile) || isSpeaking) {
                drawHappyCompletedFace(
                    leftCenter = leftCenter,
                    rightCenter = rightCenter,
                    faceCenter = shiftedCenter,
                    eyeWidth = baseEyeW * 0.80f,
                    eyeHeight = baseEyeH * 0.48f,
                    leftScale = leftPerspective,
                    rightScale = rightPerspective,
                    accent = primaryAccent,
                    pulse = fastPulse,
                    isSpeaking = isSpeaking,
                )
            } else {
                val leftHeightScale = when (phase) {
                    AssistantPhase.THINKING, AssistantPhase.EXECUTING -> 0.68f
                    AssistantPhase.LISTENING -> 1.06f
                    AssistantPhase.ERROR -> 0.82f
                    else -> 1.0f
                } * autoBlinkScale

                val rightHeightScale = when (phase) {
                    AssistantPhase.THINKING, AssistantPhase.EXECUTING -> 0.96f
                    AssistantPhase.LISTENING -> 1.06f
                    AssistantPhase.ERROR -> 0.82f
                    else -> 0.96f
                } * autoBlinkScale

                when (eyeStyle) {
                    EvaEyeStyle.EVA_MINT -> {
                        drawEvaMintJellyEye(
                            center = leftCenter,
                            width = baseEyeW * leftPerspective,
                            height = baseEyeH * leftPerspective * leftHeightScale,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            primary = primaryAccent,
                            secondary = secondaryAccent,
                            phase = phase,
                            isLeft = true,
                            isWinking = false,
                            pulse = fastPulse,
                        )

                        drawEvaMintJellyEye(
                            center = rightCenter,
                            width = baseEyeW * rightPerspective,
                            height = baseEyeH * rightPerspective * rightHeightScale,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            primary = primaryAccent,
                            secondary = secondaryAccent,
                            phase = phase,
                            isLeft = false,
                            isWinking = isPlayfulWink && (phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED),
                            pulse = fastPulse,
                        )
                    }

                    EvaEyeStyle.CYBER_COZMO -> {
                        drawCyberSquircleEye(
                            center = leftCenter,
                            width = baseEyeW * leftPerspective,
                            height = baseEyeH * leftPerspective * leftHeightScale,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            primary = primaryAccent,
                            secondary = secondaryAccent,
                            phase = phase,
                            isLeft = true,
                            scanProgress = fastPulse,
                        )

                        drawCyberSquircleEye(
                            center = rightCenter,
                            width = baseEyeW * rightPerspective,
                            height = baseEyeH * rightPerspective * rightHeightScale,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            primary = primaryAccent,
                            secondary = secondaryAccent,
                            phase = phase,
                            isLeft = false,
                            scanProgress = fastPulse,
                        )
                    }

                    EvaEyeStyle.GOLDEN_MECHA -> {
                        drawGoldenMechaEye(
                            center = leftCenter,
                            radius = (baseEyeW.coerceAtMost(baseEyeH) * 0.48f) * leftPerspective,
                            heightScale = leftHeightScale,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            primary = primaryAccent,
                            secondary = secondaryAccent,
                        )
                        drawGoldenMechaEye(
                            center = rightCenter,
                            radius = (baseEyeW.coerceAtMost(baseEyeH) * 0.48f) * rightPerspective,
                            heightScale = rightHeightScale,
                            gazeX = gazeX,
                            gazeY = gazeY,
                            primary = primaryAccent,
                            secondary = secondaryAccent,
                        )
                    }
                }

                if (phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING) {
                    drawThinkingEyebrows(
                        leftCenter = leftCenter,
                        rightCenter = rightCenter,
                        eyeWidth = baseEyeW,
                        eyeHeight = baseEyeH,
                        accent = primaryAccent,
                    )
                } else if (phase == AssistantPhase.ERROR) {
                    drawErrorEyebrowsAndSweatDrops(
                        leftCenter = leftCenter,
                        rightCenter = rightCenter,
                        eyeWidth = baseEyeW,
                        eyeHeight = baseEyeH,
                        accent = primaryAccent,
                        pulse = fastPulse,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawEvaMintJellyEye(
    center: Offset,
    width: Float,
    height: Float,
    gazeX: Float,
    gazeY: Float,
    primary: Color,
    secondary: Color,
    phase: AssistantPhase,
    isLeft: Boolean,
    isWinking: Boolean,
    pulse: Float,
) {
    if (isWinking) {
        val winkPath = Path().apply {
            moveTo(center.x - width * 0.48f, center.y + height * 0.14f)
            quadraticTo(
                center.x + width * 0.04f,
                center.y - height * 0.26f,
                center.x + width * 0.48f,
                center.y + height * 0.16f,
            )
            quadraticTo(
                center.x + width * 0.02f,
                center.y + height * 0.04f,
                center.x - width * 0.48f,
                center.y + height * 0.14f,
            )
            close()
        }
        drawPath(
            path = winkPath,
            color = primary.copy(alpha = 0.28f),
            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = winkPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF80FFD4), primary, secondary),
                startY = center.y - height * 0.25f,
                endY = center.y + height * 0.25f,
            ),
        )
        drawPath(
            path = winkPath,
            color = primary,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val starCenter = Offset(
            x = center.x + width * 0.62f,
            y = center.y - height * 0.24f,
        )
        val starSize = width * (0.20f + 0.04f * pulse)
        drawFourPointStar(
            center = starCenter,
            radius = starSize,
            color = Color(0xFFFFE066),
        )
        return
    }

    val safeHeight = height.coerceAtLeast(width * 0.14f)
    val rect = Rect(
        left = center.x - width / 2f,
        top = center.y - safeHeight / 2f,
        right = center.x + width / 2f,
        bottom = center.y + safeHeight / 2f,
    )
    val corner = CornerRadius(width * 0.38f, width * 0.38f)

    drawRoundRect(
        color = primary.copy(alpha = 0.15f + 0.05f * pulse),
        topLeft = rect.topLeft - Offset(10.dp.toPx(), 10.dp.toPx()),
        size = Size(rect.width + 20.dp.toPx(), rect.height + 20.dp.toPx()),
        cornerRadius = CornerRadius(width * 0.44f, width * 0.44f),
    )
    drawRoundRect(
        color = primary.copy(alpha = 0.28f),
        topLeft = rect.topLeft - Offset(4.dp.toPx(), 4.dp.toPx()),
        size = Size(rect.width + 8.dp.toPx(), rect.height + 8.dp.toPx()),
        cornerRadius = CornerRadius(width * 0.40f, width * 0.40f),
    )

    val eyePath = Path().apply {
        addRoundRect(RoundRect(rect, corner))
    }
    drawPath(
        path = eyePath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF75FFCA),
                primary,
                secondary,
            ),
            startY = rect.top,
            endY = rect.bottom,
        ),
    )

    clipPath(eyePath) {
        // Parallax inner jelly core highlight that shifts with gazeX / gazeY
        val highlightCenter = Offset(
            x = rect.center.x + (gazeX * width * 0.22f) - width * 0.08f,
            y = rect.top + safeHeight * 0.28f + (gazeY * safeHeight * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.Transparent,
                ),
                center = highlightCenter,
                radius = width * 0.46f,
            ),
            radius = width * 0.46f,
            center = highlightCenter,
        )

        if (phase == AssistantPhase.ERROR) {
            val sadMask = Path().apply {
                moveTo(rect.left - 10f, rect.top - 10f)
                lineTo(rect.right + 10f, rect.top - 10f)
                if (isLeft) {
                    lineTo(rect.right + 10f, rect.top + safeHeight * 0.08f)
                    lineTo(rect.left - 10f, rect.top + safeHeight * 0.36f)
                } else {
                    lineTo(rect.right + 10f, rect.top + safeHeight * 0.36f)
                    lineTo(rect.left - 10f, rect.top + safeHeight * 0.08f)
                }
                close()
            }
            drawPath(sadMask, color = Color(0xFF050608))
        } else if (phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING) {
            if (isLeft) {
                val thinkMask = Path().apply {
                    moveTo(rect.left - 10f, rect.top - 10f)
                    lineTo(rect.right + 10f, rect.top - 10f)
                    lineTo(rect.right + 10f, rect.top + safeHeight * 0.28f)
                    lineTo(rect.left - 10f, rect.top + safeHeight * 0.14f)
                    close()
                }
                drawPath(thinkMask, color = Color(0xFF050608))
            }
        }
    }
}

private fun DrawScope.drawFourPointStar(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val inner = radius * 0.26f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + inner, center.y - inner, center.x + radius, center.y)
        quadraticTo(center.x + inner, center.y + inner, center.x, center.y + radius)
        quadraticTo(center.x - inner, center.y + inner, center.x - radius, center.y)
        quadraticTo(center.x - inner, center.y - inner, center.x, center.y - radius)
        close()
    }
    drawCircle(
        color = color.copy(alpha = 0.28f),
        radius = radius * 1.25f,
        center = center,
    )
    drawPath(path = path, color = color)
}

private fun DrawScope.drawDndDivingGogglesClock(
    center: Offset,
    visorWidth: Float,
    visorHeight: Float,
    timeText: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    pulse: Float,
    cycle: Float,
) {
    val goggleW = visorWidth * 0.72f
    val goggleH = visorHeight * 0.56f
    val goggleRect = Rect(
        left = center.x - goggleW / 2f,
        top = center.y - goggleH / 2f,
        right = center.x + goggleW / 2f,
        bottom = center.y + goggleH / 2f,
    )
    val cyanFrame = Color(0xFF5CE1FF)

    drawRoundRect(
        color = Color(0xFF287C9E),
        topLeft = Offset(goggleRect.left - 16.dp.toPx(), center.y - goggleH * 0.22f),
        size = Size(18.dp.toPx(), goggleH * 0.44f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
    )

    val snorkelX = goggleRect.right + 14.dp.toPx()
    val snorkelPath = Path().apply {
        moveTo(snorkelX, center.y - goggleH * 0.58f)
        lineTo(snorkelX, center.y + goggleH * 0.24f)
        quadraticTo(
            snorkelX,
            center.y + goggleH * 0.42f,
            snorkelX - 18.dp.toPx(),
            center.y + goggleH * 0.42f,
        )
    }
    drawPath(
        path = snorkelPath,
        color = Color(0xFF90EEFF),
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    val corner = CornerRadius(goggleH * 0.46f, goggleH * 0.46f)
    drawRoundRect(
        color = Color(0xFF071521),
        topLeft = goggleRect.topLeft,
        size = goggleRect.size,
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF9BF0FF), cyanFrame, Color(0xFF2B99C2)),
        ),
        topLeft = goggleRect.topLeft,
        size = goggleRect.size,
        cornerRadius = corner,
        style = Stroke(width = 7.dp.toPx()),
    )

    val style = TextStyle(
        color = Color.White,
        fontSize = (goggleH * 0.34f).toSp(),
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
    val layout = textMeasurer.measure(timeText, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = center.x - layout.size.width / 2f,
            y = center.y - layout.size.height / 2f,
        ),
    )

    val bubbleOffsetY = (sin(cycle) * 8.dp.toPx())
    drawCircle(
        color = cyanFrame.copy(alpha = 0.65f),
        radius = 6.dp.toPx() * (0.85f + 0.2f * pulse),
        center = Offset(snorkelX + 8.dp.toPx(), goggleRect.top - 12.dp.toPx() + bubbleOffsetY),
    )
    drawCircle(
        color = cyanFrame.copy(alpha = 0.45f),
        radius = 4.dp.toPx(),
        center = Offset(snorkelX - 6.dp.toPx(), goggleRect.top - 24.dp.toPx() - bubbleOffsetY * 0.6f),
    )
}

private fun DrawScope.drawRestSleepingFace(
    leftCenter: Offset,
    rightCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    leftScale: Float,
    rightScale: Float,
    accent: Color,
    cycle: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    fun drawSleepingArc(c: Offset, scale: Float) {
        val w = eyeWidth * scale
        val h = eyeHeight * scale
        val path = Path().apply {
            moveTo(c.x - w * 0.5f, c.y - h * 0.1f)
            quadraticTo(
                c.x,
                c.y + h * 0.65f,
                c.x + w * 0.5f,
                c.y - h * 0.1f,
            )
        }
        drawPath(
            path = path,
            color = accent.copy(alpha = 0.24f),
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    drawSleepingArc(leftCenter, leftScale)
    drawSleepingArc(rightCenter, rightScale)

    val floatY = sin(cycle) * 7.dp.toPx()
    val noteStyle = TextStyle(
        color = Color(0xFF68B5FF),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
    val note1 = textMeasurer.measure("♪", noteStyle)
    val note2 = textMeasurer.measure("♫", noteStyle)
    drawText(
        textLayoutResult = note1,
        topLeft = Offset(leftCenter.x - eyeWidth * 0.6f, leftCenter.y - eyeHeight * 1.3f + floatY),
    )
    drawText(
        textLayoutResult = note2,
        topLeft = Offset(rightCenter.x + eyeWidth * 0.35f, rightCenter.y - eyeHeight * 1.5f - floatY),
    )
}

private fun DrawScope.drawGoldenMechaEye(
    center: Offset,
    radius: Float,
    heightScale: Float,
    gazeX: Float,
    gazeY: Float,
    primary: Color,
    secondary: Color,
) {
    val safeScaleY = heightScale.coerceIn(0.12f, 1.15f)
    withTransform({
        scale(scaleX = 1f, scaleY = safeScaleY, pivot = center)
    }) {
        drawCircle(
            color = primary.copy(alpha = 0.20f),
            radius = radius * 1.18f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF3B0), primary, secondary),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        val pupilCenter = center + Offset(gazeX * radius * 0.26f, gazeY * radius * 0.26f)
        drawCircle(
            color = Color(0xFF0A0D12),
            radius = radius * 0.56f,
            center = pupilCenter,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = radius * 0.14f,
            center = pupilCenter + Offset(-radius * 0.22f, -radius * 0.22f),
        )
    }
}

private fun DrawScope.drawMechaSideBrackets(
    center: Offset,
    visorWidth: Float,
    visorHeight: Float,
    accent: Color,
) {
    val bracketW = visorWidth * 0.08f
    val bracketH = visorHeight * 0.34f
    val leftX = center.x - visorWidth * 0.46f
    val rightX = center.x + visorWidth * 0.46f - bracketW
    val topY = center.y - bracketH / 2f

    for (x in listOf(leftX, rightX)) {
        drawRoundRect(
            color = accent.copy(alpha = 0.28f),
            topLeft = Offset(x, topY),
            size = Size(bracketW, bracketH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = accent.copy(alpha = 0.75f),
            start = Offset(x - 4.dp.toPx(), center.y),
            end = Offset(x + bracketW + 4.dp.toPx(), center.y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCyberSquircleEye(
    center: Offset,
    width: Float,
    height: Float,
    gazeX: Float,
    gazeY: Float,
    primary: Color,
    secondary: Color,
    phase: AssistantPhase,
    isLeft: Boolean,
    scanProgress: Float,
) {
    val safeHeight = height.coerceAtLeast(width * 0.12f)
    val outerRect = Rect(
        left = center.x - width / 2f,
        top = center.y - safeHeight / 2f,
        right = center.x + width / 2f,
        bottom = center.y + safeHeight / 2f,
    )
    val outerCorner = CornerRadius(width * 0.34f, width * 0.34f)

    drawRoundRect(
        color = primary.copy(alpha = 0.18f),
        topLeft = outerRect.topLeft - Offset(6.dp.toPx(), 6.dp.toPx()),
        size = Size(outerRect.width + 12.dp.toPx(), outerRect.height + 12.dp.toPx()),
        cornerRadius = CornerRadius(width * 0.38f, width * 0.38f),
    )

    drawRoundRect(
        color = Color(0xFF09141A),
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        cornerRadius = outerCorner,
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                primary.copy(alpha = 0.88f),
                secondary.copy(alpha = 0.45f),
                primary.copy(alpha = 0.75f),
            ),
            start = outerRect.topLeft,
            end = outerRect.bottomRight,
        ),
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        cornerRadius = outerCorner,
        style = Stroke(width = 2.6.dp.toPx()),
    )

    val irisW = width * 0.74f
    val irisH = (safeHeight * 0.74f).coerceAtLeast(width * 0.08f)
    val irisOffset = Offset(gazeX * width * 0.10f, gazeY * safeHeight * 0.10f)
    val irisCenter = center + irisOffset
    val irisRect = Rect(
        left = irisCenter.x - irisW / 2f,
        top = irisCenter.y - irisH / 2f,
        right = irisCenter.x + irisW / 2f,
        bottom = irisCenter.y + irisH / 2f,
    )
    val irisCorner = CornerRadius(irisW * 0.34f, irisW * 0.34f)
    val irisPath = Path().apply {
        addRoundRect(RoundRect(irisRect, irisCorner))
    }

    drawPath(
        path = irisPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                primary,
                secondary.copy(alpha = 0.92f),
            ),
            startY = irisRect.top,
            endY = irisRect.bottom,
        ),
    )

    clipPath(irisPath) {
        val lineCount = 12
        val stepY = irisRect.height / lineCount
        for (i in 0..lineCount) {
            val y = irisRect.top + i * stepY
            drawLine(
                color = Color(0xFF041014).copy(alpha = 0.20f),
                start = Offset(irisRect.left, y),
                end = Offset(irisRect.right, y),
                strokeWidth = 1.2.dp.toPx(),
            )
        }

        if (safeHeight > width * 0.28f) {
            val pupilW = irisW * if (phase == AssistantPhase.LISTENING) 0.42f else 0.46f
            val pupilH = irisH * if (phase == AssistantPhase.LISTENING) 0.42f else 0.48f
            val pupilCenter = irisCenter + Offset(gazeX * irisW * 0.14f, gazeY * irisH * 0.14f)
            val pupilRect = Rect(
                left = pupilCenter.x - pupilW / 2f,
                top = pupilCenter.y - pupilH / 2f,
                right = pupilCenter.x + pupilW / 2f,
                bottom = pupilCenter.y + pupilH / 2f,
            )
            drawRoundRect(
                color = Color(0xFF071318).copy(alpha = 0.88f),
                topLeft = pupilRect.topLeft,
                size = pupilRect.size,
                cornerRadius = CornerRadius(pupilW * 0.36f, pupilW * 0.36f),
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.78f),
                radius = pupilW * 0.13f,
                center = Offset(
                    pupilRect.left + pupilW * 0.24f,
                    pupilRect.top + pupilH * 0.24f,
                ),
            )
        }

        if (phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING) {
            if (isLeft) {
                val lidPath = Path().apply {
                    moveTo(irisRect.left - 10f, irisRect.top - 10f)
                    lineTo(irisRect.right + 10f, irisRect.top - 10f)
                    lineTo(irisRect.right + 10f, irisRect.top + irisRect.height * 0.34f)
                    lineTo(irisRect.left - 10f, irisRect.top + irisRect.height * 0.18f)
                    close()
                }
                drawPath(lidPath, color = Color(0xFF09101A))
            }
            val scanY = irisRect.top + irisRect.height * scanProgress
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(irisRect.left, scanY),
                end = Offset(irisRect.right, scanY),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

private fun DrawScope.drawThinkingEyebrows(
    leftCenter: Offset,
    rightCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    accent: Color,
) {
    val leftBrow = Path().apply {
        moveTo(leftCenter.x - eyeWidth * 0.34f, leftCenter.y - eyeHeight * 0.36f)
        quadraticTo(
            leftCenter.x,
            leftCenter.y - eyeHeight * 0.39f,
            leftCenter.x + eyeWidth * 0.34f,
            leftCenter.y - eyeHeight * 0.31f,
        )
    }
    drawPath(
        path = leftBrow,
        color = accent.copy(alpha = 0.9f),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
    )

    val rightBrow = Path().apply {
        moveTo(rightCenter.x - eyeWidth * 0.32f, rightCenter.y - eyeHeight * 0.42f)
        quadraticTo(
            rightCenter.x + eyeWidth * 0.04f,
            rightCenter.y - eyeHeight * 0.56f,
            rightCenter.x + eyeWidth * 0.36f,
            rightCenter.y - eyeHeight * 0.40f,
        )
    }
    drawPath(
        path = rightBrow,
        color = accent.copy(alpha = 0.9f),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawErrorEyebrowsAndSweatDrops(
    leftCenter: Offset,
    rightCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    accent: Color,
    pulse: Float,
) {
    val leftBrow = Path().apply {
        moveTo(leftCenter.x - eyeWidth * 0.34f, leftCenter.y - eyeHeight * 0.30f)
        lineTo(leftCenter.x + eyeWidth * 0.30f, leftCenter.y - eyeHeight * 0.44f)
    }
    val rightBrow = Path().apply {
        moveTo(rightCenter.x - eyeWidth * 0.30f, rightCenter.y - eyeHeight * 0.44f)
        lineTo(rightCenter.x + eyeWidth * 0.34f, rightCenter.y - eyeHeight * 0.30f)
    }
    drawPath(leftBrow, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    drawPath(rightBrow, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

    val dropShift = pulse * 4.dp.toPx()
    drawSweatDrop(
        top = Offset(leftCenter.x - eyeWidth * 0.62f, leftCenter.y - eyeHeight * 0.25f + dropShift),
        height = 12.dp.toPx(),
    )
    drawSweatDrop(
        top = Offset(rightCenter.x + eyeWidth * 0.62f, rightCenter.y - eyeHeight * 0.18f + dropShift),
        height = 12.dp.toPx(),
    )
}

private fun DrawScope.drawSweatDrop(top: Offset, height: Float) {
    val w = height * 0.56f
    val path = Path().apply {
        moveTo(top.x, top.y)
        quadraticTo(top.x + w, top.y + height * 0.65f, top.x, top.y + height)
        quadraticTo(top.x - w, top.y + height * 0.65f, top.x, top.y)
        close()
    }
    drawPath(path, color = Color.White.copy(alpha = 0.88f))
}

private fun DrawScope.drawHappyCompletedFace(
    leftCenter: Offset,
    rightCenter: Offset,
    faceCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    leftScale: Float,
    rightScale: Float,
    accent: Color,
    pulse: Float,
    isSpeaking: Boolean,
) {
    fun drawHappyEye(c: Offset, scale: Float) {
        val w = eyeWidth * scale
        val h = eyeHeight * scale
        val path = Path().apply {
            moveTo(c.x - w * 0.46f, c.y + h * 0.22f)
            quadraticTo(
                c.x,
                c.y - h * 0.58f,
                c.x + w * 0.46f,
                c.y + h * 0.22f,
            )
        }
        drawPath(
            path = path,
            color = accent.copy(alpha = 0.28f),
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    drawHappyEye(leftCenter.copy(y = faceCenter.y - eyeHeight * 0.12f), leftScale)
    drawHappyEye(rightCenter.copy(y = faceCenter.y - eyeHeight * 0.12f), rightScale)

    val mouthOpen = if (isSpeaking) (0.35f + 0.45f * pulse) else 0.42f
    val mouthW = eyeWidth * 0.54f
    val mouthY = faceCenter.y + eyeHeight * 0.42f
    val mouthPath = Path().apply {
        moveTo(faceCenter.x - mouthW / 2f, mouthY)
        quadraticTo(
            faceCenter.x,
            mouthY + eyeHeight * mouthOpen,
            faceCenter.x + mouthW / 2f,
            mouthY,
        )
    }
    drawPath(
        path = mouthPath,
        color = accent.copy(alpha = 0.26f),
        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        path = mouthPath,
        color = accent,
        style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round),
    )
}

@Composable
fun SymmetricAudioWaveform(
    phase: AssistantPhase,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "audio-wave")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    val accent = phaseAccentColor(phase)
    val active = phase == AssistantPhase.LISTENING ||
        phase == AssistantPhase.THINKING ||
        phase == AssistantPhase.EXECUTING ||
        isSpeaking

    Canvas(
        modifier = modifier
            .width(84.dp)
            .height(24.dp),
    ) {
        val barCount = 11
        val spacing = size.width / (barCount + 1)
        val centerY = size.height / 2f
        val centerIdx = barCount / 2f

        for (i in 0 until barCount) {
            val distFromCenter = 1f - (abs(i - centerIdx) / (centerIdx + 1f))
            val dynamicFactor = if (active) {
                0.35f + 0.65f * abs(sin(wavePhase + i * 0.7f))
            } else {
                0.28f + 0.22f * abs(sin(wavePhase * 0.5f + i * 0.6f))
            }
            val barHeight = (size.height * distFromCenter * dynamicFactor).coerceAtLeast(3.dp.toPx())
            val x = spacing * (i + 1)
            val alpha = (0.25f + 0.75f * distFromCenter).coerceIn(0.2f, 1f)

            drawLine(
                color = accent.copy(alpha = alpha),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

internal fun phaseAccentColor(phase: AssistantPhase): Color = when (phase) {
    AssistantPhase.IDLE -> Color(0xFF4AF5A8)
    AssistantPhase.LISTENING -> Color(0xFF2AFADF)
    AssistantPhase.THINKING, AssistantPhase.EXECUTING -> Color(0xFFB9A9FF)
    AssistantPhase.COMPLETED -> Color(0xFF5CE6B0)
    AssistantPhase.ERROR -> Color(0xFFFF7A95)
}

internal fun phaseLabel(phase: AssistantPhase): String = when (phase) {
    AssistantPhase.IDLE -> "准备就绪"
    AssistantPhase.LISTENING -> "正在聆听"
    AssistantPhase.THINKING -> "正在思考"
    AssistantPhase.EXECUTING -> "正在执行"
    AssistantPhase.COMPLETED -> "演示完成"
    AssistantPhase.ERROR -> "遇到一点问题"
}
