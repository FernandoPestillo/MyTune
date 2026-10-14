package com.mytune.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF914D)
val Green = Color(0xFF8DE0B0)
val Background = Color(0xFF101113)
val Panel = Color(0xFF191B1E)
val Muted = Color(0xFF9A9DA4)
val Foreground = Color(0xFFF5F1EA)

private val colors = darkColorScheme(
    primary = Orange, onPrimary = Color(0xFF261408),
    secondary = Green, background = Background, surface = Panel,
    onBackground = Foreground, onSurface = Foreground,
    onSurfaceVariant = Muted, outline = Color(0xFF34363A),
)

@Composable
fun MyTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
