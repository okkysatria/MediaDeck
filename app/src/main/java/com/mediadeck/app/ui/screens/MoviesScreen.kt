@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mediadeck.app.ui.screens
import androidx.activity.compose.BackHandler
import coil3.request.crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.DraggableGridScrollbar
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.components.AsyncThumbnailImage
import com.mediadeck.app.ui.components.FolderGroupCard
import com.mediadeck.app.ui.components.FolderGroupListItem
import com.mediadeck.app.ui.components.rememberBottomBarPadding
import com.mediadeck.app.ui.theme.DeckDivider
import com.mediadeck.app.ui.theme.DeckOnAccent
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
    LaunchedEffect(viewModel) {
        viewModel.enqueueMissingThumbnails(context)
    }
    LaunchedEffect(viewModel, selectedFolderName) {
        selectedFolderName?.let { viewModel.onFolderVisited(context, it) }
    }
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
                val target = movieToDelete
                if (target != null) {
                    viewModel.deleteMovie(context, target)
                } else {
                    viewModel.deleteSelectedMovies(context)
                }
                movieToDelete = null
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
    val folderGridState = rememberLazyGridState()
    var folderShuffleSeed by remember { mutableIntStateOf(kotlin.random.Random.nextInt()) }
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
            if (isSearchingLocal || isMultiSelect) {
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
                        } else {
                            Text(t("${selectedIds.size} Selected", "${selectedIds.size} Terpilih"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (isMultiSelect) {
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = t("Delete", "Hapus"), tint = com.mediadeck.app.ui.theme.DeckBadgeRed)
                            }
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val selectedFolderTitle = selectedFolderName?.let { folderKey ->
                        val folderMovie = movies.firstOrNull()
                        com.mediadeck.app.util.media.MediaFolderIdentity.displayName(
                            folderMovie?.folderUri ?: folderKey,
                            folderMovie?.uri ?: folderKey,
                            folderMovie?.folderName.orEmpty(),
                        )
                    }
                    val screenTitle = when {
                        selectedFolderTitle != null -> selectedFolderTitle
                        activeTab == "history" -> t("Recently Played", "Baru Diputar")
                        activeTab == "favorites" -> t("Favorites", "Favorit")
                        else -> t("Movies", "Film")
                    }

                    com.mediadeck.app.ui.components.MediaDeckHeader(
                        icon = Icons.Default.PlayCircle,
                        title = screenTitle,
                        subtitle = t("${movies.size} videos", "${movies.size} video"),
                        onSearchClick = { isSearchingLocal = true },
                        onMoreClick = { showMainMenu = true }
                    )

                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.reshuffle()
                folderShuffleSeed = kotlin.random.Random.nextInt()
                scannerViewModel.autoScanLibrary(true, "movies")
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isTagsPanelExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = t("Filter by Tags", "Filter berdasarkan Label"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.mediadeck.app.ui.theme.DeckAccentBlue,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val selectedList = remember(tags, selectedTags) { tags.filter { selectedTags.contains(it) }.sorted() }
                        val unselectedList = remember(tags, selectedTags, tagCounts) {
                            tags.filter { !selectedTags.contains(it) }
                                .sortedByDescending { tagCounts[it] ?: 0 }
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedTags.isNotEmpty()) {
                                item {
                                    Surface(
                                        onClick = { viewModel.clearMovieTagFilter() },
                                        shape = RoundedCornerShape(18.dp),
                                        color = com.mediadeck.app.ui.theme.DeckSurface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DeckDivider),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = com.mediadeck.app.ui.theme.DeckTextPrimary, modifier = Modifier.size(14.dp))
                                            Text(t("Clear", "Hapus"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = com.mediadeck.app.ui.theme.DeckTextPrimary)
                                        }
                                    }
                                }
                            }

                            items(selectedList.size) { index ->
                                val tag = selectedList[index]
                                val count = tagCounts[tag] ?: 0
                                Surface(
                                    onClick = { viewModel.toggleMovieTagFilter(tag) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = com.mediadeck.app.ui.theme.DeckAccentBlue,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DeckOnAccent, modifier = Modifier.size(14.dp))
                                        Text("$tag $count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DeckOnAccent)
                                    }
                                }
                            }

                            items(unselectedList.size) { index ->
                                val tag = unselectedList[index]
                                val count = tagCounts[tag] ?: 0
                                Surface(
                                    onClick = { viewModel.toggleMovieTagFilter(tag) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = com.mediadeck.app.ui.theme.DeckSurface,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(tag, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = com.mediadeck.app.ui.theme.DeckTextPrimary)
                                        Text(count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = com.mediadeck.app.ui.theme.DeckTextSecondary)
                                    }
                                }
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
                    val bottomBarPadding = rememberBottomBarPadding(16.dp)
                    val screenWidthDp = LocalConfiguration.current.screenWidthDp
                    val gridCells = when {
                        isTitleOnly -> GridCells.Fixed(1)
                        settings.gridColumns == 1 -> if (screenWidthDp >= 600) GridCells.Fixed(2) else GridCells.Fixed(1)
                        settings.gridColumns == 2 -> when {
                            screenWidthDp >= 840 -> GridCells.Fixed(4)
                            screenWidthDp >= 600 -> GridCells.Fixed(3)
                            else -> GridCells.Fixed(2)
                        }
                        else -> GridCells.Adaptive(160.dp)
                    }
                    if (isGroupedByFolder && activeTab == "all" && search.isEmpty() && selectedFolderName == null) {
                        val grouped = remember(movies) { movies.groupBy { com.mediadeck.app.util.media.MediaFolderIdentity.key(it.folderUri, it.uri) } }
                        val folderNames = remember(grouped) {
                            grouped.mapValues { (_, folderMovies) ->
                                val first = folderMovies.firstOrNull()
                                com.mediadeck.app.util.media.MediaFolderIdentity.displayName(
                                    first?.folderUri.orEmpty(), first?.uri.orEmpty(), first?.folderName.orEmpty()
                                )
                            }
                        }
                        val folders = remember(grouped, folderNames, sortOption, folderShuffleSeed) {
                            val keys = grouped.keys.toList()
                            when (sortOption) {
                                "date_desc" -> keys.sortedByDescending { key -> grouped[key].orEmpty().maxOfOrNull { it.dateAdded } ?: 0L }
                                "date_asc" -> keys.sortedBy { key -> grouped[key].orEmpty().minOfOrNull { it.dateAdded } ?: Long.MAX_VALUE }
                                "name_desc" -> keys.sortedByDescending { folderNames[it].orEmpty().lowercase() }
                                "size_desc" -> keys.sortedByDescending { key -> grouped[key].orEmpty().sumOf { it.size } }
                                "size_asc" -> keys.sortedBy { key -> grouped[key].orEmpty().sumOf { it.size } }
                                "random" -> keys.shuffled(kotlin.random.Random(folderShuffleSeed))
                                else -> keys.sortedBy { folderNames[it].orEmpty().lowercase() }
                            }
                        }
                        val folderNameCounts = remember(folderNames) { folderNames.values.groupingBy { it }.eachCount() }
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {


                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                LazyVerticalGrid(
                                    state = folderGridState,
                                    columns = gridCells,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(bottom = bottomBarPadding),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(folders, key = { it }) { folder ->
                                        val folderMovies = grouped[folder] ?: emptyList()
                                        val first = folderMovies.firstOrNull()
                                        val displayName = folderNames[folder] ?: "Folder"
                                        val duplicateName = (folderNameCounts[displayName] ?: 0) > 1
                                        val parentName = com.mediadeck.app.util.media.MediaFolderIdentity.parentDisplayName(
                                            first?.folderUri.orEmpty(), first?.uri.orEmpty()
                                        )
                                        val folderLabel = if (duplicateName && parentName != null) "$displayName ($parentName)" else displayName
                                        val onFolderClick = { viewModel.setSelectedMovieFolderName(folder) }
                                        val folderKey = "movie:$folder"
                                        if (isTitleOnly) {
                                            FolderGroupListItem(
                                                folderName = folderLabel,
                                                countLabel = t("${folderMovies.size} Videos", "${folderMovies.size} video"),
                                                processingKey = "movie|$folder",
                                                onClick = onFolderClick,
                                            )
                                        } else {
                                            LaunchedEffect(folderKey, folderMovies.size, settings.videoThumbnails) {
                                                if (settings.videoThumbnails) {
                                                    viewModel.enqueueFolderThumbnail(context, folderKey, null, folderMovies)
                                                }
                                            }
                                            FolderGroupCard(
                                                folderKey = folderKey,
                                                folderName = folderLabel,
                                                countLabel = t("${folderMovies.size} Videos", "${folderMovies.size} video"),
                                                showThumbnail = settings.videoThumbnails,
                                                processingKey = "movie|$folder",
                                                onClick = onFolderClick,
                                            )
                                        }
                                    }
                                }
                                if (settings.showSideScrollbar) {
                                    DraggableGridScrollbar(
                                        gridState = folderGridState,
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 8.dp, bottom = bottomBarPadding),
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = gridCells,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(bottom = bottomBarPadding),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(movies, key = { it.id }) { movie ->
                                    if (isTitleOnly) {
                                        MovieListItem(movie, selectedIds.contains(movie.id), isMultiSelect, onOpenMovie, viewModel, settings, onDelete = { movieToDelete = it; showDeleteConfirmDialog = true })
                                    } else {
                                        MovieCard(movie, selectedIds.contains(movie.id), isMultiSelect, onOpenMovie, viewModel, settings, onDelete = { movieToDelete = it; showDeleteConfirmDialog = true })
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
    }
    if (showMainMenu) {
        GallerySideSheet(
            title = t("Movies Options", "Opsi Film"),
            onDismiss = { showMainMenu = false },
        ) {
            SheetSectionLabel(t("VIEW", "TAMPILAN"))
            SheetSegmented(
                options = listOf("grid" to t("Grid", "Grid"), "title_only" to t("List", "Daftar")),
                selected = if (settings.layoutMode == "title_only") "title_only" else "grid",
                onSelect = { viewModel.setLayoutMode(it) },
            )
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetActionRow(
                    icon = Icons.Outlined.Folder,
                    label = t("Group by Folder", "Grup Folder"),
                    onClick = { viewModel.setMovieGroupedByFolder(!isGroupedByFolder) },
                    trailing = {
                        Switch(
                            checked = isGroupedByFolder,
                            onCheckedChange = { viewModel.setMovieGroupedByFolder(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeckOnAccent,
                                checkedTrackColor = com.mediadeck.app.ui.theme.DeckAccentBlue,
                                checkedBorderColor = com.mediadeck.app.ui.theme.DeckAccentBlue,
                                uncheckedThumbColor = com.mediadeck.app.ui.theme.DeckTextSecondary,
                                uncheckedTrackColor = com.mediadeck.app.ui.theme.DeckSurfaceVariant,
                                uncheckedBorderColor = DeckDivider,
                            ),
                        )
                    },
                )
                SheetActionRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    label = t("Favorites", "Favorit"),
                    onClick = { viewModel.setMovieTab("favorites"); showMainMenu = false },
                )
                SheetActionRow(
                    icon = Icons.Outlined.History,
                    label = t("Recently Played", "Baru Diputar"),
                    onClick = { viewModel.setMovieTab("history"); showMainMenu = false },
                )
                if (activeTab == "history") {
                    SheetActionRow(
                        icon = Icons.Outlined.History,
                        label = t("Clear History", "Hapus Riwayat"),
                        tint = com.mediadeck.app.ui.theme.DeckBadgeRed,
                        labelColor = com.mediadeck.app.ui.theme.DeckBadgeRed,
                        onClick = { viewModel.clearAllMovieHistory(); showMainMenu = false },
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
                    onClick = { viewModel.startMovieMultiSelect(); showMainMenu = false },
                )
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
        val sortGroups = listOf(sortOptions.subList(0, 2), sortOptions.subList(2, 4), sortOptions.subList(4, 6), sortOptions.subList(6, 7))
        GallerySideSheet(
            title = t("Sort Video", "Urutkan Video"),
            onDismiss = { showSortDialog = false },
            onBack = { showSortDialog = false; showMainMenu = true },
        ) {
            sortGroups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                group.forEach { (key, label) ->
                    val selected = sortOption == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) com.mediadeck.app.ui.theme.DeckAccentBlue.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                viewModel.setMovieSort(key)
                                if (key == "random") folderShuffleSeed = kotlin.random.Random.nextInt()
                                showSortDialog = false
                            }
                            .padding(horizontal = 14.dp)
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = com.mediadeck.app.ui.theme.DeckAccentBlue,
                                unselectedColor = com.mediadeck.app.ui.theme.DeckTextSecondary,
                            ),
                        )
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = com.mediadeck.app.ui.theme.DeckTextPrimary,
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieCard(movie: Movie, isSelected: Boolean, isMultiSelect: Boolean, onOpenMovie: (Movie) -> Unit, viewModel: MovieViewModel, settings: com.mediadeck.app.data.settings.AppSettings, onDelete: (Movie) -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val durationText = if (movie.duration > 0L) com.mediadeck.app.util.media.MediaUtils.formatDuration(movie.duration) else ""
    val progressFraction = if (movie.duration > 0L && movie.lastPlayedPosition > 0L) (movie.lastPlayedPosition.toFloat() / movie.duration.toFloat()) else 0f
    val thumbShape = RoundedCornerShape(16.dp)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(thumbShape)
                .background(com.mediadeck.app.ui.theme.DeckSurfaceVariant)
                .then(
                    if (isSelected) Modifier.border(2.dp, com.mediadeck.app.ui.theme.DeckAccentBlue, thumbShape) else Modifier
                )
                .combinedClickable(
                    onClick = { if (isMultiSelect) viewModel.toggleMovieSelection(movie.id) else onOpenMovie(movie) },
                    onLongClick = { viewModel.startMovieMultiSelect(movie.id) },
                ),
        ) {
            AsyncThumbnailImage(
                uriString = movie.uri,
                mediaId = movie.id,
                hasThumbnail = movie.hasThumbnail,
                settings = settings,
                thumbnailVariant = "movie",
                showProcessingIndicator = true,
                modifier = Modifier.fillMaxSize()
            )

            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleMovieSelection(movie.id) },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            if (durationText.isNotEmpty()) {
                Text(
                    text = durationText,
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
                        .padding(end = 8.dp, bottom = if (progressFraction > 0f) 10.dp else 6.dp)
                )
            }

            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .background(com.mediadeck.app.ui.theme.DeckAccentBlue)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = movie.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = com.mediadeck.app.ui.theme.DeckTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = com.mediadeck.app.ui.theme.DeckTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (movie.isFavorite) t("Remove from Favorites", "Hapus dari Favorit")
                                else t("Add to Favorites", "Tambah ke Favorit")
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (movie.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (movie.isFavorite) com.mediadeck.app.ui.theme.DeckBadgeRed else com.mediadeck.app.ui.theme.DeckTextSecondary,
                            )
                        },
                        onClick = {
                            showMenu = false
                            viewModel.toggleMovieFavorite(movie)
                        }
                    )
                    if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(movie.uri)) {
                        DropdownMenuItem(
                            text = { Text(t("Download", "Unduh")) },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = { showMenu = false; viewModel.downloadSmbFile(context, movie.uri, movie.title) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(t("Delete", "Hapus"), color = com.mediadeck.app.ui.theme.DeckBadgeRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = com.mediadeck.app.ui.theme.DeckBadgeRed) },
                        onClick = { showMenu = false; onDelete(movie) }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieListItem(movie: Movie, isSelected: Boolean, isMultiSelect: Boolean, onOpenMovie: (Movie) -> Unit, viewModel: MovieViewModel, settings: com.mediadeck.app.data.settings.AppSettings, onDelete: (Movie) -> Unit) {
    val context = LocalContext.current
    val subtitle = listOfNotNull(
        movie.resolution.takeIf { it.isNotBlank() },
        movie.duration.takeIf { it > 0 }?.let { com.mediadeck.app.util.media.MediaUtils.formatDuration(it) },
        formatSize(movie.size),
    ).joinToString(" · ")
    var showMenu by remember { mutableStateOf(false) }
    val progressFraction = if (movie.duration > 0L && movie.lastPlayedPosition > 0L) (movie.lastPlayedPosition.toFloat() / movie.duration.toFloat()) else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = com.mediadeck.app.ui.theme.DeckSurface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelect) viewModel.toggleMovieSelection(movie.id) else onOpenMovie(movie) },
                onLongClick = { viewModel.startMovieMultiSelect(movie.id) }
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(com.mediadeck.app.ui.theme.DeckSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncThumbnailImage(
                    uriString = movie.uri,
                    mediaId = movie.id,
                    hasThumbnail = movie.hasThumbnail,
                    settings = settings,
                    thumbnailVariant = "movie",
                    showProcessingIndicator = true,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = movie.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.mediadeck.app.ui.theme.DeckTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = com.mediadeck.app.ui.theme.DeckTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .background(com.mediadeck.app.ui.theme.DeckAccentBlue)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                IconButton(
                    onClick = { viewModel.toggleMovieFavorite(movie) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (movie.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (movie.isFavorite) com.mediadeck.app.ui.theme.DeckBadgeRed else com.mediadeck.app.ui.theme.DeckTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = com.mediadeck.app.ui.theme.DeckTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (movie.isFavorite) t("Remove from Favorites", "Hapus dari Favorit")
                                    else t("Add to Favorites", "Tambah ke Favorit")
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (movie.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (movie.isFavorite) com.mediadeck.app.ui.theme.DeckBadgeRed else com.mediadeck.app.ui.theme.DeckTextSecondary,
                                )
                            },
                            onClick = {
                                showMenu = false
                                viewModel.toggleMovieFavorite(movie)
                            }
                        )
                        if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(movie.uri)) {
                            DropdownMenuItem(
                                text = { Text(t("Download", "Unduh")) },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { showMenu = false; viewModel.downloadSmbFile(context, movie.uri, movie.title) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(t("Delete", "Hapus"), color = com.mediadeck.app.ui.theme.DeckBadgeRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = com.mediadeck.app.ui.theme.DeckBadgeRed) },
                            onClick = { showMenu = false; onDelete(movie) }
                        )
                    }
                }
            }
        }
    }
}
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var i = 0
    while (size >= 1024 && i < units.size - 1) {
        size /= 1024
        i++
    }
    return "%.1f %s".format(size, units[i])
}


