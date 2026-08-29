package com.mediadeck.app.viewmodel

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.i18n.translate
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.scan.MediaProcessingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.io.FileOutputStream
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class MovieViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {

    private val _movieSearch = MutableStateFlow("")
    val movieSearch = _movieSearch.asStateFlow()

    private val _movieTabFilter = MutableStateFlow("all")
    val movieTabFilter = _movieTabFilter.asStateFlow()

    private val _movieSort = MutableStateFlow("name_asc")
    val movieSort = _movieSort.asStateFlow()

    private val _shuffledMovies = MutableStateFlow<List<Movie>?>(null)

    private val _movieSelectedTagsFilter = MutableStateFlow<Set<String>>(emptySet())
    val movieSelectedTagsFilter = _movieSelectedTagsFilter.asStateFlow()

    private val _selectedMovieIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMovieIds = _selectedMovieIds.asStateFlow()

    private val _isMovieMultiSelectMode = MutableStateFlow(value = false)
    val isMovieMultiSelectMode = _isMovieMultiSelectMode.asStateFlow()

    private val _isMovieGroupedByFolder = MutableStateFlow(value = true)
    val isMovieGroupedByFolder = _isMovieGroupedByFolder.asStateFlow()

    private val _selectedMovieFolderName = MutableStateFlow<String?>(null)
    val selectedMovieFolderName = _selectedMovieFolderName.asStateFlow()

    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex = _scrollIndex.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset = _scrollOffset.asStateFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    fun requestScrollToTop() {
        _scrollToTopEvent.tryEmit(Unit)
    }

    val appSettings: StateFlow<AppSettings> = repository.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private data class GalleryFilterStateNested(
        val search: String,
        val tab: String,
        val sort: String,
        val tags: Set<String>,
        val shuffled: List<Movie>?,
        val folderName: String?,
    )

    private val _isSmbOnline = MutableStateFlow(value = true)

    private val offlineFilteredMovies: Flow<List<Movie>> = combine(
        repository.allMovies,
        repository.appSettings,
        _isSmbOnline,
    ) { items, settings, smbOnline ->
        if (settings.hideOfflineSmb && !smbOnline) {
            items.filter { !it.uri.startsWith("smb://") && !it.uri.contains("smbprovider") }
        } else {
            items
        }
    }.debounce(300L)

    val filteredMovies: StateFlow<List<Movie>> = combine(
        offlineFilteredMovies,
        combine(
            _movieSearch,
            _movieTabFilter,
            _movieSort,
            combine(_movieSelectedTagsFilter, _shuffledMovies, _selectedMovieFolderName) { tags, shuffled, folder -> Triple(tags, shuffled, folder) }
        ) { search, tab, sort, triple ->
            GalleryFilterStateNested(search, tab, sort, triple.first, triple.second, triple.third)
        }
    ) { items, filters ->
        if (items.isEmpty()) return@combine emptyList<Movie>()

        val search = filters.search
        val tab = filters.tab
        val sort = filters.sort
        val tags = filters.tags
        val shuffled = filters.shuffled
        val folderName = filters.folderName

        var list = items

        if (search.isNotEmpty()) {
            list = list.filter { it.title.contains(search, ignoreCase = true) }
        }

        if (tags.isNotEmpty()) {
            val lowercaseTags = tags.map { it.lowercase() }
            list = list.filter { item ->
                val itemTags = item.tags.split(",").map { it.trim().lowercase() }
                lowercaseTags.all { tag -> itemTags.contains(tag) }
            }
        }

        when (tab) {
            "favorites" -> list = list.filter { it.isFavorite }
            "history" -> list = list.filter { it.lastPlayedPosition > 0 }
        }

        if (folderName != null && tab == "all") {
            list = list.filter { it.folderName == folderName }
        }

        list = if (tab == "history") {
            list.sortedByDescending { it.lastPlayedPosition }
        } else {
            when (sort) {
                "name_asc" -> list.sortedBy { it.title.lowercase() }
                "name_desc" -> list.sortedByDescending { it.title.lowercase() }
                "date_desc" -> list.sortedByDescending { it.dateAdded }
                "date_asc" -> list.sortedBy { it.dateAdded }
                "size_desc" -> list.sortedByDescending { it.size }
                "size_asc" -> list.sortedBy { it.size }
                "random" -> {
                    if (shuffled == null) {
                        val newlyShuffled = list.shuffled()
                        viewModelScope.launch {
                            _shuffledMovies.value = newlyShuffled
                        }
                        newlyShuffled
                    } else {
                        val itemMap = list.associateBy { it.id }
                        shuffled.mapNotNull { itemMap[it.id] }
                    }
                }
                else -> list.sortedBy { it.title.lowercase() }
            }
        }
        list
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieTagCounts: StateFlow<Map<String, Int>> = repository.allMovies
        .debounce(500L)
        .map { items ->
            val counts = mutableMapOf<String, Int>()
            items.forEach { item ->
                if (item.tags.isNotEmpty()) {
                    item.tags.split(",").forEach { raw ->
                        val tag = raw.trim()
                        if (tag.isNotEmpty()) {
                            counts[tag] = (counts[tag] ?: 0) + 1
                        }
                    }
                }
            }
            counts
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allMovies: StateFlow<List<Movie>> = repository.allMovies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMoviesCount: StateFlow<Int> = offlineFilteredMovies
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val movieAvailableTags: StateFlow<List<String>> = repository.allMovies
        .debounce(500.milliseconds)
        .map { items ->
            items.asSequence()
                .flatMap { it.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMovie = MutableStateFlow<Movie?>(null)
    val activeMovie = _activeMovie.asStateFlow()

    private val _isPlayerPlaying = MutableStateFlow(true)
    val isPlayerPlaying = _isPlayerPlaying.asStateFlow()

    private val _isPickingFile = MutableStateFlow(false)
    val isPickingFile = _isPickingFile.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    private val _downloadingFileName = MutableStateFlow<String?>(null)

    fun setMovieSearch(query: String) {
        _movieSearch.value = query
    }

    fun setMovieTab(tab: String) {
        _movieTabFilter.value = tab
    }

    fun setMovieSort(sort: String) {
        _movieSort.value = sort
        if (sort != "random") {
            _shuffledMovies.value = null
        }
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            repository.updateSettings(settings.copy(defaultMovieSort = sort))
        }
    }

    fun setLayoutMode(mode: String) {
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            repository.updateSettings(settings.copy(layoutMode = mode))
        }
    }

    fun setMovieGroupedByFolder(grouped: Boolean) {
        _isMovieGroupedByFolder.value = grouped
        if (!grouped) {
            _selectedMovieFolderName.value = null
        }
    }

    fun setSelectedMovieFolderName(folder: String?) {
        _selectedMovieFolderName.value = folder
    }

    private var folderGenerationJob: Job? = null

    fun onFolderVisited(context: Context, folderName: String?) {
        folderGenerationJob?.cancel()
        if (folderName.isNullOrBlank()) return

        folderGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsDirect()
            val processedUris = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            repository.allMovies.map { list ->
                list.filter { it.folderName == folderName }
            }.collect { moviesInFolder -> 
                val pending = moviesInFolder
                    .filter {
                        !VideoThumbnailHelper.hasCachedThumbnail(context, it.id)
                        && !processedUris.contains(it.uri)
                    }
                    .sortedBy { it.title.lowercase() }

                if (pending.isEmpty()) return@collect

                pending.forEach { processedUris.add(it.uri) }

                coroutineScope {
                    pending.forEach { movie ->
                        launch {
                            try {
                                MediaProcessingEngine.enqueuePriority(
                                    MediaProcessingEngine.ProcessingTask(
                                        context = context,
                                        uri = movie.uri,
                                        mediaId = movie.id,
                                        settings = settings,
                                        skipThumbnail = false,
                                    ) { _ ->
                                    }
                                )
                            } catch (_: Exception) {
                                processedUris.remove(movie.uri)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onFolderExited() {
        folderGenerationJob?.cancel()
        folderGenerationJob = null
    }

    fun setScrollPosition(index: Int, offset: Int) {
        _scrollIndex.value = index
        _scrollOffset.value = offset
    }

    fun toggleMovieFavorite(movie: Movie) {
        viewModelScope.launch {
            repository.updateMovie(movie.copy(isFavorite = !movie.isFavorite))
        }
    }

    fun toggleMovieSelection(id: Long) {
        val current = _selectedMovieIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedMovieIds.value = current
        if (current.isEmpty()) {
            _isMovieMultiSelectMode.value = false
        }
    }

    fun startMovieMultiSelect(initialId: Long? = null) {
        _isMovieMultiSelectMode.value = true
        _selectedMovieIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun clearMovieSelection() {
        _isMovieMultiSelectMode.value = false
        _selectedMovieIds.value = emptySet()
    }

    fun toggleMovieTagFilter(tag: String) {
        val current = _movieSelectedTagsFilter.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _movieSelectedTagsFilter.value = current
    }

    fun clearMovieTagFilter() {
        _movieSelectedTagsFilter.value = emptySet()
    }

    private fun deletePhysicalFile(context: Context, uriString: String) {
        try {
            val uri = uriString.toUri()
            when {
                uri.scheme == "content" -> {
                    val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                        ?: androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    doc?.delete()
                }
                uri.scheme == "file" -> {
                    File(uri.path ?: return).let { if (it.exists()) it.delete() }
                }
                uriString.startsWith("/") -> {
                    File(uriString).let { if (it.exists()) it.delete() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSelectedMovies(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = repository.getAllMoviesDirect()
            val toDeleteIds = _selectedMovieIds.value
            val toDeleteItems = all.filter { toDeleteIds.contains(it.id) }

            toDeleteItems.forEach { movie ->
                deletePhysicalFile(context, movie.uri)
                repository.deleteMovie(context, movie)
            }

            withContext(Dispatchers.Main) {
                clearMovieSelection()
            }
        }
    }

    fun deleteMovie(context: Context, movie: Movie) {
        viewModelScope.launch(Dispatchers.IO) {
            deletePhysicalFile(context, movie.uri)
            repository.deleteMovie(context, movie)
        }
    }

    fun clearAllMovieHistory() {
        viewModelScope.launch {
            val all = repository.getAllMoviesDirect()
            val toUpdate = all.filter { it.lastPlayedPosition > 0 }
                .map { it.copy(lastPlayedPosition = 0) }
            if (toUpdate.isNotEmpty()) {
                toUpdate.forEach { repository.updateMovie(it) }
            }
        }
    }

    fun openMovie(movie: Movie) {
        _activeMovie.value = movie
        _isPlayerPlaying.value = true
    }

    fun closeMoviePlayer() {
        _activeMovie.value = null
    }

    fun setIsPickingFile(picking: Boolean) {
        _isPickingFile.value = picking
    }

    fun setPlayerPlaying(playing: Boolean) {
        _isPlayerPlaying.value = playing
    }

    fun setInPipMode(value: Boolean) {
        _isInPipMode.value = value
    }

    fun isUriAccessible(context: Context, uri: String?): Boolean {
        return MediaUtils.isUriAccessible(context, uri)
    }

    fun updateMovieSettings(
        movie: Movie,
        position: Long,
        speed: Float,
        subtitleUri: String?,
        audioIdx: Int,
        subIdx: Int,
        orientation: Int,
        zoomMode: Int,
    ) {
        viewModelScope.launch {
            repository.updateMovie(
                movie.copy(
                    lastPlayedPosition = position,
                    playbackSpeed = speed,
                    subtitleUri = subtitleUri,
                    audioTrackIndex = audioIdx,
                    subtitleTrackIndex = subIdx,
                    orientation = orientation,
                    zoomMode = zoomMode,
                )
            )
        }
    }

    fun downloadSmbFile(context: Context, uriString: String, title: String) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _downloadingFileName.value = title
            _downloadProgress.value = 0f
            val settings = repository.getSettingsDirect()
            var localFile: File? = null
            try {
                val smbFile = VideoThumbnailHelper.getSmbFileForUri(appContext, uriString, settings)
                if (smbFile == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, translate(settings.language, "Failed to connect to SMB server", "Gagal menyambungkan ke server SMB"), Toast.LENGTH_LONG).show()
                    }
                    _downloadProgress.value = null
                    _downloadingFileName.value = null
                    return@launch
                }

                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MediaDeck")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                val cleanFileName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val extension = if (uriString.contains(".mp4", true)) "mp4"
                else if (uriString.contains(".mkv", true)) "mkv"
                else if (uriString.contains(".jpg", true) || uriString.contains(".jpeg", true)) "jpg"
                else if (uriString.contains(".png", true)) "png"
                else "bin"

                var finalFile = File(downloadDir, "$cleanFileName.$extension")
                var counter = 1
                while (finalFile.exists()) {
                    finalFile = File(downloadDir, "${cleanFileName}_$counter.$extension")
                    counter++
                }
                localFile = finalFile
                val fileLength = smbFile.length()
                var bytesCopied = 0L
                val buffer = ByteArray(1024 * 64)

                smbFile.getInputStream().use { input ->
                    FileOutputStream(finalFile).use { output ->
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            if (!isActive) throw CancellationException("Download cancelled")
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (fileLength > 0L) {
                                _downloadProgress.value = bytesCopied.toFloat() / fileLength.toFloat()
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                val localUri = Uri.fromFile(finalFile).toString()

                MediaScannerConnection.scanFile(appContext, arrayOf(finalFile.absolutePath), null, null)

                if ((extension == "mp4" || extension == "mkv")) {
                    val newMovie = Movie(
                        title = title,
                        uri = localUri,
                        size = finalFile.length(),
                        folderName = "Downloads",
                        dateAdded = System.currentTimeMillis(),
                    )
                    repository.insertMovies(listOf(newMovie))
                } else if (extension == "jpg" || extension == "png") {
                    val newItem = GalleryItem(
                        uri = localUri,
                        name = title,
                        mimeType = if (extension == "png") "image/png" else "image/jpeg",
                        size = finalFile.length(),
                        dateAdded = System.currentTimeMillis(),
                        folderName = "Downloads",
                    )
                    repository.insertGalleryItems(listOf(newItem))
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, translate(settings.language, "Download completed: $title", "Selesai mengunduh: $title"), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                localFile?.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, translate(settings.language, "Download failed: ${e.message}", "Gagal mengunduh: ${e.message}"), Toast.LENGTH_LONG).show()
                }
            } finally {
                _downloadProgress.value = null
                _downloadingFileName.value = null
            }
        }
    }

    fun setSmbOnline(online: Boolean) {
        _isSmbOnline.value = online
    }

    fun reshuffle() {
        _shuffledMovies.value = null
    }

    init {
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            _movieSort.value = settings.defaultMovieSort
        }
    }
}
