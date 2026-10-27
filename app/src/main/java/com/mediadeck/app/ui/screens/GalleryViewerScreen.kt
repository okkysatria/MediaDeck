package com.mediadeck.app.ui.screens
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.size.Size
import coil3.size.Precision
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.ZoomableMediaBox
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.util.media.MediaFormatters
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.MovieViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryViewerScreen(
    itemId: Long,
    viewModel: GalleryViewModel,
    movieViewModel: MovieViewModel,
    onBack: () -> Unit,
    onFilterTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        ScannerStateManager.setMediaActive(true)
        onDispose {
            ScannerStateManager.setMediaActive(false)
        }
    }
    val context = LocalContext.current
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val screenHeightDp = with(LocalDensity.current) { containerHeight.toDp().value.toInt() }
    val compactViewer = screenHeightDp < 500
    val items by viewModel.filteredGalleryItems.collectAsState()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.appSettings.collectAsState()
    if (settings.keepScreenOn) {
        DisposableEffect(Unit) {
            val activity = context as? android.app.Activity
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val initialIndex = remember { items.indexOfFirst { it.id == itemId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
    ) { items.size }


    var currentId by remember { mutableLongStateOf(itemId) }
    val latestItems by rememberUpdatedState(items)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            latestItems.getOrNull(page)?.let { currentId = it.id }
        }
    }
    LaunchedEffect(items) {
        val idx = items.indexOfFirst { it.id == currentId }
        if (idx >= 0) {
            if (idx != pagerState.currentPage) pagerState.scrollToPage(idx)
        } else if (items.isNotEmpty()) {

            currentId = items[pagerState.currentPage.coerceIn(0, items.lastIndex)].id
        }
    }
    var isPagerScrollEnabled by remember { mutableStateOf(true) }
    val currentIndex = pagerState.currentPage
    PreloadGalleryNeighbors(items, currentIndex)
    val toastCopied = t("URL path copied!", "Jalur URL disalin!")
    val toastSaved = t("Saved successfully!", "Disimpan!")
    val downloadOk = t("Saved to Downloads", "Disimpan ke folder Unduhan")
    val downloadFail = t("Download failed", "Gagal mengunduh")
    val downloadDenied = t("Storage permission denied", "Izin penyimpanan ditolak")
    var pendingDownload by remember { mutableStateOf<GalleryItem?>(null) }
    val performLocalDownload: (GalleryItem) -> Unit = { target ->
        coroutineScope.launch {
            val ok = withContext(Dispatchers.IO) { saveToDownloads(context, target) }
            Toast.makeText(context, if (ok) downloadOk else downloadFail, Toast.LENGTH_SHORT).show()
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingDownload
        pendingDownload = null
        if (granted && target != null) {
            performLocalDownload(target)
        } else if (!granted) {
            Toast.makeText(context, downloadDenied, Toast.LENGTH_SHORT).show()
        }
    }
    val currentItem = remember(currentIndex, items) {
        if (items.isNotEmpty() && currentIndex < items.size) items[currentIndex] else null
    }
    if (currentItem == null) {
        LaunchedEffect(Unit) {

            delay(600.milliseconds)
            onBack()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(currentIndex) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
    BackHandler {
        onBack()
    }
    var showUi by remember { mutableStateOf(value = true) }
    var isFitMode by remember(currentItem.id) { mutableStateOf(value = true) }
    var isMuted by remember { mutableStateOf(value = false) }
    var rotationDegrees by remember(currentItem.id) { mutableFloatStateOf(0f) }
    var showSidebarDetail by remember { mutableStateOf(false) }

    LaunchedEffect(currentItem.id) { isPagerScrollEnabled = true }

    BackHandler(enabled = showSidebarDetail) { showSidebarDetail = false }
    var albumNameInput by remember(currentItem.id) { mutableStateOf(currentItem.folderName) }
    var tagsInput by remember(currentItem.id) { mutableStateOf(currentItem.tags) }
    LaunchedEffect(showUi, showSidebarDetail) {
        if (showUi && !showSidebarDetail) {
            delay(4000.milliseconds)
            showUi = false
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                viewModel.deleteGalleryItem(context, currentItem)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
            title = t("Delete Media", "Hapus Media"),
            message = t("Are you sure you want to delete this file permanently?", "Apakah Anda yakin ingin menghapus file ini secara permanen?"),
        )
    }
    val currentPageOffsetFraction by remember(pagerState) {
        derivedStateOf { pagerState.currentPageOffsetFraction }
    }
    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (currentIndex > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentIndex - 1)
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (currentIndex < items.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentIndex + 1)
                                }
                            }
                            true
                        }
                        Key.Escape -> {
                            onBack()
                            true
                        }
                        Key.MoveHome -> {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                            true
                        }
                        Key.MoveEnd -> {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((items.size - 1).coerceAtLeast(0))
                            }
                            true
                        }
                        else -> false
                    }
                    else -> false
                }
            },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                userScrollEnabled = isPagerScrollEnabled,

                key = { index -> items.getOrNull(index)?.id ?: index },
            ) { page ->
                val pageItem = items.getOrNull(page)
                if (pageItem != null) {
                    val pageOffset = (pagerState.currentPage - page) + currentPageOffsetFraction
                    val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                    val pageModifier = when (settings.galleryTransition) {
                        "fade" -> Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = pageOffset * size.width
                                alpha = (1f - absOffset).coerceIn(0f, 1f)
                            }
                        "curl" -> Modifier
                            .fillMaxSize()
                            .zIndex(if (pageOffset >= 0f) 1f else 0f)
                            .graphicsLayer {
                                translationX = pageOffset * size.width
                                cameraDistance = 16f * density
                                if (pageOffset > 0f) {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationY = -90f * pageOffset.coerceIn(0f, 1f)
                                    alpha = if (pageOffset >= 1f) 0f else (1f - pageOffset * 0.7f).coerceIn(0f, 1f)
                                } else {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    rotationY = 0f
                                    alpha = if (pageOffset <= -1f) 0f else 1f
                                }
                            }
                        else -> Modifier.fillMaxSize()
                    }
                    Box(modifier = pageModifier) {
                        GalleryPageItem(
                            item = pageItem,
                            isFitMode = isFitMode,
                            isMuted = isMuted,
                            rotation = if (page == currentIndex) rotationDegrees else 0f,
                            isCurrentPage = page == pagerState.settledPage,
                            onToggleUi = { showUi = !showUi },
                            onSwipeUp = {
                                showSidebarDetail = true
                                showUi = true
                            },
                            onSwipeDown = {
                                if (showSidebarDetail) {
                                    showSidebarDetail = false
                                } else {
                                    onBack()
                                }
                            },
                            onScaleChange = { scaleValue ->
                                isPagerScrollEnabled = scaleValue <= 1.05f
                            },
                            onOverSwipeHorizontal = { deltaValue ->
                                coroutineScope.launch {
                                    pagerState.scrollBy(deltaValue)
                                }
                            },
                            onDragStopped = {
                                coroutineScope.launch {
                                    if (items.isEmpty()) return@launch
                                    val targetPage = if (pagerState.currentPageOffsetFraction > 0.5f) {
                                        pagerState.currentPage + 1
                                    } else if (pagerState.currentPageOffsetFraction < -0.5f) {
                                        pagerState.currentPage - 1
                                    } else {
                                        pagerState.currentPage
                                    }
                                    pagerState.animateScrollToPage(targetPage.coerceIn(0, items.size - 1))
                                }
                            }
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            currentItem.name.substringBeforeLast('.', currentItem.name),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleGalleryFavorite(currentItem) }) {
                            Icon(
                                if (currentItem.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = t("Favorite", "Favorit"),
                                tint = if (currentItem.isFavorite) Color.Red else Color.White,
                            )
                        }
                        IconButton(onClick = { showSidebarDetail = !showSidebarDetail }) {
                            Icon(Icons.Outlined.Info, contentDescription = t("Info & Album Details", "Info & Album"))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = t("Delete", "Hapus"))
                        }
                    },
                    expandedHeight = 56.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            }
            AnimatedVisibility(
                visible = showUi && !showSidebarDetail,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 6.dp, top = if (compactViewer) 6.dp else 12.dp, bottom = if (compactViewer) 6.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${currentIndex + 1}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(" / ${items.size}", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                                }
                            }
                            Row {
                                val isVideo = currentItem.mimeType.contains("video", ignoreCase = true)
                                if (!isVideo) {
                                    IconButton(onClick = { rotationDegrees += 90f }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = t("Rotate Right", "Putar Kanan"))
                                    }
                                    IconButton(onClick = { rotationDegrees -= 90f }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = t("Rotate Left", "Putar Kiri"))
                                    }
                                    IconButton(onClick = { isFitMode = !isFitMode }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                                        Icon(if (isFitMode) Icons.Default.Fullscreen else Icons.Default.PictureInPicture, contentDescription = t("Fit/Original Size", "Fit/Original"))
                                    }
                                } else {
                                    IconButton(onClick = { isMuted = !isMuted }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                                        Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = t("Sound Toggle", "Suara"))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (MediaUtils.isSmbUri(currentItem.uri)) {
                                            movieViewModel.downloadSmbFile(context, currentItem.uri, currentItem.name)
                                        } else {
                                            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                            if (needsPermission) {
                                                pendingDownload = currentItem
                                                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                            } else {
                                                performLocalDownload(currentItem)
                                            }
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = t("Download", "Unduh"))
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .align(Alignment.Center),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentIndex > 0) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentIndex - 1)
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("viewer_prev_button"),
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = t("Previous", "Sebelumnya"), tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    if (currentIndex < items.size - 1) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentIndex + 1)
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("viewer_next_button"),
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = t("Next", "Berikutnya"), tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = showSidebarDetail,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showSidebarDetail = false },
                )
            }
            AnimatedVisibility(
                visible = showSidebarDetail,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                val sizeFormatted = remember(currentItem.size) { MediaFormatters.fileSize(currentItem.size) }
                val dateText = remember(currentItem.dateAdded) { MediaFormatters.dateTime(currentItem.dateAdded, unknownValue = "-") }
                val tagList = remember(currentItem.tags) {
                    currentItem.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                }
                val maxSheetHeight = (screenHeightDp * (if (compactViewer) 0.96f else 0.92f)).dp
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .pointerInput(Unit) {
                            var totalDragY = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDragY = 0f },
                                onDragEnd = {
                                    if (totalDragY > 100f) {
                                        showSidebarDetail = false
                                    }
                                },
                                onDragCancel = { totalDragY = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragY += dragAmount
                                },
                            )
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                t("Details & Album", "Detail & Album"),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showSidebarDetail = false }) {
                                Icon(Icons.Default.Close, contentDescription = t("Close", "Tutup"))
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GalleryDetailRow(
                                        t("File name", "Nama file"),
                                        currentItem.name.substringBeforeLast('.', currentItem.name),
                                    )
                                    GalleryDetailRow(t("Format", "Format"), currentItem.mimeType)
                                    GalleryDetailRow(t("Size", "Ukuran"), sizeFormatted)
                                    GalleryDetailRow(
                                        t("Dimension", "Dimensi"),
                                        if (currentItem.width > 0 && currentItem.height > 0) {
                                            "${currentItem.width} × ${currentItem.height}"
                                        } else {
                                            t("Loading...", "Memuat...")
                                        },
                                    )
                                    if (currentItem.mimeType.contains("video", true) && currentItem.duration > 0) {
                                        GalleryDetailRow(
                                            t("Duration", "Durasi"),
                                            MediaUtils.formatDuration(currentItem.duration),
                                        )
                                    }
                                    GalleryDetailRow(t("Added on", "Ditambahkan"), dateText)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentItem.uri,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("URL", currentItem.uri)))
                                            }
                                            Toast.makeText(context, toastCopied, Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = t("Copy", "Salin"), modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            OutlinedTextField(
                                value = albumNameInput,
                                onValueChange = { albumNameInput = it },
                                label = { Text(t("Album", "Album")) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 17.sp),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = tagsInput,
                                onValueChange = { tagsInput = it },
                                label = { Text(t("Tags (comma)", "Tags (koma)")) },
                                placeholder = { Text(t("Tags (comma)", "Tags (koma)")) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 17.sp),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1.2f),
                            )
                            IconButton(
                                onClick = {
                                    viewModel.updateGalleryItemDetails(
                                        currentItem,
                                        newFolderName = albumNameInput,
                                        newTags = tagsInput,
                                    )
                                    Toast.makeText(context, toastSaved, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                            ) {
                                Icon(Icons.Default.Save, contentDescription = t("Save", "Simpan"))
                            }
                        }
                        if (tagList.isNotEmpty()) {
                            Text(
                                t("Click Tag to Filter:", "Klik Tag untuk Menyaring:"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(tagList, key = { it }) { tag ->
                                    AssistChip(
                                        onClick = { onFilterTag(tag) },
                                        label = { Text("#$tag", fontSize = 11.sp) },
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
@androidx.media3.common.util.UnstableApi
@Composable
fun GalleryPageItem(
    item: GalleryItem,
    isFitMode: Boolean,
    isMuted: Boolean,
    rotation: Float,
    onToggleUi: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
    onScaleChange: (Float) -> Unit = {},
    onOverSwipeHorizontal: (Float) -> Unit = {},
    onDragStopped: () -> Unit = {},
    isCurrentPage: Boolean = true,
) {
    val isVideo = item.mimeType.contains("video", ignoreCase = true)
    var scale by remember { mutableFloatStateOf(1f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        if (scale <= 1.05f) {
            totalDragY += delta
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = { totalDragY = 0f },
                onDragStopped = { _ ->
                    if (totalDragY < -120f) {
                        onSwipeUp()
                    } else if (totalDragY > 120f) {
                        onSwipeDown()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo) {


            if (isCurrentPage) {
                VideoPlayerPage(item = item, isMuted = isMuted, onToggleUi = onToggleUi)
            }
        } else {
            val context = LocalContext.current
            val isSmb = remember(item.uri) { MediaUtils.isSmbUri(item.uri) }
            val originalRequest = remember(item.uri, context) {
                galleryOriginalRequest(context, item.uri)
            }
            var intrinsicSize by remember(item.id) { mutableStateOf(androidx.compose.ui.geometry.Size.Unspecified) }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()

                val normRotation = (kotlin.math.abs(rotation.toInt()) % 360)
                val isRotated90 = normRotation == 90 || normRotation == 270

                val rotationScale = remember(rotation, isFitMode, intrinsicSize, containerWidth, containerHeight) {
                    if (isRotated90 && intrinsicSize.width > 0f && intrinsicSize.height > 0f && containerWidth > 0f && containerHeight > 0f) {
                        val imgWidth = intrinsicSize.width
                        val imgHeight = intrinsicSize.height

                        val (w, h) = if (isFitMode) {
                            val scaleFit = minOf(containerWidth / imgWidth, containerHeight / imgHeight)
                            (imgWidth * scaleFit) to (imgHeight * scaleFit)
                        } else {
                            imgWidth to imgHeight
                        }

                        minOf(1f, containerWidth / h, containerHeight / w)
                    } else {
                        1f
                    }
                }

                ZoomableMediaBox(
                    onTap = onToggleUi,
                    onScaleChange = { s ->
                        scale = s
                        onScaleChange(s)
                    },
                    onOverSwipeHorizontal = onOverSwipeHorizontal,
                    onDragStopped = onDragStopped,
                    modifier = Modifier.fillMaxSize(),
                ) { boxScale, boxOffset ->
                    val finalScale = boxScale * rotationScale
                    SubcomposeAsyncImage(
                        model = originalRequest,
                        contentDescription = item.name,
                        contentScale = if (isFitMode) ContentScale.Fit else ContentScale.None,
                        onSuccess = { state ->
                            val pSize = state.painter.intrinsicSize
                            if (pSize.width > 0f && pSize.height > 0f) {
                                intrinsicSize = pSize
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = finalScale,
                                scaleY = finalScale,
                                translationX = boxOffset.x,
                                translationY = boxOffset.y,
                                rotationZ = rotation,
                            ),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = t("Failed to Load Image", "Gagal Memuat Gambar"),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(56.dp),
                                )
                                Text(
                                    text = t("Failed to Load Image", "Gagal Memuat Gambar"),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = if (isSmb) {
                                        t("SMB connection lost or image file is no longer accessible on the Samba Server.", "Koneksi SMB terputus atau berkas gambar tidak lagi dapat diakses di Server Samba.")
                                    } else {
                                        t("The image could not be opened. The file may be corrupted, moved, or deleted.", "Gambar tidak dapat dibuka. Berkas mungkin rusak, dipindahkan, atau dihapus.")
                                    },
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp,
                                )
                                if (isSmb) {
                                    Text(
                                        text = t("Solution:\n1. Ensure the device is connected to the same local Wifi as the server.\n2. Check your Samba Server status.", "Solusi:\n1. Pastikan Perangkat terhubung ke Wifi lokal yang sama dengan server.\n2. Periksa status Server Samba Anda."),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
}

private fun android.content.Context.findLifecycleOwner(): androidx.lifecycle.LifecycleOwner? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is androidx.lifecycle.LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun VideoPlayerPage(
    item: GalleryItem,
    isMuted: Boolean,
    onToggleUi: () -> Unit,
) {
    val context = LocalContext.current
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        }
    }
    LaunchedEffect(item.uri) {
        try {
            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)

        val owner = context.findLifecycleOwner()
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlaying = exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> if (wasPlaying) exoPlayer.play()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(videoAspectRatio),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("video_player_view_${item.id}"),
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 4000
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }

                view.setOnClickListener { onToggleUi() }
            },
            onRelease = { view ->
                view.player = null
            }
        )
    }
}
@Composable
private fun PreloadGalleryNeighbors(items: List<GalleryItem>, currentIndex: Int) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val neighborUris = remember(items, currentIndex) {
        (currentIndex - 1..currentIndex + 1)
            .filter { it in items.indices && it != currentIndex }
            .mapNotNull { items.getOrNull(it) }
            .filterNot { it.mimeType.contains("video", ignoreCase = true) }
            .map { it.uri }
    }
    LaunchedEffect(imageLoader, neighborUris) {

        delay(300.milliseconds)
        neighborUris.forEach { uri ->
            imageLoader.enqueue(galleryOriginalRequest(context, uri))
        }
    }
}


private const val MAX_VIEWER_PIXELS = 3072

private fun galleryOriginalRequest(context: android.content.Context, uri: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(uri)
        .size(Size(MAX_VIEWER_PIXELS, MAX_VIEWER_PIXELS))
        .precision(Precision.INEXACT)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

@Composable
private fun GalleryDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
