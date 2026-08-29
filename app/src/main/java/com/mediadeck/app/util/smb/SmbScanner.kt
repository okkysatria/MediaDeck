package com.mediadeck.app.util.smb

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.util.scan.LocalScanner
import com.mediadeck.app.util.scan.NaturalOrderComparator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SmbFileItem(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
)

object SmbScanner {

    fun toContentProviderUri(smbUrl: String): String {
        var path = smbUrl.removePrefix("smb://")
        
        val atIndex = path.indexOf('@')
        val firstSlash = path.indexOf('/')
        if (atIndex != -1 && (firstSlash == -1 || atIndex < firstSlash)) {
            path = path.substring(atIndex + 1)
        }
        
        return "content://${SmbContentProvider.AUTHORITY}/${path}"
    }

    fun buildSmbUrl(host: String, port: String, share: String, subpath: String): String {
        val portSuffix = if (port.isNotEmpty() && port != "445") ":$port" else ""
        val sharePart = share.trim().trim('/')
        val subPart = subpath.trim().trim('/')
        val baseUrl = "smb://$host$portSuffix/$sharePart"
        return if (subPart.isEmpty()) "$baseUrl/" else "$baseUrl/$subPart/"
    }

    suspend fun listSmbPath(
        context: Context,
        settings: AppSettings,
        shareName: String = "",
        subpath: String = "",
    ): List<SmbFileItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SmbFileItem>()

        val serverIp = settings.smbHost.trim().trim('/')
        if (serverIp.isEmpty()) return@withContext emptyList()

        val portSuffix = if (settings.smbPort.isNotEmpty() && settings.smbPort != "445") ":${settings.smbPort}" else ""

        val baseUrl = if (shareName.isEmpty()) {
            "smb://$serverIp$portSuffix/"
        } else {
            val formattedShare = shareName.trim().trim('/')
            val formattedSub = subpath.trim().trim('/')
            if (formattedSub.isEmpty()) {
                "smb://$serverIp$portSuffix/$formattedShare/"
            } else {
                "smb://$serverIp$portSuffix/$formattedShare/$formattedSub/"
            }
        }

        try {
            val dir = SmbConnectionManager.getSmbFile(context, baseUrl, settings)
            if (dir.exists() && dir.isDirectory) {
                val list = try { dir.listFiles() } catch (_: Exception) { null }
                if (list != null) {
                    for (file in list) {
                        val rawName = file.name
                        val cleanName = rawName.trim('/')
                        if (cleanName.isEmpty()) continue
                        if (shareName.isEmpty()) {
                            if (cleanName.endsWith("$") || cleanName == "IPC") continue
                        }
                        items.add(
                            SmbFileItem(
                                name = cleanName,
                                url = file.url.toString(),
                                isDirectory = file.isDirectory,
                                size = if (file.isFile) file.length() else 0L,
                                lastModified = file.lastModified(),
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmbScanner", "Gagal membaca SMB path $baseUrl", e)
            throw e
        }

        items.sortedWith(compareBy<SmbFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun scanSmbDirectory(
        context: Context,
        settings: AppSettings,
        shareNameParam: String = "",
        subpath: String = "",
        scanType: String = "",
        onProgress: (String) -> Unit = {},
        checkControl: suspend () -> Unit = {},
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        rootUrl: String? = null,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ): Triple<List<Comic>, List<GalleryItem>, List<Movie>> = withContext(Dispatchers.IO) {
        val comics = mutableListOf<Comic>()
        val gallery = mutableListOf<GalleryItem>()
        val movies = mutableListOf<Movie>()

        val rootSmbUrl = if (!rootUrl.isNullOrEmpty()) {
            if (rootUrl.endsWith("/")) rootUrl else "$rootUrl/"
        } else {
            val serverIp = settings.smbHost.trim().trim('/')
            val shareName = shareNameParam.trim().trim('/').ifEmpty { settings.smbShare.trim().trim('/') }
            if (serverIp.isEmpty() || shareName.isEmpty()) {
                return@withContext Triple(emptyList(), emptyList(), emptyList())
            }
            val portSuffix = if (settings.smbPort.isNotEmpty() && settings.smbPort != "445") ":${settings.smbPort}" else ""
            val relPath = subpath.trim().trim('/')
            val pathSuffix = if (relPath.isEmpty()) "" else "$relPath/"
            "smb://$serverIp$portSuffix/$shareName/$pathSuffix"
        }

        try {
            val rootFile = SmbConnectionManager.getSmbFile(context, rootSmbUrl, settings)
            if (rootFile.exists() && rootFile.isDirectory) {
                val uri = Uri.parse(rootSmbUrl)
                val shareName = if (!rootUrl.isNullOrEmpty()) {
                    uri.pathSegments.lastOrNull()?.trim('/') ?: settings.smbShare
                } else {
                    shareNameParam.ifEmpty { settings.smbShare }
                }

                when (scanType) {
                    "comics" -> findComicFoldersSmb(context, rootFile, shareName, comics, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, onItemsFound = onItemsFound)
                    "gallery" -> scanFileTreeForGallerySmb(context, rootFile, shareName, gallery, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, onItemsFound = onItemsFound)
                    "movies" -> scanFileTreeForMoviesSmb(context, rootFile, shareName, movies, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, onItemsFound = onItemsFound)
                    else -> {
                        scanFileTreeAllSmb(context, rootFile, shareName, comics, gallery, movies, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, onItemsFound = onItemsFound)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e.cause is kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException("Scan dibatalkan", e)
            }
            Log.e("SmbScanner", "Gagal memindai $rootSmbUrl", e)
            throw e
        }

        Triple(comics, gallery, movies)
    }

    private suspend fun scanFileTreeAllSmb(
        context: Context,
        dir: SmbFile,
        parentFolderName: String,
        comics: MutableList<Comic>,
        gallery: MutableList<GalleryItem>,
        movies: MutableList<Movie>,
        settings: AppSettings,
        onProgress: (String) -> Unit,
        checkControl: suspend () -> Unit,
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()

        val folderUrlStr = dir.url.toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else try { dir.lastModified() } catch (_: Exception) { 0L }
        val cachedMod = folderCache[folderUrlStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUrlStr] = currentMod
            return
        }

        val files = try {
            dir.listFiles()
        } catch (_: Exception) {
            onProgress("Error reading folder")
            null
        } ?: return

        val subDirs = mutableListOf<SmbFile>()
        
        val folderGallery = mutableListOf<GalleryItem>()
        val folderMovies = mutableListOf<Movie>()

        for (file in files) {
            checkControl()
            if (file.name.startsWith("._")) continue

            val fileUrlStr = file.url.toString()
            onProgress(file.name)
            val cpUri = toContentProviderUri(fileUrlStr)

            if (file.isDirectory) {
                subDirs.add(file)
            } else if (file.isFile) {
                if (knownUris.contains(cpUri) || knownUris.contains(fileUrlStr)) continue

                val name = file.name
                val ext = MediaUtils.getFileExt(name)

                if (MediaUtils.isImageExt(ext)) {
                    val parsed = LocalScanner.parseNameMetadata(name, settings)
                    val mType = MediaUtils.getMimeTypeFromExt(ext)

                    val item = GalleryItem(
                        uri = cpUri,
                        name = parsed.title,
                        mimeType = mType,
                        size = file.length(),
                        dateAdded = file.lastModified(),
                        folderName = parentFolderName,
                        tags = parsed.tags.joinToString(","),
                        width = 0,
                        height = 0,
                    )
                    folderGallery.add(item)
                    synchronized(gallery) { gallery.add(item) }
                    
                    if (folderGallery.size >= 100) {
                        onItemsFound?.invoke(emptyList(), folderGallery.toList(), emptyList())
                        folderGallery.clear()
                    }
                } else if (MediaUtils.isVideoExt(ext)) {
                    val parsed = LocalScanner.parseNameMetadata(name, settings)
                    val movieItem = Movie(
                        title = parsed.title,
                        uri = cpUri,
                        size = file.length(),
                        dateAdded = file.lastModified(),
                        folderName = parentFolderName,
                        tags = parsed.tags.joinToString(","),
                    )
                    folderMovies.add(movieItem)
                    synchronized(movies) { movies.add(movieItem) }
                    
                    if (folderMovies.size >= 50) {
                        onItemsFound?.invoke(emptyList(), emptyList(), folderMovies.toList())
                        folderMovies.clear()
                    }
                }
            }
        }

        val imageFilesRaw = files.filter { it.isFile && MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
        val filteredImages = imageFilesRaw.filter { 
            val name = it.name.lowercase()
            !name.startsWith("folder.") && !name.startsWith("cover.") && !name.startsWith("albumart") && !name.contains("banner")
        }
        
        val enoughImages = filteredImages.size >= 2 || (filteredImages.size == 1 && subDirs.isEmpty())
        val folderUrlStrNorm = folderUrlStr.removeSuffix("/")
        val isRootComicFolder = settings.comicFolders.split(",").any { it.removeSuffix("/") == folderUrlStrNorm }
        
        if (enoughImages && !isRootComicFolder) {
            val cpBase = toContentProviderUri(folderUrlStr)
            val folderUriPrefix = if (cpBase.endsWith("/")) cpBase else "$cpBase/"
            synchronized(gallery) {
                gallery.removeAll { it.uri.startsWith(folderUriPrefix) }
            }

            val isKnown = knownUris.any { it.removeSuffix("/") == folderUrlStrNorm }
            if (!isKnown) {
                val parsed = LocalScanner.parseNameMetadata(parentFolderName, settings)
                
                val firstImage = filteredImages.minWithOrNull(compareBy(NaturalOrderComparator) { it.name }) ?: (if (filteredImages.isNotEmpty()) filteredImages.first() else null)
                val cover = firstImage?.let { toContentProviderUri(it.url.toString()) } ?: ""

                val comic = Comic(
                    title = parsed.title,
                    folderUri = folderUrlStr,
                    coverUri = cover,
                    parentFolderName = parentFolderName,
                    totalPages = filteredImages.size,
                    dateAdded = currentMod.takeIf { it > 0 } ?: dir.lastModified(),
                    tags = parsed.tags.joinToString(","),
                )
                comics.add(comic) 
                onItemsFound?.invoke(listOf(comic), emptyList(), emptyList())
            } else {
                val comicStub = Comic(title = "", folderUri = folderUrlStr, coverUri = "", totalPages = filteredImages.size)
                onItemsFound?.invoke(listOf(comicStub), emptyList(), emptyList())
            }
        }
        
        if (folderGallery.isNotEmpty() || folderMovies.isNotEmpty()) {
            onItemsFound?.invoke(emptyList(), folderGallery, folderMovies)
        }

        updatedFolderCache[folderUrlStr] = currentMod
        onProgress(parentFolderName)

        if (subDirs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                subDirs.chunked(6).map { batch -> 
                    launch {
                        batch.forEach { file ->
                            checkControl()
                            val nextFolderName = Uri.parse(file.url.toString()).lastPathSegment?.trim('/') ?: file.name.trim('/')
                            val lastMod = try { file.lastModified() } catch (_: Exception) { -1L }
                            scanFileTreeAllSmb(context, file, nextFolderName, comics, gallery, movies, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, lastMod, onItemsFound)
                        }
                    }
                }.joinAll()
            }
        }
    }

    private suspend fun findComicFoldersSmb(
        context: Context,
        dir: SmbFile,
        parentFolderName: String,
        comics: MutableList<Comic>,
        settings: AppSettings,
        onProgress: (String) -> Unit,
        checkControl: suspend () -> Unit,
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        val folderUrlStr = dir.url.toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else try { dir.lastModified() } catch (_: Exception) { 0L }
        val cachedMod = folderCache[folderUrlStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUrlStr] = currentMod
            return
        }

        try {
            val allFiles = dir.listFiles() ?: return
            val files = allFiles.filter { it.isFile && !it.name.startsWith("._") }
            val subDirs = allFiles.filter { it.isDirectory }
            
            val imageFiles = files.filter { MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
            
            val filteredImages = imageFiles.filter { 
                val name = it.name.lowercase()
                !name.startsWith("folder.") && !name.startsWith("cover.") && !name.startsWith("albumart") && !name.contains("banner")
            }
            
            val enoughImages = filteredImages.size >= 2 || (filteredImages.size == 1 && subDirs.isEmpty())
            val folderUrlStrNorm = folderUrlStr.removeSuffix("/")
            val isRootComicFolder = settings.comicFolders.split(",").any { it.removeSuffix("/") == folderUrlStrNorm }

            if (enoughImages && !isRootComicFolder) {
                val isKnown = knownUris.any { it.removeSuffix("/") == folderUrlStrNorm }
                if (!isKnown) {
                    val rawName = Uri.parse(dir.url.toString()).lastPathSegment?.trim('/') ?: dir.name.trim('/')
                    val parsed = LocalScanner.parseNameMetadata(rawName, settings)

                    onProgress(parentFolderName)

                    val firstImage = filteredImages.minWithOrNull(compareBy(NaturalOrderComparator) { it.name }) ?: filteredImages.first()
                    val cover = toContentProviderUri(firstImage.url.toString())

                    val comic = Comic(
                        title = parsed.title,
                        folderUri = folderUrlStr,
                        coverUri = cover,
                        parentFolderName = parentFolderName,
                        totalPages = filteredImages.size,
                        dateAdded = dir.lastModified(),
                        tags = parsed.tags.joinToString(","),
                    )
                    onItemsFound?.invoke(listOf(comic), emptyList(), emptyList())
                    synchronized(comics) { comics.add(comic) }
                }
            }

            updatedFolderCache[folderUrlStr] = currentMod

            if (subDirs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    subDirs.chunked(6).map { batch ->
                        launch {
                            batch.forEach { file ->
                                checkControl()
                                val nextFolderName = Uri.parse(file.url.toString()).lastPathSegment?.trim('/') ?: file.name.trim('/')
                                val lastMod = try { file.lastModified() } catch (_: Exception) { -1L }
                                findComicFoldersSmb(context, file, nextFolderName, comics, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, lastMod, onItemsFound)
                            }
                        }
                    }.joinAll()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e.cause is kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException("Scan dibatalkan", e)
            }
            Log.e("SmbScanner", "Gagal memindai folder $folderUrlStr", e)
            throw e
        }
    }

    private suspend fun scanFileTreeForGallerySmb(
        context: Context,
        dir: SmbFile,
        parentFolderName: String,
        gallery: MutableList<GalleryItem>,
        settings: AppSettings,
        onProgress: (String) -> Unit,
        checkControl: suspend () -> Unit,
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        val folderUrlStr = dir.url.toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else try { dir.lastModified() } catch (_: Exception) { 0L }
        val cachedMod = folderCache[folderUrlStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUrlStr] = currentMod
            return
        }

        try {
            val files = dir.listFiles() ?: return
            val subDirs = mutableListOf<SmbFile>()
            val folderItems = mutableListOf<GalleryItem>()
            
            for (file in files) {
                checkControl()
                if (file.name.startsWith("._")) continue
                if (file.isDirectory) {
                    subDirs.add(file)
                } else if (file.isFile) {
                    val fileUrlStr = file.url.toString()
                    val cpUri = toContentProviderUri(fileUrlStr)
                    if (knownUris.contains(cpUri) || knownUris.contains(fileUrlStr)) continue

                    val name = file.name
                    onProgress(name)
                    val ext = MediaUtils.getFileExt(name)
                    if (MediaUtils.isImageExt(ext)) {
                        val parsed = LocalScanner.parseNameMetadata(name, settings)
                        val mType = MediaUtils.getMimeTypeFromExt(ext)

                        onProgress(parentFolderName)

                        val item = GalleryItem(
                            uri = cpUri,
                            name = parsed.title,
                            mimeType = mType,
                            size = file.length(),
                            dateAdded = file.lastModified(),
                            folderName = parentFolderName,
                            tags = parsed.tags.joinToString(","),
                            width = 0,
                            height = 0,
                        )
                        folderItems.add(item)
                        synchronized(gallery) { gallery.add(item) }
                        
                        if (folderItems.size >= 100) {
                            onItemsFound?.invoke(emptyList(), folderItems.toList(), emptyList())
                            folderItems.clear()
                        }
                    }
                }
            }
            
            if (folderItems.isNotEmpty()) {
                onItemsFound?.invoke(emptyList(), folderItems, emptyList())
            }

            updatedFolderCache[folderUrlStr] = currentMod

            if (subDirs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    subDirs.chunked(6).map { batch ->
                        launch {
                            batch.forEach { file ->
                                checkControl()
                                val nextFolderName = Uri.parse(file.url.toString()).lastPathSegment?.trim('/') ?: file.name.trim('/')
                                val lastMod = try { file.lastModified() } catch (_: Exception) { -1L }
                                scanFileTreeForGallerySmb(context, file, nextFolderName, gallery, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, lastMod, onItemsFound)
                            }
                        }
                    }.joinAll()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e.cause is kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException("Scan dibatalkan", e)
            }
            Log.e("SmbScanner", "Gagal memindai folder $folderUrlStr", e)
            throw e
        }
    }

    suspend fun loadComicPagesSmb(
        context: Context,
        settings: AppSettings,
        smbFolderUrl: String,
    ): List<ComicPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<ComicPage>()
        try {
            val normalizedUrl = if (smbFolderUrl.endsWith("/")) smbFolderUrl else "$smbFolderUrl/"
            val rootFile = SmbConnectionManager.getSmbFile(context, normalizedUrl, settings)
            if (rootFile.exists() && rootFile.isDirectory) {
                val files = rootFile.listFiles() ?: return@withContext emptyList()
                var pIdx = 1
                files.filter { it.isFile && !it.name.startsWith("._") && MediaUtils.isImageExt(MediaUtils.getFileExt(it.name)) }
                    .sortedWith(compareBy(NaturalOrderComparator) { it.name })
                    .forEach { file ->
                        val fileUrl = file.url.toString()
                        val cpUri = toContentProviderUri(fileUrl)
                        android.util.Log.d("SmbScanner", "Comic Page: $fileUrl -> $cpUri")
                        pages.add(
                            ComicPage(
                                comicId = 0,
                                pageIndex = pIdx++,
                                pageUri = cpUri,
                                pageName = file.name,
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            Log.e("SmbScanner", "Gagal memuat halaman komik $smbFolderUrl", e)
        }
        pages
    }

    private suspend fun scanFileTreeForMoviesSmb(
        context: Context,
        dir: SmbFile,
        parentFolderName: String,
        movies: MutableList<Movie>,
        settings: AppSettings,
        onProgress: (String) -> Unit,
        checkControl: suspend () -> Unit,
        knownUris: Set<String> = emptySet(),
        folderCache: Map<String, Long> = emptyMap(),
        updatedFolderCache: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap(),
        knownLastModified: Long = -1L,
        onItemsFound: (suspend (List<Comic>, List<GalleryItem>, List<Movie>) -> Unit)? = null,
    ) {
        checkControl()
        val folderUrlStr = dir.url.toString()
        val currentMod = if (knownLastModified != -1L) knownLastModified else try { dir.lastModified() } catch (_: Exception) { 0L }
        val cachedMod = folderCache[folderUrlStr]

        if (cachedMod != null && cachedMod == currentMod && currentMod > 0) {
            updatedFolderCache[folderUrlStr] = currentMod
            return
        }

        try {
            val files = dir.listFiles() ?: return
            val subDirs = mutableListOf<SmbFile>()
            val folderMovies = mutableListOf<Movie>()

            for (file in files) {
                checkControl()
                if (file.name.startsWith("._")) continue
                if (file.isDirectory) {
                    subDirs.add(file)
                } else if (file.isFile) {
                    val fileUrlStr = file.url.toString()
                    val cpUri = toContentProviderUri(fileUrlStr)
                    if (knownUris.contains(cpUri) || knownUris.contains(fileUrlStr)) continue

                    val name = file.name
                    onProgress(name)
                    val ext = MediaUtils.getFileExt(name)
                    if (MediaUtils.isVideoExt(ext)) {
                        val parsed = LocalScanner.parseNameMetadata(name, settings)

                        onProgress(parentFolderName)

                        val movieItem = Movie(
                            title = parsed.title,
                            uri = cpUri,
                            size = file.length(),
                            dateAdded = file.lastModified(),
                            folderName = parentFolderName,
                            tags = parsed.tags.joinToString(","),
                        )
                        folderMovies.add(movieItem)
                        synchronized(movies) { movies.add(movieItem) }
                        
                        if (folderMovies.size >= 50) {
                            onItemsFound?.invoke(emptyList(), emptyList(), folderMovies.toList())
                            folderMovies.clear()
                        }
                    }
                }
            }
            
            if (folderMovies.isNotEmpty()) {
                onItemsFound?.invoke(emptyList(), emptyList(), folderMovies)
            }

            updatedFolderCache[folderUrlStr] = currentMod

            if (subDirs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    subDirs.chunked(6).map { batch ->
                        launch {
                            batch.forEach { file ->
                                checkControl()
                                val nextFolderName = Uri.parse(file.url.toString()).lastPathSegment?.trim('/') ?: file.name.trim('/')
                                val lastMod = try { file.lastModified() } catch (_: Exception) { -1L }
                                scanFileTreeForMoviesSmb(context, file, nextFolderName, movies, settings, onProgress, checkControl, knownUris, folderCache, updatedFolderCache, lastMod, onItemsFound)
                            }
                        }
                    }.joinAll()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e.cause is kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException("Scan dibatalkan", e)
            }
            Log.e("SmbScanner", "Gagal memindai folder $folderUrlStr", e)
            throw e
        }
    }
}
