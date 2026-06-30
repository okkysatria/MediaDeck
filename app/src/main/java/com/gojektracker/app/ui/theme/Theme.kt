package com.gojektracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GojekGreen,
    secondary = GojekBlue,
    tertiary = GojekYellow,
    error = GojekRed,
    background = GojekDarkBackground,
    surface = GojekDarkSurface,
    surfaceVariant = GojekDarkCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.LightGray
)

private val LightColorScheme = lightColorScheme(
    primary = GojekGreen,
    secondary = GojekBlue,
    tertiary = GojekYellow,
    error = GojekRed,
    background = Color(0xFFFAFAFA),
    surface = GojekLightSurface,
    surfaceVariant = Color(0xFFF0F1F3),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1C1C),
    onSurface = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF555555)
)

@Composable
fun GojekTrackerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
