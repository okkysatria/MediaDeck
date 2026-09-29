package com.mediadeck.app.util.media
import android.content.Context
import android.net.Uri
object MediaUtils {
    fun isSmbUri(uriString: String): Boolean {
        val uri = Uri.parse(uriString)
        return uri.scheme.equals("smb", ignoreCase = true) ||
            (uri.scheme.equals("content", ignoreCase = true) &&
                uri.authority?.equals(com.mediadeck.app.util.smb.SmbContentProvider.AUTHORITY, ignoreCase = true) == true)
    }
    fun isUriAccessible(context: Context, uriStr: String?): Boolean {
        if (uriStr.isNullOrEmpty()) return false
        val uri = Uri.parse(uriStr)
        return when (uri.scheme) {
            "content" -> {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { true } ?: false
                } catch (_: Exception) { false }
            }
            "file" -> {
                try {
                    val path = uri.path
                    path != null && java.io.File(path).exists()
                } catch (_: Exception) { false }
            }
            "smb" -> true
            else -> uriStr.startsWith("/") && java.io.File(uriStr).exists()
        }
    }
    fun isImageMime(mime: String?): Boolean = mime?.startsWith("image/", true) == true
    fun isVideoMime(mime: String?): Boolean = mime?.startsWith("video/", true) == true
    fun isImageExt(ext: String): Boolean {
        val e = ext.lowercase()
        return e == "jpg" || e == "jpeg" || e == "png" || e == "webp" || e == "gif" || e == "bmp" || e == "heic" || e == "heif"
    }
    fun isArchiveExt(ext: String): Boolean {
        val e = ext.lowercase()
        return e == "cbz" || e == "zip"
    }
    fun getMimeTypeFromExt(ext: String): String {
        return when (ext.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "image/jpeg"
        }
    }
    fun isVideoExt(ext: String): Boolean {
        val e = ext.lowercase()
        return e in VIDEO_EXTENSIONS
    }
    fun isVideoFile(name: String, mime: String? = null): Boolean =
        isVideoMime(mime) || isVideoExt(getFileExt(name))
    private val VIDEO_EXTENSIONS = setOf(
        "3g2", "3gp", "asf", "avi", "f4v", "flv", "m2t", "m2ts", "m2v", "m4v",
        "mkv", "mov", "mp4", "mpe", "mpeg", "mpg", "mpv", "mts", "mxf", "ogv",
        "ts", "vob", "webm", "wmv",
    )
    fun getFileExt(name: String): String {
        val idx = name.lastIndexOf(".")
        return if (idx > 0) name.substring(idx + 1).lowercase() else ""
    }
    fun isMediaFile(name: String): Boolean {
        val ext = getFileExt(name)
        return isImageExt(ext) || isVideoExt(ext)
    }
    fun getSmbUrlFromUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        return when {
            isSmbUri(uriString) && uri.scheme.equals("content", ignoreCase = true) -> {
                val path = uri.path ?: return null
                val cleanPath = path.trimStart('/')
                if (cleanPath.isEmpty()) return null
                var finalPath = cleanPath
                val atIndex = finalPath.indexOf('@')
                val firstSlash = finalPath.indexOf('/')
                if (atIndex in 0 until (if (firstSlash >= 0) firstSlash else finalPath.length)) {
                    finalPath = finalPath.substring(atIndex + 1)
                }
                "smb://$finalPath"
            }
            isSmbUri(uriString) && uri.scheme.equals("smb", ignoreCase = true) -> {
                uriString
            }
            else -> null
        }
    }
    fun cleanSmbUrl(smbUrl: String): String {
        var cleanUrl = smbUrl.removePrefix("smb://")
        val firstSlash = cleanUrl.indexOf('/')
        val atIndex = cleanUrl.indexOf('@')
        if (atIndex in 0 until (if (firstSlash >= 0) firstSlash else cleanUrl.length)) {
            cleanUrl = cleanUrl.substring(atIndex + 1)
        }
        return cleanUrl
    }
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}