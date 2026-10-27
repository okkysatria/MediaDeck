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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.DraggableStaggeredGridScrollbar
import com.mediadeck.app.ui.components.FolderGroupCard
import com.mediadeck.app.ui.components.FolderGroupListItem
import com.mediadeck.app.ui.components.MediaDeckHeader
import com.mediadeck.app.ui.components.MediaDeckSideSheet
import com.mediadeck.app.ui.components.MediaOverflowMenu
import com.mediadeck.app.ui.components.MediaEmptyState
import com.mediadeck.app.ui.components.MediaSelectionCountTitle
import com.mediadeck.app.ui.components.MediaSelectionDeleteButton
import com.mediadeck.app.ui.components.MediaSelectionExitButton
import com.mediadeck.app.util.media.MediaFormatters
import com.mediadeck.app.ui.components.galleryInfoRows
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.components.SheetActionRow
import com.mediadeck.app.ui.components.SheetSegmented
import com.mediadeck.app.ui.components.rememberBottomBarPadding
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaFolderIdentity
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private enum class GallerySheet { Options, Sort }


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
                } catch (_: Exception) {

                    runCatching { resolver.delete(target, null, null) }
                    return false
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val target = uniqueFile(dir, displayName)
                try {
                    target.outputStream().use { out -> stream.copyTo(out) }
                } catch (_: Exception) {
                    runCatching { target.delete() }
                    return false
                }

                runCatching {
                    MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
                }
            }
        }
        true
    } catch (_: Exception) {
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
            first.folderUri, first.uri, first.folderName
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
        for ((_, _, _, _, size, dateAdded, _, _, _, _, _, _, _, _) in list) {
            if (dateAdded > latest) latest = dateAdded
            if (dateAdded < earliest) earliest = dateAdded
            total += size
        }
        val label = if ((nameCounts[name] ?: 0) > 1) {
            val parent = MediaFolderIdentity.parentDisplayName(first.folderUri, first.uri)
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


@OptIn(ExperimentalMaterial3Api::class)
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
    var shuffleSeed by rememberSaveable { mutableIntStateOf(Random.nextInt()) }

    val folderKeyCache = remember { HashMap<Long, Pair<Any?, String>>() }
    val folderEntries = remember(items, showFolders, sortOption, shuffleSeed) {
        if (!showFolders) {
            emptyList()
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
    LaunchedEffect(showFolders, folderEntries) {
        if (showFolders) {
            folderEntries.forEach { entry ->
                viewModel.enqueueFolderThumbnail(context, "gallery:${entry.key}", entry.covers)
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
                firstItem.folderUri.ifEmpty { key },
                firstItem.uri.ifEmpty { key },
                firstItem.folderName,
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


    val savedScrollIndex by viewModel.scrollIndex.collectAsState()
    val savedScrollOffset by viewModel.scrollOffset.collectAsState()
    val gridState = key(selectedFolderName) {
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = if (selectedFolderName == null) savedScrollIndex else 0,
            initialFirstVisibleItemScrollOffset = if (selectedFolderName == null) savedScrollOffset else 0,
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
        "size_asc" to t("Smallest Size", "Ukuran Terkecil"),
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
                            MediaSelectionExitButton(onClick = { viewModel.clearGallerySelection() })
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
                            MediaSelectionCountTitle(selectedIds.size)
                        }
                    },
                    actions = {
                        if (isMultiSelect) {
                            MediaSelectionDeleteButton(onClick = { showDeleteConfirmDialog = true })
                        }
                    }
                )
            } else {
                val itemsCountText = MediaFormatters.count(items.size)
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
                    val hasActiveFilter = search.isNotEmpty() || selectedTags.isNotEmpty() ||
                            selectedFolderName != null || isFavoriteFilterActive
                    MediaEmptyState(
                        title = if (hasActiveFilter) t("No results", "Tidak ada hasil")
                        else t("No media found", "Media tidak ditemukan"),
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        actionLabel = if (!hasActiveFilter) t("Add Folder", "Tambah Folder") else null,
                        onAction = onNavigateToScan.takeIf { !hasActiveFilter },
                    )
                } else {
                    val bottomBarPadding = rememberBottomBarPadding(16.dp)
                    val isTitleOnly = settings.layoutMode == "title_only"
                    val isUniformGrid = settings.galleryGridType == "uniform"
                    val gridColumns = settings.gridColumns
                    val containerWidth = LocalWindowInfo.current.containerSize.width
                    val screenWidthDp = with(LocalDensity.current) { containerWidth.toDp().value.toInt() }


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
                                                    FolderGroupListItem(
                                                        folderName = entry.label,
                                                        countLabel = t("${entry.count} items", "${entry.count} item"),
                                                        folderKey = "gallery:${entry.key}",
                                                        processingKey = "gallery:${entry.key}",
                                                        supportingLabel = remember(entry.latestDate) {
                                                            MediaFormatters.dateTime(entry.latestDate, withTime = false)
                                                        },
                                                        onClick = { onFolderClick(entry.key) },
                                                    )
                                                } else {
                                                    FolderGroupCard(
                                                        folderKey = "gallery:${entry.key}",
                                                        folderName = entry.label,
                                                        countLabel = t("${entry.count} items", "${entry.count} item"),
                                                        processingKey = "gallery:${entry.key}",
                                                        onClick = { onFolderClick(entry.key) },
                                                    )
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
                                        GalleryListItem(
                                            item = item,
                                            isSelected = isSelected,
                                            isMultiSelect = isMultiSelect,
                                            onOpenItem = stableOnOpenItem,
                                            viewModel = viewModel,
                                            settings = settings,
                                            onDelete = onRequestDelete,
                                            onDownload = stableOnDownload,
                                            onFavorite = { viewModel.toggleGalleryFavorite(it) },
                                        )
                                    } else {
                                        GalleryCard(
                                            item = item,
                                            isSelected = isSelected,
                                            isMultiSelect = isMultiSelect,
                                            uniformGrid = isUniformGrid,
                                            onOpenItem = stableOnOpenItem,
                                            viewModel = viewModel,
                                            settings = settings,
                                            onDelete = onRequestDelete,
                                            onDownload = stableOnDownload,
                                            onFavorite = { viewModel.toggleGalleryFavorite(it) },
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


    if (activeSheet == GallerySheet.Sort) {
        val sortGroups = listOf(
            listOf("name_asc", "name_desc"),
            listOf("date_desc", "date_asc"),
            listOf("size_desc", "size_asc"),
            listOf("random"),
        ).map { group -> group.map { key -> key to sortLabels.getValue(key) } }
        MediaDeckSideSheet(
            title = t("Sort Gallery", "Urutkan Galeri"),
            currentSort = sortOption,
            sortGroups = sortGroups,
            onSortSelected = { viewModel.setGallerySort(it); activeSheet = null },
            onDismiss = { activeSheet = null },
            onBack = { activeSheet = GallerySheet.Options },
        )
    } else if (activeSheet == GallerySheet.Options) {
        MediaDeckSideSheet(
            title = t("Gallery Options", "Opsi Galeri"),
            onDismiss = { activeSheet = null },
        ) {
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
    onFavorite: ((GalleryItem) -> Unit)? = null,
) {
    val onToggleSelection: (Long) -> Unit = remember(viewModel) { { id -> viewModel.toggleGallerySelection(id) } }
    val onStartMultiSelect: (Long) -> Unit = remember(viewModel) { { id -> viewModel.startGalleryMultiSelect(id) } }
    val onToggleFavorite: (GalleryItem) -> Unit = remember(viewModel, onFavorite) {
        onFavorite ?: { media -> viewModel.toggleGalleryFavorite(media) }
    }
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
        onFavorite = onToggleFavorite,
    )
}


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
    onFavorite: ((GalleryItem) -> Unit)? = null,
) {
    val isVideo = item.mimeType.startsWith("video/", ignoreCase = true)
    val initialRatio = if (item.width > 0 && item.height > 0) {
        item.width.toFloat() / item.height.toFloat()
    } else {
        1f
    }

    var aspectState by remember(item.id, item.width, item.height) { mutableFloatStateOf(initialRatio) }
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
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(if (uniformGrid) 1f else aspectState.coerceIn(0.5f, 2.5f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncThumbnailImage(
                uriString = item.uri,
                mediaId = item.id,
                hasThumbnail = item.hasThumbnail,
                settings = settings,
                thumbnailVariant = "gallery",
                showProcessingIndicator = true,
                modifier = Modifier.fillMaxSize(),
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
            if (item.isFavorite) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = DeckBadgeRed,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
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
                    MediaOverflowMenu(
                        isFavorite = item.isFavorite,
                        infoTitle = item.name,
                        infoRows = galleryInfoRows(item),
                        onFavorite = { onFavorite?.invoke(item) },
                        onDownload = onDownload?.let { callback -> { callback(item) } },
                        onDelete = { onDelete(item) },
                        buttonSize = 34.dp,
                        iconSize = 20.dp,
                        iconTint = Color.White,
                    )
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
    onFavorite: ((GalleryItem) -> Unit)? = null,
) {
    val onToggleSelection: (Long) -> Unit = remember(viewModel) { { id -> viewModel.toggleGallerySelection(id) } }
    val onStartMultiSelect: (Long) -> Unit = remember(viewModel) { { id -> viewModel.startGalleryMultiSelect(id) } }
    val onToggleFavorite: (GalleryItem) -> Unit = remember(viewModel, onFavorite) {
        onFavorite ?: { media -> viewModel.toggleGalleryFavorite(media) }
    }
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
        onFavorite = onToggleFavorite,
    )
}

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
    onFavorite: ((GalleryItem) -> Unit)? = null,
) {

    val details = remember(item.size, item.width, item.height, item.mimeType, item.duration) {
        val isVideo = item.mimeType.startsWith("video/", ignoreCase = true)
        val resolution = if (item.width > 0 && item.height > 0) {
            "${item.width} × ${item.height}"
        } else {
            item.mimeType.substringAfter('/', "").uppercase()
        }
        listOf(
            MediaFormatters.fileSize(item.size, unknownValue = ""),
            resolution,
            if (isVideo && item.duration > 0) MediaUtils.formatDuration(item.duration) else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }
    val dateLabel = remember(item.dateAdded) { MediaFormatters.dateTime(item.dateAdded, withTime = true) }
    val title = remember(item.id, item.name, item.mimeType) { item.name.ifBlank { resolveDisplayName(item) } }
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
            IconButton(
                onClick = { onFavorite?.invoke(item) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (item.isFavorite) DeckBadgeRed else subtle,
                    modifier = Modifier.size(22.dp),
                )
            }
            MediaOverflowMenu(
                isFavorite = item.isFavorite,
                infoTitle = item.name,
                infoRows = galleryInfoRows(item),
                onFavorite = { onFavorite?.invoke(item) },
                onDownload = onDownload?.let { callback -> { callback(item) } },
                onDelete = { onDelete(item) },
                buttonSize = 34.dp,
                iconSize = 20.dp,
            )
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
