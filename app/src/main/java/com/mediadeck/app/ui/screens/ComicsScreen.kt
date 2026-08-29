@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mediadeck.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size as CoilSize
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.ui.components.DraggableGridScrollbar
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ComicViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicsScreen(
    viewModel: ComicViewModel,
    scannerViewModel: ScannerViewModel,
    onOpenComic: (Comic) -> Unit,
    onNavigateToScan: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val heroCoverWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val heroCoverHeightPx = with(density) { 176.dp.roundToPx() }

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
    val isManualRefreshing by scannerViewModel.isManualRefreshing.collectAsState()
    val heroComic by viewModel.heroComic.collectAsState()
    val isRefreshing = isManualRefreshing

    var isSearchingLocal by rememberSaveable { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var isTagsPanelExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchingLocal) {
        if (isSearchingLocal) {
            focusRequester.requestFocus()
        }
    }

    val savedScrollIndex by viewModel.scrollIndex.collectAsState()
    val savedScrollOffset by viewModel.scrollOffset.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isTagsPanelExpanded || isMultiSelect || isSearchingLocal || search.isNotEmpty() || selectedTags.isNotEmpty() || activeTab != "all") {
        when {
            isSearchingLocal || search.isNotEmpty() -> {
                viewModel.setComicSearch("")
                isSearchingLocal = false
            }
            isTagsPanelExpanded || selectedTags.isNotEmpty() -> {
                viewModel.clearComicTagFilter()
                isTagsPanelExpanded = false
            }
            isMultiSelect -> viewModel.clearComicSelection()
            activeTab != "all" -> viewModel.setComicTab("all")
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset,
    )

    LaunchedEffect(viewModel.scrollToTopEvent) {
        viewModel.scrollToTopEvent.collect {
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .debounce(500.milliseconds)
            .collectLatest { (index, offset) ->
                viewModel.setScrollPosition(index, offset)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.clickable { viewModel.requestScrollToTop() },
                navigationIcon = {
                    if (isSearchingLocal) {
                        IconButton(onClick = {
                            viewModel.setComicSearch("")
                            isSearchingLocal = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    } else if (isMultiSelect) {
                        IconButton(onClick = { viewModel.clearComicSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = t("Cancel", "Batal"))
                        }
                    } else if (activeTab != "all") {
                        IconButton(onClick = { viewModel.setComicTab("all") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    } else {
                        Icon(imageVector = Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(28.dp))
                    }
                },
                title = {
                    if (isSearchingLocal) {
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
                    } else if (isMultiSelect) {
                        Text(t("${selectedIds.size} Selected", "${selectedIds.size} Terpilih"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(text = when (activeTab) {
                            "history" -> t("History", "Riwayat")
                            "favorites" -> t("Favorites", "Favorit")
                            "read_later" -> t("Read Later", "Baca Nanti")
                            else -> t("Comics", "Komik")
                        }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                                    onClick = { viewModel.setComicTab("favorites"); showMainMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(t("Read Later", "Baca Nanti")) },
                                    leadingIcon = { Icon(Icons.Default.Bookmark, null) },
                                    onClick = { viewModel.setComicTab("read_later"); showMainMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(t("History", "Riwayat")) },
                                    leadingIcon = { Icon(Icons.Default.History, null) },
                                    onClick = { viewModel.setComicTab("history"); showMainMenu = false },
                                )
                                if (activeTab == "history") {
                                    DropdownMenuItem(text = { Text(t("Clear History", "Hapus Riwayat"), color = Color.Red) }, leadingIcon = { Icon(Icons.Default.History, null, tint = Color.Red) }, onClick = { viewModel.clearAllComicHistory(); showMainMenu = false })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("Sort By...", "Urutkan")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }, onClick = { showMainMenu = false; showSortDialog = true })
                                DropdownMenuItem(text = { Text(t("Filter Tags...", "Filter Label")) }, leadingIcon = { Icon(Icons.Default.FilterAlt, null) }, onClick = { isTagsPanelExpanded = !isTagsPanelExpanded; showMainMenu = false })
                                DropdownMenuItem(text = { Text(t("Select Multiple", "Pilih Banyak")) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, onClick = { viewModel.startComicMultiSelect(); showMainMenu = false })
                                if (isMultiSelect) {
                                    DropdownMenuItem(text = { Text(t("Delete Selected", "Hapus"), color = Color.Red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }, onClick = { showDeleteConfirm = true; showMainMenu = false })
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
                scannerViewModel.autoScanLibrary(true, "comics")
            },
            indicator = {},
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isTagsPanelExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(bottom = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("Filter by Tags", "Label"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (selectedTags.isNotEmpty()) TextButton(onClick = { viewModel.clearComicTagFilter() }) { Text(t("Clear All", "Hapus Semua"), fontSize = 12.sp) }
                        }

                        val selectedList = remember(tags, selectedTags) { tags.filter { selectedTags.contains(it) }.sorted() }
                        val unselectedList = remember(tags, selectedTags, tagCounts) {
                            tags.filter { !selectedTags.contains(it) }
                                .sortedByDescending { tagCounts[it] ?: 0 }
                        }

                        if (selectedList.isNotEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(selectedList.size) { index ->
                                    val tag = selectedList[index]
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.toggleComicTagFilter(tag) },
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
                                        onClick = { viewModel.toggleComicTagFilter(tag) },
                                        label = { Text("$tag (${tagCounts[tag] ?: 0})", fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (scanProgress != null && settings.floatingScanStatus) {
                    ScanStatusCard(
                        scanProgress = scanProgress,
                        isScanActive = isScanActive,
                        isScanPaused = isScanPaused,
                        onTogglePause = { scannerViewModel.togglePauseScan() },
                        onStopScan = { scannerViewModel.stopScan() }
                    )
                }

                if (comics.isEmpty()) {
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
                            Text(t("No comics found", "Komik tidak ditemukan"))
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
                } else {
                    val isTitleOnly = settings.layoutMode == "title_only"
                    val gridCells = when {
                        isTitleOnly -> GridCells.Fixed(1)
                        settings.gridColumns == 1 -> GridCells.Fixed(1)
                        settings.gridColumns == 2 -> GridCells.Fixed(2)
                        else -> GridCells.Adaptive(160.dp)
                    }
                    val displayHero = if (activeTab == "all" && !isTitleOnly && search.isEmpty()) heroComic else null
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalGrid(state = gridState, columns = gridCells, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 80.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (displayHero != null) {
                                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                                    HeroComicCard(displayHero, heroCoverWidthPx, heroCoverHeightPx, onOpenComic, viewModel)
                                }
                            }
                            items(comics, key = { it.id }) { comic ->
                                if (isTitleOnly) ComicListItem(comic, selectedIds.contains(comic.id), isMultiSelect, onOpenComic, viewModel)
                                else ComicCard(comic, selectedIds.contains(comic.id), isMultiSelect, onOpenComic, viewModel)
                            }
                        }
                        if (settings.showSideScrollbar) {
                            DraggableGridScrollbar(gridState = gridState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = 80.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        val sortOptions = listOf(
            "name_asc" to t("Name (A-Z)", "Nama (A-Z)"),
            "name_desc" to t("Name (Z-A)", "Nama (Z-A)"),
            "date_desc" to t("Newest Added", "Terbaru Ditambahkan"),
            "date_asc" to t("Oldest Added", "Terlama Ditambahkan"),
            "last_read_desc" to t("Last Read", "Terakhir Dibaca"),
            "pages_desc" to t("Most Pages", "Halaman Terbanyak"),
            "random" to t("Shuffle / Random", "Acak"),
        )
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(t("Sort Comics", "Urutkan Komik")) },
            text = {
                Column {
                    sortOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setComicSort(key); showSortDialog = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = sortOption == key, onClick = { viewModel.setComicSort(key); showSortDialog = false })
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showDeleteConfirm) {
        com.mediadeck.app.ui.components.DeleteConfirmationDialog(
            title = t("Delete Comics", "Hapus Komik"),
            message = t("Are you sure you want to delete ${selectedIds.size} selected comics?", "Apakah Anda yakin ingin menghapus ${selectedIds.size} komik yang dipilih?"),
            onConfirm = {
                viewModel.deleteSelectedComics(context)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
fun HeroComicCard(comic: Comic, widthPx: Int, heightPx: Int, onOpenComic: (Comic) -> Unit, viewModel: ComicViewModel) {
    val context = LocalContext.current
    val heroStatusLabel = if (comic.currentPage > 0) t("Continue Reading", "Lanjut Baca") else t("Recently Added", "Terbaru")

    LaunchedEffect(comic.id) {
        if (comic.totalPages == 0 || comic.coverUri.isEmpty()) {
            viewModel.refreshComicMetadata(context, comic)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = heroStatusLabel, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text(text = t("VIEW ALL", "LIHAT SEMUA"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { viewModel.setComicTab("history") })
        }

        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().height(176.dp).clickable { onOpenComic(comic) }) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(Uri.parse(comic.coverUri)).size(CoilSize(widthPx, heightPx)).precision(Precision.INEXACT).build(), contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.3f, modifier = Modifier.fillMaxSize())
                Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(text = comic.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                    if (comic.totalPages > 0 && comic.currentPage > 0) {
                        val percent = ((comic.currentPage.toFloat() / comic.totalPages.toFloat()) * 100).toInt()
                        Text(text = t("Page ${comic.currentPage} ($percent%)", "Halaman ${comic.currentPage} ($percent%)"), fontSize = 12.sp)
                        LinearProgressIndicator(progress = { comic.currentPage.toFloat() / comic.totalPages.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 8.dp).clip(CircleShape))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCard(comic: Comic, isSelected: Boolean, isMultiSelect: Boolean, onOpenComic: (Comic) -> Unit, viewModel: ComicViewModel) {
    val context = LocalContext.current

    LaunchedEffect(comic.id) {
        if (comic.totalPages == 0 || comic.coverUri.isEmpty()) {
            viewModel.refreshComicMetadata(context, comic)
        }
    }

    Card(elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (isMultiSelect) viewModel.toggleComicSelection(comic.id) else onOpenComic(comic) }, onLongClick = { viewModel.startComicMultiSelect(comic.id) })) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            AsyncImage(model = Uri.parse(comic.coverUri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
            IconButton(onClick = { viewModel.toggleComicFavorite(comic) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(if (comic.isFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder, null, tint = if (comic.isFavorite) Color.Red else Color.White)
            }
            if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleComicSelection(comic.id) }, modifier = Modifier.align(Alignment.TopStart))

            if (comic.isCompleted && !isMultiSelect) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(9.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(t("Done", "Selesai"), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            ) {
                Text(
                    text = if (comic.totalPages > 0) "${comic.totalPages} " + t("Pgs", "Hlm") else "...",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }

            var showMenu by remember { mutableStateOf(false) }
            var showInfoDialog by remember { mutableStateOf(false) }

            if (showInfoDialog) {
                ComicInfoDialog(comic = comic, onDismiss = { showInfoDialog = false })
            }

            Box(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Info", "Info")) },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { showInfoDialog = true; showMenu = false }
                    )
                    DropdownMenuItem(text = { Text(t("Delete", "Hapus")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMenu = false; viewModel.deleteComic(context, comic) })
                    if (comic.currentPage > 0) DropdownMenuItem(text = { Text(t("Clear History", "Hapus Riwayat")) }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { viewModel.clearComicHistory(comic); showMenu = false })
                }
            }
        }
        Text(text = comic.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(8.dp).basicMarquee())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicListItem(comic: Comic, isSelected: Boolean, isMultiSelect: Boolean, onOpenComic: (Comic) -> Unit, viewModel: ComicViewModel) {
    val context = LocalContext.current

    LaunchedEffect(comic.id) {
        if (comic.totalPages == 0 || comic.coverUri.isEmpty()) {
            viewModel.refreshComicMetadata(context, comic)
        }
    }

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = { if (isMultiSelect) viewModel.toggleComicSelection(comic.id) else onOpenComic(comic) }, onLongClick = { viewModel.startComicMultiSelect(comic.id) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = comic.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (comic.totalPages > 0) "${comic.totalPages} " + t("Pages", "Halaman") else "...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comic.isCompleted) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                }
            }
        }
        var showMenu by remember { mutableStateOf(false) }
        var showInfoDialog by remember { mutableStateOf(false) }

        if (showInfoDialog) {
            ComicInfoDialog(comic = comic, onDismiss = { showInfoDialog = false })
        }

        Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(t("Info", "Info")) },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { showInfoDialog = true; showMenu = false }
                )
                DropdownMenuItem(text = { Text(t("Delete", "Hapus")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMenu = false; viewModel.deleteComic(context, comic) })
            }
        }
        if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleComicSelection(comic.id) })
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
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(text = value, fontSize = 14.sp)
    }
}
