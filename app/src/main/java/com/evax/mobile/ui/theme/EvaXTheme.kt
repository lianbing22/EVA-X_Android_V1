package com.evax.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EvaColors = darkColorScheme(
    primary = Color(0xFF00F5D4),
    onPrimary = Color(0xFF031F1C),
    secondary = Color(0xFFB9A9FF),
    onSecondary = Color(0xFF1E143B),
    tertiary = Color(0xFF5CE6B0),
    background = Color(0xFF050608),
    onBackground = Color(0xFFF2F6FC),
    surface = Color(0xFF0C0F17),
    onSurface = Color(0xFFF2F6FC),
    surfaceVariant = Color(0xFF141926),
    onSurfaceVariant = Color(0xFF9BA6BC),
    error = Color(0xFFFF7A95),
)

@Composable
fun EvaXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvaColors,
        content = content,
    )
}
