package com.mediadeck.app.ui.screens
import android.Manifest
import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.mediadeck.app.ui.components.MediaInfoBottomSheet
import com.mediadeck.app.ui.components.galleryInfoRows
import com.mediadeck.app.ui.components.ZoomableMediaBox
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.MovieViewModel
import com.mediadeck.app.ui.theme.DeckDivider
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
    val videoState = rememberGalleryVideoState()
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
    val onDownload: () -> Unit = {
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
    }
    val isCurrentVideo = currentItem.mimeType.contains("video", ignoreCase = true)
    val goToPage: (Int) -> Unit = { target ->
        coroutineScope.launch { pagerState.animateScrollToPage(target) }
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
    var uiActivityTick by remember { mutableIntStateOf(0) }
    val settledVideo = items.getOrNull(pagerState.settledPage)
        ?.takeIf { it.mimeType.contains("video", ignoreCase = true) }
    val videoControlsEnabled = isCurrentVideo && settledVideo?.id == currentItem.id
    LaunchedEffect(settledVideo?.id, settledVideo?.uri) {
        val target = settledVideo
        if (target != null) videoState.load(target.uri) else videoState.unload()
    }
    LaunchedEffect(isMuted) { videoState.setMuted(isMuted) }
    LaunchedEffect(videoControlsEnabled, showUi) {
        if (videoControlsEnabled && showUi) {
            while (true) {
                videoState.syncProgress()
                delay(200.milliseconds)
            }
        }
    }
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner, videoState) {
        var resumeOnReturn = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (videoState.pauseForBackground()) resumeOnReturn = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (resumeOnReturn) {
                        resumeOnReturn = false
                        videoState.resume()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // UI tidak boleh hilang saat video dijeda, di-seek, atau error; timer di-reset tiap interaksi.
    val keepUiVisible = showSidebarDetail ||
        (isCurrentVideo && (videoState.isScrubbing || !videoState.playWhenReady || videoState.hasError))
    LaunchedEffect(showUi, keepUiVisible, uiActivityTick) {
        if (showUi && !keepUiVisible) {
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
                        Key.Spacebar -> {
                            if (isCurrentVideo) {
                                videoState.togglePlay()
                                uiActivityTick++
                                true
                            } else {
                                false
                            }
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
                            videoState = videoState,
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
                    .background(Color.Black.copy(alpha = 0.55f))
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
                        containerColor = Color.Transparent,
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
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                if (isCurrentVideo) {
                    VideoBottomControls(
                        state = videoState,
                        enabled = videoControlsEnabled,
                        isMuted = isMuted,
                        currentIndex = currentIndex,
                        totalCount = items.size,
                        hasPrevious = currentIndex > 0,
                        hasNext = currentIndex < items.size - 1,
                        compact = compactViewer,
                        onPrevious = { goToPage(currentIndex - 1) },
                        onNext = { goToPage(currentIndex + 1) },
                        onToggleMute = { isMuted = !isMuted },
                        onDownload = onDownload,
                        onInteraction = { uiActivityTick++ },
                    )
                } else {
                    PhotoBottomControls(
                        currentIndex = currentIndex,
                        totalCount = items.size,
                        isFitMode = isFitMode,
                        compact = compactViewer,
                        onRotateLeft = { rotationDegrees -= 90f },
                        onRotateRight = { rotationDegrees += 90f },
                        onToggleFit = { isFitMode = !isFitMode },
                        onDownload = onDownload,
                        onInteraction = { uiActivityTick++ },
                    )
                }
            }

            if (showSidebarDetail) {
                MediaInfoBottomSheet(
                    title = currentItem.name,
                    rows = galleryInfoRows(currentItem),
                    onDismiss = { showSidebarDetail = false },
                    closeLabel = t("Close", "Tutup"),
                    extraContent = {
                        HorizontalDivider(color = DeckDivider)
                        Text(
                            t("ALBUM & TAGS", "ALBUM & TAG"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
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
                                textStyle = TextStyle(fontSize = 16.sp),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = tagsInput,
                                onValueChange = { tagsInput = it },
                                label = { Text(t("Tags (comma)", "Tags (koma)")) },
                                placeholder = { Text(t("Tags (comma)", "Tags (koma)")) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 16.sp),
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
                        val tagList = remember(currentItem.tags) {
                            currentItem.tags.split(",").map(String::trim).filter(String::isNotEmpty).distinct()
                        }
                        if (tagList.isNotEmpty()) {
                            Text(
                                t("Click Tag to Filter:", "Klik Tag untuk Menyaring:"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                    },
                )
            }
        }
    }
}
@androidx.media3.common.util.UnstableApi
@Composable
fun GalleryPageItem(
    item: GalleryItem,
    isFitMode: Boolean,
    videoState: GalleryVideoState,
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = isVideo,
                onClick = onToggleUi,
            )
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
                VideoPlayerPage(item = item, state = videoState)
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
    state: GalleryVideoState,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("video_player_view_${item.id}"),
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx)
                    .inflate(com.mediadeck.app.R.layout.exo_player_texture_view, android.widget.FrameLayout(ctx), false)
                    as androidx.media3.ui.PlayerView).apply {
                    player = state.player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                if (view.player !== state.player) {
                    view.player = state.player
                }
            },
            onRelease = { view ->
                view.player = null
            },
        )
        if (state.hasError) {
            VideoErrorOverlay(onRetry = { state.retry() })
        } else if (state.isBuffering) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun VideoErrorOverlay(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(
            t("This video cannot be played", "Video tidak dapat diputar"),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(t("Retry", "Coba lagi"))
        }
    }
}

/**
 * Satu ExoPlayer untuk seluruh viewer (dibuat lazy, hanya saat ada video).
 * State dipisah dari UI supaya kontrol bisa diletakkan di bawah layar,
 * di luar halaman pager.
 */
@Stable
class GalleryVideoState(private val context: Context) {
    private var currentUri: String? = null
    private var muted = false

    var isBuffering by mutableStateOf(false)
        private set
    var playWhenReady by mutableStateOf(false)
        private set
    var hasError by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var isScrubbing by mutableStateOf(false)
        private set
    var scrubMs by mutableLongStateOf(0L)
        private set

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            this@GalleryVideoState.playWhenReady = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                val d = player.duration
                if (d > 0L) durationMs = d
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            hasError = true
            isBuffering = false
        }
    }

    private val playerDelegate = lazy {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = if (muted) 0f else 1f
            addListener(listener)
        }
    }
    val player: ExoPlayer get() = playerDelegate.value

    private fun resetProgress() {
        positionMs = 0L
        durationMs = 0L
        bufferedMs = 0L
        isScrubbing = false
        scrubMs = 0L
    }

    fun load(uri: String) {
        currentUri = uri
        resetProgress()
        hasError = false
        isBuffering = true
        try {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
            isBuffering = false
        }
    }

    fun unload() {
        currentUri = null
        if (playerDelegate.isInitialized()) {
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
        }
        resetProgress()
        isBuffering = false
        hasError = false
        playWhenReady = false
    }

    fun retry() {
        currentUri?.let { load(it) }
    }

    fun togglePlay() {
        if (!playerDelegate.isInitialized()) return
        if (hasError) {
            retry()
            return
        }
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (playerDelegate.isInitialized()) player.volume = if (value) 0f else 1f
    }

    /** @return true jika video sedang/ingin diputar sebelum dijeda. */
    fun pauseForBackground(): Boolean {
        if (!playerDelegate.isInitialized()) return false
        val wasPlaying = player.playWhenReady
        if (wasPlaying) player.pause()
        return wasPlaying
    }

    fun resume() {
        if (playerDelegate.isInitialized()) player.play()
    }

    fun syncProgress() {
        if (!playerDelegate.isInitialized()) return
        val p = player
        if (!isScrubbing) positionMs = p.currentPosition.coerceAtLeast(0L)
        bufferedMs = p.bufferedPosition.coerceAtLeast(0L)
        val d = p.duration
        if (d > 0L && d != durationMs) durationMs = d
    }

    fun beginScrub() {
        isScrubbing = true
        scrubMs = positionMs
    }

    fun scrub(ms: Long) {
        scrubMs = ms
    }

    fun endScrub(ms: Long) {
        seekTo(ms)
        isScrubbing = false
    }

    fun cancelScrub() {
        isScrubbing = false
    }

    fun seekTo(ms: Long) {
        if (!playerDelegate.isInitialized()) return
        val max = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        val target = ms.coerceIn(0L, max)
        player.seekTo(target)
        positionMs = target
    }

    fun release() {
        if (playerDelegate.isInitialized()) player.release()
    }
}

@Composable
fun rememberGalleryVideoState(): GalleryVideoState {
    val context = LocalContext.current
    val state = remember { GalleryVideoState(context.applicationContext) }
    DisposableEffect(state) {
        onDispose { state.release() }
    }
    return state
}

private val SeekBarPadding = 10.dp

private fun seekFraction(x: Float, width: Float, pad: Float): Float {
    val track = width - 2 * pad
    return if (track <= 0f) 0f else ((x - pad) / track).coerceIn(0f, 1f)
}

private fun formatVideoTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
    }
}

/** Seek bar buatan sendiri: tipis, ada buffer, thumb membesar saat digeser, area sentuh tinggi. */
@Composable
private fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onScrubCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragging = dragFraction != null
    val progress = dragFraction
        ?: if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction =
        if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackHeight by animateDpAsState(if (dragging) 6.dp else 4.dp, label = "seek_track")
    val thumbRadius by animateDpAsState(
        if (!enabled) 0.dp else if (dragging) 10.dp else 6.dp,
        label = "seek_thumb",
    )
    val latestDuration by rememberUpdatedState(durationMs)
    val latestStart by rememberUpdatedState(onScrubStart)
    val latestScrub by rememberUpdatedState(onScrub)
    val latestEnd by rememberUpdatedState(onScrubEnd)
    val latestCancel by rememberUpdatedState(onScrubCancel)

    Canvas(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = SeekBarPadding.toPx()
                detectTapGestures { offset ->
                    val f = seekFraction(offset.x, size.width.toFloat(), pad)
                    latestEnd((f * latestDuration).toLong())
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = SeekBarPadding.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val f = seekFraction(offset.x, size.width.toFloat(), pad)
                        dragFraction = f
                        latestStart()
                        latestScrub((f * latestDuration).toLong())
                    },
                    onDragEnd = {
                        dragFraction?.let { latestEnd((it * latestDuration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = {
                        dragFraction = null
                        latestCancel()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val f = seekFraction(change.position.x, size.width.toFloat(), pad)
                        dragFraction = f
                        latestScrub((f * latestDuration).toLong())
                    },
                )
            },
    ) {
        val pad = SeekBarPadding.toPx()
        val trackWidth = size.width - 2 * pad
        val centerY = size.height / 2f
        val trackPx = trackHeight.toPx()
        val top = centerY - trackPx / 2f
        val corner = CornerRadius(trackPx / 2f, trackPx / 2f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(pad, top),
            size = androidx.compose.ui.geometry.Size(trackWidth, trackPx),
            cornerRadius = corner,
        )
        if (bufferedFraction > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.4f),
                topLeft = Offset(pad, top),
                size = androidx.compose.ui.geometry.Size(trackWidth * bufferedFraction, trackPx),
                cornerRadius = corner,
            )
        }
        if (progress > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(pad, top),
                size = androidx.compose.ui.geometry.Size(trackWidth * progress, trackPx),
                cornerRadius = corner,
            )
        }
        if (thumbRadius > 0.dp) {
            drawCircle(
                color = activeColor,
                radius = thumbRadius.toPx(),
                center = Offset(pad + trackWidth * progress, centerY),
            )
        }
    }
}

/**
 * Semua kontrol video ada di sini (bawah layar):
 * baris 1 = durasi sekarang | seek bar | durasi total
 * baris 2 = counter | prev - play/pause - next | mute, unduh
 */
@Composable
private fun VideoBottomControls(
    state: GalleryVideoState,
    enabled: Boolean,
    isMuted: Boolean,
    currentIndex: Int,
    totalCount: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    compact: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleMute: () -> Unit,
    onDownload: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = when {
        !enabled -> 0L
        state.isScrubbing -> state.scrubMs
        else -> state.positionMs
    }
    val duration = if (enabled) state.durationMs else 0L
    val buffered = if (enabled) state.bufferedMs else 0L
    val seekEnabled = enabled && duration > 0L && !state.hasError
    val sideButton = if (compact) 40.dp else 44.dp
    val playButton = if (compact) 48.dp else 56.dp
    val timeWidth = if (duration >= 3_600_000L) 52.dp else 38.dp
    val timeStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val sideColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.3f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.88f),
                    ),
                ),
            )
            .pointerInput(Unit) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (compact) 12.dp else 28.dp,
                    bottom = if (compact) 2.dp else 8.dp,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatVideoTime(position),
                    style = timeStyle,
                    maxLines = 1,
                    modifier = Modifier.width(timeWidth),
                )
                VideoSeekBar(
                    positionMs = position,
                    durationMs = duration,
                    bufferedMs = buffered,
                    enabled = seekEnabled,
                    onScrubStart = {
                        onInteraction()
                        state.beginScrub()
                    },
                    onScrub = { state.scrub(it) },
                    onScrubEnd = {
                        onInteraction()
                        state.endScrub(it)
                    },
                    onScrubCancel = { state.cancelScrub() },
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 28.dp else 36.dp),
                )
                Text(
                    if (duration > 0L) formatVideoTime(duration) else "--:--",
                    style = timeStyle,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(timeWidth),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.14f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${currentIndex + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(" / $totalCount", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onInteraction()
                            onPrevious()
                        },
                        enabled = hasPrevious,
                        colors = sideColors,
                        modifier = Modifier.size(sideButton).testTag("viewer_prev_button"),
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = t("Previous", "Sebelumnya"), modifier = Modifier.size(28.dp))
                    }
                    IconButton(
                        onClick = {
                            onInteraction()
                            state.togglePlay()
                        },
                        enabled = enabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(playButton),
                    ) {
                        val showPause = state.playWhenReady && !state.hasError
                        Icon(
                            if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (showPause) t("Pause", "Jeda") else t("Play", "Putar"),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            onInteraction()
                            onNext()
                        },
                        enabled = hasNext,
                        colors = sideColors,
                        modifier = Modifier.size(sideButton).testTag("viewer_next_button"),
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = t("Next", "Berikutnya"), modifier = Modifier.size(28.dp))
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            onInteraction()
                            onToggleMute()
                        },
                        colors = sideColors,
                        modifier = Modifier.size(sideButton),
                    ) {
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = t("Sound Toggle", "Suara"),
                        )
                    }
                    IconButton(
                        onClick = {
                            onInteraction()
                            onDownload()
                        },
                        colors = sideColors,
                        modifier = Modifier.size(sideButton),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = t("Download", "Unduh"))
                    }
                }
            }
        }
    }
}
/** Kontrol foto berada dalam satu baris di bawah, mengikuti posisi counter dan aksi viewer. */
@Composable
private fun PhotoBottomControls(
    currentIndex: Int,
    totalCount: Int,
    isFitMode: Boolean,
    compact: Boolean,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onToggleFit: () -> Unit,
    onDownload: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideButton = if (compact) 40.dp else 44.dp
    val toolColors = IconButtonDefaults.iconButtonColors(
        containerColor = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.88f),
                    ),
                ),
            )
            .pointerInput(Unit) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (compact) 8.dp else 12.dp,
                    bottom = if (compact) 2.dp else 6.dp,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.14f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${currentIndex + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(" / $totalCount", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onInteraction(); onRotateLeft() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        modifier = Modifier.size(sideButton),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = t("Rotate Left", "Putar Kiri"))
                    }
                    IconButton(
                        onClick = { onInteraction(); onRotateRight() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        modifier = Modifier.size(sideButton),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = t("Rotate Right", "Putar Kanan"))
                    }
                    IconButton(
                        onClick = { onInteraction(); onToggleFit() },
                        colors = toolColors,
                        modifier = Modifier.size(sideButton),
                    ) {
                        Icon(
                            if (isFitMode) Icons.Default.Fullscreen else Icons.Default.PictureInPicture,
                            contentDescription = t("Fit/Original Size", "Fit/Original"),
                        )
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onInteraction(); onDownload() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        modifier = Modifier.size(sideButton),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = t("Download", "Unduh"))
                    }
                }
            }
        }
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
