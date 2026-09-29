package com.mediadeck.app.ui.screens
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import coil3.request.crossfade
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.ZoomableMediaBox
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.ComicViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    viewModel: ComicViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeComic by viewModel.activeComic.collectAsState()
    val pages by viewModel.activeComicPages.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val comicLoadError by viewModel.comicLoadError.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(Unit) {
        ScannerStateManager.setMediaActive(true)
        onDispose {
            ScannerStateManager.setMediaActive(false)
        }
    }
    if (settings.keepScreenOn) {
        DisposableEffect(Unit) {
            val activity = context as? android.app.Activity
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    if (activeComic == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    var readerMode by remember { mutableStateOf(settings.defaultReaderMode) }
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val isCompactDevice = configuration.smallestScreenWidthDp < 600
    LaunchedEffect(activity, readerMode, isCompactDevice) {
        if (activity != null) {
            activity.requestedOrientation = if (readerMode == "horizontal_double" && isCompactDevice) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                initialOrientation
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) activity.requestedOrientation = initialOrientation
        }
    }
    var pageSortBy by remember { mutableStateOf("filename") }
    var reversePages by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(!settings.autoHideReaderUi) }
    BackHandler {
        onClose()
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val processedPages = remember(pages, pageSortBy, reversePages) {
        val sorted = when (pageSortBy) {
            "filename" -> pages
            else -> pages.sortedBy { it.pageIndex }
        }
        if (reversePages) sorted.reversed() else sorted
    }
    var pendingVolumeTarget by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(activeComic?.currentPage) {
        if (pendingVolumeTarget != null && activeComic?.currentPage == pendingVolumeTarget!! + 1) {
            pendingVolumeTarget = null
        }
    }
    fun pageForVolumeStep(step: Int): ComicPage? {
        val baseSlot = pendingVolumeTarget ?: ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0)
        val currentIndex = processedPages.indexOfFirst { it.pageIndex == baseSlot }
        val currentSlot = currentIndex.takeIf { it >= 0 } ?: 0
        if (readerMode != "horizontal_double") return processedPages.getOrNull(currentSlot + step)
        val spreadCount = processedPages.size / 2 + 1
        val targetSpread = bookSpreadForSlot(currentSlot) + step
        if (targetSpread !in 0 until spreadCount) return null
        return processedPages.getOrNull(rightPageSlotForSpread(targetSpread))
            ?: processedPages.getOrNull(leftPageSlotForSpread(targetSpread))
    }
    var showSidebar by remember { mutableStateOf(false) }
    var jumpToPage by remember { mutableStateOf<Int?>(null) }
    var animateJumpToPage by remember { mutableStateOf(false) }
    val sidebarListState = rememberLazyListState()
    LaunchedEffect(showSidebar, activeComic?.currentPage) {
        if (showSidebar) {
            val currentSlot = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0)
            val targetIdx = processedPages.indexOfFirst { it.pageIndex == currentSlot }
            if (targetIdx >= 0 && targetIdx < processedPages.size) {
                sidebarListState.scrollToItem(targetIdx)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (settings.readerVolumeKeysNavigation && keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            pageForVolumeStep(1)?.let { page ->
                                pendingVolumeTarget = page.pageIndex
                                animateJumpToPage = readerMode == "horizontal_single" || readerMode == "horizontal_double"
                                if (!animateJumpToPage) viewModel.updateReadingProgress(page.pageIndex + 1)
                                jumpToPage = page.pageIndex
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            pageForVolumeStep(-1)?.let { page ->
                                pendingVolumeTarget = page.pageIndex
                                animateJumpToPage = readerMode == "horizontal_single" || readerMode == "horizontal_double"
                                if (!animateJumpToPage) viewModel.updateReadingProgress(page.pageIndex + 1)
                                jumpToPage = page.pageIndex
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        if (processedPages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (comicLoadError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f).padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)),
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text("Gagal Membuka Komik", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(comicLoadError ?: "", fontSize = 12.sp, textAlign = TextAlign.Center)
                            Button(onClick = { activeComic?.let { viewModel.openComic(context, it) } }) { Text("Coba Lagi") }
                        }
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            val onPageTap = { showBars = !showBars }
            when (readerMode) {
                "vertical" -> VerticalReader(
                    pages = processedPages,
                    initialPage = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0),
                    initialOffset = activeComic?.scrollOffset ?: 0,
                    innerPadding = PaddingValues(0.dp),
                    onProgressUpdate = { idx, offset -> viewModel.updateReadingProgress(idx + 1, offset) },
                    onTap = onPageTap,
                    jumpToPage = jumpToPage,
                    onJumpHandled = { jumpToPage = null; animateJumpToPage = false },
                    settings = settings,
                )
                "horizontal_single" -> HorizontalSingleReader(
                    pages = processedPages,
                    initialPage = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0),
                    innerPadding = PaddingValues(0.dp),
                    onProgressUpdate = { idx -> viewModel.updateReadingProgress(idx + 1) },
                    onTap = onPageTap,
                    jumpToPage = jumpToPage,
                    pageTransition = settings.comicPageTransition,
                    animateJumpToPage = animateJumpToPage,
                    onJumpHandled = { jumpToPage = null; animateJumpToPage = false },
                )
                "horizontal_double" -> HorizontalDoubleReader(
                    pages = processedPages,
                    initialPage = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0),
                    innerPadding = PaddingValues(0.dp),
                    onProgressUpdate = { idx -> viewModel.updateReadingProgress(idx + 1) },
                    onTap = onPageTap,
                    jumpToPage = jumpToPage,
                    pageTransition = settings.comicPageTransition,
                    animateJumpToPage = animateJumpToPage,
                    onJumpHandled = { jumpToPage = null; animateJumpToPage = false },
                )
            }
        }
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(
                        activeComic?.title ?: t("READ COMIC", "BACA KOMIK"),
                        fontSize = 18.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.basicMarquee()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Close", "Tutup"), tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { activeComic?.let { viewModel.toggleComicReadLater(it) } }) {
                        Icon(
                            if (activeComic?.isReadLater == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            null, tint = if (activeComic?.isReadLater == true) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(onClick = { activeComic?.let { viewModel.toggleComicFavorite(it) } }) {
                        Icon(
                            if (activeComic?.isFavorite == true) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                            null, tint = if (activeComic?.isFavorite == true) Color.Red else Color.White,
                        )
                    }
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(t("Filename", "Nama File")) },
                                leadingIcon = { RadioButton(selected = pageSortBy == "filename", onClick = null) },
                                onClick = { pageSortBy = "filename"; showSortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(t("Index", "Indeks")) },
                                leadingIcon = { RadioButton(selected = pageSortBy == "index", onClick = null) },
                                onClick = { pageSortBy = "index"; showSortMenu = false },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(t("Reverse", "Terbalik")) },
                                leadingIcon = { Checkbox(checked = reversePages, onCheckedChange = null) },
                                onClick = { reversePages = !reversePages; showSortMenu = false },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(t("Reset Progress", "Reset Progres"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    activeComic?.let { viewModel.clearComicHistory(it) }
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showSidebar = !showSidebar }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            )
        }
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.76f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${(activeComic?.currentPage ?: 0).coerceAtLeast(1)} / ${(activeComic?.totalPages ?: 0).coerceAtLeast(1)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f),
                    )
                    val readerModes = listOf(
                        Triple("vertical", Icons.Default.FormatLineSpacing, t("Vertical", "Vertikal")),
                        Triple("horizontal_single", Icons.Default.Description, t("Single page", "Satu halaman")),
                        Triple("horizontal_double", Icons.Default.AutoStories, t("Two pages", "Dua halaman")),
                    )
                    SingleChoiceSegmentedButtonRow {
                        readerModes.forEachIndexed { index, (mode, icon, label) ->
                            SegmentedButton(
                                selected = readerMode == mode,
                                onClick = { readerMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = readerModes.size),
                            ) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (showSidebar) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showSidebar = false })
        }
        AnimatedVisibility(
            visible = showSidebar,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.82f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.96f))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column {
                Text(t("Page Directory", "Daftar Halaman"), color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                LazyColumn(state = sidebarListState, modifier = Modifier.weight(1f)) {
                    items(processedPages) { page ->
                        val isCurrent = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0) == page.pageIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    pendingVolumeTarget = null
                                    animateJumpToPage = false
                                    viewModel.updateReadingProgress(page.pageIndex + 1)
                                    jumpToPage = page.pageIndex
                                    showSidebar = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = Uri.parse(page.pageUri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                t("p.${page.pageIndex + 1}", "hal.${page.pageIndex + 1}"),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun VerticalReader(
    pages: List<ComicPage>,
    initialPage: Int,
    initialOffset: Int,
    innerPadding: PaddingValues,
    onProgressUpdate: (Int, Int) -> Unit,
    onTap: () -> Unit,
    jumpToPage: Int?,
    onJumpHandled: () -> Unit,
    settings: AppSettings,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isInitialPositionRestored by remember(pages) { mutableStateOf(false) }
    LaunchedEffect(pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        if (initialPage > 0 || initialOffset > 0) {
            val idx = pages.indexOfFirst { p -> p.pageIndex == initialPage }
            if (idx >= 0) listState.scrollToItem(idx, initialOffset)
        }
        isInitialPositionRestored = true
    }
    LaunchedEffect(jumpToPage) {
        jumpToPage?.let {
            val idx = pages.indexOfFirst { p -> p.pageIndex == it }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
                onProgressUpdate(pages[idx].pageIndex, 0)
            }
            onJumpHandled()
        }
    }
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstVisibleOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
    LaunchedEffect(firstVisibleIndex, firstVisibleOffset, isInitialPositionRestored) {
        if (isInitialPositionRestored && pages.isNotEmpty() && firstVisibleIndex < pages.size) {
            onProgressUpdate(pages[firstVisibleIndex].pageIndex, firstVisibleOffset)
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val maxPageWidth = minOf(maxWidth, 960.dp, if (isLandscape) maxHeight * 1.6f else 960.dp)
        val width = this.constraints.maxWidth.toFloat()
        val state = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            val maxX = (width * (scale - 1) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
            if (scale > 1.05f && panChange.y != 0f) {
                coroutineScope.launch {
                    listState.scrollBy(-panChange.y / scale)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = state)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offsetX = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                    ),
                contentPadding = innerPadding,
                userScrollEnabled = scale <= 1.05f,
            ) {
                items(pages, key = { it.pageIndex }) { page ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(page.pageUri))
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.widthIn(max = maxPageWidth).fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                    if (settings.verticalPageGap != "none") {
                        Spacer(Modifier.height(if (settings.verticalPageGap == "small") 4.dp else 12.dp))
                    }
                }
            }
        }
    }
}
@Composable
fun HorizontalSingleReader(
    pages: List<ComicPage>,
    initialPage: Int,
    innerPadding: PaddingValues,
    onProgressUpdate: (Int) -> Unit,
    onTap: () -> Unit,
    jumpToPage: Int?,
    pageTransition: String,
    animateJumpToPage: Boolean,
    onJumpHandled: () -> Unit,
) {
    val initialIndex = remember(pages, initialPage) {
        pages.indexOfFirst { it.pageIndex == initialPage }.coerceAtLeast(0)
    }
    var pageIndex by remember(pages) { androidx.compose.runtime.mutableIntStateOf(initialIndex) }
    var turningDirection by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var dragDirection by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var turnFromIndex by remember { androidx.compose.runtime.mutableIntStateOf(initialIndex) }
    var queuedTapTurns by remember(pages) { androidx.compose.runtime.mutableIntStateOf(0) }
    val turnProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var turnJob by remember { mutableStateOf<Job?>(null) }
    val currentOnTap by rememberUpdatedState(onTap)
    fun startTurn(direction: Int) {
        if (direction == 0) return
        if (turningDirection != 0 || turnJob?.isActive == true) {
            queuedTapTurns = (queuedTapTurns + direction).coerceIn(-pages.size, pages.size)
            return
        }
        val targetIndex = (pageIndex + direction).coerceIn(0, pages.lastIndex)
        if (targetIndex == pageIndex) return
        turnFromIndex = pageIndex
        turningDirection = direction
        turnJob?.cancel()
        turnJob = coroutineScope.launch {
            turnProgress.snapTo(0f)
            turnProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            pageIndex = targetIndex
            turningDirection = 0
            turnProgress.snapTo(0f)
            turnJob = null
        }
    }
    LaunchedEffect(queuedTapTurns, turningDirection, turnJob) {
        val queuedDirection = queuedTapTurns.compareTo(0)
        if (queuedDirection != 0 && turningDirection == 0 && turnJob?.isActive != true) {
            queuedTapTurns -= queuedDirection
            startTurn(queuedDirection)
        }
    }
    LaunchedEffect(pages) {
        val targetIndex = pages.indexOfFirst { it.pageIndex == initialPage }
        if (targetIndex >= 0 && targetIndex != pageIndex && turningDirection == 0 && turnJob?.isActive != true) {
            turnJob?.cancel()
            turnJob = null
            pageIndex = targetIndex
            turningDirection = 0
            dragDirection = 0
            queuedTapTurns = 0
            turnProgress.snapTo(0f)
        }
    }
    LaunchedEffect(jumpToPage, animateJumpToPage, pages) {
        jumpToPage?.let { target ->
            val targetIndex = pages.indexOfFirst { it.pageIndex == target }
            turnJob?.cancel()
            turnJob = null
            dragDirection = 0
            queuedTapTurns = 0
            if (targetIndex >= 0 && animateJumpToPage && targetIndex != pageIndex) {
                val fromIndex = pageIndex
                turnFromIndex = fromIndex
                turningDirection = if (targetIndex > fromIndex) 1 else -1
                turnProgress.snapTo(0f)
                turnJob = coroutineScope.launch {
                    turnProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                    pageIndex = targetIndex
                    turningDirection = 0
                    turnProgress.snapTo(0f)
                    turnJob = null
                    onJumpHandled()
                }
            } else {
                turningDirection = 0
                turnProgress.snapTo(0f)
                if (targetIndex >= 0) pageIndex = targetIndex
                onJumpHandled()
            }
        }
    }
    LaunchedEffect(pageIndex, pages) {
        pages.getOrNull(pageIndex)?.let { onProgressUpdate(it.pageIndex) }
    }
    if (pages.isEmpty()) return
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        val currentPage = pages.getOrNull(if (turningDirection == 0) pageIndex else turnFromIndex)
        val currentPagePainter = rememberCurlPagePainter(currentPage)
        val intrinsic = currentPagePainter?.intrinsicSize
        val pageWidth = if (
            intrinsic != null && intrinsic.width.isFinite() && intrinsic.height.isFinite() &&
            intrinsic.width > 0f && intrinsic.height > 0f
        ) {
            minOf(maxWidth, maxHeight * (intrinsic.width / intrinsic.height))
        } else {
            maxWidth
        }
        val pageWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { pageWidth.toPx() }.coerceAtLeast(1f)
        val currentPageWidthPx by rememberUpdatedState(pageWidthPx)
        val currentPageIndex by rememberUpdatedState(pageIndex)
        val currentTurnFromIndex by rememberUpdatedState(turnFromIndex)
        val currentLastIndex by rememberUpdatedState(pages.lastIndex)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(Color.Black)
                    .pointerInput(pageIndex, pages.size) {
                        detectTapGestures { position ->
                            val fraction = position.x / size.width
                            when {
                                fraction < 0.4f -> startTurn(-1)
                                fraction > 0.6f -> startTurn(1)
                                else -> currentOnTap()
                            }
                        }
                    }
                    .pointerInput(pageIndex, pages.size) {
                        var ignoreGesture = false
                        detectHorizontalDragGestures(
                            onDragStart = {
                                ignoreGesture = turningDirection != 0 || turnJob?.isActive == true
                                dragDirection = 0
                                dragDistance = 0f
                                dragProgress = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (ignoreGesture) return@detectHorizontalDragGestures
                                dragDistance += dragAmount
                                if (dragDirection == 0) {
                                    val requestedDirection = if (dragDistance < 0f) 1 else -1
                                    if ((currentPageIndex + requestedDirection) in 0..currentLastIndex) {
                                        dragDirection = requestedDirection
                                        turnFromIndex = currentPageIndex
                                        turningDirection = requestedDirection
                                    }
                                }
                                if (dragDirection != 0) {
                                    val traveled = if (dragDirection > 0) -dragDistance else dragDistance
                                    dragProgress = (traveled / currentPageWidthPx).coerceIn(0f, 1f)
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (ignoreGesture) {
                                    ignoreGesture = false
                                    return@detectHorizontalDragGestures
                                }
                                val direction = dragDirection
                                if (direction != 0) {
                                    val progress = dragProgress
                                    val fromIndex = currentTurnFromIndex
                                    turnJob?.cancel()
                                    turnJob = coroutineScope.launch {
                                        val shouldCommit = progress >= 0.28f
                                        turnProgress.snapTo(progress)
                                        dragDirection = 0
                                        turnProgress.animateTo(
                                            if (shouldCommit) 1f else 0f,
                                            tween(280, easing = FastOutSlowInEasing),
                                        )
                                        if (shouldCommit) pageIndex = (fromIndex + direction).coerceIn(0, pages.lastIndex)
                                        turningDirection = 0
                                        turnProgress.snapTo(0f)
                                        turnJob = null
                                    }
                                }
                            },
                            onDragCancel = {
                                if (ignoreGesture) {
                                    ignoreGesture = false
                                    return@detectHorizontalDragGestures
                                }
                                if (dragDirection != 0) {
                                    val progress = dragProgress
                                    turnJob?.cancel()
                                    turnJob = coroutineScope.launch {
                                        turnProgress.snapTo(progress)
                                        dragDirection = 0
                                        turnProgress.animateTo(0f, tween(180))
                                        turningDirection = 0
                                        turnJob = null
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val shownPage = pages.getOrNull(if (turningDirection == 0) pageIndex else turnFromIndex)
                val targetPage = pages.getOrNull(
                    (turnFromIndex + turningDirection).coerceIn(0, pages.lastIndex),
                )
                if (turningDirection != 0) {
                    val progress = if (dragDirection != 0) dragProgress else turnProgress.value
                    when (pageTransition) {
                        "slide" -> {
                            CurlComicPage(
                                targetPage,
                                Modifier.fillMaxSize().graphicsLayer {
                                    translationX = turningDirection * size.width * (1f - progress)
                                },
                            )
                            CurlComicPage(
                                shownPage,
                                Modifier.fillMaxSize().graphicsLayer {
                                    translationX = -turningDirection * size.width * progress
                                },
                            )
                        }
                        "fade" -> {
                            CurlComicPage(
                                targetPage,
                                Modifier.fillMaxSize().graphicsLayer { alpha = progress },
                            )
                            CurlComicPage(
                                shownPage,
                                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress },
                            )
                        }
                        else -> {
                            val isBackwardTurn = turningDirection < 0
                            CurlComicPage(
                                if (isBackwardTurn) shownPage else targetPage,
                                Modifier.fillMaxSize(),
                            )
                            SinglePageCurlSurface(
                                frontPage = if (isBackwardTurn) targetPage else shownPage,
                                backPage = if (isBackwardTurn) shownPage else targetPage,
                                direction = 1,
                                progress = if (isBackwardTurn) 1f - progress else progress,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    CurlComicPage(shownPage, Modifier.fillMaxSize())
                }
            }
        }
    }
}
@Composable
private fun CurlComicPage(
    page: ComicPage?,
    modifier: Modifier = Modifier,
    imageAlignment: Alignment = Alignment.Center,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (page != null) {
            ZoomableMediaBox(
                modifier = Modifier.fillMaxSize(),
                enableTapGestures = false,
            ) { scale, offset ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(page.pageUri))
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    alignment = imageAlignment,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
@Composable
private fun rememberCurlPagePainter(page: ComicPage?): Painter? {
    if (page == null) return null
    val context = LocalContext.current
    val request = remember(page.pageUri, context) {
        ImageRequest.Builder(context)
            .data(Uri.parse(page.pageUri))
            .crossfade(false)
            .build()
    }
    return rememberAsyncImagePainter(
        model = request,
        contentScale = ContentScale.Fit,
    )
}
@Composable
private fun SinglePageCurlSurface(
    frontPage: ComicPage?,
    backPage: ComicPage?,
    direction: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val frontAlignment = if (direction > 0) Alignment.CenterStart else Alignment.CenterEnd
    val backAlignment = if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart
    val frontPainter = rememberCurlPagePainter(frontPage)
    val backPainter = rememberCurlPagePainter(backPage)
    Canvas(modifier) {
        val pageWidth = size.width
        val pageHeight = size.height
        val stripCount = 96
        fun fittedBounds(painter: Painter?, alignment: Alignment): Rect {
            val intrinsic = painter?.intrinsicSize
            if (intrinsic == null || !intrinsic.width.isFinite() || !intrinsic.height.isFinite() || intrinsic.width <= 0f || intrinsic.height <= 0f) {
                return Rect(0f, 0f, pageWidth, pageHeight)
            }
            val fitScale = minOf(pageWidth / intrinsic.width, pageHeight / intrinsic.height)
            val width = intrinsic.width * fitScale
            val height = intrinsic.height * fitScale
            val left = when (alignment) {
                Alignment.CenterStart -> 0f
                Alignment.CenterEnd -> pageWidth - width
                else -> (pageWidth - width) / 2f
            }
            return Rect(left, (pageHeight - height) / 2f, left + width, (pageHeight + height) / 2f)
        }
        val frontBounds = fittedBounds(frontPainter, frontAlignment)
        val backBounds = fittedBounds(backPainter, backAlignment)
        val sweepWidth = 0.34f
        var edgeX = if (direction > 0) 0f else pageWidth
        val movementSign = if (direction > 0) 1f else -1f
        repeat(stripCount) { index ->
            val u = (index + 0.5f) / stripCount
            val sweepStart = (1f - u) * (1f - sweepWidth)
            val curlPhase = ((progress - sweepStart) / sweepWidth).coerceIn(0f, 1f)
            val horizontalProjection = cos(PI.toFloat() * curlPhase)
            val isBackSide = horizontalProjection < 0f
            val painter = if (isBackSide) backPainter else frontPainter
            val sourceBounds = if (isBackSide) backBounds else frontBounds
            val sourceStripWidth = sourceBounds.width / stripCount
            val projectedOffset = horizontalProjection * sourceStripWidth
            val nextEdgeX = edgeX + movementSign * projectedOffset
            val centerX = (edgeX + nextEdgeX) / 2f
            val projectedWidth = abs(projectedOffset).coerceAtLeast(0.01f)
            val sourceU = when {
                direction > 0 && isBackSide -> 1f - u
                direction > 0 -> u
                isBackSide -> u
                else -> 1f - u
            }
            val sourceCenterX = sourceBounds.left + sourceU * sourceBounds.width
            val sourceLeft = (sourceCenterX - sourceStripWidth / 2f)
                .coerceIn(sourceBounds.left, sourceBounds.right - sourceStripWidth)
            val scaleX = projectedWidth / sourceStripWidth
            if (painter != null) {
                withTransform({
                    translate(left = centerX - sourceLeft * scaleX - sourceStripWidth * scaleX / 2f)
                    scale(scaleX = scaleX, scaleY = 1f, pivot = Offset.Zero)
                    clipRect(
                        left = sourceLeft,
                        top = sourceBounds.top,
                        right = sourceLeft + sourceStripWidth,
                        bottom = sourceBounds.bottom,
                    )
                }) {
                    withTransform({ translate(left = sourceBounds.left, top = sourceBounds.top) }) {
                        with(painter) { draw(size = Size(sourceBounds.width, sourceBounds.height)) }
                    }
                }
            } else {
                drawRect(
                    color = Color.White,
                    topLeft = Offset(centerX - projectedWidth / 2f, sourceBounds.top),
                    size = Size(projectedWidth, sourceBounds.height),
                )
            }
            val foldShade = (sin(PI * curlPhase).toFloat() * 0.22f).coerceIn(0f, 0.22f)
            if (foldShade > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = foldShade),
                    topLeft = Offset(centerX - projectedWidth / 2f, sourceBounds.top),
                    size = Size(projectedWidth, sourceBounds.height),
                )
            }
            edgeX = nextEdgeX
        }
    }
}
@Composable
private fun DoublePageCurlSurface(
    frontPage: ComicPage?,
    backPage: ComicPage?,
    direction: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val frontAlignment = if (direction > 0) Alignment.CenterStart else Alignment.CenterEnd
    val backAlignment = if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart
    val frontPainter = rememberCurlPagePainter(frontPage)
    val backPainter = rememberCurlPagePainter(backPage)
    Canvas(modifier) {
        val spreadWidth = size.width
        val pageWidth = spreadWidth / 2f
        val pageHeight = size.height
        val hingeX = pageWidth
        val stripCount = 32
        fun fittedBounds(painter: Painter?, alignment: Alignment): Rect {
            val intrinsic = painter?.intrinsicSize
            if (intrinsic == null || !intrinsic.width.isFinite() || !intrinsic.height.isFinite() || intrinsic.width <= 0f || intrinsic.height <= 0f) {
                return Rect(0f, 0f, pageWidth, pageHeight)
            }
            val fitScale = minOf(pageWidth / intrinsic.width, pageHeight / intrinsic.height)
            val width = intrinsic.width * fitScale
            val height = intrinsic.height * fitScale
            val left = when (alignment) {
                Alignment.CenterStart -> 0f
                Alignment.CenterEnd -> pageWidth - width
                else -> (pageWidth - width) / 2f
            }
            return Rect(left, (pageHeight - height) / 2f, left + width, (pageHeight + height) / 2f)
        }
        val frontBounds = fittedBounds(frontPainter, frontAlignment)
        val backBounds = fittedBounds(backPainter, backAlignment)
        val sweepWidth = 0.34f
        var edgeX = hingeX
        val movementSign = if (direction > 0) 1f else -1f
        repeat(stripCount) { index ->
            val u = (index + 0.5f) / stripCount
            val sweepStart = (1f - u) * (1f - sweepWidth)
            val curlPhase = ((progress - sweepStart) / sweepWidth).coerceIn(0f, 1f)
            val angle = PI.toFloat() * curlPhase
            val horizontalProjection = cos(angle)
            val isBackSide = horizontalProjection < 0f
            val painter = if (isBackSide) backPainter else frontPainter
            val sourceBounds = if (isBackSide) backBounds else frontBounds
            val sourceStripWidth = sourceBounds.width / stripCount
            val projectedOffset = horizontalProjection * sourceStripWidth
            val nextEdgeX = edgeX + movementSign * projectedOffset
            val centerX = (edgeX + nextEdgeX) / 2f
            val projectedWidth = abs(projectedOffset).coerceAtLeast(0.01f)
            val targetLeft = centerX - projectedWidth / 2f
            val sourceU = when {
                direction > 0 && isBackSide -> 1f - u
                direction > 0 -> u
                isBackSide -> u
                else -> 1f - u
            }
            val sourceCenterX = sourceBounds.left + sourceU * sourceBounds.width
            val sourceLeft = (sourceCenterX - sourceStripWidth / 2f).coerceIn(sourceBounds.left, sourceBounds.right - sourceStripWidth)
            val painterSourceLeft = sourceLeft
            val painterSourceCenter = sourceLeft + sourceStripWidth / 2f
            val scaleX = projectedWidth / sourceStripWidth
            if (painter != null) {
                withTransform({
                    translate(left = centerX - painterSourceCenter * scaleX)
                    scale(scaleX = scaleX, scaleY = 1f, pivot = Offset.Zero)
                    clipRect(left = sourceLeft, top = sourceBounds.top, right = sourceLeft + sourceStripWidth, bottom = sourceBounds.bottom)
                }) {
                    withTransform({ translate(left = sourceBounds.left, top = sourceBounds.top) }) {
                        with(painter) { draw(size = Size(sourceBounds.width, sourceBounds.height)) }
                    }
                }
            } else {
                drawRect(
                    color = Color.White,
                    topLeft = Offset(targetLeft, sourceBounds.top),
                    size = Size(projectedWidth, sourceBounds.height),
                )
            }
            val foldShade = (sin(PI * curlPhase).toFloat() * 0.22f).coerceIn(0f, 0.22f)
            if (foldShade > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = foldShade),
                    topLeft = Offset(targetLeft, sourceBounds.top),
                    size = Size(projectedWidth, sourceBounds.height),
                )
            }
            edgeX = nextEdgeX
        }
    }
}
@Composable
fun HorizontalDoubleReader(
    pages: List<ComicPage>,
    initialPage: Int,
    innerPadding: PaddingValues,
    onProgressUpdate: (Int) -> Unit,
    onTap: () -> Unit,
    jumpToPage: Int?,
    pageTransition: String,
    animateJumpToPage: Boolean,
    onJumpHandled: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 600.dp && maxWidth <= maxHeight) {
            HorizontalSingleReader(
                pages = pages,
                initialPage = initialPage,
                innerPadding = innerPadding,
                onProgressUpdate = onProgressUpdate,
                onTap = onTap,
                jumpToPage = jumpToPage,
                pageTransition = pageTransition,
                animateJumpToPage = animateJumpToPage,
                onJumpHandled = onJumpHandled,
            )
            return@BoxWithConstraints
        }
        val initialSlot = remember(pages, initialPage) {
            pages.indexOfFirst { it.pageIndex == initialPage }.coerceAtLeast(0)
        }
        val initialSpread = bookSpreadForSlot(initialSlot)
        val spreadCount = pages.size / 2 + 1
        var spreadIndex by remember(pages) { androidx.compose.runtime.mutableIntStateOf(initialSpread) }
        var turningDirection by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var dragDirection by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var dragDistance by remember { mutableFloatStateOf(0f) }
        var dragProgress by remember { mutableFloatStateOf(0f) }
        var turnFromSpread by remember { androidx.compose.runtime.mutableIntStateOf(initialSpread) }
        var queuedTapTurns by remember(pages) { androidx.compose.runtime.mutableIntStateOf(0) }
        val turnProgress = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()
        var turnJob by remember { mutableStateOf<Job?>(null) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val currentOnTap by rememberUpdatedState(onTap)
        fun startTurn(direction: Int) {
            if (direction == 0) return
            if (turningDirection != 0 || turnJob?.isActive == true) {
                queuedTapTurns = (queuedTapTurns + direction).coerceIn(-spreadCount, spreadCount)
                return
            }
            val targetSpread = (spreadIndex + direction).coerceIn(0, spreadCount - 1)
            if (targetSpread == spreadIndex) return
            turnFromSpread = spreadIndex
            turningDirection = direction
            turnJob?.cancel()
            turnJob = coroutineScope.launch {
                turnProgress.snapTo(0f)
                turnProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                spreadIndex = targetSpread
                turningDirection = 0
                turnProgress.snapTo(0f)
                turnJob = null
            }
        }
        LaunchedEffect(queuedTapTurns, turningDirection, turnJob) {
            val queuedDirection = queuedTapTurns.compareTo(0)
            if (queuedDirection != 0 && turningDirection == 0 && turnJob?.isActive != true) {
                queuedTapTurns -= queuedDirection
                startTurn(queuedDirection)
            }
        }
        LaunchedEffect(pages) {
            val targetSlot = pages.indexOfFirst { it.pageIndex == initialPage }
            val targetSpread = targetSlot.takeIf { it >= 0 }?.let(::bookSpreadForSlot)
            if (targetSpread != null && targetSpread != spreadIndex && turningDirection == 0 && turnJob?.isActive != true) {
                spreadIndex = targetSpread
                dragDirection = 0
                queuedTapTurns = 0
                turnProgress.snapTo(0f)
            }
        }
        LaunchedEffect(jumpToPage, animateJumpToPage, pages) {
            jumpToPage?.let { target ->
                val targetSlot = pages.indexOfFirst { it.pageIndex == target }
                turnJob?.cancel()
                turnJob = null
                dragDirection = 0
                queuedTapTurns = 0
                if (targetSlot >= 0) {
                    val targetSpread = bookSpreadForSlot(targetSlot)
                    if (animateJumpToPage && targetSpread != spreadIndex) {
                        val fromSpread = spreadIndex
                        turnFromSpread = fromSpread
                        turningDirection = if (targetSpread > fromSpread) 1 else -1
                        turnProgress.snapTo(0f)
                        turnJob = coroutineScope.launch {
                            turnProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                            spreadIndex = targetSpread
                            turningDirection = 0
                            turnProgress.snapTo(0f)
                            turnJob = null
                            onJumpHandled()
                        }
                    } else {
                        turningDirection = 0
                        turnProgress.snapTo(0f)
                        spreadIndex = targetSpread
                        onJumpHandled()
                    }
                } else {
                    turningDirection = 0
                    turnProgress.snapTo(0f)
                    onJumpHandled()
                }
            }
        }
        LaunchedEffect(spreadIndex, pages) {
            (pages.getOrNull(rightPageSlotForSpread(spreadIndex))
                ?: pages.getOrNull(leftPageSlotForSpread(spreadIndex)))
                ?.let { onProgressUpdate(it.pageIndex) }
        }
        if (pages.isEmpty()) return@BoxWithConstraints
        val destinationSpread = (turnFromSpread + turningDirection).coerceIn(0, spreadCount - 1)
        val shownSpread = if (turningDirection == 0) spreadIndex else turnFromSpread
        val currentLeftPage = pages.getOrNull(leftPageSlotForSpread(shownSpread))
        val currentRightPage = pages.getOrNull(rightPageSlotForSpread(shownSpread))
        val destinationLeftPage = pages.getOrNull(leftPageSlotForSpread(destinationSpread))
        val destinationRightPage = pages.getOrNull(rightPageSlotForSpread(destinationSpread))
        val pageProgress = when {
            turningDirection == 0 -> 0f
            dragDirection != 0 -> dragProgress
            else -> turnProgress.value
        }
        val underlayLeft = if (turningDirection < 0) destinationLeftPage else currentLeftPage
        val underlayRight = if (turningDirection > 0) destinationRightPage else currentRightPage
        val turningFront = if (turningDirection > 0) currentRightPage else currentLeftPage
        val turningBack = if (turningDirection > 0) destinationLeftPage else destinationRightPage
        val layoutDirection = LocalLayoutDirection.current
        val contentWidth = maxWidth - innerPadding.calculateLeftPadding(layoutDirection) - innerPadding.calculateRightPadding(layoutDirection)
        val contentHeight = maxHeight - innerPadding.calculateTopPadding() - innerPadding.calculateBottomPadding()
        val pageWidthPx = with(density) { (contentWidth / 2).toPx() }.coerceAtLeast(1f)
        val pageHeightPx = with(density) { contentHeight.toPx() }.coerceAtLeast(1f)
        val gesturePainter = rememberCurlPagePainter(turningFront)
        val sourceSize = gesturePainter?.intrinsicSize
        val pageAspect = pageWidthPx / pageHeightPx
        val imageWidthFraction = if (
            sourceSize != null && sourceSize.width.isFinite() && sourceSize.height.isFinite() &&
            sourceSize.width > 0f && sourceSize.height > 0f
        ) {
            minOf(1f, (sourceSize.width / sourceSize.height) / pageAspect)
        } else {
            1f
        }
        val dragWidthPx = (pageWidthPx * imageWidthFraction).coerceAtLeast(1f)
        val currentDragWidthPx by rememberUpdatedState(dragWidthPx)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .pointerInput(spreadIndex, pages.size) {
                    detectTapGestures { position ->
                        val fraction = position.x / size.width
                        when {
                            fraction < 0.4f -> startTurn(-1)
                            fraction > 0.6f -> startTurn(1)
                            else -> currentOnTap()
                        }
                    }
                }
                .pointerInput(spreadIndex, pages.size) {
                    var ignoreGesture = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            ignoreGesture = turningDirection != 0 || turnJob?.isActive == true
                            dragDirection = 0
                            dragDistance = 0f
                            dragProgress = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (ignoreGesture) return@detectHorizontalDragGestures
                            dragDistance += dragAmount
                            if (dragDirection == 0) {
                                val requestedDirection = if (dragDistance < 0f) 1 else -1
                                if ((spreadIndex + requestedDirection) in 0 until spreadCount) {
                                    dragDirection = requestedDirection
                                    turnFromSpread = spreadIndex
                                    turningDirection = requestedDirection
                                }
                            }
                            if (dragDirection != 0) {
                                val traveled = if (dragDirection > 0) -dragDistance else dragDistance
                                dragProgress = (traveled / currentDragWidthPx).coerceIn(0f, 1f)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (ignoreGesture) {
                                ignoreGesture = false
                                return@detectHorizontalDragGestures
                            }
                            val direction = dragDirection
                            if (direction != 0) {
                                val progress = dragProgress
                                val fromSpread = turnFromSpread
                                turnJob?.cancel()
                                turnJob = coroutineScope.launch {
                                    val shouldCommit = progress >= 0.28f
                                    turnProgress.snapTo(progress)
                                    dragDirection = 0
                                    turnProgress.animateTo(
                                        if (shouldCommit) 1f else 0f,
                                        tween(280, easing = FastOutSlowInEasing),
                                    )
                                    if (shouldCommit) spreadIndex = (fromSpread + direction).coerceIn(0, spreadCount - 1)
                                    turningDirection = 0
                                    turnProgress.snapTo(0f)
                                    turnJob = null
                                }
                            }
                        },
                        onDragCancel = {
                            if (ignoreGesture) {
                                ignoreGesture = false
                                return@detectHorizontalDragGestures
                            }
                            val direction = dragDirection
                            if (direction != 0) {
                                val progress = dragProgress
                                turnJob?.cancel()
                                turnJob = coroutineScope.launch {
                                    turnProgress.snapTo(progress)
                                    dragDirection = 0
                                    turnProgress.animateTo(0f, tween(180))
                                    turningDirection = 0
                                    turnJob = null
                                }
                            }
                        },
                    )
                },
        ) {
            if (turningDirection != 0 && pageTransition != "curl") {
                val isSlide = pageTransition == "slide"
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        if (isSlide) {
                            translationX = -turningDirection * size.width * pageProgress
                        } else {
                            alpha = 1f - pageProgress
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CurlComicPage(
                        page = currentLeftPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterEnd,
                    )
                    CurlComicPage(
                        page = currentRightPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterStart,
                    )
                }
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        if (isSlide) {
                            translationX = turningDirection * size.width * (1f - pageProgress)
                        } else {
                            alpha = pageProgress
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CurlComicPage(
                        page = destinationLeftPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterEnd,
                    )
                    CurlComicPage(
                        page = destinationRightPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterStart,
                    )
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    CurlComicPage(
                        page = underlayLeft,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterEnd,
                    )
                    CurlComicPage(
                        page = underlayRight,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterStart,
                    )
                }
                if (turningDirection != 0) {
                    DoublePageCurlSurface(
                        frontPage = turningFront,
                        backPage = turningBack,
                        direction = turningDirection,
                        progress = pageProgress,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
private fun bookSpreadForSlot(pageSlot: Int): Int =
    if (pageSlot == 0) 0 else (pageSlot + 1) / 2
private fun leftPageSlotForSpread(spreadIndex: Int): Int =
    if (spreadIndex == 0) -1 else spreadIndex * 2 - 1
private fun rightPageSlotForSpread(spreadIndex: Int): Int =
    if (spreadIndex == 0) 0 else spreadIndex * 2
private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}