package com.evax.mobile.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Abstract props stay below the eyes; no scene claims to show real web or weather data. */
internal fun DrawScope.drawIdleSceneProp(
    scene: IdleScene,
    progress: Float,
    alpha: Float,
    sceneCenter: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    accent: Color,
) {
    if (alpha <= 0.01f || scene == IdleScene.STRETCHING) return
    val t = progress.coerceIn(0f, 1f)
    val color = accent.copy(alpha = alpha.coerceIn(0f, 1f) * 0.55f)
    val muted = accent.copy(alpha = alpha.coerceIn(0f, 1f) * 0.18f)
    val surface = accent.copy(alpha = alpha.coerceIn(0f, 1f) * 0.035f)
    val center = sceneCenter + Offset(0f, eyeHeight * 0.90f)
    val width = eyeWidth * 1.15f
    val height = eyeHeight * 0.40f
    val stroke = (eyeWidth * 0.013f).coerceAtLeast(1f)
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    fun line(a: Offset, b: Offset, faint: Boolean = false) = drawLine(
        if (faint) muted else color, a, b, stroke, StrokeCap.Round,
    )
    fun panel() {
        drawRoundRect(surface, Offset(left, top), Size(width, height), CornerRadius(width * 0.05f))
        drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(width * 0.05f), style = Stroke(stroke))
    }
    when (scene) {
        IdleScene.SURFING -> {
            panel()
            line(Offset(left, top + height * 0.20f), Offset(left + width, top + height * 0.20f), true)
            repeat(3) { drawCircle(muted, stroke * 1.2f, Offset(left + width * (0.07f + it * 0.05f), top + height * 0.10f)) }
            clipRect(left + stroke, top + height * 0.23f, left + width - stroke, top + height - stroke) {
                val scroll = ((t - 0.25f) / 0.15f).coerceIn(0f, 1f) * height * 0.20f
                repeat(4) { index ->
                    val y = top + height * (0.37f + index * 0.22f) - scroll
                    line(Offset(left + width * 0.12f, y), Offset(left + width * (if (index % 2 == 0) 0.72f else 0.86f), y), true)
                }
                if (t in 0.40f..0.78f) {
                    drawSceneStar(Offset(left + width * 0.84f, top + height * 0.48f), height * 0.13f, color)
                }
            }
        }
        IdleScene.READING -> {
            val outline = Path().apply {
                moveTo(center.x, top + height * 0.10f)
                quadraticTo(left + width * 0.25f, top - height * 0.12f, left, top)
                lineTo(left, top + height * 0.90f)
                quadraticTo(left + width * 0.25f, top + height * 0.80f, center.x, top + height)
                quadraticTo(left + width * 0.75f, top + height * 0.80f, left + width, top + height * 0.90f)
                lineTo(left + width, top)
                quadraticTo(left + width * 0.75f, top - height * 0.12f, center.x, top + height * 0.10f)
            }
            drawPath(outline, surface)
            drawPath(outline, color, style = Stroke(stroke))
            line(Offset(center.x, top + height * 0.12f), Offset(center.x, top + height * 0.95f), true)
            repeat(3) { index ->
                val y = top + height * (0.30f + index * 0.19f)
                line(Offset(left + width * 0.10f, y), Offset(left + width * 0.39f, y + height * 0.05f), true)
                line(Offset(left + width * 0.61f, y + height * 0.05f), Offset(left + width * 0.90f, y), true)
            }
            val turn = ((t - 0.43f) / 0.13f).coerceIn(0f, 1f)
            if (turn > 0f && turn < 1f) {
                val x = left + width * (0.92f - turn * 0.84f)
                val page = Path().apply {
                    moveTo(center.x, top + height * 0.1f)
                    quadraticTo(x, top - height * 0.35f, x, top + height * 0.80f)
                    lineTo(center.x, top + height * 0.95f)
                }
                drawPath(page, color, style = Stroke(stroke))
            }
        }
        IdleScene.STARS -> {
            val fall = ((t - 0.18f) / 0.28f).coerceIn(0f, 1f)
            val starCenter = center + Offset(width * (0.24f - fall * 0.24f), -eyeHeight * 0.46f * (1f - fall))
            drawSceneStar(starCenter, eyeWidth * 0.11f, color)
            if (t in 0.48f..0.72f) {
                val sparkle = sin((t - 0.48f) / 0.24f * PI).toFloat()
                drawSceneStar(center + Offset(-width * 0.28f, -height * 0.12f), eyeWidth * 0.055f * sparkle, color)
                drawSceneStar(center + Offset(width * 0.28f, -height * 0.25f), eyeWidth * 0.045f * sparkle, color)
            }
            val catch = Path().apply {
                moveTo(center.x - width * 0.24f, center.y + height * 0.32f)
                quadraticTo(center.x, center.y + height * 0.52f, center.x + width * 0.24f, center.y + height * 0.32f)
            }
            drawPath(catch, muted, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        IdleScene.COFFEE -> {
            val lift = sin(((t - 0.18f) / 0.43f).coerceIn(0f, 1f) * PI).toFloat() * height * 0.13f
            val cupTop = top + height * 0.24f - lift
            val cupWidth = width * 0.43f
            val cupLeft = center.x - cupWidth * 0.55f
            drawRoundRect(surface, Offset(cupLeft, cupTop), Size(cupWidth, height * 0.58f), CornerRadius(height * 0.10f))
            drawRoundRect(color, Offset(cupLeft, cupTop), Size(cupWidth, height * 0.58f), CornerRadius(height * 0.10f), style = Stroke(stroke))
            drawOval(color, Offset(cupLeft + cupWidth * 0.87f, cupTop + height * 0.14f), Size(width * 0.20f, height * 0.31f), style = Stroke(stroke))
            line(Offset(center.x - width * 0.34f, top + height * 0.92f), Offset(center.x + width * 0.32f, top + height * 0.92f), true)
            repeat(2) { index ->
                val x = center.x + width * (index * 0.10f - 0.08f)
                val steam = Path().apply {
                    moveTo(x, cupTop - height * 0.08f)
                    cubicTo(x - width * 0.05f, cupTop - height * 0.17f, x + width * 0.07f, cupTop - height * 0.25f,
                        x + sin(t * 5f + index).toFloat() * width * 0.025f, cupTop - height * 0.32f)
                }
                drawPath(steam, muted, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        IdleScene.GAMING -> {
            panel()
            val screenLeft = left + width * 0.23f
            val screenTop = top + height * 0.16f
            val screenWidth = width * 0.54f
            val screenHeight = height * 0.66f
            drawRoundRect(muted, Offset(screenLeft, screenTop), Size(screenWidth, screenHeight), CornerRadius(stroke * 2f), style = Stroke(stroke))
            line(Offset(left + width * 0.08f, center.y), Offset(left + width * 0.17f, center.y))
            line(Offset(left + width * 0.125f, center.y - height * 0.10f), Offset(left + width * 0.125f, center.y + height * 0.10f))
            drawCircle(color, stroke * 1.6f, Offset(left + width * 0.86f, center.y - height * 0.05f))
            drawCircle(muted, stroke * 1.6f, Offset(left + width * 0.92f, center.y + height * 0.08f))
            val ball = Offset(screenLeft + screenWidth * (0.5f + sin(t * 17f) * 0.32f), screenTop + screenHeight * (0.26f + (t * 4f % 1f) * 0.38f))
            drawCircle(color, stroke * 1.7f, ball)
            val paddleX = screenLeft + screenWidth * (0.48f + sin(t * 14f) * 0.22f)
            line(Offset(paddleX - screenWidth * 0.13f, screenTop + screenHeight * 0.85f), Offset(paddleX + screenWidth * 0.13f, screenTop + screenHeight * 0.85f))
        }
        IdleScene.NAPPING -> {
            val zCenter = center + Offset(width * 0.21f, -height * 0.06f - (t % 0.2f) * height)
            val zWidth = width * 0.15f
            val zHeight = height * 0.27f
            line(zCenter + Offset(-zWidth / 2f, -zHeight / 2f), zCenter + Offset(zWidth / 2f, -zHeight / 2f), true)
            line(zCenter + Offset(zWidth / 2f, -zHeight / 2f), zCenter + Offset(-zWidth / 2f, zHeight / 2f), true)
            line(zCenter + Offset(-zWidth / 2f, zHeight / 2f), zCenter + Offset(zWidth / 2f, zHeight / 2f), true)
        }
        IdleScene.RAIN -> {
            panel()
            line(Offset(center.x, top), Offset(center.x, top + height), true)
            line(Offset(left, center.y), Offset(left + width, center.y), true)
            clipRect(left + stroke, top + stroke, left + width - stroke, top + height - stroke) {
                repeat(7) { index ->
                    val drop = (t * 2.2f + index * 0.147f) % 1f
                    val x = left + width * (0.10f + index * 0.125f)
                    val y = top + height * drop
                    line(Offset(x, y), Offset(x - width * 0.02f, y + height * 0.12f), true)
                }
            }
        }
        IdleScene.STRETCHING -> Unit
    }
}

private fun DrawScope.drawSceneStar(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val path = Path().apply {
        repeat(10) { index ->
            val angle = -PI / 2 + index * PI / 5
            val length = radius * if (index % 2 == 0) 1f else 0.43f
            val point = center + Offset((cos(angle) * length).toFloat(), (sin(angle) * length).toFloat())
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
    drawPath(path, color.copy(alpha = color.alpha * 0.12f))
    drawPath(path, color, style = Stroke(radius * 0.10f, cap = StrokeCap.Round))
}
