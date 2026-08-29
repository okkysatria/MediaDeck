package com.mediadeck.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppTheme {
    LIGHT, DARK, ORANGE, PURPLE, SYSTEM;

    companion object {
        fun fromString(name: String): AppTheme {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: DARK
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = MidnightNavy,
    onPrimary = Color.White,
    primaryContainer = SlateContainer,
    onPrimaryContainer = MidnightNavy,
    secondary = MediaGreen,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = SlateOnSurfaceVariant,
    tertiary = MediaRed,
    onTertiary = Color.White,
    background = SlateBackground,
    onBackground = MidnightNavy,
    surface = Color.White,
    onSurface = MidnightNavy,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SlateOutline,
    outlineVariant = SlateSurfaceVariant,
    error = MediaRed,
    onError = Color.White,
    surfaceTint = MidnightNavy,
)

private val DarkColorScheme = darkColorScheme(
    primary = MediaBlue,
    onPrimary = Color.Black,
    primaryContainer = CharcoalContainer,
    onPrimaryContainer = Color.White,
    secondary = MediaBlue,
    onSecondary = Color.Black,
    secondaryContainer = CharcoalBackground,
    onSecondaryContainer = Color.White,
    tertiary = MediaRed,
    onTertiary = Color.White,
    background = CharcoalBackground,
    onBackground = Color.White,
    surface = CharcoalSurface,
    onSurface = Color.White,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = CharcoalOnSurfaceVariant,
    outline = CharcoalOutline,
    outlineVariant = CharcoalSurfaceVariant,
    error = MediaRed,
    onError = Color.White,
    surfaceTint = MediaBlue,
)

private val OrangeColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = Color.Black,
    primaryContainer = AmberContainer,
    onPrimaryContainer = AmberOnContainer,
    secondary = AmberGold,
    onSecondary = Color.Black,
    secondaryContainer = AmberSecondaryContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    background = AmberBackground,
    onBackground = AmberOnBackground,
    surface = AmberBrown,
    onSurface = AmberOnBackground,
    surfaceVariant = AmberSurfaceVariant,
    onSurfaceVariant = AmberOnSurfaceVariant,
    outline = AmberOutline,
    outlineVariant = AmberOutlineVariant,
    error = AmberTertiary,
    onError = Color.White,
    surfaceTint = AmberGold,
)

private val PurpleColorScheme = darkColorScheme(
    primary = MidnightPurple,
    onPrimary = Color.Black,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurpleOnContainer,
    secondary = MidnightPurple,
    onSecondary = Color.Black,
    secondaryContainer = PurpleSecondaryContainer,
    onSecondaryContainer = PurpleOnContainer,
    tertiary = PurpleTertiary,
    onTertiary = Color.Black,
    background = MidnightDeep,
    onBackground = PurpleOnContainer,
    surface = PurpleSurface,
    onSurface = PurpleOnContainer,
    surfaceVariant = PurpleSurfaceVariant,
    onSurfaceVariant = PurpleOnSurfaceVariant,
    outline = PurpleOutline,
    outlineVariant = PurpleOutlineVariant,
    error = PurpleTertiary,
    onError = Color.Black,
    surfaceTint = MidnightPurple,
)

@Composable
fun MediaDeckTheme(
    themeName: String = "dark",
    content: @Composable () -> Unit,
) {
    val theme = AppTheme.fromString(themeName)
    MediaDeckTheme(theme = theme, content = content)
}

@Composable
fun MediaDeckTheme(
    theme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme: ColorScheme = when (theme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.ORANGE -> OrangeColorScheme
        AppTheme.PURPLE -> PurpleColorScheme
        AppTheme.SYSTEM -> if (darkTheme) DarkColorScheme else LightColorScheme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
