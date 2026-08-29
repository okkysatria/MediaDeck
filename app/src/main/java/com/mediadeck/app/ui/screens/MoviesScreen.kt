@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mediadeck.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.graphics.Brush
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.DraggableGridScrollbar
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.rememberFolderThumbnailUri
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.rememberFolderThumbnailUri
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.MovieViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MoviesScreen(
    viewModel: MovieViewModel,
    scannerViewModel: ScannerViewModel,
    onOpenMovie: (Movie) -> Unit,
    onNavigateToScan: () -> Unit,
) {
    val context = LocalContext.current
    val movies by viewModel.filteredMovies.collectAsState()
    val search by viewModel.movieSearch.collectAsState()
    val activeTab by viewModel.movieTabFilter.collectAsState()
    val sortOption by viewModel.movieSort.collectAsState()
    val isMultiSelect by viewModel.isMovieMultiSelectMode.collectAsState()
    val selectedIds by viewModel.selectedMovieIds.collectAsState()
    val isGroupedByFolder by viewModel.isMovieGroupedByFolder.collectAsState()
    val selectedFolderName by viewModel.selectedMovieFolderName.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val scanProgress by scannerViewModel.scanProgress.collectAsState()
    val isScanActive by scannerViewModel.isScanActive.collectAsState()
    val isScanPaused by scannerViewModel.isScanPaused.collectAsState()
    val isManualRefreshing by scannerViewModel.isManualRefreshing.collectAsState()
    val tags by viewModel.movieAvailableTags.collectAsState()
    val selectedTags by viewModel.movieSelectedTagsFilter.collectAsState()
    val tagCounts by viewModel.movieTagCounts.collectAsState()
    val isRefreshing = isManualRefreshing

    var showMainMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var isTagsPanelExpanded by remember { mutableStateOf(false) }
    var isSearchingLocal by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var movieToDelete by remember { mutableStateOf<Movie?>(null) }

    if (showDeleteConfirmDialog || movieToDelete != null) {
        DeleteConfirmationDialog(
            onConfirm = {
                if (movieToDelete != null) {
                    viewModel.deleteMovie(context, movieToDelete!!)
                    movieToDelete = null
                } else {
                    viewModel.deleteSelectedMovies(context)
                }
                showDeleteConfirmDialog = false
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                movieToDelete = null
            },
            title = if (movieToDelete != null) t("Delete Movie", "Hapus Film") else t("Delete Selected", "Hapus Terpilih"),
            message = if (movieToDelete != null) t("Are you sure you want to delete this video?", "Apakah Anda yakin ingin menghapus video ini?") else t("Are you sure you want to delete selected videos?", "Apakah Anda yakin ingin menghapus video terpilih?"),
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

    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = savedScrollIndex, initialFirstVisibleItemScrollOffset = savedScrollOffset)

    BackHandler(enabled = isSearchingLocal || isTagsPanelExpanded || isMultiSelect || search.isNotEmpty() || selectedTags.isNotEmpty() || activeTab != "all" || selectedFolderName != null) {
        when {
            isSearchingLocal || search.isNotEmpty() -> {
                viewModel.setMovieSearch("")
                isSearchingLocal = false
            }
            isTagsPanelExpanded || selectedTags.isNotEmpty() -> {
                viewModel.clearMovieTagFilter()
                isTagsPanelExpanded = false
            }
            isMultiSelect -> viewModel.clearMovieSelection()
            selectedFolderName != null -> {
                viewModel.onFolderExited()
                viewModel.setSelectedMovieFolderName(null)
            }
            activeTab != "all" -> viewModel.setMovieTab("all")
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
                            viewModel.setMovieSearch("")
                            isSearchingLocal = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    } else if (isMultiSelect) {
                        IconButton(onClick = { viewModel.clearMovieSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = t("Cancel", "Batal"))
                        }
                    } else if (selectedFolderName != null) {
                        IconButton(onClick = {
                            viewModel.onFolderExited()
                            viewModel.setSelectedMovieFolderName(null)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    } else if (activeTab != "all") {
                        IconButton(onClick = { viewModel.setMovieTab("all") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(28.dp),
                        )
                    }
                },
                title = {
                    if (isSearchingLocal) {
                        TextField(
                            value = search,
                            onValueChange = { viewModel.setMovieSearch(it) },
                            placeholder = { Text(t("Search videos...", "Cari video..."), fontSize = 15.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                if (search.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setMovieSearch("") }) {
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
                        val screenTitle = when {
                            selectedFolderName != null -> selectedFolderName!!
                            activeTab == "history" -> t("Recently Played", "Baru Diputar")
                            activeTab == "favorites" -> t("Favorites", "Favorit")
                            else -> t("Movies", "Film")
                        }
                        Text(text = screenTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (!isSearchingLocal) {
                        IconButton(onClick = { isSearchingLocal = true }) {
                            Icon(Icons.Default.Search, contentDescription = t("Search", "Cari"))
                        }
                        Box {
                            IconButton(onClick = { showMainMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = t("Menu", "Menu"))
                            }
                            DropdownMenu(expanded = showMainMenu, onDismissRequest = { showMainMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (settings.layoutMode == "grid") t("List View", "Daftar") else t("Grid View", "Grid")) },
                                    leadingIcon = { Icon(imageVector = if (settings.layoutMode == "grid") Icons.AutoMirrored.Filled.List else Icons.Default.GridView, contentDescription = null) },
                                    onClick = { viewModel.setLayoutMode(if (settings.layoutMode == "grid") "title_only" else "grid"); showMainMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(t("Favorites", "Favorit")) },
                                    leadingIcon = { Icon(Icons.Default.Favorite, null) },
                                    onClick = { viewModel.setMovieTab("favorites"); showMainMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(t("Recently Played", "Baru diputar")) },
                                    leadingIcon = { Icon(Icons.Default.History, null) },
                                    onClick = { viewModel.setMovieTab("history"); showMainMenu = false },
                                )
                                if (activeTab == "history") {
                                    DropdownMenuItem(text = { Text(t("Clear History", "Hapus Riwayat"), color = Color.Red) }, leadingIcon = { Icon(Icons.Default.History, null, tint = Color.Red) }, onClick = { viewModel.clearAllMovieHistory(); showMainMenu = false })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("Sort By...", "Urutkan")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }, onClick = { showMainMenu = false; showSortDialog = true })
                                DropdownMenuItem(text = { Text(t("Filter Tags...", "Filter Label")) }, leadingIcon = { Icon(Icons.Default.FilterAlt, null) }, onClick = { isTagsPanelExpanded = !isTagsPanelExpanded; showMainMenu = false })
                                DropdownMenuItem(text = { Text(t("Group by Folder", "Grup Folder")) }, leadingIcon = { Icon(if (isGroupedByFolder) Icons.Default.FolderOpen else Icons.Default.Folder, null, tint = if (isGroupedByFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { viewModel.setMovieGroupedByFolder(!isGroupedByFolder); showMainMenu = false })
                                DropdownMenuItem(text = { Text(t("Select Multiple", "Pilih Banyak")) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, onClick = { viewModel.startMovieMultiSelect(); showMainMenu = false })
                                if (isMultiSelect) {
                                    DropdownMenuItem(
                                        text = { Text(t("Delete Selected", "Hapus"), color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                        onClick = { showDeleteConfirmDialog = true; showMainMenu = false },
                                    )
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
                scannerViewModel.autoScanLibrary(true, "movies")
            },
            indicator = {},
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isTagsPanelExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(bottom = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("Filter by Tags", "Label"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (selectedTags.isNotEmpty()) TextButton(onClick = { viewModel.clearMovieTagFilter() }) { Text(t("Clear All", "Hapus Semua"), fontSize = 12.sp) }
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
                                        onClick = { viewModel.toggleMovieTagFilter(tag) },
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
                                        onClick = { viewModel.toggleMovieTagFilter(tag) },
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

                if (movies.isEmpty()) {
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
                            Text(t("No videos found", "Video tidak ditemukan"))
                            Spacer(modifier = Modifier.height(16.dp))
                            FloatingActionButton(
                                onClick = onNavigateToScan,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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

                    if (isGroupedByFolder && activeTab == "all" && search.isEmpty() && selectedFolderName == null) {
                        val grouped = remember(movies) { movies.groupBy { it.folderName } }
                        val folders = remember(grouped) { grouped.keys.toList().sorted() }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyVerticalGrid(state = gridState, columns = gridCells, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(folders, key = { it }) { folder ->
                                    val folderMovies = grouped[folder] ?: emptyList()
                                    val onFolderClick = {
                                        viewModel.setSelectedMovieFolderName(folder)
                                        viewModel.onFolderVisited(context, folder)
                                    }
                                    if (isTitleOnly) MovieFolderListItem(folder, folderMovies.size, onFolderClick)
                                    else MovieFolderCard(folder, folderMovies.size, settings, onFolderClick)
                                }
                            }
                            DraggableGridScrollbar(gridState = gridState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = 80.dp))
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyVerticalGrid(state = gridState, columns = gridCells, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(movies, key = { it.id }) { movie ->
                                    if (isTitleOnly) MovieListItem(movie, selectedIds.contains(movie.id), isMultiSelect, onOpenMovie, viewModel, onDelete = { movieToDelete = it; showDeleteConfirmDialog = true })
                                    else MovieCard(movie, selectedIds.contains(movie.id), isMultiSelect, onOpenMovie, viewModel, settings, onDelete = { movieToDelete = it; showDeleteConfirmDialog = true })
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
    }

    if (showSortDialog) {
        val sortOptions = listOf(
            "name_asc" to t("Name (A-Z)", "Nama (A-Z)"),
            "name_desc" to t("Name (Z-A)", "Nama (Z-A)"),
            "date_desc" to t("Newest Added", "Terbaru Ditambahkan"),
            "date_asc" to t("Oldest Added", "Terlama Ditambahkan"),
            "size_desc" to t("Largest Size", "Ukuran Terbesar"),
            "size_asc" to t("Smallest Size", "Ukuran Kecil"),
            "random" to t("Shuffle / Random", "Acak"),
        )
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(t("Sort Video", "Urutkan Video")) },
            text = {
                Column {
                    sortOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setMovieSort(key); showSortDialog = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = sortOption == key, onClick = { viewModel.setMovieSort(key); showSortDialog = false })
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieCard(movie: Movie, isSelected: Boolean, isMultiSelect: Boolean, onOpenMovie: (Movie) -> Unit, viewModel: MovieViewModel, settings: com.mediadeck.app.data.settings.AppSettings, onDelete: (Movie) -> Unit) {
    val context = LocalContext.current

    Card(elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (isMultiSelect) viewModel.toggleMovieSelection(movie.id) else onOpenMovie(movie) }, onLongClick = { viewModel.startMovieMultiSelect(movie.id) })) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            AsyncThumbnailImage(
                uriString = movie.uri,
                mediaId = movie.id,
                hasThumbnail = movie.hasThumbnail,
                settings = settings,
                modifier = Modifier.fillMaxSize()
            )

            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))

            IconButton(onClick = { viewModel.toggleMovieFavorite(movie) }, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                Icon(if (movie.isFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder, null, tint = if (movie.isFavorite) Color.Red else Color.White, modifier = Modifier.size(20.dp))
            }
            if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleMovieSelection(movie.id) }, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
            var showMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (movie.uri.startsWith("smb://") || movie.uri.contains("smbprovider")) {
                        DropdownMenuItem(text = { Text(t("Download", "Unduh")) }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = { showMenu = false; viewModel.downloadSmbFile(context, movie.uri, movie.title) })
                    }
                    DropdownMenuItem(text = { Text(t("Delete", "Hapus")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMenu = false; onDelete(movie) })
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(text = movie.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (movie.lastPlayedPosition > 0 && movie.duration > 0) {
                LinearProgressIndicator(
                    progress = { (movie.lastPlayedPosition.toFloat() / movie.duration.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp).clip(CircleShape),
                )
            }
        }
    }
}


@Composable
fun MovieFolderCard(folderName: String, count: Int, settings: com.mediadeck.app.data.settings.AppSettings, onClick: () -> Unit) {
    val mosaicUri = rememberFolderThumbnailUri(folderName)

    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mosaicUri != null && settings.videoThumbnails) {
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
                Text(text = "$count Videos", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieListItem(movie: Movie, isSelected: Boolean, isMultiSelect: Boolean, onOpenMovie: (Movie) -> Unit, viewModel: MovieViewModel, onDelete: (Movie) -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = { if (isMultiSelect) viewModel.toggleMovieSelection(movie.id) else onOpenMovie(movie) }, onLongClick = { viewModel.startMovieMultiSelect(movie.id) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncThumbnailImage(
            uriString = movie.uri,
            mediaId = movie.id,
            hasThumbnail = movie.hasThumbnail,
            settings = viewModel.appSettings.collectAsState().value,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = movie.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSize(movie.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (movie.uri.startsWith("smb://") || movie.uri.contains("smbprovider")) {
                    DropdownMenuItem(text = { Text(t("Download", "Unduh")) }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = { showMenu = false; viewModel.downloadSmbFile(context, movie.uri, movie.title) })
                }
                DropdownMenuItem(text = { Text(t("Delete", "Hapus")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMenu = false; onDelete(movie) })
            }
        }
        if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleMovieSelection(movie.id) })
    }
}

@Composable
fun MovieFolderListItem(folderName: String, count: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column { Text(folderName, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("$count Videos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var i = 0
    while (size > 1024 && i < units.size - 1) {
        size /= 1024
        i++
    }
    return "%.1f %s".format(size, units[i])
}
