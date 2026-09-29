package com.mediadeck.app.util.media
import android.net.Uri
object MediaFolderIdentity {
    fun key(folderUri: String, mediaUri: String): String {
        val source = folderUri.ifBlank {
            val uri = Uri.parse(mediaUri)
            val encodedPath = uri.encodedPath.orEmpty()
            val documentMarker = "/document/"
            if (documentMarker in encodedPath) {
                val documentId = encodedPath.substringAfter(documentMarker)
                val encodedParentId = documentId.substringBeforeLast("%2F", documentId.substringBeforeLast("%2f", documentId))
                val parentUri = uri.buildUpon()
                    .encodedPath(encodedPath.substringBefore(documentMarker) + documentMarker + encodedParentId)
                    .build()
                return parentUri.toString().trimEnd('/')
            }
            val path = uri.path.orEmpty().trimEnd('/')
            val parentPath = path.substringBeforeLast('/', "")
            uri.buildUpon().path(parentPath).build().toString()
        }
        return source.trimEnd('/')
    }
    fun displayName(folderUri: String, mediaUri: String, fallback: String): String {
        val folder = Uri.parse(key(folderUri, mediaUri))
        val lastSegment = folder.lastPathSegment.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast(':')
            .trim()
        if (lastSegment.isNotBlank() && !lastSegment.contains("://")) return lastSegment
        val cleanFallback = fallback.trim()
        return if (cleanFallback.isNotBlank() && !cleanFallback.contains("://")) cleanFallback else "Folder"
    }
    fun parentDisplayName(folderUri: String, mediaUri: String): String? {
        val folderKey = key(folderUri, mediaUri)
        val folder = Uri.parse(folderKey)
        val lastSegment = folder.lastPathSegment.orEmpty().substringAfterLast(':')
        val documentParent = lastSegment.substringBeforeLast('/', "")
            .substringAfterLast('/')
            .trim()
        if (documentParent.isNotBlank()) return documentParent
        if ("/document/" in folder.encodedPath.orEmpty()) return null
        val pathParent = folder.pathSegments.dropLast(1).lastOrNull().orEmpty().trim()
        return pathParent.takeIf { it.isNotBlank() }
    }
}