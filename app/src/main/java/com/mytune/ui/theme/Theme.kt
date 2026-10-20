package com.mytune.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.mytune.domain.model.ThemeMode

val DarkColors = darkColorScheme(
    primary = Color(0xFFFF914D), onPrimary = Color(0xFF261408),
    secondary = Color(0xFF8DE0B0), onSecondary = Color(0xFF082B17),
    background = Color(0xFF101113), surface = Color(0xFF191B1E),
    onBackground = Color(0xFFF5F1EA), onSurface = Color(0xFFF5F1EA),
    onSurfaceVariant = Color(0xFFADB0B7), outline = Color(0xFF656870), error = Color(0xFFFFB4AB),
)
val LightColors = lightColorScheme(
    primary = Color(0xFFA94200), onPrimary = Color.White,
    secondary = Color(0xFF176B3C), onSecondary = Color.White,
    background = Color(0xFFFFFBF5), surface = Color(0xFFF2EEE7),
    onBackground = Color(0xFF242220), onSurface = Color(0xFF242220),
    onSurfaceVariant = Color(0xFF62616A), outline = Color(0xFF77737A), error = Color(0xFFBA1A1A),
)

// Existing component tokens now resolve to the active Material 3 scheme.
val Orange: Color @Composable get() = MaterialTheme.colorScheme.primary
val Green: Color @Composable get() = MaterialTheme.colorScheme.secondary
val Background: Color @Composable get() = MaterialTheme.colorScheme.background
val Panel: Color @Composable get() = MaterialTheme.colorScheme.surface
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Foreground: Color @Composable get() = MaterialTheme.colorScheme.onSurface

/** Physical materials are independent of semantic UI colors. */
object InstrumentColors {
    val woodEdge = Color(0xFF302A25); val wood = Color(0xFF514335)
    val woodOutline = Color(0xFF907257); val grain = Color(0xFFDDAC74)
    val metal = Color(0xFFA0A1A2); val metalDark = Color(0xFF343639)
    val peg = Color(0xFF252629); val fretboard = Color(0xFF211D1A)
    val fret = Color(0xFF8B8276); val nut = Color(0xFFB7AEA0)
    val string = Color(0xFFDED5C4); val logo = Color(0xFFDDC7A9)
    val mapleEdge = Color(0xFFAF7B40); val maple = Color(0xFFE5BF80)
    val mapleLight = Color(0xFFF0D59D); val mapleGrain = Color(0xFF906434)
    val graphiteEdge = Color(0xFF080A0C); val graphite = Color(0xFF202328)
    val graphiteOutline = Color(0xFF555B61); val darkString = Color(0xFF574A39)
    val steelLight = Color(0xFFE1E5E7); val steelMid = Color(0xFF929BA2)
    val screwSlot = Color(0xFF252B31)
    val lacquer = Color(0xFF090A0C); val lacquerReflection = Color(0xFF26282B)
    val binding = Color(0xFFE5C991); val goldDark = Color(0xFF67451C)
    val gold = Color(0xFFBC8C3B); val goldLight = Color(0xFFFFE5A4)
    val blackMetal = Color(0xFF090C0F); val blackMetalMid = Color(0xFF262C32)
    val blackMetalLight = Color(0xFF626C75)
}

fun usesDarkTheme(mode: ThemeMode, systemDark: Boolean) = when (mode) {
    ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark
}

@Composable
fun MyTuneTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (usesDarkTheme(mode, isSystemInDarkTheme())) DarkColors else LightColors,
        typography = Typography(), content = content)
}
