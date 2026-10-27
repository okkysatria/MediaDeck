package com.mediadeck.app.util.media

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared display formatting for media metadata and library counts. */
object MediaFormatters {
    fun fileSize(bytes: Long, unknownValue: String = "—"): String {
        if (bytes <= 0L) return unknownValue
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val number = if (unitIndex == 0) {
            value.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
        return "$number ${units[unitIndex]}"
    }

    fun count(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    fun dateTime(value: Long, withTime: Boolean = true, unknownValue: String = ""): String {
        if (value <= 0L) return unknownValue
        val millis = if (value < 100_000_000_000L) value * 1000L else value
        val pattern = if (withTime) "dd MMM yyyy, HH:mm" else "dd MMM yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }
}
