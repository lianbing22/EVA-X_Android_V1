package com.evax.mobile.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val EvaColors = darkColorScheme(
    primary = Color(0xFF45E5CC),
    onPrimary = Color(0xFF031F1C),
    secondary = Color(0xFF9AC8BF),
    onSecondary = Color(0xFF12221F),
    tertiary = Color(0xFF5CE6B0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F6FC),
    surface = Color(0xFF10151A),
    onSurface = Color(0xFFF2F6FC),
    surfaceVariant = Color(0xFF172027),
    onSurfaceVariant = Color(0xFF9AA9B5),
    outline = Color(0xFF52676C),
    outlineVariant = Color(0xFF26343A),
    error = Color(0xFFF0A893),
)

@Composable
fun EvaXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvaColors,
    ) {
        CompositionLocalProvider(LocalContentColor provides EvaColors.onBackground, content = content)
    }
}
