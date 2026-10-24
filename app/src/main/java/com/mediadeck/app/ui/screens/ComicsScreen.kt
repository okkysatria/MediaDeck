@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mediadeck.app.ui.screens

import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.ui.components.ContinueHeroCard
import androidx.compose.ui.graphics.luminance
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.DraggableGridScrollbar
import com.mediadeck.app.ui.components.MediaDeckHeader
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.components.rememberBottomBarPadding
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckAccentPillBg
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.ui.theme.DeckDivider
import com.mediadeck.app.ui.theme.DeckOnAccent
import com.mediadeck.app.ui.theme.DeckSurface
import com.mediadeck.app.ui.theme.DeckSurfaceVariant
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.ui.theme.LocalDeckColors
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ComicViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import java.text.NumberFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicsScreen(
    viewModel: ComicViewModel,
    scannerViewModel: ScannerViewModel,
    onOpenComic: (Comic) -> Unit,
    onNavigateToScan: () -> Unit,
    onDownloadComic: ((Comic) -> Unit)? = null,
) {
    val context = LocalContext.current
    val comics by viewModel.filteredComics.collectAsState()
    val tags by viewModel.comicAvailableTags.collectAsState()
    val selectedTags by viewModel.comicSelectedTagsFilter.collectAsState()
    val tagCounts by viewModel.comicTagCounts.collectAsState()
    val search by viewModel.comicSearch.collectAsState()
    val activeTab by viewModel.comicTabFilter.collectAsState()
    val sortOption by viewModel.comicSort.collectAsState()
    val isMultiSelect by viewModel.isComicMultiSelectMode.collectAsState()
    val selectedIds by viewModel.selectedComicIds.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val scanProgress by scannerViewModel.scanProgress.collectAsState()
    val isScanActive by scannerViewModel.isScanActive.collectAsState()
    val isScanPaused by scannerViewModel.isScanPaused.collectAsState()
    val isRefreshing by scannerViewModel.isManualRefreshing.collectAsState()
    val heroComic by viewModel.heroComic.collectAsState()
    val countFormat = remember { NumberFormat.getIntegerInstance() }

    var isSearchingLocal by rememberSaveable { mutableStateOf(false) }
    val isSearching = isSearchingLocal || search.isNotEmpty()
    var groupByFolder by rememberSaveable { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var isTagsPanelExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var scanStartCount by remember { mutableIntStateOf(-1) }
    var scanCompleteAdded by remember { mutableIntStateOf(0) }

    LaunchedEffect(isScanActive) {
        if (isScanActive) {
            scanStartCount = comics.size
            scanCompleteAdded = 0
        } else if (scanStartCount >= 0) {
            delay(400.milliseconds)
            val added = comics.size - scanStartCount
            if (added > 0) scanCompleteAdded = added
            scanStartCount = -1
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isSearchingLocal) {
        if (isSearchingLocal) runCatching { focusRequester.requestFocus() }
    }

    val initialScrollIndex = remember { viewModel.scrollIndex.value }
    val initialScrollOffset = remember { viewModel.scrollOffset.value }

    BackHandler(
        enabled = isMultiSelect || isSearching || isTagsPanelExpanded || selectedTags.isNotEmpty() || activeTab != "all",
    ) {
        when {
            isMultiSelect -> viewModel.clearComicSelection()
            isSearching -> {
                viewModel.setComicSearch("")
                isSearchingLocal = false
            }
            isTagsPanelExpanded -> isTagsPanelExpanded = false
            selectedTags.isNotEmpty() -> viewModel.clearComicTagFilter()
            activeTab != "all" -> viewModel.setComicTab("all")
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    LaunchedEffect(viewModel.scrollToTopEvent) {
        viewModel.scrollToTopEvent.collect { gridState.scrollToItem(0) }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .debounce(500.milliseconds)
            .collectLatest { (index, offset) -> viewModel.setScrollPosition(index, offset) }
    }

    Scaffold(
        topBar = {
            if (isMultiSelect || isSearching) {
                TopAppBar(
                    modifier = Modifier.clickable { viewModel.requestScrollToTop() },
                    navigationIcon = {
                        if (isMultiSelect) {
                            IconButton(onClick = { viewModel.clearComicSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = t("Cancel", "Batal"))
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.setComicSearch("")
                                isSearchingLocal = false
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                            }
                        }
                    },
                    title = {
                        if (isMultiSelect) {
                            Text(
                                t("${selectedIds.size} Selected", "${selectedIds.size} Terpilih"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            TextField(
                                value = search,
                                onValueChange = { viewModel.setComicSearch(it) },
                                placeholder = { Text(t("Search comics...", "Cari komik..."), fontSize = 15.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    if (search.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setComicSearch("") }) {
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
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 12.dp)
                                    .focusRequester(focusRequester),
                            )
                        }
                    },
                    actions = {
                        if (isMultiSelect) {
                            IconButton(
                                enabled = selectedIds.isNotEmpty(),
                                onClick = { showDeleteConfirm = true },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = t("Delete", "Hapus"),
                                    tint = if (selectedIds.isNotEmpty()) DangerColor else DangerColor.copy(alpha = 0.35f),
                                )
                            }
                        }
                    },
                )
            } else {
                MediaDeckHeader(
                    icon = Icons.Default.Book,
                    title = when (activeTab) {
                        "history" -> t("Recently Read", "Terakhir Dibaca")
                        "favorites" -> t("Favorites", "Favorit")
                        "read_later" -> t("Read Later", "Baca Nanti")
                        else -> t("Comics", "Komik")
                    },
                    subtitle = countFormat.format(comics.size).let { n -> t("$n titles", "$n judul") },
                    onSearchClick = { isSearchingLocal = true },
                    onMoreClick = { showMainMenu = true },
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.reshuffle()
                scannerViewModel.autoScanLibrary(true, "comics")
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isTagsPanelExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                        Text(
                            text = t("Filter by Tags", "Filter Label"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = DeckAccentBlue,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        val selectedList = remember(tags, selectedTags) { tags.filter { selectedTags.contains(it) }.sorted() }
                        val unselectedList = remember(tags, selectedTags, tagCounts) {
                            tags.filter { !selectedTags.contains(it) }
                                .sortedByDescending { tagCounts[it] ?: 0 }
                        }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (selectedList.isNotEmpty()) {
                                item(key = "clear_all") {
                                    TagPill(
                                        text = t("Clear", "Hapus"),
                                        count = null,
                                        style = TagPillStyle.Clear,
                                        onClick = { viewModel.clearComicTagFilter() },
                                    )
                                }
                            }
                            items(count = selectedList.size, key = { "sel_${selectedList[it]}" }) { index ->
                                val tag = selectedList[index]
                                TagPill(
                                    text = tag,
                                    count = tagCounts[tag] ?: 0,
                                    style = TagPillStyle.Selected,
                                    onClick = { viewModel.toggleComicTagFilter(tag) },
                                )
                            }
                            items(count = unselectedList.size, key = { "uns_${unselectedList[it]}" }) { index ->
                                val tag = unselectedList[index]
                                TagPill(
                                    text = tag,
                                    count = tagCounts[tag] ?: 0,
                                    style = TagPillStyle.Unselected,
                                    onClick = { viewModel.toggleComicTagFilter(tag) },
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = scanProgress != null && (settings.floatingScanStatus || isRefreshing || isScanActive),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ScanStatusCard(
                        scanProgress = scanProgress,
                        isScanActive = isScanActive,
                        isScanPaused = isScanPaused,
                        onTogglePause = { scannerViewModel.togglePauseScan() },
                        onStopScan = { scannerViewModel.stopScan() },
                    )
                }
                AnimatedVisibility(
                    visible = !isScanActive && !isScanPaused && scanCompleteAdded > 0 && scanProgress == null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ScanCompleteCard(
                        addedCount = scanCompleteAdded,
                        onShow = {
                            scanCompleteAdded = 0
                            viewModel.setComicTab("all")
                            viewModel.setComicSort("date_desc")
                            viewModel.requestScrollToTop()
                        },
                        onDismiss = { scanCompleteAdded = 0 },
                    )
                }
                if (comics.isEmpty()) {
                    val hasActiveFilter = search.isNotEmpty() || selectedTags.isNotEmpty() || activeTab != "all"
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
                            Text(t("No comics found", "Komik tidak ditemukan"), color = DeckTextSecondary)
                            if (!hasActiveFilter) {
                                Spacer(modifier = Modifier.height(16.dp))
                                FloatingActionButton(
                                    onClick = onNavigateToScan,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = t("Add Folder", "Tambah Folder"))
                                }
                            }
                        }
                    }
                } else {
                    val isTitleOnly = settings.layoutMode == "title_only"


                    val bottomBarPadding = rememberBottomBarPadding(16.dp)
                    val containerWidth = LocalWindowInfo.current.containerSize.width
                    val screenWidthDp = with(LocalDensity.current) { containerWidth.toDp().value.toInt() }
                    val gridCells = when {
                        isTitleOnly -> GridCells.Fixed(1)
                        settings.gridColumns == 1 -> if (screenWidthDp >= 600) GridCells.Fixed(2) else GridCells.Fixed(1)
                        settings.gridColumns == 2 -> when {
                            screenWidthDp >= 840 -> GridCells.Fixed(4)
                            screenWidthDp >= 600 -> GridCells.Fixed(3)
                            else -> GridCells.Fixed(2)
                        }
                        else -> GridCells.Adaptive(150.dp)
                    }
                    val displayHero = if (
                        activeTab == "all" && search.isEmpty() && selectedTags.isEmpty() && !isMultiSelect
                    ) heroComic else null
                    val folderGroups = remember(comics, groupByFolder) {
                        if (groupByFolder) {
                            comics.groupBy { it.parentFolderName.ifBlank { "-" } }.entries.toList()
                        } else {
                            emptyList()
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = gridCells,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = if (isTitleOnly) 12.dp else 0.dp, bottom = bottomBarPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isTitleOnly) 8.dp else 16.dp),
                        ) {
                            if (displayHero != null) {
                                item(key = "hero", span = { GridItemSpan(maxLineSpan) }, contentType = "hero") {
                                    HeroComicCard(displayHero, onOpenComic, viewModel, compact = isTitleOnly)
                                }
                            }
                            if (groupByFolder) {
                                folderGroups.forEach { (folder, list) ->
                                    item(key = "folder_$folder", span = { GridItemSpan(maxLineSpan) }, contentType = "folder") {
                                        FolderHeader(name = folder, count = list.size)
                                    }
                                    items(list, key = { it.id }, contentType = { "comic" }) { comic ->
                                        ComicEntry(
                                            comic = comic,
                                            isTitleOnly = isTitleOnly,
                                            isSelected = selectedIds.contains(comic.id),
                                            isMultiSelect = isMultiSelect,
                                            onOpenComic = onOpenComic,
                                            viewModel = viewModel,
                                            onDownloadComic = onDownloadComic,
                                            showClearHistory = activeTab == "history",
                                        )
                                    }
                                }
                            } else {
                                items(comics, key = { it.id }, contentType = { "comic" }) { comic ->
                                    ComicEntry(
                                        comic = comic,
                                        isTitleOnly = isTitleOnly,
                                        isSelected = selectedIds.contains(comic.id),
                                        isMultiSelect = isMultiSelect,
                                        onOpenComic = onOpenComic,
                                        viewModel = viewModel,
                                        onDownloadComic = onDownloadComic,
                                        showClearHistory = activeTab == "history",
                                    )
                                }
                            }
                        }
                        if (settings.showSideScrollbar) {
                            DraggableGridScrollbar(
                                gridState = gridState,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = bottomBarPadding),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortSidePanel(
            current = sortOption,
            onSelect = { viewModel.setComicSort(it); showSortDialog = false },
            onDismiss = { showSortDialog = false },
            onBack = { showSortDialog = false; showMainMenu = true },
        )
    }
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            title = t("Delete Comics", "Hapus Komik"),
            message = t(
                "Are you sure you want to delete ${selectedIds.size} selected comics?",
                "Apakah Anda yakin ingin menghapus ${selectedIds.size} komik yang dipilih?",
            ),
            onConfirm = {
                viewModel.deleteSelectedComics(context)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showMainMenu) {
        GallerySideSheet(
            title = t("Comics Options", "Opsi Komik"),
            onDismiss = { showMainMenu = false },
        ) {
            SheetSectionLabel(t("VIEW", "TAMPILAN"))
            SheetSegmented(
                options = listOf("grid" to t("Grid", "Grid"), "title_only" to t("List", "Daftar")),
                selected = if (settings.layoutMode == "title_only") "title_only" else "grid",
                onSelect = { viewModel.setLayoutMode(it) },
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetActionRow(
                    icon = Icons.Outlined.Folder,
                    label = t("Group by Folder", "Kelompokkan per Folder"),
                    onClick = { groupByFolder = !groupByFolder },
                    trailing = {
                        Switch(
                            checked = groupByFolder,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeckOnAccent,
                                checkedTrackColor = DeckAccentBlue,
                                checkedBorderColor = DeckAccentBlue,
                                uncheckedThumbColor = DeckTextSecondary,
                                uncheckedTrackColor = DeckSurfaceVariant,
                                uncheckedBorderColor = DeckDivider,
                            ),
                        )
                    },
                )
                SheetActionRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    label = t("Favorites", "Favorit"),
                    onClick = {
                        viewModel.setComicTab(if (activeTab == "favorites") "all" else "favorites")
                        showMainMenu = false
                    },
                )
                SheetActionRow(
                    icon = Icons.Outlined.BookmarkBorder,
                    label = t("Read Later", "Baca Nanti"),
                    onClick = {
                        viewModel.setComicTab(if (activeTab == "read_later") "all" else "read_later")
                        showMainMenu = false
                    },
                )
                SheetActionRow(
                    icon = Icons.Outlined.History,
                    label = t("Recently Read", "Terakhir Dibaca"),
                    onClick = {
                        viewModel.setComicTab(if (activeTab == "history") "all" else "history")
                        showMainMenu = false
                    },
                )
                if (activeTab == "history") {
                    SheetActionRow(
                        icon = Icons.Outlined.Delete,
                        label = t("Clear History", "Hapus Riwayat"),
                        onClick = { viewModel.clearAllComicHistory(); showMainMenu = false },
                        tint = DangerColor,
                        labelColor = DangerColor,
                    )
                }
                SheetActionRow(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    label = t("Sort By...", "Urutkan..."),
                    onClick = { showMainMenu = false; showSortDialog = true },
                )
                SheetActionRow(
                    icon = Icons.Outlined.FilterAlt,
                    label = t("Filter Tags...", "Filter Label..."),
                    onClick = { isTagsPanelExpanded = !isTagsPanelExpanded; showMainMenu = false },
                )
                SheetActionRow(
                    icon = Icons.Outlined.CheckCircle,
                    label = t("Select Multiple", "Pilih Banyak"),
                    onClick = { viewModel.startComicMultiSelect(); showMainMenu = false },
                )
            }
        }
    }
}


@Composable
private fun ComicEntry(
    comic: Comic,
    isTitleOnly: Boolean,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onOpenComic: (Comic) -> Unit,
    viewModel: ComicViewModel,
    onDownloadComic: ((Comic) -> Unit)?,
    showClearHistory: Boolean,
) {
    if (isTitleOnly) {
        ComicListItem(
            comic = comic,
            isSelected = isSelected,
            isMultiSelect = isMultiSelect,
            onOpenComic = onOpenComic,
            viewModel = viewModel,
            onDownloadComic = onDownloadComic,
            showClearHistory = showClearHistory,
        )
    } else {
        ComicCard(
            comic = comic,
            isSelected = isSelected,
            isMultiSelect = isMultiSelect,
            onOpenComic = onOpenComic,
            viewModel = viewModel,
            onDownloadComic = onDownloadComic,
            showClearHistory = showClearHistory,
        )
    }
}

@Composable
private fun FolderHeader(name: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Folder, null, tint = DeckAccentBlue, modifier = Modifier.size(20.dp))
        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DeckTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(text = count.toString(), fontSize = 13.sp, color = DeckTextSecondary)
    }
}

@Composable
fun HeroComicCard(
    comic: Comic,
    onOpenComic: (Comic) -> Unit,
    viewModel: ComicViewModel,
    compact: Boolean = false,
) {
    EnsureComicMetadata(comic, viewModel)
    if (compact) {
        CompactHeroCard(comic = comic, onClick = { onOpenComic(comic) })
    } else {
        val isRead = comic.currentPage > 0 || comic.lastReadTime > 0
        val headerTitle = when {
            isRead -> t("Continue Reading", "Lanjut Baca")
            comic.isFavorite -> t("Favorite Comic", "Komik Favorit")
            else -> t("Featured Comic", "Komik Pilihan")
        }
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckTextPrimary,
                )
                TextButton(
                    onClick = { viewModel.setComicTab("history") },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = t("VIEW ALL", "LIHAT SEMUA"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckAccentBlue,
                    )
                }
            }
            val totalPages = if (comic.totalPages > 0) comic.totalPages else 1
            val readPage = if (comic.currentPage > 0) comic.currentPage.coerceIn(1, totalPages) else 1
            val progressText = t("Page $readPage / $totalPages", "Halaman $readPage / $totalPages")
            val progressFraction = if (comic.totalPages > 0) readPage.toFloat() / comic.totalPages.toFloat() else 0f
            val coverUri = remember(comic.coverUri) {
                if (comic.coverUri.isNotBlank()) comic.coverUri.toUri() else null
            }
            ContinueHeroCard(
                title = comic.title,
                progressText = progressText,
                progressFraction = progressFraction,
                coverUri = coverUri,
                onClick = { onOpenComic(comic) },
            )
        }
    }
}


@Composable
private fun CompactHeroCard(comic: Comic, onClick: () -> Unit) {
    val totalPages = if (comic.totalPages > 0) comic.totalPages else 1
    val readPage = if (comic.currentPage > 0) comic.currentPage.coerceIn(1, totalPages) else 1
    val fraction = if (comic.totalPages > 0) readPage.toFloat() / comic.totalPages.toFloat() else 0f
    val progressText = t("Page $readPage / $totalPages", "Halaman $readPage / $totalPages")
    val coverUri = remember(comic.coverUri) {
        if (comic.coverUri.isNotBlank()) comic.coverUri.toUri() else null
    }
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        ContinueHeroCard(
            title = comic.title,
            progressText = progressText,
            progressFraction = fraction,
            coverUri = coverUri,
            onClick = onClick,
        )
    }
}

@Composable
fun ComicCard(
    comic: Comic,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onOpenComic: (Comic) -> Unit,
    viewModel: ComicViewModel,
    onDownloadComic: ((Comic) -> Unit)? = null,
    showClearHistory: Boolean = false,
) {
    EnsureComicMetadata(comic, viewModel)

    val progressFraction = comicProgressFraction(comic)
    val coverShape = RoundedCornerShape(20.dp)
    val coverModel = remember(comic.coverUri) { comic.coverUri.toUri() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(coverShape)
                .background(DeckSurfaceVariant)
                .combinedClickable(
                    onClick = { if (isMultiSelect) viewModel.toggleComicSelection(comic.id) else onOpenComic(comic) },

                    onLongClick = {
                        if (isMultiSelect) viewModel.toggleComicSelection(comic.id)
                        else viewModel.startComicMultiSelect(comic.id)
                    },
                )
                .then(if (isSelected) Modifier.border(2.dp, DeckAccentBlue, coverShape) else Modifier),
        ) {
            if (comic.coverUri.isNotBlank()) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CoverPlaceholder(Modifier.fillMaxSize())
            }

            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleComicSelection(comic.id) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            IconButton(
                onClick = { viewModel.toggleComicFavorite(comic) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(34.dp),
            ) {
                Icon(
                    imageVector = if (comic.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = t("Favorite", "Favorit"),
                    tint = if (comic.isFavorite) DeckBadgeRed else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (comic.totalPages > 0) {
                Text(
                    text = "${comic.totalPages} ${t("pgs", "hlm")}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 4f,
                        ),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = if (progressFraction > 0f) 10.dp else 6.dp),
                )
            }

            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(DeckAccentBlue),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = comic.title,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = DeckTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ComicMoreButton(
                comic = comic,
                viewModel = viewModel,
                onDownloadComic = onDownloadComic,
                showClearHistory = showClearHistory,
            )
        }
    }
}


@Composable
private fun ComicMoreButton(
    comic: Comic,
    viewModel: ComicViewModel,
    onDownloadComic: ((Comic) -> Unit)?,
    showClearHistory: Boolean,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }




    if (showInfoDialog) {
        ComicInfoDialog(comic = comic, onDismiss = { showInfoDialog = false })
    }
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            title = t("Delete Comic", "Hapus Komik"),
            message = t("Are you sure you want to delete this comic?", "Apakah Anda yakin ingin menghapus komik ini?"),
            onConfirm = {
                viewModel.deleteComic(context, comic)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    Box {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (showMenu) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                .clickable { showMenu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = t("Menu", "Menu"),
                tint = DeckTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showMenu) {
            ComicActionMenu(
                onDismiss = { showMenu = false },
                onInfo = { showInfoDialog = true },
                onDownload = onDownloadComic?.let { cb -> { cb(comic) } },
                onClearHistory = if (showClearHistory && comic.currentPage > 0) ({ viewModel.clearComicHistory(comic) }) else null,
                onDelete = { showDeleteConfirm = true },
            )
        }
    }
}


@Composable
private fun ComicActionMenu(
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onDownload: (() -> Unit)?,
    onClearHistory: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = DeckSurfaceVariant,
        offset = DpOffset(0.dp, 6.dp),
        modifier = Modifier.width(168.dp),
    ) {
        DropdownMenuItem(
            text = { Text(t("Info", "Info"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary) },
            leadingIcon = { Icon(Icons.Outlined.Info, null, tint = DeckTextPrimary) },
            onClick = { onDismiss(); onInfo() },
        )
        if (onDownload != null) {
            DropdownMenuItem(
                text = { Text(t("Download", "Unduh"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary) },
                leadingIcon = { Icon(Icons.Default.Download, null, tint = DeckTextPrimary) },
                onClick = { onDismiss(); onDownload() },
            )
        }
        if (onClearHistory != null) {
            DropdownMenuItem(
                text = { Text(t("Clear History", "Hapus Riwayat"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary) },
                leadingIcon = { Icon(Icons.Default.History, null, tint = DeckTextPrimary) },
                onClick = { onDismiss(); onClearHistory() },
            )
        }
        DropdownMenuItem(
            text = { Text(t("Delete", "Hapus"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DangerColor) },
            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = DangerColor) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}


@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cover_shimmer")
    val progress = transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "cover_shimmer_progress",
    )
    val shimmerBase = ShimmerBase
    val shimmerHighlight = ShimmerHighlight
    Box(
        modifier = modifier.drawBehind {
            drawRect(shimmerBase)
            val center = size.width * progress.value
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, shimmerHighlight, Color.Transparent),
                    start = Offset(center - size.width * 0.35f, 0f),
                    end = Offset(center + size.width * 0.35f, size.height * 0.25f),
                ),
            )
        },
    )
}

private enum class TagPillStyle { Clear, Selected, Unselected }

@Composable
private fun TagPill(text: String, count: Int?, style: TagPillStyle, onClick: () -> Unit) {
    val container = when (style) {
        TagPillStyle.Clear -> Color.Transparent
        TagPillStyle.Selected -> DeckAccentBlue
        TagPillStyle.Unselected -> DeckSurfaceVariant
    }
    val content = when (style) {
        TagPillStyle.Selected -> DeckOnAccent
        else -> DeckTextPrimary
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = if (style == TagPillStyle.Clear) BorderStroke(1.dp, DeckDivider) else null,
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (style) {
                TagPillStyle.Clear -> Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                TagPillStyle.Selected -> Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                TagPillStyle.Unselected -> {}
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (count != null) {
                Text(count.toString(), fontSize = 14.sp, color = content.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ScanCompleteCard(addedCount: Int, onShow: () -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(addedCount) {
        if (addedCount > 0) {
            delay(4000.milliseconds)
            onDismiss()
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DeckSurface,
        border = BorderStroke(1.dp, DeckDivider),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(18.dp),
            )
            Text(
                t("$addedCount new comics added", "$addedCount komik baru ditambahkan"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DeckTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onShow,
                shape = CircleShape,
                color = DeckAccentBlue,
                modifier = Modifier.height(28.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t("Show", "Lihat"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckOnAccent,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = t("Close", "Tutup"),
                    tint = DeckTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


@Composable
private fun SortSidePanel(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val groups = comicSortGroups()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                enter = slideInHorizontally(initialOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.77f)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = t("Back", "Kembali"),
                                        tint = DeckTextPrimary,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = t("Sort Comics", "Urutkan Komik"),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeckTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = t("Close", "Tutup"), tint = DeckTextPrimary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        groups.forEachIndexed { index, group ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                group.forEach { (key, label) ->
                                    val selected = current == key
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) SortSelectedColor else Color.Transparent)
                                            .clickable { onSelect(key) }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = DeckAccentBlue,
                                                unselectedColor = DeckTextSecondary,
                                            ),
                                        )
                                        Text(
                                            text = label,
                                            fontSize = 16.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = DeckTextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComicListItem(
    comic: Comic,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onOpenComic: (Comic) -> Unit,
    viewModel: ComicViewModel,
    onDownloadComic: ((Comic) -> Unit)? = null,
    showClearHistory: Boolean = false,
) {
    EnsureComicMetadata(comic, viewModel)

    val progressFraction = comicProgressFraction(comic)
    val pgs = t("pgs", "hlm")
    val coverModel = remember(comic.coverUri) { comic.coverUri.toUri() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) SelectedListItemColor else DeckSurface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelect) viewModel.toggleComicSelection(comic.id) else onOpenComic(comic) },
                onLongClick = {
                    if (isMultiSelect) viewModel.toggleComicSelection(comic.id)
                    else viewModel.startComicMultiSelect(comic.id)
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeckSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (comic.coverUri.isNotBlank()) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.Book, null, tint = DeckAccentBlue)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comic.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (comic.isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Check, null, tint = DeckAccentBlue, modifier = Modifier.size(16.dp))
                        Text(t("Completed", "Selesai"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DeckAccentBlue)
                    }
                } else if (comic.currentPage > 0 && comic.totalPages > 0) {
                    Text("${comic.currentPage} / ${comic.totalPages} $pgs", fontSize = 14.sp, color = DeckTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .fillMaxWidth(progressFraction)
                                .background(DeckAccentBlue),
                        )
                    }
                } else {
                    val unread = t("Unread", "Belum dibaca")
                    Text(
                        text = if (comic.totalPages > 0) "${comic.totalPages} $pgs · $unread" else unread,
                        fontSize = 14.sp,
                        color = DeckTextSecondary,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { viewModel.toggleComicFavorite(comic) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (comic.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = t("Favorite", "Favorit"),
                        tint = if (comic.isFavorite) DeckBadgeRed else DeckTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                ComicMoreButton(
                    comic = comic,
                    viewModel = viewModel,
                    onDownloadComic = onDownloadComic,
                    showClearHistory = showClearHistory,
                )
            }
        }
    }
}

@Composable
fun ComicInfoDialog(comic: Comic, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Comic Information", "Informasi Komik"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(t("Title:", "Judul:"), comic.title)
                InfoRow(t("Folder:", "Folder:"), comic.parentFolderName)
                InfoRow(t("Total Pages:", "Total Halaman:"), comic.totalPages.toString())
                InfoRow(t("Tags:", "Tag:"), comic.tags.ifEmpty { "-" })
                InfoRow(t("Path:", "Lokasi:"), comic.folderUri)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Close", "Tutup"))
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(text = value, fontSize = 14.sp)
    }
}


private val SelectedListItemColor: Color
    @Composable
    get() = DeckAccentPillBg
private val DangerColor = Color(0xFFFF6B7A)
private val SortSelectedColor: Color
    @Composable
    get() = DeckAccentPillBg
private val ShimmerBase: Color
    @Composable
    get() = if (LocalDeckColors.current.background.luminance() > 0.5f) Color(0xFFE2E8F0) else Color(0xFF1C1F26)
private val ShimmerHighlight: Color
    @Composable
    get() = if (LocalDeckColors.current.background.luminance() > 0.5f) Color(0xFFF1F5F9) else Color(0xFF343944)


@Composable
private fun EnsureComicMetadata(comic: Comic, viewModel: ComicViewModel) {
    val context = LocalContext.current
    LaunchedEffect(comic.id) {
        if (comic.totalPages == 0 || comic.coverUri.isEmpty()) {
            viewModel.refreshComicMetadata(context, comic)
        }
    }
}

private fun comicProgressFraction(comic: Comic): Float =
    if (comic.totalPages > 0 && comic.currentPage > 0)
        (comic.currentPage.toFloat() / comic.totalPages.toFloat()).coerceIn(0f, 1f)
    else 0f


@Composable
private fun comicSortLabel(key: String): String = when (key) {
    "name_asc" -> t("Name (A-Z)", "Nama (A-Z)")
    "name_desc" -> t("Name (Z-A)", "Nama (Z-A)")
    "date_desc" -> t("Newest Added", "Terbaru Ditambahkan")
    "date_asc" -> t("Oldest Added", "Terlama Ditambahkan")
    "last_read_desc" -> t("Last Read", "Terakhir Dibaca")
    "pages_desc" -> t("Most Pages", "Halaman Terbanyak")
    "size_desc" -> t("Largest Size", "Ukuran Terbesar")
    "size_asc" -> t("Smallest Size", "Ukuran Terkecil")
    "random" -> t("Shuffle / Random", "Acak")
    else -> t("Newest Added", "Terbaru Ditambahkan")
}


@Composable
private fun comicSortGroups(): List<List<Pair<String, String>>> =
    listOf(
        listOf("name_asc", "name_desc"),
        listOf("date_desc", "date_asc"),
        listOf("size_desc", "size_asc"),
        listOf("random"),
    ).map { group -> group.map { key -> key to comicSortLabel(key) } }
