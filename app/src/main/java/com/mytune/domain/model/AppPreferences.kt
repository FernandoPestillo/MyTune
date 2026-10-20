package com.mytune.domain.model

enum class HeadstockType(val title: String) {
    THREE_PER_SIDE("3 + 3"), SIX_INLINE("6 em linha"), SIX_INLINE_REVERSE("6 em linha invertido")
}

enum class ThemeMode(val title: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro") }

data class AppPreferences(
    val headstock: HeadstockType = HeadstockType.THREE_PER_SIDE,
    val theme: ThemeMode = ThemeMode.DARK,
    val haptics: Boolean = true,
    val stringAnimation: Boolean = true,
)
