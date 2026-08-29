package com.mediadeck.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.scan.MediaProcessingEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {

    private val _gallerySearch = MutableStateFlow("")
    val gallerySearch = _gallerySearch.asStateFlow()

    private val _selectedTagsFilter = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagsFilter = _selectedTagsFilter.asStateFlow()

    private val _galleryFavoriteOnlyFilter = MutableStateFlow(value = false)

    private val _shuffledItems = MutableStateFlow<List<GalleryItem>?>(null)

    private val _gallerySort = MutableStateFlow("date_desc")
    val gallerySort = _gallerySort.asStateFlow()

    private val _selectedGalleryIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedGalleryIds = _selectedGalleryIds.asStateFlow()

    private val _isGalleryMultiSelectMode = MutableStateFlow(value = false)
    val isGalleryMultiSelectMode = _isGalleryMultiSelectMode.asStateFlow()

    private val _isGalleryGroupedByFolder = MutableStateFlow(value = true)
    val isGalleryGroupedByFolder = _isGalleryGroupedByFolder.asStateFlow()

    private val _selectedGalleryFolderName = MutableStateFlow<String?>(null)
    val selectedGalleryFolderName = _selectedGalleryFolderName.asStateFlow()

    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex = _scrollIndex.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset = _scrollOffset.asStateFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    fun requestScrollToTop() {
        _scrollToTopEvent.tryEmit(Unit)
    }

    private val _isSmbOnline = MutableStateFlow(value = true)

    val availableTags: StateFlow<List<String>> = repository.allGalleryItems
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

    private data class GalleryFilterState(
        val search: String,
        val tags: Set<String>,
        val favoriteOnly: Boolean,
        val sort: String,
        val shuffled: List<GalleryItem>?,
        val folderName: String?,
    )

    private val galleryFilterState = combine(
        _gallerySearch,
        _selectedTagsFilter,
        _galleryFavoriteOnlyFilter,
        combine(_gallerySort, _shuffledItems, _selectedGalleryFolderName) { sort, shuffled, folder -> Triple(sort, shuffled, folder) },
    ) { search, tags, favorite, triple ->
        GalleryFilterState(search, tags, favorite, triple.first, triple.second, triple.third)
    }

    private val offlineFilteredGalleryItems: Flow<List<GalleryItem>> = combine(
        repository.allGalleryItems,
        repository.appSettings,
        _isSmbOnline,
    ) { items, settings, smbOnline ->
        if (settings.hideOfflineSmb && !smbOnline) {
            items.filter { !it.uri.startsWith("smb://") && !it.uri.contains("smbprovider") }
        } else {
            items
        }
    }.debounce(300L)

    val appSettings: StateFlow<AppSettings> = repository.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val filteredGalleryItems: StateFlow<List<GalleryItem>> = combine(
        offlineFilteredGalleryItems,
        galleryFilterState,
    ) { items, filters ->
        if (items.isEmpty()) return@combine emptyList()

        var list = items

        if (filters.search.isNotEmpty()) {
            list = list.filter {
                it.name.contains(filters.search, ignoreCase = true) ||
                    it.tags.contains(filters.search, ignoreCase = true)
            }
        }

        if (filters.tags.isNotEmpty()) {
            val lowercaseTags = filters.tags.map { it.lowercase() }
            list = list.filter { item ->
                val itemTags = item.tags.split(",").map { it.trim().lowercase() }
                lowercaseTags.all { tag -> itemTags.contains(tag) }
            }
        }

        if (filters.favoriteOnly) {
            list = list.filter { it.isFavorite }
        }

        if (filters.folderName != null) {
            list = list.filter { it.folderName == filters.folderName }
        }

        list = when (filters.sort) {
            "date_desc" -> list.sortedByDescending { it.dateAdded }
            "date_asc" -> list.sortedBy { it.dateAdded }
            "name_asc" -> list.sortedBy { it.name.lowercase() }
            "name_desc" -> list.sortedByDescending { it.name.lowercase() }
            "size_desc" -> list.sortedByDescending { it.size }
            "size_asc" -> list.sortedBy { it.size }
            "random" -> {
                if (filters.shuffled == null) {
                    val newlyShuffled = list.shuffled()
                    viewModelScope.launch {
                        _shuffledItems.value = newlyShuffled
                    }
                    newlyShuffled
                } else {
                    val itemMap = list.associateBy { it.id }
                    filters.shuffled.mapNotNull { itemMap[it.id] }
                }
            }
            else -> list.sortedByDescending { it.dateAdded }
        }
        list
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val galleryTagCounts: StateFlow<Map<String, Int>> = repository.allGalleryItems
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

    val totalGalleryCount: StateFlow<Int> = offlineFilteredGalleryItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setGallerySearch(query: String) {
        _gallerySearch.value = query
    }

    fun toggleTagFilter(tag: String) {
        val current = _selectedTagsFilter.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _selectedTagsFilter.value = current
    }

    fun selectSingleTagFilter(tag: String) {
        _selectedTagsFilter.value = setOf(tag)
    }

    fun clearTagFilter() {
        _selectedTagsFilter.value = emptySet()
    }

    fun setGalleryFavoriteFilter(enabled: Boolean) {
        _galleryFavoriteOnlyFilter.value = enabled
    }

    fun updateGalleryItemDetails(item: GalleryItem, newFolderName: String, newTags: String, width: Int = 0, height: Int = 0) {
        viewModelScope.launch {
            repository.updateGalleryItem(item.copy(
                folderName = newFolderName,
                tags = newTags,
                width = if (width > 0) width else item.width,
                height = if (height > 0) height else item.height
            ))
        }
    }

    fun setGallerySort(sort: String) {
        _gallerySort.value = sort
        if (sort != "random") {
            _shuffledItems.value = null
        }
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            repository.updateSettings(settings.copy(defaultGallerySort = sort))
        }
    }

    fun setLayoutMode(mode: String) {
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            repository.updateSettings(settings.copy(layoutMode = mode))
        }
    }

    fun toggleGalleryFavorite(item: GalleryItem) {
        viewModelScope.launch {
            repository.updateGalleryItem(item.copy(isFavorite = !item.isFavorite))
        }
    }

    fun toggleGallerySelection(id: Long) {
        val current = _selectedGalleryIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedGalleryIds.value = current
        if (current.isEmpty()) {
            _isGalleryMultiSelectMode.value = false
        }
    }

    fun startGalleryMultiSelect(initialId: Long? = null) {
        _isGalleryMultiSelectMode.value = true
        _selectedGalleryIds.value = initialId?.let { setOf(it) } ?: emptySet()
    }

    fun clearGallerySelection() {
        _isGalleryMultiSelectMode.value = false
        _selectedGalleryIds.value = emptySet()
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

    fun deleteSelectedGalleryItems(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = repository.getAllGalleryItemsDirect()
            val toDeleteIds = _selectedGalleryIds.value
            val toDeleteItems = all.filter { toDeleteIds.contains(it.id) }

            toDeleteItems.forEach { item ->
                deletePhysicalFile(context, item.uri)
                repository.deleteGalleryItem(context, item)
            }

            withContext(Dispatchers.Main) {
                clearGallerySelection()
            }
        }
    }

    fun deleteGalleryItem(context: Context, item: GalleryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            deletePhysicalFile(context, item.uri)
            repository.deleteGalleryItem(context, item)
        }
    }

    fun setGalleryGroupedByFolder(grouped: Boolean) {
        _isGalleryGroupedByFolder.value = grouped
        if (!grouped) {
            _selectedGalleryFolderName.value = null
        }
    }

    fun setSelectedGalleryFolderName(folder: String?) {
        _selectedGalleryFolderName.value = folder
    }

    private var folderGenerationJob: Job? = null

    fun onFolderVisited(context: Context, folderName: String?) {
        folderGenerationJob?.cancel()
        if (folderName.isNullOrBlank()) return

        folderGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsDirect()
            val processedUris = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            repository.allGalleryItems.map { list ->
                list.filter { it.folderName == folderName }
            }.collect { itemsInFolder ->
                val pending = itemsInFolder
                    .filter {
                        !VideoThumbnailHelper.hasCachedThumbnail(context, it.id)
                        && !processedUris.contains(it.uri)
                    }
                    .sortedBy { it.name.lowercase() }

                if (pending.isEmpty()) return@collect

                pending.forEach { processedUris.add(it.uri) }

                coroutineScope {
                    pending.forEach { item ->
                        launch {
                            try {
                                MediaProcessingEngine.enqueuePriority(
                                    MediaProcessingEngine.ProcessingTask(
                                        context = context,
                                        uri = item.uri,
                                        mediaId = item.id,
                                        settings = settings,
                                        skipThumbnail = false,
                                    ) { _ ->
                                    }
                                )
                            } catch (_: Exception) {
                                processedUris.remove(item.uri)
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

    fun setSmbOnline(online: Boolean) {
        _isSmbOnline.value = online
    }

    fun reshuffle() {
        _shuffledItems.value = null
    }

    init {
        viewModelScope.launch {
            val settings = repository.getSettingsDirect()
            _gallerySort.value = settings.defaultGallerySort
        }
    }
}
