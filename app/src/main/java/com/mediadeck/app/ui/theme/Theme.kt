package com.mediadeck.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppTheme {
    LIGHT, DARK, SYSTEM;
    companion object {
        fun fromString(name: String): AppTheme {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1972E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF1972E8),
    secondary = Color(0xFF1972E8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF1F4),
    onSecondaryContainer = Color(0xFF656D76),
    tertiary = Color(0xFFD93025),
    onTertiary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEEF1F4),
    onSurfaceVariant = Color(0xFF656D76),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFE1E4E8),
    error = Color(0xFFD93025),
    onError = Color.White,
    surfaceTint = Color(0xFF1972E8),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF22252B),
    onPrimaryContainer = Color(0xFFF3F5F8),
    secondary = Color(0xFF4DA3FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1F222A),
    onSecondaryContainer = Color(0xFFF3F5F8),
    tertiary = Color(0xFFC93A3F),
    onTertiary = Color.White,
    background = Color(0xFF131417),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF22252B),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF1F222A),
    onSurfaceVariant = Color(0xFFA9B0BD),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF1F222A),
    error = Color(0xFFC93A3F),
    onError = Color.White,
    surfaceTint = Color(0xFF4DA3FF),
)

@Composable
fun MediaDeckTheme(
    themeName: String = "system",
    content: @Composable () -> Unit,
) {
    val theme = AppTheme.fromString(themeName)
    MediaDeckTheme(theme = theme, content = content)
}

@Composable
fun MediaDeckTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme: ColorScheme = when (theme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.SYSTEM -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
    val deckColors: DeckColors = when (theme) {
        AppTheme.LIGHT -> LightDeckColors
        AppTheme.DARK -> DarkDeckColors
        AppTheme.SYSTEM -> if (darkTheme) DarkDeckColors else LightDeckColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            val isLight = colorScheme.background.luminance() > 0.5f
            insetsController.isAppearanceLightStatusBars = isLight
            insetsController.isAppearanceLightNavigationBars = isLight
        }
    }

    CompositionLocalProvider(LocalDeckColors provides deckColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
