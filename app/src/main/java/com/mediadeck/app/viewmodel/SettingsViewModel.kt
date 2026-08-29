package com.mediadeck.app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.util.smb.SmbCredentialStore
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.i18n.translate
import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.smb.SmbDiscoveryManager
import com.mediadeck.app.util.smb.SmbFileItem
import com.mediadeck.app.util.smb.SmbScanner
import jcifs.smb.SmbAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.mediadeck.app.service.MediaScannerService

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppRepository,
    @ApplicationContext private val context: Context,
    private val smbCredentialStore: SmbCredentialStore,
) : ViewModel() {

    private val settingsMutex = Mutex()

    val appSettings = repository.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _isSmbOnline = MutableStateFlow(value = true)
    val isSmbOnline: StateFlow<Boolean> = _isSmbOnline.asStateFlow()

    private val _isGeneratingThumbnails = MutableStateFlow(false)
    val isGeneratingThumbnails: StateFlow<Boolean> = _isGeneratingThumbnails.asStateFlow()

    private val _thumbnailProgress = MutableStateFlow("")
    val thumbnailProgress: StateFlow<String> = _thumbnailProgress.asStateFlow()

    private val _currentBrowserPath = MutableStateFlow("")
    val currentBrowserPath = _currentBrowserPath.asStateFlow()

    private val _browserItems = MutableStateFlow<List<SmbFileItem>>(emptyList())
    val browserItems = _browserItems.asStateFlow()

    private val _isBrowserLoading = MutableStateFlow(false)
    val isBrowserLoading = _isBrowserLoading.asStateFlow()

    private val _browserError = MutableStateFlow<String?>(null)
    val browserError = _browserError.asStateFlow()

    private val _browserNeedsAuthentication = MutableStateFlow(false)
    val browserNeedsAuthentication = _browserNeedsAuthentication.asStateFlow()

    private val _smbPassword = MutableStateFlow("")
    val smbPassword = _smbPassword.asStateFlow()

    private val _browseShareBySession = MutableStateFlow("")
    val browseShareBySession = _browseShareBySession.asStateFlow()

    private val _browseSubpathBySession = MutableStateFlow("")
    val browseSubpathBySession = _browseSubpathBySession.asStateFlow()

    private val _selectedSettingsTabIndex = MutableStateFlow(0)
    val selectedSettingsTabIndex = _selectedSettingsTabIndex.asStateFlow()

    private val _comicsThumbSize = MutableStateFlow(0L)
    val comicsThumbSize = _comicsThumbSize.asStateFlow()

    private val _galleryThumbSize = MutableStateFlow(0L)
    val galleryThumbSize = _galleryThumbSize.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache = _isClearingCache.asStateFlow()

    private val _isClearingData = MutableStateFlow(false)
    val isClearingData = _isClearingData.asStateFlow()

    private val _cacheClearCompleted = MutableStateFlow(0)
    val cacheClearCompleted = _cacheClearCompleted.asStateFlow()

    private val _moviesThumbSize = MutableStateFlow(0L)
    val moviesThumbSize = _moviesThumbSize.asStateFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    private val _messageEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageEvent = _messageEvent.asSharedFlow()

    fun requestScrollToTop() {
        _scrollToTopEvent.tryEmit(Unit)
    }

    fun checkSmbStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = repository.getSettingsDirect()
                val host = currentSettings.smbHost.trim().trim('/')
                if (host.isNotEmpty()) {
                    val port = currentSettings.smbPort.trim().toIntOrNull() ?: 445
                    val timeout = currentSettings.smbConnTimeout.coerceAtLeast(500)
                    val online = try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), timeout)
                            true
                        }
                    } catch (_: Exception) {
                        false
                    }
                    _isSmbOnline.value = online
                } else {
                    _isSmbOnline.value = true
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error checking SMB status", e)
            }
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsMutex.withLock {
                try {
                    val current = repository.getSettingsDirect()
                    repository.updateSettings(transform(current))
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to update settings", e)
                }
            }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) = updateSettings { it.copy(keepScreenOn = enabled) }

    fun setAutoScanOnStart(enabled: Boolean) = updateSettings { it.copy(autoScanOnStart = enabled) }

    fun setGridColumns(cols: Int) = updateSettings { it.copy(gridColumns = cols) }

    fun setLayoutMode(mode: String) = updateSettings { it.copy(layoutMode = mode) }

    fun setGalleryGridType(type: String) = updateSettings { it.copy(galleryGridType = type) }

    fun setVerticalPageGap(gap: String) = updateSettings { it.copy(verticalPageGap = gap) }

    fun setAutoHideReaderUi(enabled: Boolean) = updateSettings { it.copy(autoHideReaderUi = enabled) }

    fun setCacheSizeLimit(mb: Int) = updateSettings { it.copy(cacheSizeLimitMB = mb) }

    fun setCachePurgeTarget(mb: Int) = updateSettings { it.copy(cachePurgeTargetMB = mb) }

    fun setAutoManageCache(enabled: Boolean) = updateSettings { it.copy(autoManageCache = enabled) }

    fun setDefaultReaderMode(mode: String) = updateSettings { it.copy(defaultReaderMode = mode) }

    fun setReaderVolumeKeysNavigation(enabled: Boolean) = updateSettings { it.copy(readerVolumeKeysNavigation = enabled) }

    fun setDefaultVideoZoomMode(mode: Int) = updateSettings { it.copy(defaultVideoZoomMode = mode) }

    fun setDefaultVideoOrientation(orientation: Int) = updateSettings { it.copy(defaultVideoOrientation = orientation) }

    fun setVideoSkipInterval(seconds: Int) = updateSettings { it.copy(videoSkipInterval = seconds) }

    fun setSkipDuplicateScan(skip: Boolean) = updateSettings { it.copy(skipDuplicateScan = skip) }

    fun setPrioritizeLocalScan(prioritize: Boolean) = updateSettings { it.copy(prioritizeLocalScan = prioritize) }

    fun setHideOfflineSmb(hide: Boolean) = updateSettings { it.copy(hideOfflineSmb = hide) }

    fun setHideScannedFromGallery(enabled: Boolean) = updateSettings { it.copy(hideScannedFromGallery = enabled) }

    fun setLanguage(lang: String) = updateSettings { it.copy(language = lang) }

    fun updateTheme(theme: String) = updateSettings { it.copy(theme = theme) }

    fun setScanFormat(format: String) = updateSettings { it.copy(scanFormat = format) }

    fun setTagSeparator(sep: String) = updateSettings { it.copy(tagSeparator = sep) }

    fun setTagDelimiter(delim: String) = updateSettings { it.copy(tagDelimiter = delim) }

    fun setStripNumericId(strip: Boolean) = updateSettings { it.copy(stripNumericId = strip) }

    fun setLowercaseTags(lowercase: Boolean) = updateSettings { it.copy(lowercaseTags = lowercase) }

    fun setVideoThumbnails(enabled: Boolean) = updateSettings { it.copy(videoThumbnails = enabled) }

    fun setFloatingScanStatus(enabled: Boolean) = updateSettings { it.copy(floatingScanStatus = enabled) }

    fun setEnablePiP(enabled: Boolean) = updateSettings { it.copy(enablePiP = enabled) }

    fun setShowSideScrollbar(enabled: Boolean) = updateSettings { it.copy(showSideScrollbar = enabled) }

    fun clearComicsData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingData.value = true
            val lang = repository.getSettingsDirect().language
            MediaScannerService.startService(context, translate(lang, "Deleting comics data...", "Menghapus data komik..."))
            try {
                repository.clearAllComics(context)
                _messageEvent.emit(translate(lang, "Comics data cleared", "Data komik berhasil dihapus"))
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to clear comics data", e)
            } finally {
                _isClearingData.value = false
                MediaScannerService.stopService(context)
            }
        }
    }

    fun clearGalleryData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingData.value = true
            val lang = repository.getSettingsDirect().language
            MediaScannerService.startService(context, translate(lang, "Deleting gallery data...", "Menghapus data galeri..."))
            try {
                repository.clearAllGalleryItems(context)
                _messageEvent.emit(translate(lang, "Gallery data cleared", "Data galeri berhasil dihapus"))
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to clear gallery data", e)
            } finally {
                _isClearingData.value = false
                MediaScannerService.stopService(context)
            }
        }
    }

    fun clearMoviesData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingData.value = true
            val lang = repository.getSettingsDirect().language
            MediaScannerService.startService(context, translate(lang, "Deleting movies data...", "Menghapus data film..."))
            try {
                repository.clearAllMovies(context)
                _messageEvent.emit(translate(lang, "Movies data cleared", "Data film berhasil dihapus"))
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to clear movies data", e)
            } finally {
                _isClearingData.value = false
                MediaScannerService.stopService(context)
            }
        }
    }

    fun resetAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingData.value = true
            try {
                repository.resetAllComicHistory()
                repository.resetAllMovieHistory()
                _messageEvent.emit(translate(repository.getSettingsDirect().language, "History reset completed", "Riwayat berhasil direset"))
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to reset history", e)
            } finally {
                _isClearingData.value = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _smbPassword.value = smbCredentialStore.getPassword()
        }
    }

    fun updateSmbPreferences(
        host: String,
        share: String,
        user: String,
        pass: String,
        domain: String,
        port: String,
        enableSMB2: Boolean,
        disableSMB1: Boolean,
        connTimeout: Int,
        soTimeout: Int,
        isGuest: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsMutex.withLock {
                smbCredentialStore.savePassword(pass)
                _smbPassword.value = pass
                val current = repository.getSettingsDirect()
                repository.updateSettings(
                    current.copy(
                        smbHost = host,
                        smbShare = share,
                        smbUser = user,
                        smbPass = "",
                        smbDomain = domain,
                        smbPort = port,
                        smbEnableSMB2 = enableSMB2,
                        smbDisableSMB1 = disableSMB1,
                        smbConnTimeout = connTimeout,
                        smbSoTimeout = soTimeout,
                        smbIsGuest = isGuest,
                    )
                )
            }
        }
    }

    fun updateSmbPreferencesAndBrowse(
        host: String,
        share: String,
        user: String,
        pass: String,
        domain: String,
        port: String,
        enableSMB2: Boolean,
        disableSMB1: Boolean,
        connTimeout: Int,
        soTimeout: Int,
        isGuest: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsMutex.withLock {
                smbCredentialStore.savePassword(pass)
                _smbPassword.value = pass
                val current = repository.getSettingsDirect()
                repository.updateSettings(
                    current.copy(
                        smbHost = host,
                        smbShare = share,
                        smbUser = user,
                        smbPass = "",
                        smbDomain = domain,
                        smbPort = port,
                        smbEnableSMB2 = enableSMB2,
                        smbDisableSMB1 = disableSMB1,
                        smbConnTimeout = connTimeout,
                        smbSoTimeout = soTimeout,
                        smbIsGuest = isGuest,
                    )
                )
            }
            startBrowsingServer()
        }
    }

    fun startBrowsingServer() {
        _browseShareBySession.value = ""
        _browseSubpathBySession.value = ""
        loadCurrentBrowserPath()
    }

    fun navigateIntoDirectory(item: SmbFileItem) {
        if (!item.isDirectory) return
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsDirect()
            val share = settings.smbShare.trim().trim('/')
            if (share.isEmpty() && _browseShareBySession.value.isEmpty()) {
                _browseShareBySession.value = item.name
                _browseSubpathBySession.value = ""
            } else {
                _browseSubpathBySession.value = if (_browseSubpathBySession.value.isEmpty()) item.name else "${_browseSubpathBySession.value}/${item.name}"
            }
            loadCurrentBrowserPath()
        }
    }

    fun navigateUpBrowser() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsDirect()
            val share = settings.smbShare.trim().trim('/')
            val activeShare = share.ifEmpty { _browseShareBySession.value }
            if (activeShare.isEmpty()) {
                clearBrowserState()
                return@launch
            }
            if (_browseSubpathBySession.value.isEmpty()) {
                if (share.isEmpty()) {
                    _browseShareBySession.value = ""
                    loadCurrentBrowserPath()
                } else {
                    clearBrowserState()
                }
            } else {
                val lastSlash = _browseSubpathBySession.value.lastIndexOf('/')
                _browseSubpathBySession.value = if (lastSlash >= 0) _browseSubpathBySession.value.substring(0, lastSlash) else ""
                loadCurrentBrowserPath()
            }
        }
    }

    fun retryBrowserLoading() {
        loadCurrentBrowserPath()
    }

    fun clearBrowserState() {
        _currentBrowserPath.value = ""
        _browserItems.value = emptyList()
        _isBrowserLoading.value = false
        _browserError.value = null
        _browserNeedsAuthentication.value = false
        _browseShareBySession.value = ""
        _browseSubpathBySession.value = ""
    }

    private fun loadCurrentBrowserPath() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsDirect()
            val host = settings.smbHost.trim().trim('/')
            if (host.isEmpty()) {
                _currentBrowserPath.value = ""
                _browserItems.value = emptyList()
                _browserError.value = translate(settings.language, "Samba Host/IP has not been configured in Settings.", "Host/IP Samba belum dikonfigurasi di Pengaturan.")
                _browserNeedsAuthentication.value = false
                return@launch
            }
            _isBrowserLoading.value = true
            _browserError.value = null
            _browserNeedsAuthentication.value = false

            val portNum = settings.smbPort.trim().toIntOrNull()
            if (portNum == null || portNum !in 1..65535) {
                _browserError.value = translate(settings.language, "Samba port must be between 1 and 65535.", "Port Samba harus antara 1 dan 65535.")
                _isBrowserLoading.value = false
                return@launch
            }
            val portSuffix = if (portNum != 445) ":$portNum" else ""

            val activeShare = settings.smbShare.trim().trim('/').ifEmpty { _browseShareBySession.value }
            val displayPath = StringBuilder("smb://").append(host).append(portSuffix)
            if (activeShare.isNotEmpty()) {
                displayPath.append("/").append(activeShare)
                if (_browseSubpathBySession.value.isNotEmpty()) displayPath.append("/").append(_browseSubpathBySession.value)
            }
            _currentBrowserPath.value = displayPath.toString()
            try {
                val list = SmbScanner.listSmbPath(context, settings, activeShare, _browseSubpathBySession.value)
                _browserItems.value = list
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "SMB Browse failed", e)
                _browserError.value = e.message ?: translate(settings.language, "Failed to connect to Samba host", "Gagal terhubung ke host Samba")
                _browserNeedsAuthentication.value = generateSequence<Throwable>(e) { it.cause }
                    .any { it is SmbAuthException }
            } finally {
                _isBrowserLoading.value = false
            }
        }
    }

    fun generateAllVideoThumbnails(context: Context) {
        val appContext = context.applicationContext
        if (_isGeneratingThumbnails.value) return
        _isGeneratingThumbnails.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val moviesList = repository.getAllMoviesDirect()
                val total = moviesList.size
                if (total > 0) {
                    val completedCount = AtomicInteger(0)
                    val failedCount = AtomicInteger(0)

                    val (smbMovies, localMovies) = moviesList.partition { it.uri.startsWith("smb://") || it.uri.contains("smbprovider") }

                    val updateProgress = {
                        val done = completedCount.get()
                        val failed = failedCount.get()
                        _thumbnailProgress.value = if (failed > 0) "$done/$total ($failed gagal)" else "$done/$total"
                    }

                    val localJob = async {
                        val semaphoreLocal = Semaphore(5)
                        localMovies.map { movie ->
                            async {
                                semaphoreLocal.withPermit {
                                    try {
                                        val thumbFolder = File(context.filesDir, "thumbnails")
                                        val cacheFile = File(thumbFolder, VideoThumbnailHelper.getCacheFilename(movie.id, "explore"))
                                        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
                                            VideoThumbnailHelper.loadThumbnail(appContext, movie.uri, movie.id)
                                        }
                                        completedCount.incrementAndGet()
                                    } catch (e: Throwable) {
                                        Log.e("SettingsViewModel", "Thumbnail failed: ${movie.title}", e)
                                        failedCount.incrementAndGet()
                                    } finally {
                                        updateProgress()
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                    val smbJob = async {
                        val semaphoreSmb = Semaphore(2)
                        smbMovies.map { movie ->
                            async {
                                semaphoreSmb.withPermit {
                                    try {
                                        val thumbFolder = File(context.filesDir, "thumbnails")
                                        val cacheFile = File(thumbFolder, VideoThumbnailHelper.getCacheFilename(movie.id, "explore"))
                                        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
                                            VideoThumbnailHelper.loadThumbnail(appContext, movie.uri, movie.id)
                                        }
                                        completedCount.incrementAndGet()
                                    } catch (e: Throwable) {
                                        Log.e("SettingsViewModel", "SMB Thumbnail failed: ${movie.title}", e)
                                        failedCount.incrementAndGet()
                                    } finally {
                                        updateProgress()
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                    awaitAll(localJob, smbJob)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Thumbnail generation crashed", e)
            } finally {
                _isGeneratingThumbnails.value = false
                _thumbnailProgress.value = ""
            }
        }
    }

    fun setSelectedSettingsTabIndex(index: Int) {
        _selectedSettingsTabIndex.value = index
    }

    fun clearPermanentThumbnails(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val thumbFolder = File(context.filesDir, "thumbnails")
                if (!thumbFolder.exists()) return@launch

                val mediaIds = when (category) {
                    "comics" -> repository.getAllComicsDirect().map { it.id }
                    "gallery" -> repository.getAllGalleryItemsDirect().map { it.id }
                    "movies" -> repository.getAllMoviesDirect().map { it.id }
                    else -> emptyList()
                }

                mediaIds.forEach { mediaId ->
                    if (mediaId > 0L) {
                        listOf("explore", "view").forEach { variant ->
                            val filename = VideoThumbnailHelper.getCacheFilename(mediaId, variant)
                            val file = File(thumbFolder, filename)
                            if (file.exists()) file.delete()
                        }
                    }
                }

                VideoThumbnailHelper.clearMemoryCache()
                refreshThumbnailsSize()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to clear thumbnails for $category", e)
            }
        }
    }

    fun clearAllCaches() {
        if (_isClearingCache.value) return
        _isClearingCache.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("smb_") || file.name.startsWith("zip_pages_")) {
                        file.deleteRecursively()
                    }
                }
                repository.clearAllThumbnails(context)
                refreshThumbnailsSize()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to clear caches", e)
            } finally {
                _isClearingCache.value = false
                _cacheClearCompleted.value += 1
            }
        }
    }

    fun refreshThumbnailsSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val thumbFolder = File(context.filesDir, "thumbnails")
                if (!thumbFolder.exists()) {
                    _comicsThumbSize.value = 0L
                    _galleryThumbSize.value = 0L
                    _moviesThumbSize.value = 0L
                    return@launch
                }

                val comicItems = repository.getAllComicsDirect()
                val galleryItems = repository.getAllGalleryItemsDirect()
                val movieItems = repository.getAllMoviesDirect()

                val files = thumbFolder.listFiles() ?: emptyArray()
                val fileMap = files.associateBy({ it.name }, { it.length() })

                fun calcSizeForGallery(items: List<com.mediadeck.app.data.gallery.GalleryItem>): Long {
                    var total = 0L
                    items.forEach { item ->
                        if (item.id > 0L) {
                            total += fileMap[VideoThumbnailHelper.getCacheFilename(item.id, "explore")] ?: 0L
                            total += fileMap[VideoThumbnailHelper.getCacheFilename(item.id, "view")] ?: 0L
                        }
                    }
                    return total
                }

                fun calcSizeForMovies(items: List<com.mediadeck.app.data.movie.Movie>): Long {
                    var total = 0L
                    items.forEach { item ->
                        if (item.id > 0L) {
                            total += fileMap[VideoThumbnailHelper.getCacheFilename(item.id, "explore")] ?: 0L
                            total += fileMap[VideoThumbnailHelper.getCacheFilename(item.id, "view")] ?: 0L
                        }
                    }
                    return total
                }

                fun calcSizeForComics(items: List<com.mediadeck.app.data.comic.Comic>): Long {
                    var total = 0L
                    items.forEach { item ->
                        if (item.id > 0L) {
                            total += fileMap[VideoThumbnailHelper.getCacheFilename(item.id, "explore")] ?: 0L
                            total += fileMap[VideoThumbnailHelper.getCacheFilename(item.id, "view")] ?: 0L
                        }
                    }
                    return total
                }

                _comicsThumbSize.value = calcSizeForComics(comicItems)
                _galleryThumbSize.value = calcSizeForGallery(galleryItems)
                _moviesThumbSize.value = calcSizeForMovies(movieItems)

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to refresh thumbnail sizes", e)
            }
        }
    }
}
