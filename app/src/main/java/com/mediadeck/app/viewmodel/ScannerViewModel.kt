package com.mediadeck.app.viewmodel
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.data.settings.ScannedFolder
import com.mediadeck.app.util.i18n.translate
import com.mediadeck.app.util.scan.LocalScanner
import com.mediadeck.app.util.scan.MediaProcessingEngine
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.util.smb.SmbDiscoveryManager
import com.mediadeck.app.util.smb.SmbScanner
import com.mediadeck.app.util.media.VideoThumbnailHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
@HiltViewModel
@OptIn(kotlinx.coroutines.FlowPreview::class)
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AppRepository,
) : ViewModel() {
    private val _folderRemovalMediaCount = MutableStateFlow<Int?>(null)
    val folderRemovalMediaCount = _folderRemovalMediaCount.asStateFlow()
    private val scanMutex = Mutex()
    private var activeScanJob: Job? = null
    private val _currentScanTarget = MutableStateFlow<String?>(null)
    val currentScanTarget: StateFlow<String?> = _currentScanTarget.asStateFlow()
    private val _scanProgress = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = _scanProgress.asStateFlow()
    private val _comicCount = MutableStateFlow(0)
    private val _galleryCount = MutableStateFlow(0)
    private val _movieCount = MutableStateFlow(0)
    private val _skippedOfflineSmbFolders = MutableStateFlow(0)
    val skippedOfflineSmbFolders: StateFlow<Int> = _skippedOfflineSmbFolders.asStateFlow()
    private val _isScanPaused = MutableStateFlow(value = false)
    val isScanPaused: StateFlow<Boolean> = _isScanPaused.asStateFlow()
    private val _isScanActive = MutableStateFlow(value = false)
    val isScanActive: StateFlow<Boolean> = _isScanActive.asStateFlow()
    private val _isManualRefreshing = MutableStateFlow(value = false)
    val isManualRefreshing: StateFlow<Boolean> = _isManualRefreshing.asStateFlow()
    private val _comicFolders = MutableStateFlow<List<String>>(emptyList())
    val comicFolders = _comicFolders.asStateFlow()
    private val _galleryFolders = MutableStateFlow<List<String>>(emptyList())
    val galleryFolders = _galleryFolders.asStateFlow()
    private val _movieFolders = MutableStateFlow<List<String>>(emptyList())
    val movieFolders = _movieFolders.asStateFlow()
    private val _navigationRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationRequest: SharedFlow<String> = _navigationRequest.asSharedFlow()
    @Volatile private var isScanCancelled = false
    @Volatile private var isForceStopped = false
    init {
        viewModelScope.launch {
            repository.appSettings.collect { settings ->
                _comicFolders.value = settings.comicFolders.split(",").filter { it.isNotEmpty() }.distinct()
                _galleryFolders.value = settings.galleryFolders.split(",").filter { it.isNotEmpty() }.distinct()
                _movieFolders.value = settings.movieFolders.split(",").filter { it.isNotEmpty() }.distinct()
            }
        }
        viewModelScope.launch {
            _scanProgress.debounce(500L).collect { progress ->
                ScannerStateManager.updateProgress(progress)
                if (_isScanActive.value && progress != null) {
                    com.mediadeck.app.service.MediaScannerService.startService(appContext, progress)
                }
            }
        }
        _isScanActive.onEach { ScannerStateManager.setScanActive(it) }.launchIn(viewModelScope)
        _isScanPaused.onEach { ScannerStateManager.setScanPaused(it) }.launchIn(viewModelScope)
        _isManualRefreshing.onEach { ScannerStateManager.setManualRefreshing(it) }.launchIn(viewModelScope)
    }
    fun startNetworkDiscovery(context: Context) {
        SmbDiscoveryManager.startDiscovery(context)
    }
    fun saveLibraryFolder(context: Context, uri: String, type: String = "") {
        viewModelScope.launch {
            val normalizedUri = uri.removeSuffix("/")
            val uriWithSlash = if (normalizedUri.endsWith("/")) normalizedUri else "$normalizedUri/"
            var hideFromGallery = false
            repository.updateSettings { settings ->
                val currentFolders = when (type) {
                    "comics" -> settings.comicFolders
                    "gallery" -> settings.galleryFolders
                    "movies" -> settings.movieFolders
                    else -> settings.libraryFolders
                }.split(",").filter {
                    it.isNotEmpty() &&
                        it.removeSuffix("/").let { folder -> if (folder.endsWith("/")) folder else "$folder/" } != uriWithSlash
                }.toMutableList()
                currentFolders.add(normalizedUri)
                val updated = currentFolders.distinct().joinToString(",")
                val nextSettings = when (type) {
                    "comics" -> settings.copy(comicFolders = updated)
                    "gallery" -> settings.copy(galleryFolders = updated)
                    "movies" -> settings.copy(movieFolders = updated)
                    else -> settings.copy(libraryFolders = updated)
                }
                hideFromGallery = nextSettings.hideScannedFromGallery
                nextSettings
            }
            if (hideFromGallery && !com.mediadeck.app.util.media.MediaUtils.isSmbUri(normalizedUri)) {
                applyGalleryHidingToSingleFolder(context, normalizedUri, true)
            }
        }
    }
    private fun applyGalleryHidingToSingleFolder(context: Context, uriStr: String, hide: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (uriStr.startsWith("content://")) {
                    val treeUri = Uri.parse(uriStr)
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        val nomedia = rootDoc.findFile(".nomedia")
                        if (hide && nomedia == null) rootDoc.createFile("application/octet-stream", ".nomedia")
                        else if (!hide && nomedia != null) nomedia.delete()
                    }
                } else {
                    val path = Uri.parse(uriStr).path ?: return@launch
                    val folder = File(path)
                    if (folder.exists() && folder.isDirectory) {
                        val nomedia = File(folder, ".nomedia")
                        if (hide && !nomedia.exists()) nomedia.createNewFile()
                        else if (!hide && nomedia.exists()) nomedia.delete()
                        MediaScannerConnection.scanFile(context, arrayOf(nomedia.absolutePath), null, null)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    fun removeLibraryFolder(context: Context, uri: String, type: String = "") {
        viewModelScope.launch {
            val normalizedUri = uri.removeSuffix("/")
            val hideFromGallery = repository.getSettingsDirect().hideScannedFromGallery
            if (hideFromGallery && !com.mediadeck.app.util.media.MediaUtils.isSmbUri(normalizedUri)) {
                applyGalleryHidingToSingleFolder(context, normalizedUri, false)
            }
            repository.updateSettings { settings ->
                when (type) {
                    "comics" -> settings.copy(comicFolders = settings.comicFolders.split(",").filter { it.removeSuffix("/") != normalizedUri }.joinToString(","))
                    "gallery" -> settings.copy(galleryFolders = settings.galleryFolders.split(",").filter { it.removeSuffix("/") != normalizedUri }.joinToString(","))
                    "movies" -> settings.copy(movieFolders = settings.movieFolders.split(",").filter { it.removeSuffix("/") != normalizedUri }.joinToString(","))
                    else -> settings
                }
            }
            repository.deleteMediaByFolderUri(context, normalizedUri)
        }
    }
    fun previewLibraryFolderRemoval(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _folderRemovalMediaCount.value = repository.getFolderRemovalSummary(uri).mediaCount
        }
    }
    fun autoScanLibrary(isManualRefresh: Boolean = false, scanType: String = "") {
        val target = "AUTO_ALL_$scanType"
        if (isManualRefresh && scanType.isNotEmpty()) _navigationRequest.tryEmit(scanType)
        viewModelScope.launch {
            activeScanJob?.cancelAndJoin()
            isScanCancelled = false
            isForceStopped = false
            activeScanJob = viewModelScope.launch {
                scanMutex.withLock {
                    _currentScanTarget.value = target
                    val settings = repository.getSettingsDirect()
                    val lang = settings.language
                    if (!isManualRefresh && !settings.autoScanOnStart) return@withLock
                    if (isManualRefresh) _isManualRefreshing.value = true
                    _isScanActive.value = true
                    resetCounters()
                    _scanProgress.value = com.mediadeck.app.util.i18n.translate(lang, "Starting scan...", "Memulai pemindaian...")
                    val updatedFolderCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
                    val foldersModified = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
                    val failedFolders = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
                    val allConfiguredFolders = (settings.comicFolders + settings.galleryFolders + settings.movieFolders).split(",").filter { it.isNotEmpty() }
                    if (allConfiguredFolders.isEmpty()) {
                        _scanProgress.value = translate(
                            lang,
                            "No folders added. Go to Settings -> Scan to add folders.",
                            "Belum ada folder. Ke Pengaturan -> Pindai untuk tambah folder."
                        )
                        delay(3000)
                        _isScanActive.value = false
                        _isManualRefreshing.value = false
                        _currentScanTarget.value = null
                        _scanProgress.value = null
                        return@withLock
                    }
                    try {
                        val (allExistingGallery, allExistingMovies, allExistingComics) = withContext(Dispatchers.IO) {
                            Triple(
                                repository.getAllGalleryItemsDirect().associateBy { it.uri }.toMutableMap(),
                                repository.getAllMoviesDirect().associateBy { it.uri }.toMutableMap(),
                                repository.getAllComicsDirect().associateBy { it.folderUri.removeSuffix("/") }.toMutableMap()
                            )
                        }
                        val galleryByFolder = allExistingGallery.values.groupBy { 
                            it.uri.substringBeforeLast('/', "") + "/" 
                        }.toMutableMap()
                        val folderCache = if (isManualRefresh) emptyMap() else withContext(Dispatchers.IO) { repository.getScannedFolders().associateBy { it.folderUri.removeSuffix("/") }.mapValues { it.value.lastModified } }
                        val dedupeTracker = ScanDedupeTracker()
                        val mapMutex = Mutex()
                        val categories = if (scanType.isEmpty()) listOf("comics", "gallery", "movies") else listOf(scanType)
                        for (cat in categories) {
                            if (isScanCancelled) break
                            val folders = when (cat) {
                                "comics" -> settings.comicFolders
                                "gallery" -> settings.galleryFolders
                                "movies" -> settings.movieFolders
                                else -> ""
                            }.split(",").filter { it.isNotEmpty() }
                            if (folders.isEmpty()) continue
                            val (smbFolders, localFolders) = folders.partition { com.mediadeck.app.util.media.MediaUtils.isSmbUri(it) }
                            suspend fun scanFoldersInternal(folderList: List<String>) {
                                coroutineScope {
                                    folderList.map { uri ->
                                        launch {
                                            try {
                                                checkScanControl(lang)
                                                val knownUris = (allExistingMovies.keys + allExistingComics.keys + allExistingGallery.keys)
                                                    .map { it.removeSuffix("/") }.toSet()
                                                val checkControl: suspend () -> Unit = { checkScanControl(lang) }
                                                if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(uri)) {
                                                    SmbScanner.scanSmbDirectory(
                                                        context = appContext,
                                                        settings = settings,
                                                        scanType = cat,
                                                        onProgress = { path ->
                                                            _scanProgress.value = com.mediadeck.app.util.i18n.translate(
                                                                lang,
                                                                "Scanning $path",
                                                                "Memindai $path",
                                                            )
                                                        },
                                                        checkControl = checkControl,
                                                        knownUris = knownUris,
                                                        folderCache = folderCache,
                                                        updatedFolderCache = updatedFolderCache,
                                                        rootUrl = uri,
                                                        onItemsFound = { c, g, m ->
                                                            mapMutex.withLock { processScanResultsInternal(appContext, dedupeTracker.dedupe(Triple(c, g, m)), settings, allExistingMovies, allExistingComics, allExistingGallery, galleryByFolder, foldersModified) }
                                                        }
                                                    )
                                                } else {
                                                    LocalScanner.scanLocalDirectory(
                                                        context = appContext,
                                                        folderPathOrUri = uri,
                                                        settings = settings,
                                                        scanType = cat,
                                                        onProgress = { path ->
                                                            _scanProgress.value = com.mediadeck.app.util.i18n.translate(
                                                                lang,
                                                                "Scanning $path",
                                                                "Memindai $path",
                                                            )
                                                        },
                                                        checkControl = checkControl,
                                                        knownUris = knownUris,
                                                        folderCache = folderCache,
                                                        updatedFolderCache = updatedFolderCache,
                                                        onItemsFound = { c, g, m ->
                                                            mapMutex.withLock { processScanResultsInternal(appContext, dedupeTracker.dedupe(Triple(c, g, m)), settings, allExistingMovies, allExistingComics, allExistingGallery, galleryByFolder, foldersModified) }
                                                        }
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                failedFolders.add(uri)
                                                val failedPrefix = uri.removeSuffix("/") + "/"
                                                updatedFolderCache.keys.filter {
                                                    it == uri.removeSuffix("/") || it.startsWith(failedPrefix)
                                                }.forEach(updatedFolderCache::remove)
                                                android.util.Log.e("ScannerViewModel", "Error scanning folder: $uri", e)
                                            }
                                        }
                                    }.joinAll()
                                }
                            }
                            val orderedFolderGroups = if (settings.prioritizeLocalScan) {
                                listOf(localFolders, smbFolders)
                            } else {
                                listOf(smbFolders, localFolders)
                            }
                            for (folderGroup in orderedFolderGroups) {
                                if (folderGroup.isNotEmpty() && !isScanCancelled) {
                                    scanFoldersInternal(folderGroup)
                                }
                            }
                            if (foldersModified.isNotEmpty()) {
                                val changedFolders = foldersModified.toList()
                                viewModelScope.launch(Dispatchers.IO) {
                                    triggerFolderMosaics(changedFolders, settings, force = true)
                                }
                                foldersModified.clear()
                            }
                        }
                        val allFolders = withContext(Dispatchers.IO) {
                            (repository.getAllMoviesDirect().map { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) } +
                                    repository.getAllGalleryItemsDirect().map { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) }).distinct()
                        }
                        viewModelScope.launch(Dispatchers.IO) {
                            triggerFolderMosaics(allFolders, settings)
                        }
                        if (updatedFolderCache.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                repository.insertScannedFolders(
                                    updatedFolderCache.map { (uri, lastMod) -> ScannedFolder(uri, lastMod) }
                                )
                            }
                        }
                        val totalFound = _comicCount.value + _galleryCount.value + _movieCount.value
                        _scanProgress.value = if (failedFolders.isNotEmpty()) {
                            translate(
                                lang,
                                "Scan incomplete: ${failedFolders.size} folder(s) could not be read.",
                                "Scan belum lengkap: ${failedFolders.size} folder tidak dapat dibaca."
                            )
                        } else if (totalFound > 0) {
                            translate(lang, "Scan Completed! ($totalFound items)", "Scan Selesai! ($totalFound item)")
                        } else {
                            translate(lang, "Scan Completed (0 items found). Check folder content/permissions.", "Scan Selesai (0 item ditemukan). Periksa isi folder/izin.")
                        }
                        delay(2500)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        android.util.Log.e("ScannerViewModel", "Scan job failed", e)
                        _scanProgress.value = translate(lang, "Scan failed. Check SMB connection and folder access.", "Scan gagal. Periksa koneksi SMB dan akses folder.")
                        delay(2500)
                    } finally {
                        _isScanActive.value = false
                        _isManualRefreshing.value = false
                        _currentScanTarget.value = null
                        _scanProgress.value = null
                        ScannerStateManager.setScanActive(false)
                        ScannerStateManager.setManualRefreshing(false)
                        com.mediadeck.app.service.MediaScannerService.stopService(appContext)
                    }
                }
            }
        }
    }
    private fun resetCounters() {
        _comicCount.value = 0
        _galleryCount.value = 0
        _movieCount.value = 0
        _skippedOfflineSmbFolders.value = 0
    }
    private class ScanDedupeTracker {
        private val seenComics = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val seenGallery = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val seenMovies = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun dedupe(triple: Triple<List<Comic>, List<GalleryItem>, List<Movie>>): Triple<List<Comic>, List<GalleryItem>, List<Movie>> {
            return Triple(
                triple.first.filter { seenComics.add(it.folderUri.removeSuffix("/")) },
                triple.second.filter { seenGallery.add(it.uri) },
                triple.third.filter { seenMovies.add(it.uri) }
            )
        }
    }
    private fun isSmbMediaUri(uri: String): Boolean =
        com.mediadeck.app.util.media.MediaUtils.isSmbUri(uri)
    private suspend fun processScanResultsInternal(
        context: Context,
        results: Triple<List<Comic>, List<GalleryItem>, List<Movie>>,
        settings: AppSettings,
        existingMovies: MutableMap<String, Movie>,
        existingComics: MutableMap<String, Comic>,
        existingGallery: MutableMap<String, GalleryItem>,
        galleryByFolder: MutableMap<String, List<GalleryItem>>,
        foldersModified: MutableSet<String>? = null,
    ) {
        val lang = settings.language
        if (results.second.isNotEmpty()) {
            val cat = com.mediadeck.app.util.i18n.translate(lang, "Gallery", "Galeri")
            val toInsert = mutableListOf<GalleryItem>()
            val contentKeyToItem = existingGallery.values.associateBy { "${it.name.lowercase().trim()}|${it.size}" }
            results.second.forEach { scanned ->
                checkScanControl(lang)
                val existing = existingGallery[scanned.uri]
                if (existing == null) {
                    val key = "${scanned.name.lowercase().trim()}|${scanned.size}"
                    if (!settings.skipDuplicateScan || isSmbMediaUri(scanned.uri) || contentKeyToItem[key] == null) {
                        toInsert.add(scanned)
                        _galleryCount.update { it + 1 }
                        foldersModified?.add(com.mediadeck.app.util.media.MediaFolderIdentity.key(scanned.folderUri, scanned.uri))
                    }
                } else {
                    _galleryCount.update { it + 1 }
                    if (!VideoThumbnailHelper.hasCachedThumbnail(context, existing.id, "gallery")) {
                        viewModelScope.launch(Dispatchers.IO) {
                            MediaProcessingEngine.enqueue(MediaProcessingEngine.ProcessingTask(context, existing.uri, existing.id, settings, false, "gallery") { _ -> })
                        }
                    }
                }
            }
            if (toInsert.isNotEmpty()) {
                val ids = repository.insertGalleryItems(toInsert)
                toInsert.zip(ids).forEach { (item, id) ->
                    val finalItem = item.copy(id = id)
                    existingGallery[finalItem.uri] = finalItem
                    viewModelScope.launch(Dispatchers.IO) { MediaProcessingEngine.enqueue(MediaProcessingEngine.ProcessingTask(context, finalItem.uri, id, settings, false, "gallery") { _ -> }) }
                }
            }
            _scanProgress.value = com.mediadeck.app.util.i18n.translate(lang, "Scanning $cat (${_galleryCount.value})", "Memindai $cat (${_galleryCount.value})")
        }
        if (results.first.isNotEmpty()) {
            val cat = com.mediadeck.app.util.i18n.translate(lang, "Comic", "Komik")
            val toInsert = mutableListOf<Comic>()
            val galleryToRemoveIds = mutableListOf<Long>()
            val contentKeyToComic = existingComics.values.associateBy { "${it.title.lowercase().trim()}|${it.parentFolderName.lowercase().trim()}" }
            results.first.forEach { scanned ->
                checkScanControl(lang)
                val normUri = scanned.folderUri.removeSuffix("/")
                val folderUriPrefix = if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(scanned.folderUri)) {
                    val cp = SmbScanner.toContentProviderUri(normUri)
                    if (cp.endsWith("/")) cp else "$cp/"
                } else {
                    if (normUri.endsWith("/")) normUri else "$normUri/"
                }
                val overlappingGallery = galleryByFolder[folderUriPrefix]
                if (overlappingGallery != null) {
                    galleryToRemoveIds.addAll(overlappingGallery.map { it.id })
                    overlappingGallery.forEach { existingGallery.remove(it.uri) }
                    galleryByFolder.remove(folderUriPrefix)
                }
                val existing = existingComics[normUri] ?: existingComics[scanned.folderUri]
                if (existing == null) {
                    val key = "${scanned.title.lowercase().trim()}|${scanned.parentFolderName.lowercase().trim()}"
                    val contentDuplicate = contentKeyToComic[key]
                    if (!settings.skipDuplicateScan || isSmbMediaUri(scanned.folderUri) || contentDuplicate == null) {
                        toInsert.add(scanned.copy(folderUri = normUri))
                        _comicCount.update { it + 1 }
                    }
                } else {
                    _comicCount.update { it + 1 }
                    if (scanned.totalPages > 0 && scanned.totalPages != existing.totalPages) {
                        repository.updateComic(existing.copy(totalPages = scanned.totalPages))
                    }
                }
            }
            if (galleryToRemoveIds.isNotEmpty()) {
                repository.deleteGalleryItemsByIds(context, galleryToRemoveIds)
            }
            if (toInsert.isNotEmpty()) {
                toInsert.forEach { android.util.Log.d("ScannerViewModel", "Inserting Comic: ${it.title} -> ${it.folderUri}") }
                repository.insertComics(toInsert)
                toInsert.forEach { existingComics[it.folderUri] = it }
            }
            _scanProgress.value = com.mediadeck.app.util.i18n.translate(lang, "Scanning $cat (${_comicCount.value})", "Memindai $cat (${_comicCount.value})")
        }
        if (results.third.isNotEmpty()) {
            val cat = com.mediadeck.app.util.i18n.translate(lang, "Movie", "Film")
            val toInsert = mutableListOf<Movie>()
            val contentKeyToItem = existingMovies.values.associateBy { "${it.title.lowercase().trim()}|${it.size}" }
            results.third.forEach { scanned ->
                checkScanControl(lang)
                val existing = existingMovies[scanned.uri]
                if (existing == null) {
                    val key = "${scanned.title.lowercase().trim()}|${scanned.size}"
                    if (!settings.skipDuplicateScan || isSmbMediaUri(scanned.uri) || contentKeyToItem[key] == null) {
                        toInsert.add(scanned)
                        _movieCount.update { it + 1 }
                        foldersModified?.add(com.mediadeck.app.util.media.MediaFolderIdentity.key(scanned.folderUri, scanned.uri))
                    }
                } else {
                    _movieCount.update { it + 1 }
                    if (!VideoThumbnailHelper.hasCachedThumbnail(context, existing.id, "movie")) {
                        viewModelScope.launch(Dispatchers.IO) {
                            MediaProcessingEngine.enqueue(MediaProcessingEngine.ProcessingTask(context, existing.uri, existing.id, settings, false, "movie") { _ -> })
                        }
                    }
                }
            }
            if (toInsert.isNotEmpty()) {
                val ids = repository.insertMovies(toInsert)
                toInsert.zip(ids).forEach { (movie, id) ->
                    val finalMovie = movie.copy(id = id)
                    existingMovies[finalMovie.uri] = finalMovie
                    viewModelScope.launch(Dispatchers.IO) { MediaProcessingEngine.enqueue(MediaProcessingEngine.ProcessingTask(context, finalMovie.uri, id, settings, false, "movie") { _ -> }) }
                }
            }
            _scanProgress.value = com.mediadeck.app.util.i18n.translate(lang, "Scanning $cat (${_movieCount.value})", "Memindai $cat (${_movieCount.value})")
        }
    }
    fun togglePauseScan() { _isScanPaused.value = !_isScanPaused.value }
    fun stopScan() {
        isScanCancelled = true
        isForceStopped = true
        _isScanPaused.value = false
        activeScanJob?.cancel(CancellationException("Scan stopped by user"))
        _isScanActive.value = false
        _isManualRefreshing.value = false
        _currentScanTarget.value = null
        _scanProgress.value = null
        ScannerStateManager.setScanActive(false)
        ScannerStateManager.setScanPaused(false)
        ScannerStateManager.setManualRefreshing(false)
        com.mediadeck.app.service.MediaScannerService.stopService(appContext)
    }
    fun applyGalleryHiding(context: Context, hide: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsDirect()
            val allFolders = (settings.comicFolders.split(",") + settings.galleryFolders.split(",") + settings.movieFolders.split(","))
                .filter { it.isNotEmpty() && !com.mediadeck.app.util.media.MediaUtils.isSmbUri(it) }.distinct()
            allFolders.forEach { uriStr ->
                try {
                    if (uriStr.startsWith("content://")) {
                        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                        if (rootDoc != null && rootDoc.isDirectory) {
                            val nomedia = rootDoc.findFile(".nomedia")
                            if (hide && nomedia == null) rootDoc.createFile("application/octet-stream", ".nomedia")
                            else if (!hide && nomedia != null) nomedia.delete()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
    private suspend fun triggerFolderMosaics(folderKeys: List<String>, settings: AppSettings, force: Boolean = false) {
        withContext(Dispatchers.IO) {
            val movies = repository.getAllMoviesDirect()
            val gallery = repository.getAllGalleryItemsDirect()
            val comics = repository.getAllComicsDirect()
            val moviesByFolder = movies.groupBy { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) }
            val galleryByFolder = gallery.groupBy { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) }
            val comicsByFolder = comics.groupBy { it.folderUri.trimEnd('/') }
            folderKeys.distinct().forEach { folderKey ->
                val cacheFilename = VideoThumbnailHelper.getFolderCacheFilename(folderKey)
                val file = File(appContext.filesDir, "thumbnails/$cacheFilename")
                if (force || !file.exists() || file.length() == 0L) {
                    val items = mutableListOf<Triple<String, Long, String>>()
                    items.addAll(moviesByFolder[folderKey].orEmpty().map { Triple(it.uri, it.id, "movie") })
                    items.addAll(galleryByFolder[folderKey].orEmpty().map { Triple(it.uri, it.id, "gallery") })
                    items.addAll(comicsByFolder[folderKey].orEmpty().map { Triple(it.coverUri, it.id, "explore") })
                    val finalItems = items.take(4)
                    if (finalItems.isNotEmpty()) {
                        MediaProcessingEngine.enqueueFolderMosaic(
                            MediaProcessingEngine.FolderMosaicTask(appContext, folderKey, finalItems, settings)
                        )
                    }
                }
            }
        }
    }
    private suspend fun checkScanControl(lang: String) {
        if (isScanCancelled || isForceStopped) throw CancellationException("Scan stopped by user.")
        while (_isScanPaused.value || ScannerStateManager.isMediaActive.value) {
            if (isScanCancelled || isForceStopped) throw CancellationException("Scan stopped by user.")
            _scanProgress.value = if (ScannerStateManager.isMediaActive.value) com.mediadeck.app.util.i18n.translate(lang, "Scan Paused (User Busy)", "Scan Dijeda (Sedang Dipakai)") else com.mediadeck.app.util.i18n.translate(lang, "Scan Paused", "Scan Dijeda")
            delay(200.milliseconds)
        }
    }
    fun runFolderScan(folderUriStr: String, scanType: String = "") {
        if (_currentScanTarget.value == folderUriStr && _isScanActive.value) return
        if (scanType.isNotEmpty()) _navigationRequest.tryEmit(scanType)
        viewModelScope.launch {
            activeScanJob?.cancelAndJoin()
            isScanCancelled = false
            isForceStopped = false
            activeScanJob = viewModelScope.launch {
                scanMutex.withLock {
                    val settings = repository.getSettingsDirect()
                    val lang = settings.language
                    _currentScanTarget.value = folderUriStr
                    _isScanActive.value = true
                    resetCounters()
                    val foldersModified = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
                    val updatedFolderCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
                    _scanProgress.value = com.mediadeck.app.util.i18n.translate(lang, "Starting scan...", "Memulai pemindaian...")
                    try {
                        val (allExistingGallery, allExistingMovies, allExistingComics) = withContext(Dispatchers.IO) { Triple(repository.getAllGalleryItemsDirect().associateBy { it.uri }.toMutableMap(), repository.getAllMoviesDirect().associateBy { it.uri }.toMutableMap(), repository.getAllComicsDirect().associateBy { it.folderUri.removeSuffix("/") }.toMutableMap()) }
                        val galleryByFolder = allExistingGallery.values.groupBy { 
                            it.uri.substringBeforeLast('/', "") + "/" 
                        }.toMutableMap()
                        val dedupeTracker = ScanDedupeTracker()
                        val mapMutex = Mutex()
                        val checkControl: suspend () -> Unit = { checkScanControl(lang) }
                        val knownUris = (allExistingMovies.keys + allExistingComics.keys + allExistingGallery.keys)
                            .map { it.removeSuffix("/") }.toSet()
                        if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(folderUriStr)) {
                            SmbScanner.scanSmbDirectory(
                                context = appContext,
                                settings = settings,
                                scanType = scanType,
                                onProgress = { path ->
                                    _scanProgress.value = com.mediadeck.app.util.i18n.translate(
                                        lang,
                                        "Scanning $path",
                                        "Memindai $path",
                                    )
                                },
                                checkControl = checkControl,
                                knownUris = knownUris,
                                folderCache = emptyMap(),
                                updatedFolderCache = updatedFolderCache,
                                rootUrl = folderUriStr,
                                onItemsFound = { c, g, m ->
                                    mapMutex.withLock { processScanResultsInternal(appContext, dedupeTracker.dedupe(Triple(c, g, m)), settings, allExistingMovies, allExistingComics, allExistingGallery, galleryByFolder, foldersModified) }
                                }
                            )
                        } else {
                            LocalScanner.scanLocalDirectory(
                                context = appContext,
                                folderPathOrUri = folderUriStr,
                                settings = settings,
                                scanType = scanType,
                                onProgress = { path ->
                                    _scanProgress.value = com.mediadeck.app.util.i18n.translate(
                                        lang,
                                        "Scanning $path",
                                        "Memindai $path",
                                    )
                                },
                                checkControl = checkControl,
                                knownUris = knownUris,
                                folderCache = emptyMap(),
                                updatedFolderCache = updatedFolderCache,
                                onItemsFound = { c, g, m ->
                                    mapMutex.withLock { processScanResultsInternal(appContext, dedupeTracker.dedupe(Triple(c, g, m)), settings, allExistingMovies, allExistingComics, allExistingGallery, galleryByFolder, foldersModified) }
                                }
                            )
                        }
                        val safePrefix = folderUriStr.removeSuffix("/").let { if (it.endsWith("/")) it else "$it/" }
                        val currentFolders = withContext(Dispatchers.IO) {
                            (repository.getAllMoviesDirect().filter { 
                                val uP = if (it.uri.endsWith("/")) it.uri else "${it.uri}/"
                                uP.startsWith(safePrefix) 
                            }.map { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) } +
                                    repository.getAllGalleryItemsDirect().filter { 
                                        val uP = if (it.uri.endsWith("/")) it.uri else "${it.uri}/"
                                        uP.startsWith(safePrefix) 
                                    }.map { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) }).distinct()
                        }
                        currentFolders.forEach { folderKey ->
                            val cacheFilename = VideoThumbnailHelper.getFolderCacheFilename(folderKey)
                            val file = File(appContext.filesDir, "thumbnails/$cacheFilename")
                            if (!file.exists() || file.length() == 0L || foldersModified.contains(folderKey)) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val movies = repository.getAllMoviesDirect().filter { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) == folderKey }
                                    val gallery = repository.getAllGalleryItemsDirect().filter { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) == folderKey }
                                    val items = (movies.map { Triple(it.uri, it.id, "movie") } + gallery.map { Triple(it.uri, it.id, "gallery") }).take(4)
                                    if (items.isNotEmpty()) {
                                        MediaProcessingEngine.enqueueFolderMosaic(
                                            MediaProcessingEngine.FolderMosaicTask(appContext, folderKey, items, settings)
                                        )
                                    }
                                }
                            }
                        }
                        if (updatedFolderCache.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                repository.insertScannedFolders(
                                    updatedFolderCache.map { (uri, lastMod) -> ScannedFolder(uri, lastMod) }
                                )
                            }
                        }
                        val totalFound = _comicCount.value + _galleryCount.value + _movieCount.value
                        _scanProgress.value = if (totalFound > 0) {
                            translate(lang, "Scan Completed! ($totalFound items)", "Scan Selesai! ($totalFound item)")
                        } else {
                            translate(lang, "Scan Completed (0 items found). Check folder content/permissions.", "Scan Selesai (0 item ditemukan). Periksa isi folder/izin.")
                        }
                        delay(2500)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        android.util.Log.e("ScannerViewModel", "Scan job failed", e)
                        _scanProgress.value = translate(lang, "Scan failed. Check SMB connection and folder access.", "Scan gagal. Periksa koneksi SMB dan akses folder.")
                        delay(2500)
                    } finally {
                        _isScanActive.value = false
                        _isManualRefreshing.value = false
                        _currentScanTarget.value = null
                        _scanProgress.value = null
                        ScannerStateManager.setScanActive(false)
                        ScannerStateManager.setManualRefreshing(false)
                        com.mediadeck.app.service.MediaScannerService.stopService(appContext)
                    }
                }
            }
        }
    }
}
