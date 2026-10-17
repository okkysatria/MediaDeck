@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mediadeck.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.DraggableStaggeredGridScrollbar
import com.mediadeck.app.ui.components.MediaDeckHeader
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.components.rememberBottomBarPadding
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaFolderIdentity
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private enum class GallerySheet { Options, Sort }


private fun formatCount(n: Int): String = NumberFormat.getInstance().format(n)

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= (1L shl 30) -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
    bytes >= (1L shl 20) -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> "${(bytes / 1024L).coerceAtLeast(1L)} KB"
}

private val dateFormatCache = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
    override fun initialValue(): HashMap<String, SimpleDateFormat> = HashMap()
}


private fun formatDate(value: Long, withTime: Boolean): String {
    if (value <= 0L) return ""
    val millis = if (value < 100_000_000_000L) value * 1000L else value
    val pattern = if (withTime) "dd MMM yyyy, HH:mm" else "dd MMM yyyy"
    val locale = Locale.getDefault()
    val cache = dateFormatCache.get() ?: HashMap()
    val format = cache.getOrPut("$pattern|$locale") { SimpleDateFormat(pattern, locale) }
    return format.format(Date(millis))
}

private val InvalidFileNameChars = Regex("[\\\\/:*?\"<>|]")


private fun resolveDisplayName(item: GalleryItem): String {
    val base = item.name.ifBlank { "media_${item.id}" }
        .replace(InvalidFileNameChars, "_")
        .trim()
        .ifEmpty { "media_${item.id}" }
    if (base.substringAfterLast('.', "").isNotEmpty()) return base
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType)
    return if (ext.isNullOrEmpty()) base else "$base.$ext"
}


private fun uniqueFile(dir: File, name: String): File {
    var file = File(dir, name)
    if (!file.exists()) return file
    val stem = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "")
    var index = 1
    while (file.exists()) {
        file = File(dir, if (ext.isEmpty()) "$stem ($index)" else "$stem ($index).$ext")
        index++
    }
    return file
}


internal fun saveToDownloads(context: Context, item: GalleryItem): Boolean {
    return try {
        val source = item.uri.toUri()
        val input = (if (source.scheme.isNullOrEmpty()) File(item.uri).inputStream()
        else context.contentResolver.openInputStream(source)) ?: return false
        val displayName = resolveDisplayName(item)
        val mime = item.mimeType.ifBlank { "application/octet-stream" }
        input.use { stream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                try {
                    val out = resolver.openOutputStream(target) ?: error("No output stream")
                    out.use { stream.copyTo(it) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(target, values, null, null)
                } catch (e: Exception) {

                    runCatching { resolver.delete(target, null, null) }
                    return false
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val target = uniqueFile(dir, displayName)
                try {
                    target.outputStream().use { out -> stream.copyTo(out) }
                } catch (e: Exception) {
                    runCatching { target.delete() }
                    return false
                }

                runCatching {
                    MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
                }
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}


@Immutable
private data class FolderEntry(
    val key: String,
    val label: String,
    val sortName: String,
    val count: Int,
    val covers: List<GalleryItem>,
    val latestDate: Long,
    val earliestDate: Long,
    val totalSize: Long,
)

private fun buildFolderEntries(
    items: List<GalleryItem>,
    sortOption: String,
    shuffleSeed: Int,
    keyOf: (GalleryItem) -> String,
): List<FolderEntry> {
    if (items.isEmpty()) return emptyList()


    val groups = LinkedHashMap<String, MutableList<GalleryItem>>()
    for (media in items) {
        groups.getOrPut(keyOf(media)) { ArrayList() }.add(media)
    }

    val names = HashMap<String, String>(groups.size * 2)
    val nameCounts = HashMap<String, Int>()
    for ((key, list) in groups) {
        val first = list.first()
        val name = MediaFolderIdentity.displayName(
            first.folderUri.orEmpty(), first.uri.orEmpty(), first.folderName.orEmpty()
        )
        names[key] = name
        nameCounts[name] = (nameCounts[name] ?: 0) + 1
    }

    val entries = ArrayList<FolderEntry>(groups.size)
    for ((key, list) in groups) {
        val first = list.first()
        val name = names[key].orEmpty()
        var latest = Long.MIN_VALUE
        var earliest = Long.MAX_VALUE
        var total = 0L
        for (media in list) {
            if (media.dateAdded > latest) latest = media.dateAdded
            if (media.dateAdded < earliest) earliest = media.dateAdded
            total += media.size
        }
        val label = if ((nameCounts[name] ?: 0) > 1) {
            val parent = MediaFolderIdentity.parentDisplayName(first.folderUri.orEmpty(), first.uri.orEmpty())
            if (parent != null) "$name ($parent)" else name
        } else name
        entries.add(
            FolderEntry(
                key = key,
                label = label,
                sortName = name.lowercase(),
                count = list.size,
                covers = list.take(4),
                latestDate = if (latest == Long.MIN_VALUE) 0L else latest,
                earliestDate = if (earliest == Long.MAX_VALUE) 0L else earliest,
                totalSize = total,
            )
        )
    }

    return when (sortOption) {
        "date_desc" -> entries.sortedByDescending { it.latestDate }
        "date_asc" -> entries.sortedBy { it.earliestDate }
        "name_desc" -> entries.sortedByDescending { it.sortName }
        "size_desc" -> entries.sortedByDescending { it.totalSize }
        "size_asc" -> entries.sortedBy { it.totalSize }
        "random" -> entries.shuffled(Random(shuffleSeed))
        else -> entries.sortedBy { it.sortName }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    scannerViewModel: ScannerViewModel,
    onOpenItem: (Long) -> Unit,
    onNavigateToScan: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    val items by viewModel.filteredGalleryItems.collectAsState()
    val search by viewModel.gallerySearch.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val isGroupedByFolder by viewModel.isGalleryGroupedByFolder.collectAsState()
    val selectedFolderName by viewModel.selectedGalleryFolderName.collectAsState()
    val selectedIds by viewModel.selectedGalleryIds.collectAsState()
    val isMultiSelect by viewModel.isGalleryMultiSelectMode.collectAsState()
    val sortOption by viewModel.gallerySort.collectAsState()
    val isManualRefreshing by scannerViewModel.isManualRefreshing.collectAsState()
    val selectedTags by viewModel.selectedTagsFilter.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.enqueueMissingThumbnails(context)
    }
    LaunchedEffect(viewModel, selectedFolderName) {
        selectedFolderName?.let { viewModel.onFolderVisited(context, it) }
    }

    val inFolder = selectedFolderName != null
    val showFolders = isGroupedByFolder && search.isEmpty() && selectedFolderName == null

    var isTagsPanelExpanded by remember { mutableStateOf(selectedTags.isNotEmpty()) }
    var isSearchingLocal by rememberSaveable { mutableStateOf(search.isNotEmpty()) }
    var activeSheet by remember { mutableStateOf<GallerySheet?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isFavoriteFilterActive by rememberSaveable { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<GalleryItem?>(null) }
    var shuffleSeed by rememberSaveable { mutableStateOf(Random.nextInt()) }

    val folderKeyCache = remember { HashMap<Long, Pair<Any?, String>>() }
    val folderEntries = remember(items, showFolders, sortOption, shuffleSeed) {
        if (!showFolders) {
            emptyList<FolderEntry>()
        } else {
            buildFolderEntries(items, sortOption, shuffleSeed) { media ->
                val cached = folderKeyCache[media.id]
                if (cached != null && cached.first == media.folderUri) {
                    cached.second
                } else {
                    MediaFolderIdentity.key(media.folderUri, media.uri).also {
                        folderKeyCache[media.id] = media.folderUri to it
                    }
                }
            }
        }
    }
    val selectedLookup = remember(selectedIds) { selectedIds.toHashSet() }


    val firstItem = items.firstOrNull()
    val computedFolderTitle = remember(selectedFolderName, firstItem?.id) {
        val key = selectedFolderName
        if (key == null || firstItem == null) {
            null
        } else {
            MediaFolderIdentity.displayName(
                firstItem.folderUri.orEmpty().ifEmpty { key },
                firstItem.uri.orEmpty().ifEmpty { key },
                firstItem.folderName.orEmpty(),
            )
        }
    }
    var lastFolderTitle by remember(selectedFolderName) { mutableStateOf<String?>(null) }
    LaunchedEffect(computedFolderTitle) {
        if (computedFolderTitle != null) lastFolderTitle = computedFolderTitle
    }
    val folderTitle = computedFolderTitle ?: lastFolderTitle ?: selectedFolderName?.substringAfterLast('/')


    val downloadOk = t("Saved to Downloads", "Disimpan ke folder Unduhan")
    val downloadFail = t("Download failed", "Gagal mengunduh")
    val downloadDenied = t("Storage permission denied", "Izin penyimpanan ditolak")
    var pendingDownload by remember { mutableStateOf<GalleryItem?>(null) }
    val performDownload: (GalleryItem) -> Unit = { item ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveToDownloads(context, item) }
            Toast.makeText(context, if (ok) downloadOk else downloadFail, Toast.LENGTH_SHORT).show()
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val item = pendingDownload
        pendingDownload = null
        if (granted && item != null) {
            performDownload(item)
        } else if (!granted) {
            Toast.makeText(context, downloadDenied, Toast.LENGTH_SHORT).show()
        }
    }
    val onDownload: (GalleryItem) -> Unit = { item ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = item
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performDownload(item)
        }
    }


    val latestOnDownload by rememberUpdatedState(onDownload)
    val stableOnDownload: (GalleryItem) -> Unit = remember { { item -> latestOnDownload(item) } }
    val latestOnOpenItem by rememberUpdatedState(onOpenItem)
    val stableOnOpenItem: (Long) -> Unit = remember { { id -> latestOnOpenItem(id) } }
    val onToggleSelection: (Long) -> Unit = remember(viewModel) { { id -> viewModel.toggleGallerySelection(id) } }
    val onStartMultiSelect: (Long) -> Unit = remember(viewModel) { { id -> viewModel.startGalleryMultiSelect(id) } }
    val onRequestDelete: (GalleryItem) -> Unit = remember { { item -> itemToDelete = item } }
    val onFolderClick: (String) -> Unit = remember(viewModel) { { key -> viewModel.setSelectedGalleryFolderName(key) } }


    val pendingDelete = itemToDelete
    if (pendingDelete != null || showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                if (pendingDelete != null) {
                    viewModel.deleteGalleryItem(context, pendingDelete)
                } else {
                    viewModel.deleteSelectedGalleryItems(context)
                }
                itemToDelete = null
                showDeleteConfirmDialog = false
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                itemToDelete = null
            },
            title = if (pendingDelete != null) t("Delete Media", "Hapus Media") else t("Delete Selected", "Hapus Terpilih"),
            message = if (pendingDelete != null) t("Are you sure you want to delete this file?", "Apakah Anda yakin ingin menghapus file ini?") else t("Are you sure you want to delete selected items?", "Apakah Anda yakin ingin menghapus media terpilih?"),
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isSearchingLocal) {
        if (isSearchingLocal) {

            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }


    BackHandler(enabled = isSearchingLocal || isTagsPanelExpanded || isMultiSelect || isFavoriteFilterActive || search.isNotEmpty() || selectedTags.isNotEmpty() || selectedFolderName != null) {
        when {
            isMultiSelect -> viewModel.clearGallerySelection()
            isSearchingLocal || search.isNotEmpty() -> {
                viewModel.setGallerySearch("")
                isSearchingLocal = false
            }
            isTagsPanelExpanded || selectedTags.isNotEmpty() -> {
                viewModel.clearTagFilter()
                isTagsPanelExpanded = false
            }
            isFavoriteFilterActive -> {
                viewModel.setGalleryFavoriteFilter(false)
                isFavoriteFilterActive = false
            }
            selectedFolderName != null -> {
                viewModel.setSelectedGalleryFolderName(null)
            }
        }
    }


    val gridState = key(selectedFolderName) {
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = if (selectedFolderName == null) viewModel.scrollIndex.value else 0,
            initialFirstVisibleItemScrollOffset = if (selectedFolderName == null) viewModel.scrollOffset.value else 0,
        )
    }
    LaunchedEffect(viewModel.scrollToTopEvent, gridState) {
        viewModel.scrollToTopEvent.collect {
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(gridState, inFolder) {
        if (inFolder) return@LaunchedEffect
        try {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .debounce(500.milliseconds)
                .collectLatest { (index, offset) -> viewModel.setScrollPosition(index, offset) }
        } finally {

            withContext(NonCancellable) {
                viewModel.setScrollPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            }
        }
    }


    var skipFirstScrollReset by remember { mutableStateOf(true) }
    LaunchedEffect(search, sortOption, selectedTags, isFavoriteFilterActive, isGroupedByFolder) {
        if (skipFirstScrollReset) {
            skipFirstScrollReset = false
        } else {
            gridState.scrollToItem(0)
        }
    }

    val sortLabels = mapOf(
        "name_asc" to t("Name (A-Z)", "Nama (A-Z)"),
        "name_desc" to t("Name (Z-A)", "Nama (Z-A)"),
        "date_desc" to t("Newest Added", "Terbaru Ditambahkan"),
        "date_asc" to t("Oldest Added", "Terlama Ditambahkan"),
        "size_desc" to t("Largest Size", "Ukuran Terbesar"),
        "size_asc" to t("Smallest Size", "Ukuran Kecil"),
        "random" to t("Shuffle / Random", "Acak"),
    )

    Scaffold(
        topBar = {
            if (isSearchingLocal || isMultiSelect) {
                TopAppBar(
                    modifier = Modifier.clickable { viewModel.requestScrollToTop() },
                    navigationIcon = {
                        if (isSearchingLocal) {
                            IconButton(onClick = {
                                viewModel.setGallerySearch("")
                                isSearchingLocal = false
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                            }
                        } else if (isMultiSelect) {
                            IconButton(onClick = { viewModel.clearGallerySelection() }) {
                                Icon(Icons.Default.Close, contentDescription = t("Cancel", "Batal"))
                            }
                        }
                    },
                    title = {
                        if (isSearchingLocal) {
                            TextField(
                                value = search,
                                onValueChange = { viewModel.setGallerySearch(it) },
                                placeholder = { Text(t("Search gallery...", "Cari galeri..."), fontSize = 15.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    if (search.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setGallerySearch("") }) {
                                            Icon(Icons.Default.Clear, null)
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 12.dp)
                                    .focusRequester(focusRequester),
                            )
                        } else {
                            Text(t("${selectedIds.size} Selected", "${selectedIds.size} Terpilih"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (isMultiSelect) {
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = t("Delete", "Hapus"), tint = DeckBadgeRed)
                            }
                        }
                    }
                )
            } else {
                val itemsCountText = formatCount(items.size)
                val subtitle = t("$itemsCountText items", "$itemsCountText media")
                MediaDeckHeader(
                    icon = Icons.Outlined.Image,
                    title = folderTitle ?: t("Gallery", "Galeri"),
                    subtitle = subtitle,
                    onBackClick = if (selectedFolderName != null) ({ viewModel.setSelectedGalleryFolderName(null) }) else null,
                    onSearchClick = { isSearchingLocal = true },
                    onMoreClick = { activeSheet = GallerySheet.Options },
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isManualRefreshing,
            onRefresh = {
                if (sortOption == "random") {
                    viewModel.reshuffle()
                    shuffleSeed = Random.nextInt()
                }
                scannerViewModel.autoScanLibrary(true, "gallery")
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isFavoriteFilterActive) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AssistChip(
                            onClick = {
                                viewModel.setGalleryFavoriteFilter(false)
                                isFavoriteFilterActive = false
                            },
                            label = { Text(t("Favorites", "Favorit"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Favorite, null, tint = DeckBadgeRed, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) },
                            shape = CircleShape,
                        )
                    }
                }

                AnimatedVisibility(visible = isTagsPanelExpanded) {
                    TagFilterPanel(viewModel)
                }

                ScanStatusSection(
                    scannerViewModel = scannerViewModel,
                    floatingScanStatus = settings.floatingScanStatus,
                )

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val hasActiveFilter = search.isNotEmpty() || selectedTags.isNotEmpty() ||
                                    selectedFolderName != null || isFavoriteFilterActive
                            Text(
                                if (hasActiveFilter) t("No results", "Tidak ada hasil")
                                else t("No media found", "Media tidak ditemukan")
                            )
                            if (!hasActiveFilter) {
                                Spacer(modifier = Modifier.height(16.dp))
                                FloatingActionButton(
                                    onClick = onNavigateToScan,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = t("Add Folder", "Tambah Folder"))
                                }
                            }
                        }
                    }
                } else {
                    val bottomBarPadding = rememberBottomBarPadding(16.dp)
                    val isTitleOnly = settings.layoutMode == "title_only"
                    val isUniformGrid = settings.galleryGridType == "uniform"
                    val gridColumns = settings.gridColumns
                    val screenWidthDp = LocalConfiguration.current.screenWidthDp


                    val folderColumns = when {
                        isTitleOnly || gridColumns == 1 -> if (screenWidthDp >= 600) 2 else 1
                        gridColumns == 2 -> when {
                            screenWidthDp >= 840 -> 4
                            screenWidthDp >= 600 -> 3
                            else -> 2
                        }
                        else -> ((screenWidthDp - 32 + 12) / (152 + 12)).coerceAtLeast(1)
                    }
                    val folderRows = remember(folderEntries, folderColumns) { folderEntries.chunked(folderColumns) }
                    val gridCells: StaggeredGridCells = remember(isTitleOnly, gridColumns, showFolders, screenWidthDp) {
                        when {
                            showFolders -> StaggeredGridCells.Fixed(1)
                            isTitleOnly -> StaggeredGridCells.Fixed(1)
                            gridColumns == 1 -> if (screenWidthDp >= 600) StaggeredGridCells.Fixed(2) else StaggeredGridCells.Fixed(1)
                            gridColumns == 2 -> when {
                                screenWidthDp >= 840 -> StaggeredGridCells.Fixed(4)
                                screenWidthDp >= 600 -> StaggeredGridCells.Fixed(3)
                                else -> StaggeredGridCells.Fixed(2)
                            }
                            else -> StaggeredGridCells.Adaptive(140.dp)
                        }
                    }


                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalStaggeredGrid(
                            state = gridState,
                            columns = gridCells,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomBarPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = if (isTitleOnly) 8.dp else 12.dp,
                        ) {
                            if (showFolders) {
                                items(folderRows, key = { it.firstOrNull()?.key ?: it.hashCode().toString() }, contentType = { "folder_row" }) { rowEntries ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        rowEntries.forEach { entry ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (isTitleOnly) {
                                                    FolderListRow(entry = entry, settings = settings, onClick = onFolderClick)
                                                } else {
                                                    FolderCard(entry = entry, settings = settings, onClick = onFolderClick)
                                                }
                                            }
                                        }
                                        val emptySlots = (folderColumns - rowEntries.size).coerceAtLeast(0)
                                        repeat(emptySlots) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(items, key = { it.id }, contentType = { "media" }) { item ->
                                    val isSelected = selectedLookup.contains(item.id)
                                    if (isTitleOnly) {
                                        GalleryListItemContent(
                                            item = item,
                                            isSelected = isSelected,
                                            isMultiSelect = isMultiSelect,
                                            settings = settings,
                                            onOpenItem = stableOnOpenItem,
                                            onToggleSelection = onToggleSelection,
                                            onStartMultiSelect = onStartMultiSelect,
                                            onDelete = onRequestDelete,
                                            onDownload = stableOnDownload,
                                        )
                                    } else {
                                        GalleryCardContent(
                                            item = item,
                                            isSelected = isSelected,
                                            isMultiSelect = isMultiSelect,
                                            uniformGrid = isUniformGrid,
                                            settings = settings,
                                            onOpenItem = stableOnOpenItem,
                                            onToggleSelection = onToggleSelection,
                                            onStartMultiSelect = onStartMultiSelect,
                                            onDelete = onRequestDelete,
                                            onDownload = stableOnDownload,
                                        )
                                    }
                                }
                            }
                        }
                        if (settings.showSideScrollbar) {
                            DraggableStaggeredGridScrollbar(
                                gridState = gridState,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = bottomBarPadding),
                            )
                        }
                    }
                }
            }
        }
    }


    activeSheet?.let { sheet ->
        GallerySideSheet(
            title = if (sheet == GallerySheet.Options) t("Gallery Options", "Opsi Galeri") else t("Sort Gallery", "Urutkan Galeri"),
            onDismiss = { activeSheet = null },
            onBack = if (sheet == GallerySheet.Sort) {
                { activeSheet = GallerySheet.Options }
            } else null,
        ) {
            if (sheet == GallerySheet.Options) {
                SheetSectionLabel(t("View", "Tampilan"))
                SheetSegmented(
                    options = listOf("grid" to t("Grid", "Grid"), "title_only" to t("List", "Daftar")),
                    selected = if (settings.layoutMode == "title_only") "title_only" else "grid",
                    onSelect = { viewModel.setLayoutMode(it) },
                )
                SheetSectionLabel(t("Grid style", "Gaya grid"))
                SheetSegmented(
                    options = listOf("staggered" to t("Staggered", "Staggered"), "uniform" to t("Uniform", "Seragam")),
                    selected = if (settings.galleryGridType == "uniform") "uniform" else "staggered",
                    onSelect = { viewModel.setGalleryGridType(it) },
                )
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetActionRow(
                        icon = Icons.Outlined.Folder,
                        label = t("Group by Folder", "Grup Folder"),
                        onClick = { viewModel.setGalleryGroupedByFolder(!isGroupedByFolder) },
                        trailing = {
                            Switch(checked = isGroupedByFolder, onCheckedChange = { viewModel.setGalleryGroupedByFolder(it) })
                        },
                    )
                    SheetActionRow(
                        icon = Icons.Outlined.FavoriteBorder,
                        label = t("Favorites", "Favorit"),
                        onClick = {
                            val enabled = !isFavoriteFilterActive
                            isFavoriteFilterActive = enabled
                            viewModel.setGalleryFavoriteFilter(enabled)
                            activeSheet = null
                        },
                        trailing = { Switch(checked = isFavoriteFilterActive, onCheckedChange = null) },
                    )
                    SheetActionRow(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        label = t("Sort By...", "Urutkan"),
                        onClick = { activeSheet = GallerySheet.Sort },
                    )
                    SheetActionRow(
                        icon = Icons.Outlined.FilterAlt,
                        label = t("Filter Tags...", "Filter Label"),
                        onClick = { isTagsPanelExpanded = !isTagsPanelExpanded; activeSheet = null },
                    )
                    SheetActionRow(
                        icon = Icons.Outlined.CheckCircle,
                        label = t("Select Multiple", "Pilih Banyak"),
                        onClick = { viewModel.startGalleryMultiSelect(); activeSheet = null },
                    )
                }
            } else {
                val groups = listOf(
                    listOf("name_asc", "name_desc"),
                    listOf("date_desc", "date_asc"),
                    listOf("size_desc", "size_asc"),
                    listOf("random"),
                )
                groups.forEachIndexed { index, group ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    group.forEach { key ->
                        val selected = sortOption == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { viewModel.setGallerySort(key); activeSheet = null }
                                .padding(horizontal = 12.dp)
                                .height(52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                sortLabels[key].orEmpty(),
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ScanStatusSection(scannerViewModel: ScannerViewModel, floatingScanStatus: Boolean) {
    val scanProgress by scannerViewModel.scanProgress.collectAsState()
    val isScanActive by scannerViewModel.isScanActive.collectAsState()
    val isScanPaused by scannerViewModel.isScanPaused.collectAsState()
    val isManualRefreshing by scannerViewModel.isManualRefreshing.collectAsState()
    AnimatedVisibility(
        visible = scanProgress != null && (floatingScanStatus || isManualRefreshing || isScanActive),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        ScanStatusCard(
            scanProgress = scanProgress,
            isScanActive = isScanActive,
            isScanPaused = isScanPaused,
            onTogglePause = { scannerViewModel.togglePauseScan() },
            onStopScan = { scannerViewModel.stopScan() }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterPanel(viewModel: GalleryViewModel) {
    val allTags by viewModel.availableTags.collectAsState()
    val selectedTags by viewModel.selectedTagsFilter.collectAsState()
    val tagCounts by viewModel.galleryTagCounts.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            t("Filter by Tags", "Filter berdasarkan Label"),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val selectedList = remember(allTags, selectedTags) { allTags.filter { selectedTags.contains(it) }.sorted() }
        val unselectedList = remember(allTags, selectedTags, tagCounts) {
            allTags.filter { !selectedTags.contains(it) }
                .sortedByDescending { tagCounts[it] ?: 0 }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedTags.isNotEmpty()) {
                item(key = "__clear") {
                    AssistChip(
                        onClick = { viewModel.clearTagFilter() },
                        label = { Text(t("Clear", "Hapus"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) },
                        shape = CircleShape,
                    )
                }
            }
            lazyRowItems(selectedList, key = { "s_$it" }) { tag ->
                TagChip(tag, "${tagCounts[tag] ?: 0}", selected = true) { viewModel.toggleTagFilter(tag) }
            }
            lazyRowItems(unselectedList, key = { "u_$it" }) { tag ->
                TagChip(tag, "${tagCounts[tag] ?: 0}", selected = false) { viewModel.toggleTagFilter(tag) }
            }
        }
    }
}


@Composable
private fun GalleryHeader(
    title: String,
    subtitle: String,
    showIcon: Boolean,
    onBack: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onBack != null) 8.dp else 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
            }
            Spacer(Modifier.width(4.dp))
        } else if (showIcon) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Default.Search, contentDescription = t("Search", "Cari"))
        }
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreVert, contentDescription = t("More", "Lainnya"))
        }
    }
}


@Composable
private fun TagChip(tag: String, countText: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        border = null,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tag, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(countText, fontSize = 15.sp, fontWeight = FontWeight.Normal)
            }
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}


@Composable
private fun FolderMosaic(covers: List<GalleryItem>, settings: AppSettings, modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    if (covers.size == 1) {
        val cover = covers.first()
        Box(modifier.background(placeholder)) {
            AsyncThumbnailImage(
                uriString = cover.uri,
                mediaId = cover.id,
                hasThumbnail = cover.hasThumbnail,
                settings = settings,
                thumbnailVariant = "gallery",
                showProcessingIndicator = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    Column(modifier.background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0..1) {
                    val cover = covers.getOrNull(row * 2 + col)
                    Box(Modifier.weight(1f).fillMaxHeight().background(placeholder)) {
                        if (cover != null) {
                            AsyncThumbnailImage(
                                uriString = cover.uri,
                                mediaId = cover.id,
                                hasThumbnail = cover.hasThumbnail,
                                settings = settings,
                                thumbnailVariant = "gallery",
                                showProcessingIndicator = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    entry: FolderEntry,
    settings: AppSettings,
    onClick: (String) -> Unit,
) {
    val countLabel = t("${entry.count} items", "${entry.count} item")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(shape)
            .clickable { onClick(entry.key) },
    ) {
        FolderMosaic(
            covers = entry.covers,
            settings = settings,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(0f, 1f),
                            blurRadius = 3f,
                        ),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = countLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(0f, 1f),
                            blurRadius = 3f,
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun FolderListRow(
    entry: FolderEntry,
    settings: AppSettings,
    onClick: (String) -> Unit,
) {
    val countLabel = t("${entry.count} items", "${entry.count} item")
    val dateLabel = remember(entry.latestDate) { formatDate(entry.latestDate, withTime = false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick(entry.key) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderMosaic(entry.covers, settings, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(countLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (dateLabel.isNotEmpty()) {
                Text(dateLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}


@Composable
private fun ItemDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = com.mediadeck.app.ui.theme.DeckSurface,
    ) {
        if (onDownload != null) {
            DropdownMenuItem(
                text = { Text(t("Download", "Unduh"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = com.mediadeck.app.ui.theme.DeckTextPrimary) },
                leadingIcon = { Icon(Icons.Default.Download, null, tint = com.mediadeck.app.ui.theme.DeckTextPrimary, modifier = Modifier.size(18.dp)) },
                onClick = { onDismiss(); onDownload() },
            )
        }
        DropdownMenuItem(
            text = { Text(t("Delete", "Hapus"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = com.mediadeck.app.ui.theme.DeckBadgeRed) },
            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = com.mediadeck.app.ui.theme.DeckBadgeRed, modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}


@Composable
fun GalleryCard(
    item: GalleryItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onOpenItem: (Long) -> Unit,
    viewModel: GalleryViewModel,
    settings: AppSettings,
    uniformGrid: Boolean = false,
    onDelete: (GalleryItem) -> Unit,
    onDownload: ((GalleryItem) -> Unit)? = null,
) {
    val onToggleSelection: (Long) -> Unit = remember(viewModel) { { id -> viewModel.toggleGallerySelection(id) } }
    val onStartMultiSelect: (Long) -> Unit = remember(viewModel) { { id -> viewModel.startGalleryMultiSelect(id) } }
    GalleryCardContent(
        item = item,
        isSelected = isSelected,
        isMultiSelect = isMultiSelect,
        uniformGrid = uniformGrid,
        settings = settings,
        onOpenItem = onOpenItem,
        onToggleSelection = onToggleSelection,
        onStartMultiSelect = onStartMultiSelect,
        onDelete = onDelete,
        onDownload = onDownload,
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryCardContent(
    item: GalleryItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    uniformGrid: Boolean,
    settings: AppSettings,
    onOpenItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onStartMultiSelect: (Long) -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onDownload: ((GalleryItem) -> Unit)?,
) {
    val isVideo = item.mimeType.startsWith("video/", ignoreCase = true)
    val initialRatio = if (item.width > 0 && item.height > 0) {
        item.width.toFloat() / item.height.toFloat()
    } else {
        1f
    }

    var aspectState by remember(item.id, item.width, item.height) { mutableFloatStateOf(initialRatio) }
    var showMenu by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(16.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(
                onClick = { if (isMultiSelect) onToggleSelection(item.id) else onOpenItem(item.id) },
                onLongClick = { onStartMultiSelect(item.id) }
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(if (uniformGrid) 1f else aspectState.coerceIn(0.5f, 2.5f))) {
            AsyncThumbnailImage(
                uriString = item.uri,
                mediaId = item.id,
                hasThumbnail = item.hasThumbnail,
                settings = settings,
                thumbnailVariant = "gallery",
                showProcessingIndicator = true,
                onSuccess = { state ->
                    if (item.width == 0 || item.height == 0) {
                        val ratio = state.painter.intrinsicSize.width / state.painter.intrinsicSize.height
                        if (ratio > 0f && ratio.isFinite()) {
                            aspectState = ratio
                        }
                    }
                }
            )
            if (isSelected) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
            }
            if (isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (item.duration > 0) {
                    val durationText = remember(item.duration) { MediaUtils.formatDuration(item.duration) }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    ) {
                        Text(
                            text = durationText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (!isMultiSelect) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (showMenu) {
                        ItemDropdown(
                            expanded = true,
                            onDismiss = { showMenu = false },
                            onDownload = onDownload?.let { cb -> { cb(item) } },
                            onDelete = { onDelete(item) },
                        )
                    }
                }
            }
            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(item.id) },
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                )
            }
        }
    }
}


@Composable
fun GalleryListItem(
    item: GalleryItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onOpenItem: (Long) -> Unit,
    viewModel: GalleryViewModel,
    settings: AppSettings,
    onDelete: (GalleryItem) -> Unit,
    onDownload: ((GalleryItem) -> Unit)? = null,
) {
    val onToggleSelection: (Long) -> Unit = remember(viewModel) { { id -> viewModel.toggleGallerySelection(id) } }
    val onStartMultiSelect: (Long) -> Unit = remember(viewModel) { { id -> viewModel.startGalleryMultiSelect(id) } }
    GalleryListItemContent(
        item = item,
        isSelected = isSelected,
        isMultiSelect = isMultiSelect,
        settings = settings,
        onOpenItem = onOpenItem,
        onToggleSelection = onToggleSelection,
        onStartMultiSelect = onStartMultiSelect,
        onDelete = onDelete,
        onDownload = onDownload,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryListItemContent(
    item: GalleryItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    settings: AppSettings,
    onOpenItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onStartMultiSelect: (Long) -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onDownload: ((GalleryItem) -> Unit)?,
) {

    val details = remember(item.size, item.width, item.height, item.mimeType, item.duration) {
        val isVideo = item.mimeType.startsWith("video/", ignoreCase = true)
        val resolution = if (item.width > 0 && item.height > 0) {
            "${item.width} × ${item.height}"
        } else {
            item.mimeType.substringAfter('/', "").uppercase()
        }
        listOf(
            formatSize(item.size),
            resolution,
            if (isVideo && item.duration > 0) MediaUtils.formatDuration(item.duration) else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }
    val dateLabel = remember(item.dateAdded) { formatDate(item.dateAdded, withTime = true) }
    val title = remember(item.id, item.name, item.mimeType) { item.name.ifBlank { resolveDisplayName(item) } }
    var showMenu by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(20.dp) }
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(
                onClick = { if (isMultiSelect) onToggleSelection(item.id) else onOpenItem(item.id) },
                onLongClick = { onStartMultiSelect(item.id) }
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelect) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection(item.id) })
        }
        AsyncThumbnailImage(
            uriString = item.uri,
            mediaId = item.id,
            hasThumbnail = item.hasThumbnail,
            settings = settings,
            thumbnailVariant = "gallery",
            showProcessingIndicator = true,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, fontSize = 14.sp, color = subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (dateLabel.isNotEmpty()) {
                Text(dateLabel, fontSize = 14.sp, color = subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            modifier = Modifier.height(72.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (item.isFavorite) DeckBadgeRed else subtle,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp).size(24.dp),
            )
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = subtle)
                }
                if (showMenu) {
                    ItemDropdown(
                        expanded = true,
                        onDismiss = { showMenu = false },
                        onDownload = onDownload?.let { cb -> { cb(item) } },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}


@Composable
fun GallerySideSheet(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 360.dp),
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = t("Back", "Kembali"),
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = t("Close", "Tutup"))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun SheetSectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun SheetSegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = com.mediadeck.app.ui.theme.DeckAccentBlue,
    labelColor: Color = com.mediadeck.app.ui.theme.DeckTextPrimary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.mediadeck.app.ui.theme.DeckSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = labelColor, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
