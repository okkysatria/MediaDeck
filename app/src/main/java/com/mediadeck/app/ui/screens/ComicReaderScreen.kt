package com.mediadeck.app.ui.screens
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.ZoomableMediaBox
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.ComicViewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ReaderAccent = Color(0xFF4DA3FF)
private val ReaderBar = Color(0xFF23262E)
private val ReaderChip = Color(0xFF2D3039)
private val ReaderPanel = Color(0xFF131519)
private val ReaderCurrentRow = Color(0xFF1D2A3C)
private val ReaderOnAccent = Color(0xFF0B1B2E)
private val ReaderMuted = Color(0xFFA0A5AF)
private val ReaderDanger = Color(0xFFFF6B7A)


private const val DEFAULT_PAGE_HEIGHT_PX = 1200

private const val PROGRESS_SAVE_DELAY_MS = 250L
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
    var readerMode by rememberSaveable { mutableStateOf(settings.defaultReaderMode) }
    var labelMode by rememberSaveable { mutableStateOf("filename") }
    var reversePages by rememberSaveable { mutableStateOf(false) }
    var showBars by rememberSaveable { mutableStateOf(!settings.autoHideReaderUi) }
    val focusRequester = remember { FocusRequester() }
    fun restoreFocus() {
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(Unit) { restoreFocus() }

    if (activeComic == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val processedPages = remember(pages, reversePages, readerMode) {
        val sorted = pages
            .map { page -> page to pageFileName(page) }
            .sortedWith { (pa, nameA), (pb, nameB) ->
                val byName = naturalCompare(nameA, nameB)
                if (byName != 0) byName else pa.pageIndex.compareTo(pb.pageIndex)
            }
            .map { it.first }
        if (reversePages && readerMode == "horizontal_single") sorted.reversed() else sorted
    }
    var pendingVolumeTarget by remember { mutableStateOf<Int?>(null) }
    val isDoubleRtl = reversePages && readerMode == "horizontal_double"
    fun pageForVolumeStep(step: Int): ComicPage? {
        val baseSlot = pendingVolumeTarget ?: ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0)
        val currentIndex = processedPages.indexOfFirst { it.pageIndex == baseSlot }
        val currentSlot = currentIndex.takeIf { it >= 0 } ?: 0
        val direction = if (reversePages && readerMode == "horizontal_single") -step else step
        if (readerMode != "horizontal_double") return processedPages.getOrNull(currentSlot + direction)
        val spreadCount = processedPages.size / 2 + 1
        val targetSpread = bookSpreadForSlot(currentSlot) + (if (isDoubleRtl) -step else step)
        if (targetSpread !in 0 until spreadCount) return null
        return processedPages.getOrNull(rightPageSlotForSpread(targetSpread, isDoubleRtl))
            ?: processedPages.getOrNull(leftPageSlotForSpread(targetSpread, isDoubleRtl))
    }
    var showDirectory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var jumpToPage by remember { mutableStateOf<Int?>(null) }
    var animateJumpToPage by remember { mutableStateOf(false) }
    var resetProgressRequested by remember { mutableStateOf(false) }
    BackHandler {
        if (showDirectory) {
            showDirectory = false
        } else if (showSettings) {
            showSettings = false
        } else {
            onClose()
        }
    }

    LaunchedEffect(activeComic?.currentPage, jumpToPage) {
        val target = pendingVolumeTarget
        if (target != null && jumpToPage == null) {
            val currentPageIndex = (activeComic?.currentPage ?: 1) - 1
            val reached = if (readerMode == "horizontal_double") {
                val currentIdx = processedPages.indexOfFirst { it.pageIndex == currentPageIndex }
                val targetIdx = processedPages.indexOfFirst { it.pageIndex == target }
                currentIdx >= 0 && targetIdx >= 0 && bookSpreadForSlot(currentIdx) == bookSpreadForSlot(targetIdx)
            } else {
                currentPageIndex == target
            }
            if (reached) pendingVolumeTarget = null
        }
    }
    LaunchedEffect(readerMode, reversePages) { pendingVolumeTarget = null }

    LaunchedEffect(resetProgressRequested, jumpToPage) {
        if (resetProgressRequested && jumpToPage == null) {
            delay(400)
            activeComic?.let { viewModel.clearComicHistory(it) }
            resetProgressRequested = false
        }
    }
    fun jumpByVolumeStep(step: Int) {
        pageForVolumeStep(step)?.let { page ->
            pendingVolumeTarget = page.pageIndex
            animateJumpToPage = readerMode == "horizontal_single" || readerMode == "horizontal_double"
            if (!animateJumpToPage) viewModel.updateReadingProgress(page.pageIndex + 1)
            jumpToPage = page.pageIndex
        }
    }
    val sidebarListState = rememberLazyListState()
    LaunchedEffect(showDirectory) { if (!showDirectory) restoreFocus() }
    LaunchedEffect(showSettings) { if (!showSettings) restoreFocus() }
    LaunchedEffect(showBars) { if (!showBars) restoreFocus() }
    LaunchedEffect(showDirectory, activeComic?.currentPage) {
        if (showDirectory) {
            val currentSlot = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0)
            val targetIdx = processedPages.indexOfFirst { it.pageIndex == currentSlot }
            if (targetIdx >= 0 && targetIdx < processedPages.size) {
                sidebarListState.scrollToItem(targetIdx)
            }
        }
    }
    val currentSlot = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0)
    val currentProcessedIndex = processedPages.indexOfFirst { it.pageIndex == currentSlot }.takeIf { it >= 0 } ?: 0
    val totalPages = processedPages.size.coerceAtLeast(1)
    val currentPageDisplay = (currentProcessedIndex + 1).coerceAtLeast(1)

    val bottomPageText = remember(activeComic?.currentPage, activeComic?.totalPages, readerMode, processedPages, isDoubleRtl) {
        if (readerMode == "horizontal_double" && processedPages.isNotEmpty()) {
            val currIdx = processedPages.indexOfFirst { it.pageIndex == currentSlot }.coerceAtLeast(0)
            val spread = bookSpreadForSlot(currIdx)
            val leftSlot = leftPageSlotForSpread(spread, isDoubleRtl)
            val rightSlot = rightPageSlotForSpread(spread, isDoubleRtl)
            val pLeft = leftSlot.takeIf { it in processedPages.indices }?.let { it + 1 }
            val pRight = rightSlot.takeIf { it in processedPages.indices }?.let { it + 1 }
            if (pLeft != null && pRight != null) {
                val p1 = minOf(pLeft, pRight)
                val p2 = maxOf(pLeft, pRight)
                "$p1–$p2"
            } else {
                "${pLeft ?: pRight ?: currentPageDisplay}"
            }
        } else {
            "$currentPageDisplay"
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (settings.readerVolumeKeysNavigation) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                            val step = if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1
                            jumpByVolumeStep(step)
                        }
                        return@onKeyEvent true
                    }
                }
                false
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
                            Text(t("Failed to Open Comic", "Gagal Membuka Komik"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(comicLoadError ?: "", fontSize = 12.sp, textAlign = TextAlign.Center)
                            Button(onClick = { activeComic?.let { viewModel.openComic(context, it) } }) { Text(t("Try Again", "Coba Lagi")) }
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
                    showScrollbar = showBars,
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
                    isRtl = isDoubleRtl,
                )
            }
        }
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = ReaderBar,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = t("Close", "Tutup"),
                            tint = Color.White,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = activeComic?.title ?: t("Comic", "Komik"),
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        showDirectory = !showDirectory
                        showSettings = false
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = t("Page Directory", "Daftar Halaman"),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        showSettings = !showSettings
                        showDirectory = false
                    }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = t("Reader Settings", "Pengaturan Pembaca"),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val isLandscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            Surface(
                color = ReaderBar,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 2.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(ReaderChip)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bottomPageText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = " / $totalPages",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = ReaderMuted,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(ReaderChip)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("vertical", "horizontal_single", "horizontal_double").forEach { mode ->
                            val isSelected = readerMode == mode
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (isSelected) ReaderAccent else Color.Transparent)
                                    .clickable { readerMode = mode },
                                contentAlignment = Alignment.Center,
                            ) {
                                val tint = if (isSelected) ReaderOnAccent else Color.White
                                when (mode) {
                                    "vertical" -> VerticalModeIcon(tint = tint)
                                    "horizontal_single" -> SinglePageModeIcon(tint = tint)
                                    "horizontal_double" -> DoublePageModeIcon(tint = tint)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showDirectory || showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        showDirectory = false
                        showSettings = false
                    }
            )
        }

        ReaderSidePanel(
            visible = showDirectory,
            title = t("Page Directory", "Daftar Halaman"),
            onClose = { showDirectory = false },
        ) {
            LazyColumn(
                state = sidebarListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(processedPages, key = { it.pageIndex }) { page ->
                    val isCurrent = ((activeComic?.currentPage ?: 1) - 1).coerceAtLeast(0) == page.pageIndex
                    val pagePosition = processedPages.indexOfFirst { it.pageIndex == page.pageIndex }.takeIf { it >= 0 } ?: 0
                    val labelText = remember(page.pageUri, page.pageIndex, labelMode, pagePosition) {
                        if (labelMode == "index") {
                            "p.${pagePosition + 1}"
                        } else {
                            val rawName = (Uri.parse(page.pageUri).lastPathSegment ?: page.pageUri).substringAfterLast('/')
                            val cleanName = rawName.substringBeforeLast('.')
                            if (cleanName.isNotBlank() && cleanName.length <= 15) cleanName else "p.${pagePosition + 1}"
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isCurrent) ReaderCurrentRow else Color.Transparent)
                            .clickable {
                                pendingVolumeTarget = null
                                val animated = readerMode == "horizontal_single" || readerMode == "horizontal_double"
                                animateJumpToPage = animated
                                if (!animated) viewModel.updateReadingProgress(page.pageIndex + 1)
                                jumpToPage = page.pageIndex
                                showDirectory = false
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = Uri.parse(page.pageUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp, 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ReaderBar),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = labelText,
                            color = if (isCurrent) ReaderAccent else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }

        ReaderSidePanel(
            visible = showSettings,
            title = t("Reader Settings", "Pengaturan Pembaca"),
            onClose = { showSettings = false },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                val isReadLater = activeComic?.isReadLater == true
                ReaderActionRow(
                    icon = if (isReadLater) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    text = if (isReadLater) t("Remove from Read Later", "Hapus dari Nanti Dibaca") else t("Read Later", "Nanti Dibaca"),
                    onClick = { activeComic?.let { viewModel.toggleComicReadLater(it) } },
                )
                Spacer(Modifier.height(8.dp))
                val isFavorite = activeComic?.isFavorite == true
                ReaderActionRow(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    text = if (isFavorite) t("Remove Favorite", "Hapus Favorit") else t("Add to Favorites", "Tambah ke Favorit"),
                    onClick = { activeComic?.let { viewModel.toggleComicFavorite(it) } },
                )

                Spacer(Modifier.height(20.dp))
                ReaderSectionLabel(t("PAGE EFFECT", "EFEK HALAMAN"))
                Spacer(Modifier.height(8.dp))
                ReaderSegmented(
                    options = listOf(
                        "curl" to t("Page Curl", "Page Curl"),
                        "slide" to t("Slide", "Geser"),
                        "fade" to t("Fade", "Memudar"),
                    ),
                    selected = settings.comicPageTransition,
                    onSelect = { viewModel.setComicPageTransition(it) },
                )

                if (readerMode != "vertical") {
                    Spacer(Modifier.height(20.dp))
                    ReaderSectionLabel(t("READING DIRECTION", "ARAH MEMBACA"))
                    Spacer(Modifier.height(8.dp))
                    ReaderSegmented(
                        options = listOf(
                            false to t("Left to right", "Kiri ke kanan"),
                            true to t("Right to left", "Kanan ke kiri"),
                        ),
                        selected = reversePages,
                        onSelect = { reversePages = it },
                    )
                }

                Spacer(Modifier.height(20.dp))
                ReaderSectionLabel(t("PAGE LABEL", "LABEL HALAMAN"))
                Spacer(Modifier.height(8.dp))
                ReaderSegmented(
                    options = listOf(
                        "filename" to t("Filename", "Nama file"),
                        "index" to t("Index", "Indeks"),
                    ),
                    selected = labelMode,
                    onSelect = { labelMode = it },
                )
                Spacer(Modifier.height(16.dp))
            }

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                border = BorderStroke(2.dp, ReaderDanger),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 20.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        pendingVolumeTarget = null
                        animateJumpToPage = false
                        resetProgressRequested = true
                        jumpToPage = processedPages.firstOrNull()?.pageIndex
                        showSettings = false
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = ReaderDanger,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = t("Reset Progress", "Reset Progres"),
                        color = ReaderDanger,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
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
    showScrollbar: Boolean = false,
) {
    val listState = rememberLazyListState()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isInitialPositionRestored by remember(pages) { mutableStateOf(false) }
    val density = LocalDensity.current
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
                scale = 1f
                offsetX = 0f
                listState.animateScrollToItem(idx)
                onProgressUpdate(pages[idx].pageIndex, 0)
            }
            onJumpHandled()
        }
    }


    val latestPages by rememberUpdatedState(pages)
    val latestOnProgressUpdate by rememberUpdatedState(onProgressUpdate)
    LaunchedEffect(listState, isInitialPositionRestored) {
        if (!isInitialPositionRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(PROGRESS_SAVE_DELAY_MS)
                val current = latestPages
                if (index < current.size) latestOnProgressUpdate(current[index].pageIndex, offset)
            }
    }
    DisposableEffect(listState) {
        onDispose {
            val current = latestPages
            val index = listState.firstVisibleItemIndex
            if (isInitialPositionRestored && index < current.size) {
                latestOnProgressUpdate(current[index].pageIndex, listState.firstVisibleItemScrollOffset)
            }
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val maxPageWidth = minOf(maxWidth, 960.dp, if (isLandscape) maxHeight * 1.6f else 960.dp)
        val width = this.constraints.maxWidth.toFloat()
        val pageWidthPx = with(density) { maxPageWidth.toPx() }.coerceAtMost(width)
        fun horizontalPanLimit(currentScale: Float): Float =
            ((pageWidthPx * currentScale - width) / 2f).coerceAtLeast(0f)
        val currentOnTap by rememberUpdatedState(onTap)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(width, pageWidthPx) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                        val effectiveZoom = newScale / oldScale
                        val c = centroid.x - width / 2f
                        val maxX = horizontalPanLimit(newScale)
                        offsetX = (c - (c - offsetX) * effectiveZoom + panChange.x).coerceIn(-maxX, maxX)
                        scale = newScale
                        if (scale > 1.05f && panChange.y != 0f) {
                            listState.dispatchRawDelta(-panChange.y / scale)
                        }
                    }
                }
                .pointerInput(width, pageWidthPx) {
                    detectTapGestures(
                        onTap = { currentOnTap() },
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.05f) {
                                scale = 1f
                                offsetX = 0f
                            } else {
                                scale = 2.5f
                                val maxX = horizontalPanLimit(scale)
                                offsetX = ((tapOffset.x - width / 2f) * (1f - scale))
                                    .coerceIn(-maxX, maxX)
                            }
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                    },
                contentPadding = innerPadding,
                userScrollEnabled = scale <= 1.05f,
            ) {
                items(pages, key = { it.pageIndex }) { page ->
                    val context = LocalContext.current
                    var retryCount by remember(page.pageUri) { mutableIntStateOf(0) }
                    val imageRequest = remember(page.pageUri, retryCount, context) {
                        ImageRequest.Builder(context)
                            .data(Uri.parse(page.pageUri))
                            .memoryCacheKey("${page.pageUri}_retry_$retryCount")
                            .diskCacheKey("${page.pageUri}_retry_$retryCount")
                            .crossfade(false)
                            .build()
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier.widthIn(max = maxPageWidth).fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                            loading = {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(0.7f).background(Color.Black),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            error = {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(0.7f).background(Color.Black),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Text(t("Page failed to load", "Halaman gagal dimuat"), color = Color.White, fontSize = 12.sp)
                                        Button(onClick = { retryCount++ }) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(t("Retry", "Coba lagi"))
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (settings.verticalPageGap != "none") {
                        Spacer(Modifier.height(if (settings.verticalPageGap == "small") 4.dp else 12.dp))
                    }
                }
            }
        }


        var isDraggingScrollbar by remember { mutableStateOf(false) }
        val scrollbarVisible = showScrollbar
        LaunchedEffect(showScrollbar) {
            if (!showScrollbar) isDraggingScrollbar = false
        }
        val scrollbarAlpha by animateFloatAsState(
            targetValue = if (scrollbarVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "scrollbar_alpha",
        )
        if (scale <= 1.05f && pages.isNotEmpty() && (showScrollbar || scrollbarAlpha > 0.01f)) {
            val totalCount = pages.size.coerceAtLeast(1)
            val progress by remember(listState, totalCount) {
                derivedStateOf {
                    if (!listState.canScrollForward) {
                        1f
                    } else if (!listState.canScrollBackward && listState.firstVisibleItemScrollOffset == 0) {
                        0f
                    } else {
                        val maxIndex = (totalCount - 1).coerceAtLeast(1)
                        val itemSize = (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: DEFAULT_PAGE_HEIGHT_PX).coerceAtLeast(1)
                        val itemProgress = (listState.firstVisibleItemScrollOffset.toFloat() / itemSize).coerceIn(0f, 1f)
                        ((listState.firstVisibleItemIndex.toFloat() + itemProgress) / maxIndex.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
            val compactHeight = maxHeight < 600.dp
            val seekScope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = if (compactHeight) 64.dp else 110.dp,
                        bottom = if (compactHeight) 64.dp else 154.dp,
                        end = 6.dp,
                    )
                    .width(36.dp)
                    .graphicsLayer { alpha = scrollbarAlpha },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val thumbHeight = 56.dp
                    val maxOffset = maxHeight - thumbHeight
                    val currentOffset = maxOffset * progress
                    val thumbPx = with(density) { thumbHeight.toPx() }
                    val trackPx = with(density) { maxHeight.toPx() }
                    fun seekTo(y: Float) {
                        val fraction = ((y - thumbPx / 2f) / (trackPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val target = fraction * totalCount
                        val idx = target.toInt().coerceIn(0, totalCount - 1)
                        val within = (target - idx).coerceIn(0f, 1f)
                        val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: DEFAULT_PAGE_HEIGHT_PX
                        seekScope.launch { listState.scrollToItem(idx, (within * itemSize).toInt()) }
                    }

                    val touchModifier = if (scrollbarVisible) {
                        Modifier
                            .fillMaxSize()
                            .pointerInput(totalCount, trackPx) {
                                detectTapGestures(onTap = { seekTo(it.y) })
                            }
                            .pointerInput(totalCount, trackPx) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        isDraggingScrollbar = true
                                        seekTo(offset.y)
                                    },
                                    onDragEnd = { isDraggingScrollbar = false },
                                    onDragCancel = { isDraggingScrollbar = false },
                                    onVerticalDrag = { change, _ ->
                                        change.consume()
                                        seekTo(change.position.y)
                                    },
                                )
                            }
                    } else {
                        Modifier.fillMaxSize()
                    }
                    Box(modifier = touchModifier) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = currentOffset)
                                .size(width = if (isDraggingScrollbar) 24.dp else 20.dp, height = thumbHeight)
                                .background(ReaderAccent, RoundedCornerShape(12.dp))
                        )
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
    var pageIndex by remember(pages) { mutableIntStateOf(initialIndex) }
    var turningDirection by remember { mutableIntStateOf(0) }
    var dragDirection by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var turnFromIndex by remember { mutableIntStateOf(initialIndex) }
    var turnTargetIndex by remember(pages) { mutableIntStateOf(initialIndex) }
    var isCurrentPageZoomed by remember(pageIndex) { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var queuedTapTurns by remember(pages) { mutableIntStateOf(0) }
    val turnProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var turnJob by remember { mutableStateOf<Job?>(null) }
    val currentOnTap by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    fun startTurn(direction: Int) {
        if (direction == 0) return
        if (turningDirection != 0 || turnJob?.isActive == true) {
            queuedTapTurns = (queuedTapTurns + direction).coerceIn(-pages.size, pages.size)
            return
        }
        val targetIndex = (pageIndex + direction).coerceIn(0, pages.lastIndex)
        if (targetIndex == pageIndex) return
        turnFromIndex = pageIndex
        turnTargetIndex = targetIndex
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
            turnTargetIndex = targetIndex
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
                turnTargetIndex = targetIndex
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
                if (targetIndex >= 0) {
                    pageIndex = targetIndex
                    turnTargetIndex = targetIndex
                }
                onJumpHandled()
            }
        }
    }
    LaunchedEffect(pageIndex, pages) {
        pages.getOrNull(pageIndex)?.let { onProgressUpdate(it.pageIndex) }
    }
    if (pages.isEmpty()) return
    PreloadComicPages(pages, (pageIndex - 1)..(pageIndex + 1))
    if (turningDirection != 0) {
        PreloadComicPages(pages, turnTargetIndex..turnTargetIndex)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        val currentPage = pages.getOrNull(if (turningDirection == 0) pageIndex else turnFromIndex)
        val currentPagePainter = rememberPageMeasurePainter(currentPage)
        val intrinsic = currentPagePainter?.intrinsicSize
        val pageWidth = if (
            intrinsic != null && intrinsic.width.isFinite() && intrinsic.height.isFinite() &&
            intrinsic.width > 0f && intrinsic.height > 0f
        ) {
            minOf(maxWidth, maxHeight * (intrinsic.width / intrinsic.height))
        } else {
            maxWidth
        }
        val pageWidthPx = with(density) { pageWidth.toPx() }.coerceAtLeast(1f)
        val currentPageWidthPx by rememberUpdatedState(pageWidthPx)
        val currentPageIndex by rememberUpdatedState(pageIndex)
        val currentTurnFromIndex by rememberUpdatedState(turnFromIndex)
        val currentLastIndex by rememberUpdatedState(pages.lastIndex)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageIndex, pages.size, isCurrentPageZoomed) {
                    detectTapGestures { position ->
                        if (isCurrentPageZoomed) {
                            currentOnTap()
                            return@detectTapGestures
                        }
                        val fraction = position.x / size.width
                        when {
                            fraction < 0.4f -> startTurn(-1)
                            fraction > 0.6f -> startTurn(1)
                            else -> currentOnTap()
                        }
                    }
                }
                .pointerInput(pageIndex, pages.size, isCurrentPageZoomed) {
                    var ignoreGesture = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            ignoreGesture = turningDirection != 0 || turnJob?.isActive == true || isCurrentPageZoomed
                            dragDirection = 0
                            dragDistance = 0f
                            dragProgress = 0f
                            velocityTracker.resetTracking()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (ignoreGesture) return@detectHorizontalDragGestures
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragDistance += dragAmount
                            if (dragDirection == 0) {
                                val requestedDirection = if (dragDistance < 0f) 1 else -1
                                val targetIdx = currentPageIndex + requestedDirection
                                if (targetIdx in 0..currentLastIndex) {
                                    dragDirection = requestedDirection
                                    turnFromIndex = currentPageIndex
                                    turnTargetIndex = targetIdx
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
                                val velocityX = velocityTracker.calculateVelocity().x
                                val flingThreshold = with(density) { 600.dp.toPx() }
                                val isFling = abs(velocityX) > flingThreshold && ((direction > 0 && velocityX < 0) || (direction < 0 && velocityX > 0))
                                val shouldCommit = progress >= 0.28f || isFling
                                turnJob?.cancel()
                                turnJob = coroutineScope.launch {
                                    turnProgress.snapTo(progress)
                                    dragDirection = 0
                                    turnProgress.animateTo(
                                        if (shouldCommit) 1f else 0f,
                                        tween(280, easing = FastOutSlowInEasing),
                                    )
                                    if (shouldCommit) pageIndex = turnTargetIndex
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
            Box(
                Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val shownPage = pages.getOrNull(if (turningDirection == 0) pageIndex else turnFromIndex)
                val targetPage = pages.getOrNull(if (turningDirection != 0) turnTargetIndex else pageIndex)
                if (turningDirection != 0) {
                    val progress: () -> Float = { if (dragDirection != 0) dragProgress else turnProgress.value }
                    when (pageTransition) {
                        "slide" -> {
                            CurlComicPage(
                                targetPage,
                                Modifier.fillMaxSize().graphicsLayer {
                                    translationX = turningDirection * size.width * (1f - progress())
                                },
                            )
                            CurlComicPage(
                                shownPage,
                                Modifier.fillMaxSize().graphicsLayer {
                                    translationX = -turningDirection * size.width * progress()
                                },
                            )
                        }
                        "fade" -> {
                            CurlComicPage(
                                targetPage,
                                Modifier.fillMaxSize().graphicsLayer { alpha = progress() },
                            )
                            CurlComicPage(
                                shownPage,
                                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress() },
                            )
                        }
                        else -> {
                            val isBackwardTurn = turningDirection < 0
                            val curlProgress: () -> Float = {
                                if (isBackwardTurn) 1f - progress() else progress()
                            }
                            CurlComicPage(
                                if (isBackwardTurn) shownPage else targetPage,
                                Modifier.fillMaxSize(),
                            )
                            SinglePageCurlSurface(
                                frontPage = if (isBackwardTurn) targetPage else shownPage,
                                backPage = if (isBackwardTurn) shownPage else targetPage,
                                direction = 1,
                                progress = curlProgress,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    CurlComicPage(
                        shownPage,
                        Modifier.fillMaxSize(),
                        onScaleChange = { isCurrentPageZoomed = it > 1.05f },
                    )
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
    imageTranslationX: Float = 0f,
    onScaleChange: ((Float) -> Unit)? = null,
) {
    Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (page != null) {
            key(page.pageIndex) {
                ZoomableMediaBox(
                    modifier = Modifier.fillMaxSize(),
                    enableTapGestures = false,
                    onScaleChange = onScaleChange,
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
                                translationX = offset.x + imageTranslationX,
                                translationY = offset.y,
                            ),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreloadComicPages(pages: List<ComicPage>, pageSlots: IntRange) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val pageUris = remember(pages, pageSlots) {
        pageSlots.mapNotNull(pages::getOrNull).map(ComicPage::pageUri).distinct()
    }
    LaunchedEffect(imageLoader, pageUris) {
        pageUris.forEach { pageUri ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(Uri.parse(pageUri))
                    .crossfade(false)
                    .build(),
            )
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
private fun rememberPageMeasurePainter(page: ComicPage?): Painter? {
    if (page == null) return null
    val context = LocalContext.current
    val request = remember(page.pageUri, context) {
        ImageRequest.Builder(context)
            .data(Uri.parse(page.pageUri))
            .size(512, 512)
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
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val frontAlignment = if (direction > 0) Alignment.CenterStart else Alignment.CenterEnd
    val backAlignment = if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart
    val frontPainter = rememberCurlPagePainter(frontPage)
    val backPainter = rememberCurlPagePainter(backPage)
    Canvas(modifier) {
        val progressValue = progress()
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
            val curlPhase = ((progressValue - sweepStart) / sweepWidth).coerceIn(0f, 1f)
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
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val frontAlignment = if (direction > 0) Alignment.CenterStart else Alignment.CenterEnd
    val backAlignment = if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart
    val frontPainter = rememberCurlPagePainter(frontPage)
    val backPainter = rememberCurlPagePainter(backPage)
    Canvas(modifier) {
        val progressValue = progress()
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
            val curlPhase = ((progressValue - sweepStart) / sweepWidth).coerceIn(0f, 1f)
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
    isRtl: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val initialSlot = remember(pages, initialPage) {
            pages.indexOfFirst { it.pageIndex == initialPage }.coerceAtLeast(0)
        }
        val initialSpread = bookSpreadForSlot(initialSlot)
        val spreadCount = pages.size / 2 + 1
        var spreadIndex by remember(pages) { mutableIntStateOf(initialSpread) }
        var turningDirection by remember { mutableIntStateOf(0) }
        var dragDirection by remember { mutableIntStateOf(0) }
        var dragDistance by remember { mutableFloatStateOf(0f) }
        var dragProgress by remember { mutableFloatStateOf(0f) }
        var turnFromSpread by remember { mutableIntStateOf(initialSpread) }
        var turnTargetSpread by remember(pages) { mutableIntStateOf(initialSpread) }
        var queuedTapTurns by remember(pages) { mutableIntStateOf(0) }
        var isCurrentLeftZoomed by remember { mutableStateOf(false) }
        var isCurrentRightZoomed by remember { mutableStateOf(false) }
        val isAnyZoomed = isCurrentLeftZoomed || isCurrentRightZoomed
        val turnProgress = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()
        var turnJob by remember { mutableStateOf<Job?>(null) }
        val density = LocalDensity.current
        val currentOnTap by rememberUpdatedState(onTap)
        val velocityTracker = remember { VelocityTracker() }

        fun startTurn(direction: Int) {
            if (direction == 0) return
            if (turningDirection != 0 || turnJob?.isActive == true) {
                queuedTapTurns = (queuedTapTurns + direction).coerceIn(-spreadCount, spreadCount)
                return
            }
            val targetSpread = (spreadIndex + direction).coerceIn(0, spreadCount - 1)
            if (targetSpread == spreadIndex) return
            turnFromSpread = spreadIndex
            turnTargetSpread = targetSpread
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
                turnTargetSpread = targetSpread
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
                        turnTargetSpread = targetSpread
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
                        turnTargetSpread = targetSpread
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
            (pages.getOrNull(leftPageSlotForSpread(spreadIndex, isRtl))
                ?: pages.getOrNull(rightPageSlotForSpread(spreadIndex, isRtl)))
                ?.let { onProgressUpdate(it.pageIndex) }
        }
        if (pages.isEmpty()) return@BoxWithConstraints
        val destinationSpread = if (turningDirection != 0) turnTargetSpread else spreadIndex
        val shownSpread = if (turningDirection == 0) spreadIndex else turnFromSpread
        val preloadSpread = if (turningDirection == 0) spreadIndex else turnTargetSpread
        PreloadComicPages(
            pages,
            (leftPageSlotForSpread(preloadSpread - 1, isRtl))..rightPageSlotForSpread(preloadSpread + 1, isRtl),
        )
        val currentLeftPage = pages.getOrNull(leftPageSlotForSpread(shownSpread, isRtl))
        val currentRightPage = pages.getOrNull(rightPageSlotForSpread(shownSpread, isRtl))
        val destinationLeftPage = pages.getOrNull(leftPageSlotForSpread(destinationSpread, isRtl))
        val destinationRightPage = pages.getOrNull(rightPageSlotForSpread(destinationSpread, isRtl))
        val pageProgress: () -> Float = {
            when {
                turningDirection == 0 -> 0f
                dragDirection != 0 -> dragProgress
                else -> turnProgress.value
            }
        }
        val underlayLeft = when {
            turningDirection == 0 -> currentLeftPage
            isRtl -> if (turningDirection > 0) destinationLeftPage else currentLeftPage
            else -> if (turningDirection < 0) destinationLeftPage else currentLeftPage
        }
        val underlayRight = when {
            turningDirection == 0 -> currentRightPage
            isRtl -> if (turningDirection < 0) destinationRightPage else currentRightPage
            else -> if (turningDirection > 0) destinationRightPage else currentRightPage
        }
        val turningFront = when {
            isRtl -> if (turningDirection > 0) currentLeftPage else currentRightPage
            else -> if (turningDirection > 0) currentRightPage else currentLeftPage
        }
        val turningBack = when {
            isRtl -> if (turningDirection > 0) destinationRightPage else destinationLeftPage
            else -> if (turningDirection > 0) destinationLeftPage else destinationRightPage
        }
        val layoutDirection = LocalLayoutDirection.current
        val contentWidth = maxWidth - innerPadding.calculateLeftPadding(layoutDirection) - innerPadding.calculateRightPadding(layoutDirection)
        val contentHeight = maxHeight - innerPadding.calculateTopPadding() - innerPadding.calculateBottomPadding()
        val pageWidthPx = with(density) { (contentWidth / 2).toPx() }.coerceAtLeast(1f)
        val pageHeightPx = with(density) { contentHeight.toPx() }.coerceAtLeast(1f)
        val gesturePainter = rememberPageMeasurePainter(turningFront)
        val currentLeftPainter = rememberPageMeasurePainter(currentLeftPage)
        val currentRightPainter = rememberPageMeasurePainter(currentRightPage)
        val destinationLeftPainter = rememberPageMeasurePainter(destinationLeftPage)
        val destinationRightPainter = rememberPageMeasurePainter(destinationRightPage)
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
        fun fittedImageWidth(page: ComicPage?, painter: Painter?): Float {
            if (page == null) return 0f
            val intrinsic = painter?.intrinsicSize
            if (intrinsic == null || !intrinsic.width.isFinite() || !intrinsic.height.isFinite() || intrinsic.width <= 0f || intrinsic.height <= 0f) {
                return pageWidthPx
            }
            return intrinsic.width * minOf(pageWidthPx / intrinsic.width, pageHeightPx / intrinsic.height)
        }
        fun spreadImageBounds(leftWidth: Float, rightWidth: Float): Pair<Float, Float> =
            (pageWidthPx - leftWidth) to (pageWidthPx + rightWidth)
        val (currentImageLeft, currentImageRight) = spreadImageBounds(
            fittedImageWidth(currentLeftPage, currentLeftPainter),
            fittedImageWidth(currentRightPage, currentRightPainter),
        )
        val (destinationImageLeft, destinationImageRight) = spreadImageBounds(
            fittedImageWidth(destinationLeftPage, destinationLeftPainter),
            fittedImageWidth(destinationRightPage, destinationRightPainter),
        )
        val viewportWidth = pageWidthPx * 2f
        val slideDragWidthPx = maxOf(
            currentImageRight,
            viewportWidth - currentImageLeft,
            viewportWidth - destinationImageLeft,
            destinationImageRight,
        ).coerceAtLeast(1f)
        val currentDragWidthPx by rememberUpdatedState(
            if (pageTransition == "slide") slideDragWidthPx else dragWidthPx,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .clipToBounds()
                .pointerInput(spreadIndex, pages.size, isRtl, isAnyZoomed) {
                    detectTapGestures { position ->
                        if (isAnyZoomed) {
                            currentOnTap()
                            return@detectTapGestures
                        }
                        val fraction = position.x / size.width
                        val tapDirection = if (isRtl) {
                            when {
                                fraction < 0.4f -> 1
                                fraction > 0.6f -> -1
                                else -> 0
                            }
                        } else {
                            when {
                                fraction < 0.4f -> -1
                                fraction > 0.6f -> 1
                                else -> 0
                            }
                        }
                        if (tapDirection != 0) {
                            startTurn(tapDirection)
                        } else {
                            currentOnTap()
                        }
                    }
                }
                .pointerInput(spreadIndex, pages.size, isRtl, isAnyZoomed) {
                    var ignoreGesture = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                            ignoreGesture = turningDirection != 0 || turnJob?.isActive == true || isAnyZoomed
                            dragDirection = 0
                            dragDistance = 0f
                            dragProgress = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (ignoreGesture) return@detectHorizontalDragGestures
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragDistance += dragAmount
                            if (dragDirection == 0) {
                                val requestedDirection = if (isRtl) {
                                    if (dragDistance > 0f) 1 else -1
                                } else {
                                    if (dragDistance < 0f) 1 else -1
                                }
                                val target = (spreadIndex + requestedDirection).coerceIn(0, spreadCount - 1)
                                if (target != spreadIndex) {
                                    dragDirection = requestedDirection
                                    turnFromSpread = spreadIndex
                                    turnTargetSpread = target
                                    turningDirection = requestedDirection
                                }
                            }
                            if (dragDirection != 0) {
                                val traveled = if (isRtl) {
                                    if (dragDirection > 0) dragDistance else -dragDistance
                                } else {
                                    if (dragDirection > 0) -dragDistance else dragDistance
                                }
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
                                val velocityX = velocityTracker.calculateVelocity().x
                                val flingThreshold = with(density) { 600.dp.toPx() }
                                val isFling = abs(velocityX) > flingThreshold && if (isRtl) {
                                    (direction > 0 && velocityX > 0) || (direction < 0 && velocityX < 0)
                                } else {
                                    (direction > 0 && velocityX < 0) || (direction < 0 && velocityX > 0)
                                }
                                val shouldCommit = progress >= 0.28f || isFling
                                val targetSpread = turnTargetSpread
                                turnJob?.cancel()
                                turnJob = coroutineScope.launch {
                                    turnProgress.snapTo(progress)
                                    dragDirection = 0
                                    turnProgress.animateTo(
                                        if (shouldCommit) 1f else 0f,
                                        tween(280, easing = FastOutSlowInEasing),
                                    )
                                    if (shouldCommit) spreadIndex = targetSpread
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
            if (turningDirection != 0 && pageTransition == "slide") {
                val slideDir = if (isRtl) -turningDirection else turningDirection
                val currentSlideDistance = if (slideDir > 0) {
                    currentImageRight
                } else {
                    viewportWidth - currentImageLeft
                }
                val destinationSlideDistance = if (slideDir > 0) {
                    viewportWidth - destinationImageLeft
                } else {
                    destinationImageRight
                }
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = -slideDir * currentSlideDistance * pageProgress()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CurlComicPage(
                        page = currentLeftPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterEnd,
                        onScaleChange = { isCurrentLeftZoomed = it > 1.05f },
                    )
                    CurlComicPage(
                        page = currentRightPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterStart,
                        onScaleChange = { isCurrentRightZoomed = it > 1.05f },
                    )
                }
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = slideDir * destinationSlideDistance * (1f - pageProgress())
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
            } else if (turningDirection != 0 && pageTransition == "fade") {
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - pageProgress()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CurlComicPage(
                        page = currentLeftPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterEnd,
                        onScaleChange = { isCurrentLeftZoomed = it > 1.05f },
                    )
                    CurlComicPage(
                        page = currentRightPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterStart,
                        onScaleChange = { isCurrentRightZoomed = it > 1.05f },
                    )
                }
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = pageProgress()
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
                        onScaleChange = { isCurrentLeftZoomed = it > 1.05f },
                    )
                    CurlComicPage(
                        page = underlayRight,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageAlignment = Alignment.CenterStart,
                        onScaleChange = { isCurrentRightZoomed = it > 1.05f },
                    )
                }
                if (turningDirection != 0) {
                    DoublePageCurlSurface(
                        frontPage = turningFront,
                        backPage = turningBack,
                        direction = if (isRtl) -turningDirection else turningDirection,
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
private fun leftPageSlotForSpread(spreadIndex: Int, isRtl: Boolean = false): Int =
    if (spreadIndex == 0) -1 else if (isRtl) spreadIndex * 2 else spreadIndex * 2 - 1
private fun rightPageSlotForSpread(spreadIndex: Int, isRtl: Boolean = false): Int =
    if (spreadIndex == 0) 0 else if (isRtl) spreadIndex * 2 - 1 else spreadIndex * 2
private fun pageFileName(page: ComicPage): String =
    (Uri.parse(page.pageUri).lastPathSegment ?: page.pageUri)
        .substringAfterLast('/')
        .lowercase()
private fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ei = i
            while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j
            while (ej < b.length && b[ej].isDigit()) ej++
            val na = a.substring(i, ei).trimStart('0')
            val nb = b.substring(j, ej).trimStart('0')
            if (na.length != nb.length) return na.length.compareTo(nb.length)
            val c = na.compareTo(nb)
            if (c != 0) return c
            i = ei
            j = ej
        } else {
            val c = ca.compareTo(cb)
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}
@Composable
private fun VerticalModeIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        val rectWidth = size.width * 0.7f
        val rectHeight = size.height * 0.34f
        val left = (size.width - rectWidth) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, size.height * 0.1f),
            size = Size(rectWidth, rectHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, size.height * 0.56f),
            size = Size(rectWidth, rectHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun SinglePageModeIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val rectWidth = size.width * 0.54f
        val rectHeight = size.height * 0.8f
        val left = (size.width - rectWidth) / 2f
        val top = (size.height - rectHeight) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun DoublePageModeIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        val gap = 2.dp.toPx()
        val rectWidth = (size.width * 0.82f - gap) / 2f
        val rectHeight = size.height * 0.7f
        val startX = (size.width - (rectWidth * 2f + gap)) / 2f
        val top = (size.height - rectHeight) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX + rectWidth + gap, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )
    }
}


@Composable
private fun ReaderActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ReaderBar,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ReaderAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ReaderSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = ReaderMuted,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun <T> ReaderSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(ReaderBar)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSelected) ReaderAccent else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) ReaderOnAccent else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}


@Composable
private fun BoxScope.ReaderSidePanel(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.77f)
            .widthIn(max = 380.dp)
            .background(ReaderPanel)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = t("Close", "Tutup"),
                        tint = Color.White,
                    )
                }
            }
            content()
        }
    }
}
