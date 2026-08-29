package com.mediadeck.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.ZoomableMediaBox
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.ComicViewModel
import kotlinx.coroutines.launch

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

    if (activeComic == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var readerMode by remember { mutableStateOf(settings.defaultReaderMode) }
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

    var showSidebar by remember { mutableStateOf(false) }
    var jumpToPage by remember { mutableStateOf<Int?>(null) }
    val sidebarListState = rememberLazyListState()
    
    LaunchedEffect(showSidebar, activeComic?.currentPage) {
        if (showSidebar) {
            val currentPage = activeComic?.currentPage ?: 1
            val targetIdx = processedPages.indexOfFirst { it.pageIndex == currentPage }
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
                            val nextIdx = (activeComic?.currentPage ?: 1) + 1
                            if (nextIdx <= processedPages.size) {
                                viewModel.updateReadingProgress(nextIdx)
                                jumpToPage = nextIdx
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            val prevIdx = (activeComic?.currentPage ?: 1) - 1
                            if (prevIdx >= 1) {
                                viewModel.updateReadingProgress(prevIdx)
                                jumpToPage = prevIdx
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
                    initialPage = activeComic?.currentPage ?: 1,
                    initialOffset = activeComic?.scrollOffset ?: 0,
                    innerPadding = PaddingValues(0.dp),
                    onProgressUpdate = { idx, offset -> viewModel.updateReadingProgress(idx, offset) },
                    onTap = onPageTap,
                    jumpToPage = jumpToPage,
                    onJumpHandled = { jumpToPage = null },
                    settings = settings,
                )
                "horizontal_single" -> HorizontalSingleReader(
                    pages = processedPages,
                    initialPage = activeComic?.currentPage ?: 1,
                    innerPadding = PaddingValues(0.dp),
                    onProgressUpdate = { idx -> viewModel.updateReadingProgress(idx) },
                    onTap = onPageTap,
                    jumpToPage = jumpToPage,
                    onJumpHandled = { jumpToPage = null },
                )
                "horizontal_double" -> HorizontalDoubleReader(
                    pages = processedPages,
                    initialPage = activeComic?.currentPage ?: 1,
                    innerPadding = PaddingValues(0.dp),
                    onProgressUpdate = { idx -> viewModel.updateReadingProgress(idx) },
                    onTap = onPageTap,
                    jumpToPage = jumpToPage,
                    onJumpHandled = { jumpToPage = null },
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
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${activeComic?.currentPage ?: 1} / ${activeComic?.totalPages ?: 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(
                            "vertical" to Icons.Default.FormatLineSpacing,
                            "horizontal_single" to Icons.Default.Description,
                            "horizontal_double" to Icons.Default.AutoStories,
                        ).forEach { (mode, icon) ->
                            IconButton(onClick = { readerMode = mode }) {
                                Icon(icon, null, tint = if (readerMode == mode) MaterialTheme.colorScheme.primary else Color.White)
                            }
                        }
                    }
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
            modifier = Modifier.align(Alignment.CenterEnd).width(220.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.95f)),
        ) {
            Column {
                Text(t("Page Directory", "Daftar Halaman"), color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                LazyColumn(state = sidebarListState, modifier = Modifier.weight(1f)) {
                    items(processedPages) { page ->
                        val isCurrent = (activeComic?.currentPage ?: 1) == page.pageIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    viewModel.updateReadingProgress(page.pageIndex)
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
                                t("p.${page.pageIndex}", "hal.${page.pageIndex}"),
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

    LaunchedEffect(Unit) {
        if (initialPage > 1 || initialOffset > 0) {
            val idx = pages.indexOfFirst { p -> p.pageIndex == initialPage }
            if (idx >= 0) listState.scrollToItem(idx, initialOffset)
        }
    }

    LaunchedEffect(jumpToPage) {
        jumpToPage?.let {
            val idx = pages.indexOfFirst { p -> p.pageIndex == it }
            if (idx >= 0) listState.scrollToItem(idx)
            onJumpHandled()
        }
    }

    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstVisibleOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

    LaunchedEffect(firstVisibleIndex, firstVisibleOffset) {
        if (pages.isNotEmpty() && firstVisibleIndex < pages.size) {
            onProgressUpdate(pages[firstVisibleIndex].pageIndex, firstVisibleOffset)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = this.constraints.maxWidth.toFloat()
        val state = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            val maxX = (width * (scale - 1) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)

            if (scale > 1.05f && panChange.y != 0f) {
                coroutineScope.launch {
                    listState.scrollBy(-panChange.y)
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(page.pageUri))
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
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
    onJumpHandled: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = (initialPage - 1).coerceIn(0, pages.size - 1), pageCount = { pages.size })
    var isPagerScrollEnabled by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(jumpToPage) {
        jumpToPage?.let { pagerState.scrollToPage(it - 1); onJumpHandled() }
    }
    LaunchedEffect(pagerState.currentPage) {
        onProgressUpdate(pagerState.currentPage + 1)
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
        userScrollEnabled = isPagerScrollEnabled,
        beyondViewportPageCount = 4, 
    ) { page ->
        ZoomableMediaBox(
            modifier = Modifier.fillMaxSize(),
            onTap = onTap,
            onScaleChange = { isPagerScrollEnabled = it <= 1.05f },
            onOverSwipeHorizontal = { delta ->
                coroutineScope.launch {
                    pagerState.scrollBy(delta)
                }
            },
            onDragStopped = {
                coroutineScope.launch {
                    val targetPage = if (pagerState.currentPageOffsetFraction > 0.5f) {
                        pagerState.currentPage + 1
                    } else if (pagerState.currentPageOffsetFraction < -0.5f) {
                        pagerState.currentPage - 1
                    } else {
                        pagerState.currentPage
                    }
                    if (targetPage in 0 until pages.size) {
                        pagerState.animateScrollToPage(targetPage)
                    } else {
                        pagerState.animateScrollToPage(pagerState.currentPage)
                    }
                }
            },
        ) { scale, offset ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(pages[page].pageUri))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
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

@Composable
fun HorizontalDoubleReader(
    pages: List<ComicPage>,
    initialPage: Int,
    innerPadding: PaddingValues,
    onProgressUpdate: (Int) -> Unit,
    onTap: () -> Unit,
    jumpToPage: Int?,
    onJumpHandled: () -> Unit,
) {
    val pagePairs = remember(pages) {
        val pairs = mutableListOf<List<ComicPage>>()
        var i = 0
        while (i < pages.size) {
            if (i == 0) {
                pairs.add(listOf(pages[0]))
                i++
            } else {
                val p = mutableListOf<ComicPage>()
                p.add(pages[i])
                if (i + 1 < pages.size) {
                    p.add(pages[i + 1])
                    i += 2
                } else {
                    i++
                }
                pairs.add(p)
            }
        }
        pairs
    }

    val initialIdx = remember(pagePairs, initialPage) {
        val idx = pagePairs.indexOfFirst { pair -> pair.any { it.pageIndex == initialPage } }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIdx, pageCount = { pagePairs.size })
    var isPagerScrollEnabled by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(jumpToPage) {
        jumpToPage?.let { target ->
            val idx = pagePairs.indexOfFirst { pair -> pair.any { it.pageIndex == target } }
            if (idx >= 0) pagerState.scrollToPage(idx)
            onJumpHandled()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        pagePairs.getOrNull(pagerState.currentPage)?.firstOrNull()?.let {
            onProgressUpdate(it.pageIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
        pageSpacing = 8.dp,
        userScrollEnabled = isPagerScrollEnabled,
        beyondViewportPageCount = 2, 
    ) { pageIdx ->
        val pair = pagePairs.getOrNull(pageIdx) ?: return@HorizontalPager
        ZoomableMediaBox(
            modifier = Modifier.fillMaxSize(),
            onTap = onTap,
            onScaleChange = { isPagerScrollEnabled = it <= 1.05f },
            onOverSwipeHorizontal = { delta ->
                coroutineScope.launch {
                    pagerState.scrollBy(delta)
                }
            },
            onDragStopped = {
                coroutineScope.launch {
                    val targetPage = if (pagerState.currentPageOffsetFraction > 0.5f) {
                        pagerState.currentPage + 1
                    } else if (pagerState.currentPageOffsetFraction < -0.5f) {
                        pagerState.currentPage - 1
                    } else {
                        pagerState.currentPage
                    }
                    if (targetPage in 0 until pagePairs.size) {
                        pagerState.animateScrollToPage(targetPage)
                    } else {
                        pagerState.animateScrollToPage(pagerState.currentPage)
                    }
                }
            },
        ) { scale, offset ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pair.forEach { page ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(page.pageUri))
                                .crossfade(false) 
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}
