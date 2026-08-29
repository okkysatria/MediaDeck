package com.mediadeck.app.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size as CoilSize
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.ui.components.DeleteConfirmationDialog
import com.mediadeck.app.ui.components.ZoomableMediaBox
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.smb.SmbContentProvider
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.MovieViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
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

    val initialIndex = remember(itemId, items) {
        items.indexOfFirst { it.id == itemId }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
    ) { items.size }

    var isPagerScrollEnabled by remember { mutableStateOf(true) }

    val currentIndex = pagerState.currentPage
    val currentItem = remember(currentIndex, items) {
        if (items.isNotEmpty() && currentIndex < items.size) items[currentIndex] else null
    }

    if (currentItem == null) {
        LaunchedEffect(Unit) {
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
    var isFitMode by remember(currentIndex) { mutableStateOf(value = true) }
    var isMuted by remember { mutableStateOf(value = false) }
    var rotationDegrees by remember(currentIndex) { mutableFloatStateOf(0f) }
    var showSidebarDetail by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = Color.Black,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
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
                                pagerState.animateScrollToPage(items.size - 1)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) { innerPadding ->
        @Suppress("UNUSED_VARIABLE")
        val ignoredPadding = innerPadding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                userScrollEnabled = isPagerScrollEnabled,
            ) { page ->
                val pageItem = items.getOrNull(page)
                if (pageItem != null) {
                    GalleryPageItem(
                        item = pageItem,
                        isFitMode = isFitMode,
                        isMuted = isMuted,
                        rotation = if (page == currentIndex) rotationDegrees else 0f,
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

            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Text(currentItem.name, fontSize = 16.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleGalleryFavorite(currentItem) }) {
                            Icon(
                                if (currentItem.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = t("Favorite", "Favorit"),
                                tint = if (currentItem.isFavorite) Color.Red else Color.White,
                            )
                        }
                        IconButton(onClick = { showSidebarDetail = !showSidebarDetail }) {
                            Icon(Icons.Outlined.Info, contentDescription = t("Info & Album Details", "Info & Album"))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = t("Delete", "Hapus"))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
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
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                t("Item ${currentIndex + 1} of ${items.size}", "Item ${currentIndex + 1} dari ${items.size}"),
                                color = Color.White,
                                fontSize = 14.sp,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        val uri = currentItem.uri
                                        val name = currentItem.name
                                        if (uri.startsWith("smb://") || uri.startsWith("content://${SmbContentProvider.AUTHORITY}")) {
                                            movieViewModel.downloadSmbFile(context, uri, name)
                                        } else {
                                            android.widget.Toast.makeText(context, if (settings.language == "en") "File is already stored locally!" else "File sudah tersimpan secara lokal!", android.widget.Toast.LENGTH_SHORT).show()
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
                visible = showUi && !showSidebarDetail,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
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
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                val safeItem = currentItem
                if (safeItem != null) {
                    val sizeKb = safeItem.size / 1024
                    val sizeMb = sizeKb.toDouble() / 1024
                    val sizeFormatted = if (sizeMb > 1.0) "%.1f MB".format(sizeMb) else "$sizeKb KB"

                    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()) }
                    val dateText = remember(safeItem.dateAdded) { dateFormat.format(Date(safeItem.dateAdded)) }

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
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
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .width(40.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    t("Details & Album", "Detail & Album"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { showSidebarDetail = false }) {
                                    Icon(Icons.Default.Close, contentDescription = t("Close", "Tutup"))
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(t("File Name:", "Nama File:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(safeItem.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(t("Format:", "Format:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(safeItem.mimeType, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(t("Size:", "Ukuran:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(sizeFormatted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(t("Dimension:", "Dimensi:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                        val dimText = if (safeItem.width > 0 && safeItem.height > 0) {
                                            "${safeItem.width} x ${safeItem.height}"
                                        } else {
                                            t("Loading...", "Memuat...")
                                        }
                                        Text(dimText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    if (safeItem.mimeType.contains("video", true) && safeItem.duration > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(t("Duration:", "Durasi:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                            Text(com.mediadeck.app.util.media.MediaUtils.formatDuration(safeItem.duration), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(t("Added On:", "Ditambahkan:"), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(dateText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = safeItem.uri,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("URL", safeItem.uri)))
                                        }
                                        android.widget.Toast.makeText(context, if (settings.language == "en") "URL path copied!" else "Jalur URL disalin!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = t("Copy", "Salin"), modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = albumNameInput,
                                        onValueChange = { albumNameInput = it },
                                        label = { Text(t("Album", "Album"), fontSize = 11.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Column(modifier = Modifier.weight(1.2f)) {
                                    OutlinedTextField(
                                        value = tagsInput,
                                        onValueChange = { tagsInput = it },
                                        label = { Text(t("Tags (comma)", "Tags (koma)"), fontSize = 11.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.updateGalleryItemDetails(
                                            safeItem,
                                            newFolderName = albumNameInput,
                                            newTags = tagsInput,
                                        )
                                        android.widget.Toast.makeText(context, if (settings.language == "en") "Saved successfully!" else "Disimpan!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .size(42.dp),
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = t("Save", "Simpan"))
                                }
                            }

                            if (safeItem.tags.isNotEmpty()) {
                                Text(
                                    t("Click Tag to Filter:", "Klik Tag untuk Menyaring:"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(safeItem.tags.split(",")) { rawTag ->
                                        val tag = rawTag.trim()
                                        if (tag.isNotEmpty()) {
                                            AssistChip(
                                                onClick = {
                                                    onFilterTag(tag)
                                                },
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
    }
}

suspend fun PointerInputScope.customDetectTransformGestures(
    getScale: () -> Float,
    isVideo: Boolean,
    onGesture: (centroid: androidx.compose.ui.geometry.Offset, pan: androidx.compose.ui.geometry.Offset, zoom: Float) -> Unit,
) {
    if (isVideo) return
    awaitEachGesture {
        var zoom = 1f
        var pan = androidx.compose.ui.geometry.Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown()
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero) {
                        onGesture(centroid, panChange, zoomChange)
                    }

                    val numFingers = event.changes.size
                    val isPinching = numFingers > 1
                    val isZoomedIn = getScale() > 1.05f

                    if (isPinching || isZoomedIn) {
                        event.changes.forEach {
                            if (it.previousPosition != it.position) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f && !isVideo) {
                                val newScale = (scale - delta * 0.12f).coerceIn(1f, 5f)
                                scale = newScale
                            }
                        }
                    }
                }
            }
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
            val context = LocalContext.current
            var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

            val exoPlayer = remember {
                androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                }
            }

            LaunchedEffect(item.uri) {
                try {
                    val mediaItem = androidx.media3.common.MediaItem.fromUri(item.uri)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            LaunchedEffect(isMuted) {
                exoPlayer.volume = if (isMuted) 0f else 1f
            }

            DisposableEffect(Unit) {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                        }
                    }
                }
                exoPlayer.addListener(listener)
                onDispose {
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
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    onRelease = { view ->
                        view.player = null
                    }
                )
            }
        } else {
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
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .precision(Precision.EXACT)
                        .build(),
                    contentDescription = item.name,
                    contentScale = if (isFitMode) ContentScale.Fit else ContentScale.None,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = boxScale,
                            scaleY = boxScale,
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
                                    text = t("SMB connection lost or image file is no longer accessible on the Samba Server.", "Koneksi SMB terputus atau berkas gambar tidak lagi dapat diakses di Server Samba."),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    text = t("Solution:\n1. Ensure the device is connected to the same local Wifi as the server.\n2. Check your Samba Server status.", "Solusi:\n1. Pastikan Perangkat terhubung ke Wifi lokal yang sama dengan server.\n2. Periksa status Server Samba Anda."),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
