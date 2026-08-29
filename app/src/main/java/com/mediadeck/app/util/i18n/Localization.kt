package com.mediadeck.app.util.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalLanguage = staticCompositionLocalOf { "en" }

@Composable
fun t(en: String, id: String): String {
    val lang = LocalLanguage.current
    return translate(lang, en, id)
}

fun translate(lang: String, en: String, id: String): String {
    return if (lang == "en") en else id
}
