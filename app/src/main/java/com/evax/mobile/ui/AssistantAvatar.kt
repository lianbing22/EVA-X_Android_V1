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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evax.mobile.presentation.AssistantPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AssistantAvatar(
    phase: AssistantPhase,
    modifier: Modifier = Modifier,
    diameter: Dp = 188.dp,
    isSpeaking: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "eva-cyber-eyes")

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

    val primaryAccent by animateColorAsState(
        targetValue = phaseAccentColor(phase),
        animationSpec = tween(durationMillis = 360),
        label = "primaryAccent",
    )

    val secondaryAccent by animateColorAsState(
        targetValue = when (phase) {
            AssistantPhase.IDLE -> Color(0xFF00B4D8)
            AssistantPhase.LISTENING -> Color(0xFF00F5D4)
            AssistantPhase.THINKING, AssistantPhase.EXECUTING -> Color(0xFF7B61FF)
            AssistantPhase.COMPLETED -> Color(0xFF00F5D4)
            AssistantPhase.ERROR -> Color(0xFFFF477E)
        },
        animationSpec = tween(durationMillis = 360),
        label = "secondaryAccent",
    )

    val targetGazeX = when {
        touchGaze != null -> touchGaze!!.x
        phase == AssistantPhase.IDLE -> -0.22f + 0.35f * sin(cycle)
        phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> 0.32f
        phase == AssistantPhase.LISTENING -> 0.08f * sin(cycle * 2f)
        else -> 0f
    }
    val targetGazeY = when {
        touchGaze != null -> touchGaze!!.y
        phase == AssistantPhase.IDLE -> -0.06f + 0.12f * cos(cycle)
        phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> -0.26f
        else -> 0f
    }
    val targetTiltDeg = when {
        touchGaze != null -> touchGaze!!.x * -6.5f
        phase == AssistantPhase.IDLE -> -4.2f + 2.4f * sin(cycle)
        phase == AssistantPhase.LISTENING -> 1.5f * sin(cycle * 2f)
        phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING -> -2.8f
        phase == AssistantPhase.COMPLETED -> 2.0f * sin(cycle * 2f)
        AssistantPhase.ERROR == phase -> 3.5f * sin(cycle * 6f)
        else -> 0f
    }

    val gazeX by animateFloatAsState(
        targetValue = targetGazeX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "gazeX",
    )
    val gazeY by animateFloatAsState(
        targetValue = targetGazeY,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "gazeY",
    )
    val headTilt by animateFloatAsState(
        targetValue = targetTiltDeg,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headTilt",
    )

    // Natural blink pulse between 92..97 of the 100-unit ticker
    val autoBlinkScale = if (blinkTicker in 92f..96.5f) {
        val dist = abs(blinkTicker - 94.25f) / 2.25f
        (0.08f + 0.92f * dist).coerceIn(0.08f, 1f)
    } else {
        1f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(diameter)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { touchGaze = null },
                    onTap = { tapOffset ->
                        val normX = ((tapOffset.x / size.width) - 0.5f) * 1.4f
                        val normY = ((tapOffset.y / size.height) - 0.5f) * 1.2f
                        touchGaze = Offset(
                            normX.coerceIn(-0.65f, 0.65f),
                            normY.coerceIn(-0.5f, 0.5f),
                        )
                    },
                )
            }
            .semantics { contentDescription = phaseLabel(phase) },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val visorWidth = (size.width * 0.88f).coerceAtMost(size.height * 1.85f)
        val visorHeight = size.height * 0.90f

        // 1. Ambient outer neon bloom behind the eyes
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryAccent.copy(alpha = 0.22f + 0.06f * fastPulse),
                    secondaryAccent.copy(alpha = 0.07f),
                    Color.Transparent,
                ),
                center = center,
                radius = visorWidth * 0.62f,
            ),
            radius = visorWidth * 0.62f,
            center = center,
        )

        // 2. Subtle visor shell frame (like Image 2's sleek robot head visor)
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
                    primaryAccent.copy(alpha = 0.48f),
                    secondaryAccent.copy(alpha = 0.22f),
                    primaryAccent.copy(alpha = 0.42f),
                ),
                start = visorRect.topLeft,
                end = visorRect.bottomRight,
            ),
            topLeft = visorRect.topLeft,
            size = visorRect.size,
            cornerRadius = visorCorner,
            style = Stroke(width = 1.6.dp.toPx()),
        )

        val baseEyeW = visorWidth * 0.34f
        val baseEyeH = visorHeight * 0.64f
        val eyeSpacing = visorWidth * 0.21f

        // 3D perspective scaling: looking left makes left eye slightly larger, right eye slightly smaller
        val leftPerspective = (1f - gazeX * 0.16f).coerceIn(0.84f, 1.14f)
        val rightPerspective = (1f + gazeX * 0.16f).coerceIn(0.84f, 1.14f)

        val gazeShiftX = gazeX * baseEyeW * 0.22f
        val gazeShiftY = gazeY * baseEyeH * 0.20f

        withTransform({
            rotate(degrees = headTilt, pivot = center)
        }) {
            val leftCenter = Offset(
                x = center.x - eyeSpacing + gazeShiftX,
                y = center.y + gazeShiftY,
            )
            val rightCenter = Offset(
                x = center.x + eyeSpacing + gazeShiftX,
                y = center.y + gazeShiftY,
            )

            when (phase) {
                AssistantPhase.COMPLETED -> {
                    drawHappyCompletedFace(
                        leftCenter = leftCenter,
                        rightCenter = rightCenter,
                        faceCenter = Offset(center.x + gazeShiftX * 0.6f, center.y + gazeShiftY * 0.6f),
                        eyeWidth = baseEyeW * 0.78f,
                        eyeHeight = baseEyeH * 0.48f,
                        accent = primaryAccent,
                        pulse = fastPulse,
                        isSpeaking = isSpeaking,
                    )
                }

                else -> {
                    val leftHeightScale = when (phase) {
                        AssistantPhase.THINKING, AssistantPhase.EXECUTING -> 0.66f
                        AssistantPhase.LISTENING -> 1.05f
                        AssistantPhase.ERROR -> 0.78f
                        else -> 1.0f
                    } * autoBlinkScale

                    val rightHeightScale = when (phase) {
                        AssistantPhase.THINKING, AssistantPhase.EXECUTING -> 0.96f
                        AssistantPhase.LISTENING -> 1.05f
                        AssistantPhase.ERROR -> 0.78f
                        else -> 0.94f
                    } * autoBlinkScale

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

                    if (phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING) {
                        drawThinkingEyebrows(
                            leftCenter = leftCenter,
                            rightCenter = rightCenter,
                            eyeWidth = baseEyeW,
                            eyeHeight = baseEyeH,
                            accent = primaryAccent,
                        )
                    } else if (phase == AssistantPhase.ERROR) {
                        drawErrorEyebrows(
                            leftCenter = leftCenter,
                            rightCenter = rightCenter,
                            eyeWidth = baseEyeW,
                            eyeHeight = baseEyeH,
                            accent = primaryAccent,
                        )
                    }
                }
            }
        }
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

    // Soft outer neon halo around each eye
    drawRoundRect(
        color = primary.copy(alpha = 0.18f),
        topLeft = outerRect.topLeft - Offset(6.dp.toPx(), 6.dp.toPx()),
        size = Size(outerRect.width + 12.dp.toPx(), outerRect.height + 12.dp.toPx()),
        cornerRadius = CornerRadius(width * 0.38f, width * 0.38f),
    )

    // Outer glassmorphic squircle casing (like Image 1)
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

    // Inner glowing iris squircle
    val irisW = width * 0.74f
    val irisH = (safeHeight * 0.74f).coerceAtLeast(width * 0.08f)
    val irisOffset = Offset(gazeX * width * 0.07f, gazeY * safeHeight * 0.07f)
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

    // Clip inside iris for pupil core, scanlines, and eyelid masks
    clipPath(irisPath) {
        // Subtle digital scanlines inside the glowing iris
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

        // Dark rounded pupil ring core (matches Image 1 & Image 2's iconic ring-pupil look)
        if (safeHeight > width * 0.28f) {
            val pupilW = irisW * if (phase == AssistantPhase.LISTENING) 0.42f else 0.46f
            val pupilH = irisH * if (phase == AssistantPhase.LISTENING) 0.42f else 0.48f
            val pupilCenter = irisCenter + Offset(gazeX * irisW * 0.10f, gazeY * irisH * 0.10f)
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

            // Crisp specular catchlight dot
            drawCircle(
                color = Color.White.copy(alpha = 0.78f),
                radius = pupilW * 0.13f,
                center = Offset(
                    pupilRect.left + pupilW * 0.24f,
                    pupilRect.top + pupilH * 0.24f,
                ),
            )
        }

        // Thinking / Executing upper eyelid slant + active scan beam
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
    // Left eyebrow: flat/focused
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

    // Right eyebrow: raised high and arched (like Image 2's EXECUTING state)
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

private fun DrawScope.drawErrorEyebrows(
    leftCenter: Offset,
    rightCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    accent: Color,
) {
    val leftBrow = Path().apply {
        moveTo(leftCenter.x - eyeWidth * 0.34f, leftCenter.y - eyeHeight * 0.32f)
        lineTo(leftCenter.x + eyeWidth * 0.30f, leftCenter.y - eyeHeight * 0.44f)
    }
    val rightBrow = Path().apply {
        moveTo(rightCenter.x - eyeWidth * 0.30f, rightCenter.y - eyeHeight * 0.44f)
        lineTo(rightCenter.x + eyeWidth * 0.34f, rightCenter.y - eyeHeight * 0.32f)
    }
    drawPath(leftBrow, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    drawPath(rightBrow, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawHappyCompletedFace(
    leftCenter: Offset,
    rightCenter: Offset,
    faceCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    accent: Color,
    pulse: Float,
    isSpeaking: Boolean,
) {
    // Draw happy chevron/crescent eyes (^ ^) with neon bloom (matches Image 2 COMPLETED)
    fun drawHappyEye(c: Offset) {
        val path = Path().apply {
            moveTo(c.x - eyeWidth * 0.46f, c.y + eyeHeight * 0.22f)
            quadraticTo(
                c.x,
                c.y - eyeHeight * 0.58f,
                c.x + eyeWidth * 0.46f,
                c.y + eyeHeight * 0.22f,
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

    drawHappyEye(leftCenter.copy(y = faceCenter.y - eyeHeight * 0.12f))
    drawHappyEye(rightCenter.copy(y = faceCenter.y - eyeHeight * 0.12f))

    // Cute glowing mouth arc underneath
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
    AssistantPhase.IDLE -> Color(0xFF00F5D4)
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
