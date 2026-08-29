@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mediadeck.app.ui.screens

import android.net.Uri
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.DraggableStaggeredGridScrollbar
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.rememberFolderThumbnailUri
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.rememberFolderThumbnailUri
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    scannerViewModel: ScannerViewModel,
    onOpenItem: (Long) -> Unit,
    onNavigateToScan: () -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.filteredGalleryItems.collectAsState()
    val search by viewModel.gallerySearch.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val isGroupedByFolder by viewModel.isGalleryGroupedByFolder.collectAsState()
    val selectedFolderName by viewModel.selectedGalleryFolderName.collectAsState()
    val scanProgress by scannerViewModel.scanProgress.collectAsState()
    val selectedIds by viewModel.selectedGalleryIds.collectAsState()
    val isMultiSelect by viewModel.isGalleryMultiSelectMode.collectAsState()
    val sortOption by viewModel.gallerySort.collectAsState()
    val isScanActive by scannerViewModel.isScanActive.collectAsState()
    val isScanPaused by scannerViewModel.isScanPaused.collectAsState()
    val isManualRefreshing by scannerViewModel.isManualRefreshing.collectAsState()
    val allTags by viewModel.availableTags.collectAsState()
    val selectedTags by viewModel.selectedTagsFilter.collectAsState()
    val tagCounts by viewModel.galleryTagCounts.collectAsState()
    val isRefreshing = isManualRefreshing

    var isTagsPanelExpanded by remember { mutableStateOf(false) }
    var isSearchingLocal by rememberSaveable { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<com.mediadeck.app.data.gallery.GalleryItem?>(null) }

    if (showDeleteConfirmDialog || itemToDelete != null) {
        DeleteConfirmationDialog(
            onConfirm = {
                if (itemToDelete != null) {
                    viewModel.deleteGalleryItem(context, itemToDelete!!)
                    itemToDelete = null
                } else {
                    viewModel.deleteSelectedGalleryItems(context)
                }
                showDeleteConfirmDialog = false
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                itemToDelete = null
            },
            title = if (itemToDelete != null) t("Delete Media", "Hapus Media") else t("Delete Selected", "Hapus Terpilih"),
            message = if (itemToDelete != null) t("Are you sure you want to delete this file?", "Apakah Anda yakin ingin menghapus file ini?") else t("Are you sure you want to delete selected items?", "Apakah Anda yakin ingin menghapus media terpilih?"),
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchingLocal) {
        if (isSearchingLocal) {
            focusRequester.requestFocus()
        }
    }

    val savedScrollIndex by viewModel.scrollIndex.collectAsState()
    val savedScrollOffset by viewModel.scrollOffset.collectAsState()

    BackHandler(enabled = isSearchingLocal || isTagsPanelExpanded || isMultiSelect || search.isNotEmpty() || selectedTags.isNotEmpty() || selectedFolderName != null) {
        when {
            isSearchingLocal || search.isNotEmpty() -> {
                viewModel.setGallerySearch("")
                isSearchingLocal = false
            }
            isTagsPanelExpanded || selectedTags.isNotEmpty() -> {
                viewModel.clearTagFilter()
                isTagsPanelExpanded = false
            }
            isMultiSelect -> viewModel.clearGallerySelection()
            selectedFolderName != null -> {
                viewModel.onFolderExited()
                viewModel.setSelectedGalleryFolderName(null)
            }
        }
    }

    val gridState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = savedScrollIndex, initialFirstVisibleItemScrollOffset = savedScrollOffset)

    LaunchedEffect(viewModel.scrollToTopEvent) {
        viewModel.scrollToTopEvent.collect {
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .debounce(500.milliseconds)
            .collectLatest { (index, offset) -> viewModel.setScrollPosition(index, offset) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.clickable { viewModel.requestScrollToTop() },
                navigationIcon = {
                    if (isSearchingLocal) IconButton(onClick = {
                        viewModel.setGallerySearch("")
                        isSearchingLocal = false
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                    else if (isMultiSelect) IconButton(onClick = { viewModel.clearGallerySelection() }) { Icon(Icons.Default.Close, null) }
                    else if (selectedFolderName != null) {
                        IconButton(onClick = {
                            viewModel.onFolderExited()
                            viewModel.setSelectedGalleryFolderName(null)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    } else Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(28.dp))
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
                    } else if (isMultiSelect) {
                        Text(t("${selectedIds.size} Selected", "${selectedIds.size} Terpilih"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        val screenTitle = if (selectedFolderName != null) selectedFolderName!! else t("Gallery", "Galeri")
                        Text(screenTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (!isSearchingLocal) {
                        IconButton(onClick = { isSearchingLocal = true }) { Icon(Icons.Default.Search, null) }
                        Box {
                            IconButton(onClick = { showMainMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = showMainMenu, onDismissRequest = { showMainMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (settings.layoutMode == "grid") t("List View", "Daftar") else t("Grid View", "Grid")) },
                                    leadingIcon = { Icon(if (settings.layoutMode == "grid") Icons.AutoMirrored.Filled.List else Icons.Default.GridView, null) },
                                    onClick = { viewModel.setLayoutMode(if (settings.layoutMode == "grid") "title_only" else "grid"); showMainMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(t("Favorites", "Favorit")) },
                                    leadingIcon = { Icon(Icons.Default.Favorite, null) },
                                    onClick = { viewModel.setGalleryFavoriteFilter(true); showMainMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("Sort By...", "Urutkan")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }, onClick = { showMainMenu = false; showSortDialog = true })
                                DropdownMenuItem(text = { Text(t("Filter Tags...", "Filter Label")) }, leadingIcon = { Icon(Icons.Default.FilterAlt, null) }, onClick = { isTagsPanelExpanded = !isTagsPanelExpanded; showMainMenu = false })
                                DropdownMenuItem(text = { Text(t("Group by Folder", "Grup Folder")) }, leadingIcon = { Icon(if (isGroupedByFolder) Icons.Default.FolderOpen else Icons.Default.Folder, null, tint = if (isGroupedByFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { viewModel.setGalleryGroupedByFolder(!isGroupedByFolder); showMainMenu = false })
                                DropdownMenuItem(text = { Text(t("Select Multiple", "Pilih Banyak")) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, onClick = { viewModel.startGalleryMultiSelect(); showMainMenu = false })
                                if (isMultiSelect) {
                                    DropdownMenuItem(text = { Text(t("Delete Selected", "Hapus"), color = Color.Red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }, onClick = { showDeleteConfirmDialog = true; showMainMenu = false })
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.reshuffle()
                scannerViewModel.autoScanLibrary(true, "gallery")
            },
            indicator = {},
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isTagsPanelExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(bottom = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("Filter by Tags", "Label"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (selectedTags.isNotEmpty()) TextButton(onClick = { viewModel.clearTagFilter() }) { Text(t("Clear All", "Hapus Semua"), fontSize = 12.sp) }
                        }

                        val selectedList = remember(allTags, selectedTags) { allTags.filter { selectedTags.contains(it) }.sorted() }
                        val unselectedList = remember(allTags, selectedTags, tagCounts) {
                            allTags.filter { !selectedTags.contains(it) }
                                .sortedByDescending { tagCounts[it] ?: 0 }
                        }

                        if (selectedList.isNotEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(selectedList.size) { index ->
                                    val tag = selectedList[index]
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.toggleTagFilter(tag) },
                                        label = { Text("$tag (${tagCounts[tag] ?: 0})", fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        if (unselectedList.isNotEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(unselectedList.size) { index ->
                                    val tag = unselectedList[index]
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.toggleTagFilter(tag) },
                                        label = { Text("$tag (${tagCounts[tag] ?: 0})", fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if ((scanProgress != null && settings.floatingScanStatus)) {
                    ScanStatusCard(
                        scanProgress = scanProgress,
                        isScanActive = isScanActive,
                        isScanPaused = isScanPaused,
                        onTogglePause = { scannerViewModel.togglePauseScan() },
                        onStopScan = { scannerViewModel.stopScan() }
                    )
                }

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
                            Text(t("No media found", "Media tidak ditemukan"))
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
                } else {
                    val isTitleOnly = settings.layoutMode == "title_only"
                    val gridCells = when {
                        isTitleOnly -> StaggeredGridCells.Fixed(1)
                        settings.gridColumns == 1 -> StaggeredGridCells.Fixed(1)
                        settings.gridColumns == 2 -> StaggeredGridCells.Fixed(2)
                        else -> StaggeredGridCells.Adaptive(135.dp)
                    }
                    if (isGroupedByFolder && search.isEmpty() && selectedFolderName == null) {
                        val grouped = remember(items) { items.groupBy { it.folderName } }
                        val folders = remember(grouped) { grouped.keys.toList().sorted() }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyVerticalStaggeredGrid(
                                state = gridState,
                                columns = gridCells,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                items(folders) { folder ->
                                    val onFolderClick = {
                                        viewModel.setSelectedGalleryFolderName(folder)
                                        viewModel.onFolderVisited(context, folder)
                                    }
                                    if (isTitleOnly) GalleryFolderListItem(folder, (grouped[folder] ?: emptyList()).size, onFolderClick)
                                    else GalleryFolderCard(folder, (grouped[folder] ?: emptyList()).size, onFolderClick)
                                }
                            }
                            if (settings.showSideScrollbar) {
                                com.mediadeck.app.ui.components.DraggableStaggeredGridScrollbar(gridState = gridState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = 80.dp))
                            }
                        }
                    } else {
                        val displayList = if (selectedFolderName != null) items.filter { it.folderName == selectedFolderName } else items
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyVerticalStaggeredGrid(
                                state = gridState,
                                columns = gridCells,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                items(displayList, key = { it.id }) { item ->
                                    if (isTitleOnly) GalleryListItem(item, selectedIds.contains(item.id), isMultiSelect, onOpenItem, viewModel, onDelete = { itemToDelete = item; showDeleteConfirmDialog = true })
                                    else GalleryCard(item, selectedIds.contains(item.id), isMultiSelect, onOpenItem, viewModel, settings, onDelete = { itemToDelete = item; showDeleteConfirmDialog = true })
                                }
                            }
                            if (settings.showSideScrollbar) {
                                com.mediadeck.app.ui.components.DraggableStaggeredGridScrollbar(gridState = gridState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = 80.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        val sortOptions = listOf(
            "date_desc" to t("Newest Added", "Terbaru Ditambahkan"),
            "date_asc" to t("Oldest Added", "Terlama Ditambahkan"),
            "name_asc" to t("Name (A-Z)", "Nama (A-Z)"),
            "name_desc" to t("Name (Z-A)", "Nama (Z-A)"),
            "size_desc" to t("Largest Size", "Ukuran Terbesar"),
            "size_asc" to t("Smallest Size", "Ukuran Kecil"),
            "random" to t("Shuffle / Random", "Acak"),
        )
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(t("Sort Gallery", "Urutkan Galeri")) },
            text = {
                Column {
                    sortOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setGallerySort(key); showSortDialog = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = sortOption == key, onClick = { viewModel.setGallerySort(key); showSortDialog = false })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
fun GalleryFolderCard(folderName: String, count: Int, onClick: () -> Unit) {
    val mosaicUri = rememberFolderThumbnailUri(folderName)

    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mosaicUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mosaicUri)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Column(modifier = Modifier.align(Alignment.Center).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = Color.White.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = Color.White) } }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = folderName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "$count Items", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun GalleryFolderListItem(folderName: String, count: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column { Text(folderName, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("$count Items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryCard(item: com.mediadeck.app.data.gallery.GalleryItem, isSelected: Boolean, isMultiSelect: Boolean, onOpenItem: (Long) -> Unit, viewModel: GalleryViewModel, settings: com.mediadeck.app.data.settings.AppSettings, onDelete: (com.mediadeck.app.data.gallery.GalleryItem) -> Unit) {
    val isVideo = item.mimeType.contains("video", ignoreCase = true)

    val initialRatio = if (item.width > 0 && item.height > 0) {
        item.width.toFloat() / item.height.toFloat()
    } else {
        1f
    }
    var aspectState by remember(item.id) { mutableFloatStateOf(initialRatio) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (isMultiSelect) viewModel.toggleGallerySelection(item.id) else onOpenItem(item.id) },
                onLongClick = { viewModel.startGalleryMultiSelect(item.id) }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspectState.coerceIn(0.5f, 2.5f))) {
            AsyncThumbnailImage(
                uriString = item.uri,
                mediaId = item.id,
                hasThumbnail = item.hasThumbnail,
                settings = settings,
                onSuccess = { state ->
                    if (item.width == 0 || item.height == 0) {
                        val ratio = state.painter.intrinsicSize.width / state.painter.intrinsicSize.height
                        if (ratio > 0f && !ratio.isNaN()) {
                            aspectState = ratio
                        }
                    }
                }
            )

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }

                if (item.duration > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    ) {
                        Text(
                            text = com.mediadeck.app.util.media.MediaUtils.formatDuration(item.duration),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            var showMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(2.dp)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Delete", "Hapus")) },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { showMenu = false; onDelete(item) }
                    )
                }
            }

            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleGallerySelection(item.id) },
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryListItem(item: com.mediadeck.app.data.gallery.GalleryItem, isSelected: Boolean, isMultiSelect: Boolean, onOpenItem: (Long) -> Unit, viewModel: GalleryViewModel, onDelete: (com.mediadeck.app.data.gallery.GalleryItem) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = { if (isMultiSelect) viewModel.toggleGallerySelection(item.id) else onOpenItem(item.id) }, onLongClick = { viewModel.startGalleryMultiSelect(item.id) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncThumbnailImage(
            uriString = item.uri,
            mediaId = item.id,
            hasThumbnail = item.hasThumbnail,
            settings = viewModel.appSettings.collectAsState().value,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = item.mimeType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.isFavorite) Icon(Icons.Filled.Favorite, null, tint = Color.Red, modifier = Modifier.size(16.dp))
        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(t("Delete", "Hapus")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMenu = false; onDelete(item) })
            }
        }
        if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleGallerySelection(item.id) })
    }
}
