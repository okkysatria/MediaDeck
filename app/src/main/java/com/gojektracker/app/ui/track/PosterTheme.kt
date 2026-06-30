package com.gojektracker.app.ui.track

import androidx.compose.ui.graphics.Color

/**
 * Tema warna untuk poster rute. Disederhanakan dari versi lama yang punya 2 enum
 * berlapis (StravaCardTheme x TrackerTemplatePreset, total 9 x 12 kombinasi) menjadi
 * satu daftar tema yang sudah jadi & jelas hasilnya, cukup 6 pilihan yang paling kepake.
 */
enum class PosterTheme(
    val displayName: String,
    val background: Color,
    val cardBackground: Color,
    val accent: Color,
    val textPrimary: Color,
    val useMapTiles: Boolean
) {
    GOJEK_EMERALD(
        displayName = "Gojek Emerald",
        background = Color(0xFF041E15),
        cardBackground = Color(0xFF0C2C20),
        accent = Color(0xFF00C853),
        textPrimary = Color.White,
        useMapTiles = true
    ),
    SUNSET_ORANGE(
        displayName = "Sunset Orange",
        background = Color(0xFF120D0A),
        cardBackground = Color(0xFF1F1712),
        accent = Color(0xFFFC6100),
        textPrimary = Color.White,
        useMapTiles = true
    ),
    MIDNIGHT_CYAN(
        displayName = "Midnight Cyan",
        background = Color(0xFF020911),
        cardBackground = Color(0xFF0A1625),
        accent = Color(0xFF00E5FF),
        textPrimary = Color.White,
        useMapTiles = true
    ),
    STEALTH_CARBON(
        displayName = "Stealth Carbon",
        background = Color(0xFF0A0A0A),
        cardBackground = Color(0xFF161616),
        accent = Color(0xFFCCFF00),
        textPrimary = Color.White,
        useMapTiles = false
    ),
    CLEAN_DAYLIGHT(
        displayName = "Clean Daylight",
        background = Color(0xFFF5F5F0),
        cardBackground = Color.White,
        accent = Color(0xFFFF8A00),
        textPrimary = Color(0xFF1B1B1B),
        useMapTiles = true
    ),
    ROYAL_VIOLET(
        displayName = "Royal Violet",
        background = Color(0xFF110720),
        cardBackground = Color(0xFF1D0F34),
        accent = Color(0xFFFF4FA3),
        textPrimary = Color.White,
        useMapTiles = false
    )
}
