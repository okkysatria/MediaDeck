package com.mediadeck.app.util.scan
import android.net.Uri
import com.mediadeck.app.util.media.MediaFolderIdentity
object ScanProgressFormatter {
    private fun scanningPrefix(text: String): String? = when {
        text.startsWith("Scanning ", ignoreCase = true) -> "Scanning "
        text.startsWith("Memindai ", ignoreCase = true) -> "Memindai "
        else -> null
    }
    fun location(text: String): String? {
        val prefix = scanningPrefix(text) ?: return null
        val rawLocation = text.removePrefix(prefix).trim()
        if (rawLocation.isBlank()) return null
        val scheme = Uri.parse(rawLocation).scheme?.lowercase()
        return if (scheme == "content" || scheme == "file" || scheme == "smb") {
            MediaFolderIdentity.displayName(rawLocation, rawLocation, rawLocation)
        } else {
            rawLocation.trimEnd('/', '\\').substringAfterLast('/').ifBlank { rawLocation }
        }
    }
    fun compact(text: String): String {
        val prefix = scanningPrefix(text) ?: return text
        val location = location(text) ?: return prefix.trim()
        return "${prefix.trim()}: $location"
    }
}