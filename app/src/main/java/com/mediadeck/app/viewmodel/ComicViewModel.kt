package com.mediadeck.app.viewmodel

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.scan.LocalScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ComicViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {

    private val comicPagesCache = android.util.LruCache<Long, List<ComicPage>>(10)

    private val _comicSearch = MutableStateFlow("")
    val comicSearch = _comicSearch.asStateFlow()

    private val _comicTabFilter = MutableStateFlow("all")
    val comicTabFilter = _comicTabFilter.asStateFlow()

    private val _comicSort = MutableStateFlow("name_asc")
    val comicSort = _comicSort.asStateFlow()

    private val _shuffledComics = MutableStateFlow<List<Comic>?>(null)

    private val _comicSelectedTagsFilter = MutableStateFlow<Set<String>>(emptySet())
    val comicSelectedTagsFilter = _comicSelectedTagsFilter.asStateFlow()

    private val _selectedComicIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedComicIds = _selectedComicIds.asStateFlow()

    private val _isComicMultiSelectMode = MutableStateFlow(value = false)
    val isComicMultiSelectMode = _isComicMultiSelectMode.asStateFlow()

    private val _isSmbOnline = MutableStateFlow(value = true)

    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex = _scrollIndex.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset = _scrollOffset.asStateFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    fun requestScrollToTop() {
        _scrollToTopEvent.tryEmit(Unit)
    }

    data class ComicFilters(
        val search: String,
        val tab: String,
        val sort: String,
        val tags: Set<String>,
        val shuffled: List<Comic>?,
    )

    private val offlineFilteredComics: Flow<List<Comic>> = combine(
        repository.allComics,
        repository.appSettings,
        _isSmbOnline,
    ) { items, settings, smbOnline ->
        if (settings.hideOfflineSmb && !smbOnline) {
            items.filter { !it.folderUri.startsWith("smb://") && !it.folderUri.contains("smbprovider") }
        } else {
            items
        }
    }.debounce(300L)

    val filteredComics: StateFlow<List<Comic>> = combine(
        offlineFilteredComics,
        combine(
            _comicSearch,
            _comicTabFilter,
            _comicSort,
            _comicSelectedTagsFilter,
            _shuffledComics,
        ) { search, tab, sort, tags, shuffled ->
            ComicFilters(search, tab, sort, tags, shuffled)
        }
    ) { items, filters ->
        if (items.isEmpty()) return@combine emptyList()

        var list = items

        if (filters.search.isNotEmpty()) {
            list = list.filter { it.title.contains(filters.search, ignoreCase = true) }
        }

        if (filters.tags.isNotEmpty()) {
            val lowercaseTags = filters.tags.map { it.lowercase() }
            list = list.filter { item ->
                val itemTags = item.tags.split(",").map { it.trim().lowercase() }
                lowercaseTags.all { tag -> itemTags.contains(tag) }
            }
        }

        when (filters.tab) {
            "continue" -> list = list.asSequence().filter { it.currentPage > 0 }.sortedByDescending { it.lastReadTime }.toList()
            "history" -> list = list.asSequence().filter { it.lastReadTime > 0 }.sortedByDescending { it.lastReadTime }.toList()
            "favorites" -> list = list.filter { it.isFavorite }
            "read_later" -> list = list.filter { it.isReadLater }
        }

        if (filters.tab == "all") {
            when (filters.sort) {
                "name_asc" -> list = list.sortedBy { it.title.lowercase() }
                "name_desc" -> list = list.sortedByDescending { it.title.lowercase() }
                "date_desc" -> list = list.sortedByDescending { it.dateAdded }
                "date_asc" -> list = list.sortedBy { it.dateAdded }
                "last_read_desc" -> list = list.sortedByDescending { it.lastReadTime }
                "pages_desc" -> list = list.sortedByDescending { it.totalPages }
                "random" -> {
                    list = if (filters.shuffled == null) {
                        val newlyShuffled = list.shuffled()
                        viewModelScope.launch {
                            _shuffledComics.value = newlyShuffled
                        }
                        newlyShuffled
                    } else {
                        val itemMap = list.associateBy { it.id }
                        filters.shuffled.mapNotNull { itemMap[it.id] }
                    }
                }
                else -> list = list.sortedBy { it.title }
            }
        }
        list
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val comicTagCounts: StateFlow<Map<String, Int>> = repository.allComics
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

    val appSettings: StateFlow<AppSettings> = repository.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val totalComicsCount: StateFlow<Int> = offlineFilteredComics
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val comicAvailableTags: StateFlow<List<String>> = repository.allComics
        .debounce(500L)
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

    val heroComic: StateFlow<Comic?> = repository.allComics
        .map { items ->
            val lastRead = items.filter { it.currentPage > 0 }.maxByOrNull { it.lastReadTime }
            lastRead ?: items.filter { it.isFavorite }.maxByOrNull { it.dateAdded }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activeComic = MutableStateFlow<Comic?>(null)
    val activeComic = _activeComic.asStateFlow()

    private val _activeComicPages = MutableStateFlow<List<ComicPage>>(emptyList())
    val activeComicPages = _activeComicPages.asStateFlow()

    private val _comicLoadError = MutableStateFlow<String?>(null)
    val comicLoadError = _comicLoadError.asStateFlow()

    private val _isComicLoading = MutableStateFlow(false)

    private val refreshingComicIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun refreshComicMetadata(context: Context, comic: Comic, force: Boolean = false) {
        if (!force && (comic.totalPages > 0) && (comic.coverUri.isNotEmpty())) return
        if (refreshingComicIds.contains(comic.id)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshingComicIds.add(comic.id)
                val isSmb = comic.folderUri.startsWith("smb://")

                var totalPages = 0
                var coverUri = ""

                if (isSmb) {
                    val settings = repository.getSettingsDirect()
                    val pages = com.mediadeck.app.util.smb.SmbScanner.loadComicPagesSmb(context, settings, comic.folderUri)
                    if (pages.isNotEmpty()) {
                        totalPages = pages.size
                        coverUri = pages.first().pageUri
                    }
                } else {
                    val pages = LocalScanner.loadComicPages(context, comic.folderUri)
                    if (pages.isNotEmpty()) {
                        totalPages = pages.size
                        coverUri = pages.first().pageUri
                    }
                }

                if (totalPages > 0) {
                    val updated = comic.copy(
                        totalPages = totalPages,
                        coverUri = coverUri,
                    )
                    repository.updateComic(updated)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                refreshingComicIds.remove(comic.id)
            }
        }
    }

    fun setSmbOnline(online: Boolean) {
        _isSmbOnline.value = online
    }

    private val _readingProgressFlow = MutableSharedFlow<Comic>(extraBufferCapacity = 1)

    init {
        _readingProgressFlow
            .debounce(1000.milliseconds)
            .onEach { comic ->
                repository.updateComic(comic)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = repository.getSettingsDirect()
                _comicSort.value = settings.defaultComicSort

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setComicSearch(query: String) {
        _comicSearch.value = query
    }

    fun setComicTab(tab: String) {
        _comicTabFilter.value = tab
    }

    fun setComicSort(sort: String) {
        _comicSort.value = sort
        if (sort != "random") {
            _shuffledComics.value = null
        }
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            repository.updateSettings(settings.copy(defaultComicSort = sort))
        }
    }

    fun setLayoutMode(mode: String) {
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            repository.updateSettings(settings.copy(layoutMode = mode))
        }
    }

    fun toggleComicFavorite(comic: Comic) {
        viewModelScope.launch {
            val updated = comic.copy(isFavorite = !comic.isFavorite)
            repository.updateComic(updated)
            if (_activeComic.value?.id == updated.id) {
                _activeComic.value = updated
            }
        }
    }

    fun toggleComicReadLater(comic: Comic) {
        viewModelScope.launch {
            val updated = comic.copy(isReadLater = !comic.isReadLater)
            repository.updateComic(updated)
            if (_activeComic.value?.id == updated.id) {
                _activeComic.value = updated
            }
        }
    }

    fun toggleComicSelection(id: Long) {
        val current = _selectedComicIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedComicIds.value = current
        if (current.isEmpty()) {
            _isComicMultiSelectMode.value = false
        }
    }

    fun startComicMultiSelect(initialId: Long? = null) {
        _isComicMultiSelectMode.value = true
        _selectedComicIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun clearComicSelection() {
        _isComicMultiSelectMode.value = false
        _selectedComicIds.value = emptySet()
    }

    fun toggleComicTagFilter(tag: String) {
        val current = _comicSelectedTagsFilter.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _comicSelectedTagsFilter.value = current
    }

    fun clearComicTagFilter() {
        _comicSelectedTagsFilter.value = emptySet()
    }

    private fun deletePhysicalFolder(context: Context, uriString: String) {
        try {
            val uri = uriString.toUri()
            when {
                uri.scheme == "content" -> {
                    val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                        ?: androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    doc?.delete()
                }
                uri.scheme == "file" -> {
                    File(uri.path ?: return).let { if (it.exists()) it.deleteRecursively() }
                }
                uriString.startsWith("/") -> {
                    File(uriString).let { if (it.exists()) it.deleteRecursively() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSelectedComics(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = repository.getAllComicsDirect()
            val toDeleteIds = _selectedComicIds.value
            val toDeleteItems = all.filter { toDeleteIds.contains(it.id) }

            toDeleteItems.forEach { comic ->
                deletePhysicalFolder(context, comic.folderUri)
                repository.deleteComic(context, comic)
                comicPagesCache.remove(comic.id)
            }

            withContext(Dispatchers.Main) {
                clearComicSelection()
            }
        }
    }

    fun deleteComic(context: Context, comic: Comic) {
        viewModelScope.launch(Dispatchers.IO) {
            deletePhysicalFolder(context, comic.folderUri)
            repository.deleteComic(context, comic)
            comicPagesCache.remove(comic.id)
        }
    }

    fun clearComicHistory(comic: Comic) {
        viewModelScope.launch {
            val updated = comic.copy(currentPage = 0, scrollOffset = 0, lastReadTime = 0, isCompleted = false)
            repository.updateComic(updated)
            if (_activeComic.value?.id == comic.id) {
                _activeComic.value = updated
            }
        }
    }

    fun clearAllComicHistory() {
        viewModelScope.launch {
            val all = repository.getAllComicsDirect()
            val toUpdate = all.filter { (it.currentPage > 0 || it.lastReadTime > 0 || it.isCompleted) }
                .map { it.copy(currentPage = 0, scrollOffset = 0, lastReadTime = 0, isCompleted = false) }
            if (toUpdate.isNotEmpty()) {
                toUpdate.forEach { repository.updateComic(it) }
            }
        }
    }

    fun openComic(context: Context, comic: Comic) {
        _comicLoadError.value = null
        _isComicLoading.value = true
        val cached = comicPagesCache[comic.id]
        if (cached != null) {
            _activeComic.value = comic
            _activeComicPages.value = cached
            _isComicLoading.value = false
        } else {
            _activeComic.value = comic
            _activeComicPages.value = emptyList()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var pages = cached ?: repository.getComicPagesDirect(comic.id)
                if (pages.isEmpty()) {
                    val loadedPages: List<ComicPage> = if (comic.folderUri.startsWith("smb://")) {
                        val currentSettings = repository.getSettingsDirect()
                        android.util.Log.d("ComicViewModel", "Loading SMB pages for: ${comic.folderUri}")
                        com.mediadeck.app.util.smb.SmbScanner.loadComicPagesSmb(
                            context = context,
                            settings = currentSettings,
                            smbFolderUrl = comic.folderUri,
                        )
                    } else {
                        LocalScanner.loadComicPages(context, comic.folderUri)
                    }

                    if (loadedPages.isEmpty()) {
                        throw Exception(if (repository.getSettingsDirect().language == "en") "No image files detected in target folder." else "Tidak ada berkas gambar terdeteksi di folder tujuan.")
                    }

                    val pagesWithId = loadedPages.map { p ->
                        ComicPage(
                            id = p.id,
                            comicId = comic.id,
                            pageIndex = p.pageIndex,
                            pageUri = p.pageUri,
                            pageName = p.pageName,
                        )
                    }

                    repository.insertComicPages(pagesWithId)
                    pages = repository.getComicPagesDirect(comic.id)
                    comicPagesCache.put(comic.id, pages)
                }

                withContext(Dispatchers.Main) {
                    _activeComicPages.value = pages
                    _activeComic.value = comic
                    _comicLoadError.value = null
                    _isComicLoading.value = false
                }

                repository.updateComic(comic.copy(lastReadTime = System.currentTimeMillis()))
            } catch (e: Exception) {
                android.util.Log.e("ComicViewModel", "Error opening comic: ${comic.title}", e)
                val settings = repository.getSettingsDirect()
                val isSmb = comic.folderUri.startsWith("smb://")
                val isEn = settings.language == "en"
                val friendlyMessage = if (isSmb) {
                    if (isEn) {
                        "Unable to connect to the Samba server.\n\nDetails:\n${e.message}\n\nSolutions:\n1. Ensure your device is connected to the same local Wi-Fi network as the server.\n2. Verify that the Samba host IP/address and authentication credentials are correct."
                    } else {
                        "Tidak dapat terhubung ke server Samba.\n\nDetail:\n${e.message}\n\nSolusi:\n1. Pastikan perangkat Anda terhubung ke jaringan Wi-Fi lokal yang sama dengan server.\n2. Periksa apakah alamat IP/Host server Samba atau kredensial autentikasi Anda sudah benar."
                    }
                } else {
                    if (isEn) {
                        "Failed to access the local folder.\n\nDetails:\n${e.message}\n\nSolutions:\n1. Ensure that the application has been granted permission to access this folder.\n2. Verify that the folder still exists and has not been moved or deleted."
                    } else {
                        "Gagal mengakses folder lokal.\n\nDetail:\n${e.message}\n\nSolusi:\n1. Pastikan aplikasi telah diberikan izin untuk mengakses folder ini.\n2. Pastikan folder tersebut masih ada dan belum dipindahkan atau dihapus."
                    }
                }
                withContext(Dispatchers.Main) {
                    _comicLoadError.value = friendlyMessage
                    _isComicLoading.value = false
                }
            }
        }
    }

    fun updateReadingProgress(pageIndex: Int, scrollOffset: Int = 0) {
        val current = _activeComic.value ?: return
        val totalPages = current.totalPages
        val justCompleted = totalPages > 0 && pageIndex >= totalPages
        val updated = current.copy(
            currentPage = pageIndex,
            scrollOffset = scrollOffset,
            lastReadTime = System.currentTimeMillis(),
            isCompleted = if (justCompleted) true else current.isCompleted,
        )
        _activeComic.value = updated
        _readingProgressFlow.tryEmit(updated)
    }

    fun closeComicReader() {
        _activeComic.value = null
        _activeComicPages.value = emptyList()
        _comicLoadError.value = null
    }

    fun setScrollPosition(index: Int, offset: Int) {
        _scrollIndex.value = index
        _scrollOffset.value = offset
    }

    fun reshuffle() {
        _shuffledComics.value = null
    }
}
