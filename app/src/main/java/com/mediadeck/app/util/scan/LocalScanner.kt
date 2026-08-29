package com.mediadeck.app.util.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mediadeck.app.util.zip.ZipContentProvider
import java.io.File
import java.util.zip.ZipFile

object LocalScanner {

    data class SimpleDocument(
        val uriStr: String,
        val documentId: String,
        val name: String,
        val mimeType: String,
        val lastModified: Long,
        val size: Long,
    )

    private fun listChildrenFast(context: Context, treeUri: Uri, parentDocId: String): List<SimpleDocument> {
        val result = mutableListOf<SimpleDocument>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val mod = if (modIdx != -1) cursor.getLong(modIdx) else 0L
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                    val fileUriStr = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                    result.add(SimpleDocument(fileUriStr, docId, name, mime, mod, size))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun getFolderLastModified(context: Context, treeUri: Uri, documentId: String): Long {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (_: Exception) {
        }
        return 0L
    }

    private suspend fun findComicFoldersFast(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        parentFolderName: String,
        comics: MutableList<Comic>,
        settings: AppSettings,
        currentDepth: Int = 0,
        maxDepth: Int = 100,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        if (currentDepth > maxDepth) return
        checkControl()

        val folderUriStr = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId).toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else getFolderLastModified(context, treeUri, parentDocId)
        val cachedMod = folderCache[folderUriStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUriStr] = currentMod
            return
        }

        onProgress?.invoke(parentFolderName)

        val children = listChildrenFast(context, treeUri, parentDocId)
        val childImages = children.filter {
            !it.name.startsWith("._") &&
                    !it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) &&
                    (MediaUtils.isImageMime(it.mimeType) || MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)))
        }

        if (childImages.isNotEmpty()) {
            val isKnown = knownUris.contains(folderUriStr)
            if (!isKnown) {
                val parsed = parseNameMetadata(parentFolderName, settings)
                val cover = childImages.minWithOrNull(compareBy(NaturalOrderComparator) { it.name })?.uriStr ?: childImages.first().uriStr
                val comic = Comic(
                    title = parsed.title,
                    folderUri = folderUriStr,
                    coverUri = cover,
                    parentFolderName = parentFolderName,
                    totalPages = childImages.size,
                    dateAdded = currentMod.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    tags = parsed.tags.joinToString(","),
                )
                onItemsFound?.invoke(listOf(comic), emptyList(), emptyList())
                synchronized(comics) {
                    comics.add(comic)
                }
            } else {
                val comicStub = Comic(title = "", folderUri = folderUriStr, coverUri = "", totalPages = childImages.size)
                onItemsFound?.invoke(listOf(comicStub), emptyList(), emptyList())
                synchronized(comics) {
                    comics.add(comicStub)
                }
            }
        }

        updatedFolderCache[folderUriStr] = currentMod

        val subdirs = children.filter { it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) }
        if (subdirs.isNotEmpty()) {
            coroutineScope {
                subdirs.chunked(4).forEach { batch ->
                    checkControl()
                    batch.map { dir ->
                        launch {
                            findComicFoldersFast(context, treeUri, dir.documentId, dir.name, comics, settings, currentDepth + 1, maxDepth, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, dir.lastModified, onItemsFound)
                        }
                    }.joinAll()
                }
            }
        }
    }

    private suspend fun scanSafTreeForGalleryFast(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        parentFolderName: String,
        galleryItems: MutableList<GalleryItem>,
        settings: AppSettings,
        currentDepth: Int = 0,
        maxDepth: Int = 100,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        if (currentDepth > maxDepth) return
        checkControl()

        val folderUriStr = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId).toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else getFolderLastModified(context, treeUri, parentDocId)
        val cachedMod = folderCache[folderUriStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUriStr] = currentMod
            return
        }

        onProgress?.invoke(parentFolderName)

        val children = listChildrenFast(context, treeUri, parentDocId)
        val files = children.filter { !it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) }
        val dirs = children.filter { it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) }

        val folderFoundItems = mutableListOf<GalleryItem>()

        for (file in files) {
            if (file.name.startsWith("._")) continue
            if (knownUris.contains(file.uriStr)) continue

            checkControl()
            val name = file.name
            onProgress?.invoke(name)
            val mime = file.mimeType
            if (MediaUtils.isImageMime(mime) || MediaUtils.isImageExt(MediaUtils.getFileExt(name))) {
                val parsed = parseNameMetadata(name, settings)
                val item = GalleryItem(
                    uri = file.uriStr,
                    name = parsed.title,
                    mimeType = MediaUtils.getMimeTypeFromExt(MediaUtils.getFileExt(name)),
                    size = file.size,
                    dateAdded = file.lastModified,
                    folderName = parentFolderName,
                    tags = parsed.tags.joinToString(","),
                    width = 0,
                    height = 0,
                )
                folderFoundItems.add(item)
                synchronized(galleryItems) {
                    galleryItems.add(item)
                }

                if (folderFoundItems.size >= 100) {
                    onItemsFound?.invoke(emptyList(), folderFoundItems.toList(), emptyList())
                    folderFoundItems.clear()
                }
            }
        }

        if (folderFoundItems.isNotEmpty()) {
            onItemsFound?.invoke(emptyList(), folderFoundItems, emptyList())
        }

        updatedFolderCache[folderUriStr] = currentMod

        if (dirs.isNotEmpty()) {
            coroutineScope {
                dirs.chunked(4).forEach { batch ->
                    checkControl()
                    batch.map { dir ->
                        launch {
                            scanSafTreeForGalleryFast(context, treeUri, dir.documentId, dir.name, galleryItems, settings, currentDepth + 1, maxDepth, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, dir.lastModified, onItemsFound)
                        }
                    }.joinAll()
                }
            }
        }
    }

    private suspend fun scanSafTreeForMoviesFast(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        parentFolderName: String,
        movies: MutableList<Movie>,
        settings: AppSettings,
        currentDepth: Int = 0,
        maxDepth: Int = 100,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        if (currentDepth > maxDepth) return
        checkControl()

        val folderUriStr = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId).toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else getFolderLastModified(context, treeUri, parentDocId)
        val cachedMod = folderCache[folderUriStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUriStr] = currentMod
            return
        }

        onProgress?.invoke(parentFolderName)

        val children = listChildrenFast(context, treeUri, parentDocId)
        val files = children.filter { !it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) }
        val dirs = children.filter { it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) }

        val folderFoundItems = mutableListOf<Movie>()

        for (file in files) {
            if (file.name.startsWith("._")) continue
            if (knownUris.contains(file.uriStr)) continue

            checkControl()
            val name = file.name
            onProgress?.invoke(name)
            val mime = file.mimeType
            if (MediaUtils.isVideoMime(mime) || MediaUtils.isVideoExt(MediaUtils.getFileExt(name))) {
                val parsed = parseNameMetadata(name, settings)
                val movie = Movie(
                    title = parsed.title,
                    uri = file.uriStr,
                    size = file.size,
                    dateAdded = file.lastModified,
                    folderName = parentFolderName,
                    tags = parsed.tags.joinToString(","),
                )
                folderFoundItems.add(movie)
                synchronized(movies) {
                    movies.add(movie)
                }

                if (folderFoundItems.size >= 50) {
                    onItemsFound?.invoke(emptyList(), emptyList(), folderFoundItems.toList())
                    folderFoundItems.clear()
                }
            }
        }

        if (folderFoundItems.isNotEmpty()) {
            onItemsFound?.invoke(emptyList(), emptyList(), folderFoundItems)
        }

        updatedFolderCache[folderUriStr] = currentMod

        if (dirs.isNotEmpty()) {
            coroutineScope {
                dirs.chunked(4).forEach { batch ->
                    checkControl()
                    batch.map { dir ->
                        launch {
                            scanSafTreeForMoviesFast(context, treeUri, dir.documentId, dir.name, movies, settings, currentDepth + 1, maxDepth, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, dir.lastModified, onItemsFound)
                        }
                    }.joinAll()
                }
            }
        }
    }

    suspend fun scanLocalDirectory(
        context: Context,
        folderPathOrUri: String,
        settings: AppSettings,
        scanType: String = "",
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ): Triple<List<Comic>, List<GalleryItem>, List<Movie>> = withContext(Dispatchers.IO) {
        val comics = mutableListOf<Comic>()
        val galleryItems = mutableListOf<GalleryItem>()
        val movies = mutableListOf<Movie>()

        if (folderPathOrUri.startsWith("content://")) {
            val treeUri = folderPathOrUri.toUri()
            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
                var rootName = "Folder"
                context.contentResolver.query(documentUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        rootName = cursor.getString(0) ?: "Folder"
                    }
                }
                when (scanType) {
                    "comics" -> findComicFoldersFast(context, treeUri, rootDocId, rootName, comics, settings, onProgress = onProgress, checkControl = checkControl, knownUris = knownUris, folderCache = folderCache, updatedFolderCache = updatedFolderCache, onItemsFound = onItemsFound)
                    "gallery" -> scanSafTreeForGalleryFast(context, treeUri, rootDocId, rootName, galleryItems, settings, onProgress = onProgress, checkControl = checkControl, knownUris = knownUris, folderCache = folderCache, updatedFolderCache = updatedFolderCache, onItemsFound = onItemsFound)
                    "movies" -> scanSafTreeForMoviesFast(context, treeUri, rootDocId, rootName, movies, settings, onProgress = onProgress, checkControl = checkControl, knownUris = knownUris, folderCache = folderCache, updatedFolderCache = updatedFolderCache, onItemsFound = onItemsFound)
                    else -> {
                        scanSafTreeAllFast(context, treeUri, rootDocId, rootName, comics, galleryItems, movies, settings, onProgress = onProgress, checkControl = checkControl, knownUris = knownUris, folderCache = folderCache, updatedFolderCache = updatedFolderCache, onItemsFound = onItemsFound)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            }
        } else {
            val baseFile = File(folderPathOrUri)
            if (baseFile.exists() && baseFile.isDirectory) {
                val rootName = baseFile.name
                when (scanType) {
                    "comics" -> findComicFoldersLocal(baseFile, rootName, comics, settings, onProgress, checkControl, onItemsFound = onItemsFound)
                    "gallery" -> scanFileTreeForGallery(baseFile, galleryItems, settings, onProgress, checkControl, onItemsFound = onItemsFound)
                    "movies" -> scanFileTreeForMovies(baseFile, movies, settings, onProgress, checkControl, onItemsFound = onItemsFound)
                    else -> {
                        scanFileTreeAllLocal(baseFile, rootName, comics, galleryItems, movies, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, onItemsFound = onItemsFound)
                    }
                }
            }
        }

        Triple(comics, galleryItems, movies)
    }

    private suspend fun scanSafTreeAllFast(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        parentFolderName: String,
        comics: MutableList<Comic>,
        gallery: MutableList<GalleryItem>,
        movies: MutableList<Movie>,
        settings: AppSettings,
        currentDepth: Int = 0,
        maxDepth: Int = 100,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        if (currentDepth > maxDepth) return
        checkControl()

        val folderUriStr = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId).toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else getFolderLastModified(context, treeUri, parentDocId)
        val cachedMod = folderCache[folderUriStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUriStr] = currentMod
            return
        }

        val children = listChildrenFast(context, treeUri, parentDocId)
        var hasImagesInThisFolder = false
        val subDirs = mutableListOf<SimpleDocument>()

        val folderGallery = mutableListOf<GalleryItem>()
        val folderMovies = mutableListOf<Movie>()

        for (item in children) {
            checkControl()
            if (item.mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR, true)) {
                subDirs.add(item)
            } else {
                val isImage = MediaUtils.isImageMime(item.mimeType) || MediaUtils.isImageExt(MediaUtils.getFileExt(item.name))
                val isVideo = MediaUtils.isVideoMime(item.mimeType) || MediaUtils.isVideoExt(MediaUtils.getFileExt(item.name))

                if (isImage) hasImagesInThisFolder = true

                if (knownUris.contains(item.uriStr)) continue

                if (isImage) {
                    val parsed = parseNameMetadata(item.name, settings)
                    val galleryItem = GalleryItem(
                        uri = item.uriStr,
                        name = parsed.title,
                        mimeType = MediaUtils.getMimeTypeFromExt(MediaUtils.getFileExt(item.name)),
                        size = item.size,
                        dateAdded = item.lastModified,
                        folderName = parentFolderName,
                        tags = parsed.tags.joinToString(","),
                        width = 0,
                        height = 0,
                    )
                    folderGallery.add(galleryItem)
                    synchronized(gallery) { gallery.add(galleryItem) }

                    if (folderGallery.size >= 100) {
                        onItemsFound?.invoke(emptyList(), folderGallery.toList(), emptyList())
                        folderGallery.clear()
                    }
                } else if (isVideo) {
                    val parsed = parseNameMetadata(item.name, settings)
                    val movie = Movie(
                        title = parsed.title,
                        uri = item.uriStr,
                        size = item.size,
                        dateAdded = item.lastModified,
                        folderName = parentFolderName,
                        tags = parsed.tags.joinToString(","),
                    )
                    folderMovies.add(movie)
                    synchronized(movies) { movies.add(movie) }

                    if (folderMovies.size >= 50) {
                        onItemsFound?.invoke(emptyList(), emptyList(), folderMovies.toList())
                        folderMovies.clear()
                    }
                }
            }
        }

        val folderComics = mutableListOf<Comic>()

        if (hasImagesInThisFolder) {
            val folderUriPrefix = if (folderUriStr.endsWith("/")) folderUriStr else "$folderUriStr/"
            synchronized(gallery) {
                gallery.removeAll { it.uri.startsWith(folderUriPrefix) }
            }

            val isKnown = knownUris.contains(folderUriStr)
            if (!isKnown) {
                val parsed = parseNameMetadata(parentFolderName, settings)
                val imageFiles = children.filter { !it.mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR, true) && (MediaUtils.isImageMime(it.mimeType) || MediaUtils.isImageExt(MediaUtils.getFileExt(it.name))) }
                val cover = imageFiles.minWithOrNull(compareBy(NaturalOrderComparator) { it.name })?.uriStr ?: (if (imageFiles.isNotEmpty()) imageFiles.first().uriStr else "")

                val comic = Comic(
                    title = parsed.title,
                    folderUri = folderUriStr,
                    coverUri = cover,
                    parentFolderName = parentFolderName,
                    totalPages = imageFiles.size,
                    dateAdded = currentMod.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    tags = parsed.tags.joinToString(","),
                )
                folderComics.add(comic)
                synchronized(comics) { comics.add(comic) }
            } else {
                val imageCount = children.count { !it.mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR, true) && (MediaUtils.isImageMime(it.mimeType) || MediaUtils.isImageExt(MediaUtils.getFileExt(it.name))) }
                val comicStub = Comic(title = "", folderUri = folderUriStr, coverUri = "", totalPages = imageCount)
                folderComics.add(comicStub)
                synchronized(comics) { comics.add(comicStub) }
            }
        }

        if (folderComics.isNotEmpty() || folderGallery.isNotEmpty() || folderMovies.isNotEmpty()) {
            onItemsFound?.invoke(folderComics, folderGallery, folderMovies)
        }

        updatedFolderCache[folderUriStr] = currentMod
        onProgress?.invoke(parentFolderName)

        if (subDirs.isNotEmpty()) {
            coroutineScope {
                subDirs.chunked(4).forEach { batch ->
                    checkControl()
                    batch.map { dir ->
                        launch {
                            scanSafTreeAllFast(context, treeUri, dir.documentId, dir.name, comics, gallery, movies, settings, currentDepth + 1, maxDepth, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, dir.lastModified, onItemsFound)
                        }
                    }.joinAll()
                }
            }
        }
    }

    private suspend fun scanFileTreeAllLocal(
        dir: File,
        parentFolderName: String,
        comics: MutableList<Comic>,
        gallery: MutableList<GalleryItem>,
        movies: MutableList<Movie>,
        settings: AppSettings,
        onProgress: ((String) -> Unit)?,
        checkControl: suspend () -> Unit,
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        val folderUri = Uri.fromFile(dir).toString()

        val currentMod = dir.lastModified()
        val cachedMod = folderCache[folderUri]
        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUri] = currentMod
            return
        }

        val files = dir.listFiles() ?: return
        var hasImagesInThisFolder = false

        val subdirs = mutableListOf<File>()

        val folderComics = mutableListOf<Comic>()
        val folderGallery = mutableListOf<GalleryItem>()
        val folderMovies = mutableListOf<Movie>()

        for (file in files) {
            checkControl()
            if (file.name.startsWith("._")) continue
            val uri = Uri.fromFile(file).toString()

            if (file.isDirectory) {
                subdirs.add(file)
            } else {
                if (knownUris.contains(uri)) {
                    if (MediaUtils.isImageExt(MediaUtils.getFileExt(file.name))) {
                        hasImagesInThisFolder = true
                    }
                    continue
                }

                val name = file.name
                onProgress?.invoke(name)
                val ext = MediaUtils.getFileExt(name)

                if (MediaUtils.isArchiveExt(ext)) {
                    val comic = getZipComicMetadata(file, settings)
                    if (comic != null) {
                        folderComics.add(comic)
                        synchronized(comics) { comics.add(comic) }
                    }
                } else if (MediaUtils.isImageExt(ext)) {
                    hasImagesInThisFolder = true
                    val parsed = parseNameMetadata(name, settings)
                    val item = GalleryItem(
                        uri = uri,
                        name = parsed.title,
                        mimeType = MediaUtils.getMimeTypeFromExt(ext),
                        size = file.length(),
                        dateAdded = file.lastModified(),
                        folderName = parentFolderName,
                        tags = parsed.tags.joinToString(","),
                        width = 0,
                        height = 0,
                    )
                    folderGallery.add(item)
                    synchronized(gallery) {
                        gallery.add(item)
                    }

                    if (folderGallery.size >= 100) {
                        onItemsFound?.invoke(emptyList(), folderGallery.toList(), emptyList())
                        folderGallery.clear()
                    }
                } else if (MediaUtils.isVideoExt(ext)) {
                    val parsed = parseNameMetadata(name, settings)
                    val movie = Movie(
                        title = parsed.title,
                        uri = uri,
                        size = file.length(),
                        dateAdded = file.lastModified(),
                        folderName = parentFolderName,
                        tags = parsed.tags.joinToString(","),
                    )
                    folderMovies.add(movie)
                    synchronized(movies) {
                        movies.add(movie)
                    }

                    if (folderMovies.size >= 50) {
                        onItemsFound?.invoke(emptyList(), emptyList(), folderMovies.toList())
                        folderMovies.clear()
                    }
                }
            }
        }

        if (hasImagesInThisFolder) {
            val isKnown = knownUris.contains(folderUri)
            if (!isKnown) {
                val parsed = parseNameMetadata(dir.name, settings)
                val imageFiles = files.filter { !it.isDirectory && MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
                val cover = imageFiles.minWithOrNull(compareBy(NaturalOrderComparator) { it.name })?.let { Uri.fromFile(it).toString() } ?: (if (imageFiles.isNotEmpty()) Uri.fromFile(imageFiles.first()).toString() else "")

                val comic = Comic(
                    title = parsed.title,
                    folderUri = folderUri,
                    coverUri = cover,
                    parentFolderName = parentFolderName,
                    totalPages = imageFiles.size,
                    dateAdded = dir.lastModified(),
                    tags = parsed.tags.joinToString(","),
                )
                folderComics.add(comic)
                synchronized(comics) {
                    comics.add(comic)
                }
            }
        }

        if (folderComics.isNotEmpty() || folderGallery.isNotEmpty() || folderMovies.isNotEmpty()) {
            onItemsFound?.invoke(folderComics, folderGallery, folderMovies)
        }

        updatedFolderCache[folderUri] = currentMod
        onProgress?.invoke(parentFolderName)

        if (subdirs.isNotEmpty()) {
            coroutineScope {
                subdirs.chunked(4).forEach { batch ->
                    checkControl()
                    batch.map { file ->
                        launch {
                            scanFileTreeAllLocal(file, file.name, comics, gallery, movies, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, onItemsFound)
                        }
                    }.joinAll()
                }
            }
        }
    }

    private suspend fun findComicFoldersLocal(
        dir: File,
        parentFolderName: String,
        comics: MutableList<Comic>,
        settings: AppSettings,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        onProgress?.invoke(dir.name)
        val files = dir.listFiles() ?: return
        val childImages = files.filter { !it.isDirectory && MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
        if (childImages.isNotEmpty()) {
            val sortedImages = childImages.minWithOrNull(compareBy(NaturalOrderComparator) { it.name })
            val cover = sortedImages?.let { Uri.fromFile(it).toString() } ?: Uri.fromFile(childImages.first()).toString()
            val parsed = parseNameMetadata(dir.name, settings)
            val comic = Comic(
                title = parsed.title,
                folderUri = Uri.fromFile(dir).toString(),
                coverUri = cover,
                parentFolderName = parentFolderName,
                totalPages = childImages.size,
                dateAdded = dir.lastModified(),
                tags = parsed.tags.joinToString(","),
            )
            onItemsFound?.invoke(listOf(comic), emptyList(), emptyList())
            synchronized(comics) {
                comics.add(comic)
            }
        }
        if (childImages.isEmpty()) {
            val subdirs = files.filter { it.isDirectory }
            if (subdirs.isNotEmpty()) {
                coroutineScope {
                    subdirs.chunked(4).forEach { batch ->
                        checkControl()
                        batch.map { file ->
                            launch {
                                findComicFoldersLocal(file, file.name, comics, settings, onProgress, checkControl, onItemsFound)
                            }
                        }.joinAll()
                    }
                }
            }
        }
    }

    private suspend fun scanFileTreeForGallery(
        dir: File,
        galleryItems: MutableList<GalleryItem>,
        settings: AppSettings,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        onProgress?.invoke(dir.name)
        val files = dir.listFiles() ?: return
        val foundItems = mutableListOf<GalleryItem>()
        for (file in files) {
            checkControl()
            if (file.isDirectory) {
                scanFileTreeForGallery(file, galleryItems, settings, onProgress, checkControl, onItemsFound)
            } else {
                val name = file.name
                onProgress?.invoke(name)
                val ext = MediaUtils.getFileExt(name)
                if (MediaUtils.isImageExt(ext)) {
                    val parsed = parseNameMetadata(name, settings)
                    val item = GalleryItem(
                        uri = Uri.fromFile(file).toString(),
                        name = parsed.title,
                        mimeType = MediaUtils.getMimeTypeFromExt(ext),
                        size = file.length(),
                        dateAdded = file.lastModified(),
                        folderName = dir.name,
                        tags = parsed.tags.joinToString(","),
                        width = 0,
                        height = 0,
                    )
                    foundItems.add(item)
                    galleryItems.add(item)

                    if (foundItems.size >= 100) {
                        onItemsFound?.invoke(emptyList(), foundItems.toList(), emptyList())
                        foundItems.clear()
                    }
                }
            }
        }
        if (foundItems.isNotEmpty()) {
            onItemsFound?.invoke(emptyList(), foundItems, emptyList())
        }
    }

    private suspend fun scanFileTreeForMovies(
        dir: File,
        movies: MutableList<Movie>,
        settings: AppSettings,
        onProgress: ((String) -> Unit)? = null,
        checkControl: suspend () -> Unit = {},
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        onProgress?.invoke(dir.name)
        val files = dir.listFiles() ?: return
        val foundMovies = mutableListOf<Movie>()
        for (file in files) {
            checkControl()
            if (file.isDirectory) {
                scanFileTreeForMovies(file, movies, settings, onProgress, checkControl, onItemsFound)
            } else {
                val name = file.name
                onProgress?.invoke(name)
                val ext = MediaUtils.getFileExt(name)
                if (MediaUtils.isVideoExt(ext)) {
                    val parsed = parseNameMetadata(name, settings)
                    val movie = Movie(
                        title = parsed.title,
                        uri = Uri.fromFile(file).toString(),
                        size = file.length(),
                        dateAdded = file.lastModified(),
                        folderName = dir.name,
                        tags = parsed.tags.joinToString(","),
                    )
                    foundMovies.add(movie)
                    movies.add(movie)

                    if (foundMovies.size >= 50) {
                        onItemsFound?.invoke(emptyList(), emptyList(), foundMovies.toList())
                        foundMovies.clear()
                    }
                }
            }
        }
        if (foundMovies.isNotEmpty()) {
            onItemsFound?.invoke(emptyList(), emptyList(), foundMovies)
        }
    }

    data class ParsedMediaMetadata(
        val id: String = "",
        val title: String,
        val tags: List<String> = emptyList(),
    )

    fun parseNameMetadata(fileName: String, settings: AppSettings): ParsedMediaMetadata {
        val nameWithoutExt = if (fileName.contains(".")) {
            fileName.substringBeforeLast(".")
        } else {
            fileName
        }

        val separator = settings.tagSeparator.ifEmpty { "-" }
        val delimiter = settings.tagDelimiter.ifEmpty { "," }
        val lowercase = settings.lowercaseTags
        val stripId = settings.stripNumericId

        var parsedId = ""
        var parsedTitle = nameWithoutExt
        val parsedTags = mutableListOf<String>()

        val format = settings.scanFormat

        fun extractIdAndClean(text: String): Pair<String, String> {
            val trimmed = text.trim()
            val matchResult = Regex("^(\\d+)[\\s_\\.\\-]*").find(trimmed)
            if (matchResult != null) {
                val id = matchResult.groups[1]?.value ?: ""
                val cleanTitle = trimmed.substring(matchResult.value.length).trim()
                return Pair(id, cleanTitle)
            }
            return Pair("", trimmed)
        }

        fun processTags(tagsStr: String) {
            val rawTags = tagsStr.split(delimiter)
            for (t in rawTags) {
                val cleanT = t.trim()
                if (cleanT.isNotEmpty()) {
                    parsedTags.add(if (lowercase) cleanT.lowercase() else cleanT)
                }
            }
        }

        try {
            if (format == "[Bracket] tags - Title") {
                if (nameWithoutExt.contains("[") && nameWithoutExt.contains("]")) {
                    val startBracket = nameWithoutExt.indexOf("[")
                    val endBracket = nameWithoutExt.indexOf("]")
                    if (endBracket > startBracket) {
                        val tagPart = nameWithoutExt.substring(startBracket + 1, endBracket)
                        processTags(tagPart)

                        var remainder = nameWithoutExt.substring(endBracket + 1).trim()
                        if (remainder.startsWith(separator)) {
                            remainder = remainder.substring(separator.length).trim()
                        }

                        val (extractedId, cleanTitle) = extractIdAndClean(remainder)
                        parsedId = extractedId
                        parsedTitle = if (stripId) cleanTitle else remainder
                    }
                } else {
                    val (extractedId, cleanTitle) = extractIdAndClean(nameWithoutExt)
                    parsedId = extractedId
                    parsedTitle = if (stripId) cleanTitle else nameWithoutExt
                }
            } else if (nameWithoutExt.contains(separator)) {
                val parts = nameWithoutExt.split(separator, limit = 2)
                val firstPart = parts[0].trim()
                val secondPart = parts[1].trim()

                when (format) {
                    "ID Title - tags" -> {
                        val (extractedId, cleanTitle) = extractIdAndClean(firstPart)
                        parsedId = extractedId
                        parsedTitle = if (stripId) cleanTitle else firstPart
                        processTags(secondPart)
                    }
                    "Title - tags" -> {
                        val (extractedId, cleanTitle) = extractIdAndClean(firstPart)
                        parsedId = extractedId
                        parsedTitle = if (stripId) cleanTitle else firstPart
                        processTags(secondPart)
                    }
                    "ID tags - Title" -> {
                        val (extractedId, cleanTagsPart) = extractIdAndClean(firstPart)
                        parsedId = extractedId
                        processTags(cleanTagsPart)

                        val (_, cleanTitle) = extractIdAndClean(secondPart)
                        parsedTitle = if (stripId) cleanTitle else secondPart
                    }
                    "tags - Title" -> {
                        processTags(firstPart)
                        val (extractedId, cleanTitle) = extractIdAndClean(secondPart)
                        parsedId = extractedId
                        parsedTitle = if (stripId) cleanTitle else secondPart
                    }
                    else -> {
                        val (extractedId, cleanTitle) = extractIdAndClean(firstPart)
                        parsedId = extractedId
                        parsedTitle = if (stripId) cleanTitle else firstPart
                        processTags(secondPart)
                    }
                }
            } else {
                val (extractedId, cleanTitle) = extractIdAndClean(nameWithoutExt)
                parsedId = extractedId
                parsedTitle = if (stripId) cleanTitle else nameWithoutExt
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ParsedMediaMetadata(
            id = parsedId,
            title = parsedTitle.ifEmpty { nameWithoutExt },
            tags = parsedTags,
        )
    }

    private fun getZipComicMetadata(file: File, settings: AppSettings): Comic? {
        return try {
            ZipFile(file).use { zip ->
                val imageEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
                    .sortedWith(compareBy(NaturalOrderComparator) { it.name })
                    .toList()

                if (imageEntries.isNotEmpty()) {
                    val parsed = parseNameMetadata(file.name, settings)
                    val cover = ZipContentProvider.buildUri(file.absolutePath, imageEntries.first().name)
                    Comic(
                        title = parsed.title,
                        folderUri = Uri.fromFile(file).toString(),
                        coverUri = cover,
                        parentFolderName = file.parentFile?.name ?: "Lainnya",
                        totalPages = imageEntries.size,
                        dateAdded = file.lastModified(),
                        tags = parsed.tags.joinToString(","),
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadComicPages(context: Context, folderUriStr: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<ComicPage>()
        val uri = Uri.parse(folderUriStr)

        if (folderUriStr.startsWith("content://")) {
            try {
                val treeUriStr = folderUriStr.substringBefore("/document/")
                val docIdStr = Uri.decode(folderUriStr.substringAfter("/document/"))
                val treeUri = treeUriStr.toUri()

                val children = listChildrenFast(context, treeUri, docIdStr)
                var pIdx = 1
                children.asSequence()
                    .filter { !it.mimeType.equals(other = DocumentsContract.Document.MIME_TYPE_DIR, ignoreCase = true) && (MediaUtils.isImageMime(it.mimeType) || MediaUtils.isImageExt(MediaUtils.getFileExt(it.name))) }
                    .sortedWith(compareBy(NaturalOrderComparator) { it.name })
                    .forEach { file ->
                        pages.add(
                            ComicPage(
                                comicId = 0,
                                pageIndex = pIdx++,
                                pageUri = file.uriStr,
                                pageName = file.name,
                            )
                        )
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val baseFile = File(uri.path ?: "")
            if (baseFile.exists()) {
                if (baseFile.isDirectory) {
                    var pIdx = 1
                    baseFile.listFiles()
                        ?.filter { MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
                        ?.sortedWith(compareBy(NaturalOrderComparator) { it.name })
                        ?.forEach { file ->
                            pages.add(
                                ComicPage(
                                    comicId = 0,
                                    pageIndex = pIdx++,
                                    pageUri = Uri.fromFile(file).toString(),
                                    pageName = file.name,
                                )
                            )
                        }
                } else if (MediaUtils.isArchiveExt(MediaUtils.getFileExt(baseFile.name))) {
                    try {
                        ZipFile(baseFile).use { zip ->
                            var pIdx = 1
                            zip.entries().asSequence()
                                .filter { !it.isDirectory && MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
                                .sortedWith(compareBy(NaturalOrderComparator) { it.name })
                                .forEach { entry ->
                                    pages.add(
                                        ComicPage(
                                            comicId = 0,
                                            pageIndex = pIdx++,
                                            pageUri = ZipContentProvider.buildUri(baseFile.absolutePath, entry.name),
                                            pageName = entry.name,
                                        )
                                    )
                                }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        pages
    }
}
