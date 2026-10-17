package com.mediadeck.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class DeckColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val accentBlue: Color,
    val onAccent: Color,
    val accentPillBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val badgeRed: Color,
    val divider: Color,
)

val DarkDeckColors = DeckColors(
    background = Color(0xFF131417),
    surface = Color(0xFF22252B),
    surfaceVariant = Color(0xFF1F222A),
    accentBlue = Color(0xFF4DA3FF),
    onAccent = Color(0xFF06182B),
    accentPillBg = Color(0x334DA3FF),
    textPrimary = Color(0xFFF3F5F8),
    textSecondary = Color(0xFFA9B0BD),
    badgeRed = Color(0xFFC93A3F),
    divider = Color(0x14FFFFFF),
)

val LightDeckColors = DeckColors(
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF1F4),
    accentBlue = Color(0xFF1972E8),
    onAccent = Color.White,
    accentPillBg = Color(0x1F1972E8),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF656D76),
    badgeRed = Color(0xFFD93025),
    divider = Color(0x1F000000),
)

val LocalDeckColors = staticCompositionLocalOf { DarkDeckColors }

val DeckBackground: Color
    @Composable
    get() = LocalDeckColors.current.background

val DeckSurface: Color
    @Composable
    get() = LocalDeckColors.current.surface

val DeckSurfaceVariant: Color
    @Composable
    get() = LocalDeckColors.current.surfaceVariant

val DeckAccentBlue: Color
    @Composable
    get() = LocalDeckColors.current.accentBlue

val DeckOnAccent: Color
    @Composable
    get() = LocalDeckColors.current.onAccent

val DeckAccentPillBg: Color
    @Composable
    get() = LocalDeckColors.current.accentPillBg

val DeckTextPrimary: Color
    @Composable
    get() = LocalDeckColors.current.textPrimary

val DeckTextSecondary: Color
    @Composable
    get() = LocalDeckColors.current.textSecondary

val DeckBadgeRed: Color
    @Composable
    get() = LocalDeckColors.current.badgeRed

val DeckDivider: Color
    @Composable
    get() = LocalDeckColors.current.divider
