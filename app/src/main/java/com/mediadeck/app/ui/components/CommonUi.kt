package com.mediadeck.app.ui.components

import android.util.LruCache
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.i18n.translate
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun rememberThumbnailUri(uriString: String, mediaId: Long, hasThumbnail: Boolean, settings: AppSettings): Uri? {
    val context = LocalContext.current
    var uri by remember(mediaId, hasThumbnail) {
        if (hasThumbnail) {
            val cacheFilename = com.mediadeck.app.util.media.VideoThumbnailHelper.getCacheFilename(mediaId, "explore")
            val file = File(context.filesDir, "thumbnails/$cacheFilename")
            mutableStateOf(Uri.fromFile(file))
        } else {
            mutableStateOf(null)
        }
    }

    if (uri == null) {
        LaunchedEffect(mediaId) {
            withContext(Dispatchers.IO) {
                com.mediadeck.app.util.media.VideoThumbnailHelper.loadThumbnail(context, uriString, mediaId, settings, "explore")
                val cacheFilename = com.mediadeck.app.util.media.VideoThumbnailHelper.getCacheFilename(mediaId, "explore")
                val file = File(context.filesDir, "thumbnails/$cacheFilename")
                if (file.exists() && file.length() > 0) {
                    uri = Uri.fromFile(file)
                }
            }
        }
    }
    return uri
}

@Composable
fun AsyncThumbnailImage(
    uriString: String,
    mediaId: Long,
    hasThumbnail: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onSuccess: ((coil.compose.AsyncImagePainter.State.Success) -> Unit)? = null
) {
    val context = LocalContext.current
    val thumbnailUri = rememberThumbnailUri(uriString, mediaId, hasThumbnail, settings)

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(thumbnailUri)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = contentScale,
            onSuccess = { onSuccess?.invoke(it) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun rememberFolderThumbnailUri(folderName: String): Uri? {
    val context = LocalContext.current
    var uri by remember(folderName) {
        val cacheFilename = com.mediadeck.app.util.media.VideoThumbnailHelper.getFolderCacheFilename(folderName)
        val file = File(context.filesDir, "thumbnails/$cacheFilename")
        mutableStateOf(if (file.exists() && file.length() > 0) Uri.fromFile(file) else null)
    }

    if (uri == null) {
        LaunchedEffect(folderName) {
            val cacheFilename = com.mediadeck.app.util.media.VideoThumbnailHelper.getFolderCacheFilename(folderName)
            val file = File(context.filesDir, "thumbnails/$cacheFilename")

            for (i in 0..120) {
                if (file.exists() && file.length() > 0) {
                    uri = Uri.fromFile(file)
                    break
                }
                delay(500.milliseconds)
            }
        }
    }
    return uri
}

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmText: String = translate(com.mediadeck.app.util.i18n.LocalLanguage.current, "Delete", "Hapus"),
    dismissText: String = translate(com.mediadeck.app.util.i18n.LocalLanguage.current, "Cancel", "Batal")
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
fun DraggableGridScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val totalItems = remember { derivedStateOf { gridState.layoutInfo.totalItemsCount } }
    val visibleItems = remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo } }
    if (totalItems.value == 0 || visibleItems.value.isEmpty()) return

    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }

    val showFull by remember { derivedStateOf { isDragging || isScrolling } }
    val width by animateDpAsState(targetValue = if (showFull) 12.dp else 4.dp, label = "width")
    val alphaVal by animateFloatAsState(targetValue = if (showFull) 0.9f else 0.3f, label = "alpha")

    val scrollbarInfo by remember {
        derivedStateOf {
            val total = totalItems.value
            val visible = visibleItems.value.size
            val first = gridState.firstVisibleItemIndex
            val ratio = (visible.toFloat() / total.toFloat()).coerceIn(0.1f, 1.0f)
            val thumbHeightPx = trackHeightPx * ratio
            val maxScroll = (total - visible).coerceAtLeast(1)
            val progress = (first.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
            val thumbOffsetPx = (trackHeightPx - thumbHeightPx) * progress
            Triple(thumbHeightPx, thumbOffsetPx, ratio)
        }
    }
    val (thumbHeightPx, thumbOffsetPx, ratio) = scrollbarInfo
    if (ratio >= 1.0f) return

    val currentThumbOffsetPx by rememberUpdatedState(thumbOffsetPx)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentTotalItems by rememberUpdatedState(totalItems.value)
    val currentVisibleSize by rememberUpdatedState(visibleItems.value.size)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .alpha(alphaVal)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(width / 2))
    ) {
        Box(
            modifier = Modifier
                .offset(y = with(density) { (thumbOffsetPx / density.density).dp })
                .fillMaxWidth()
                .height(with(density) { (thumbHeightPx / density.density).dp })
                .clip(RoundedCornerShape(width / 2))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaY = dragAmount.y
                        val newOffset = (currentThumbOffsetPx + deltaY).coerceIn(0f, currentTrackHeightPx - currentThumbHeightPx)
                        val maxScroll = (currentTotalItems - currentVisibleSize).coerceAtLeast(1)
                        val progress = newOffset / (currentTrackHeightPx - currentThumbHeightPx).coerceAtLeast(1f)
                        val targetIndex = (progress * maxScroll).toInt().coerceIn(0, currentTotalItems - 1)
                        coroutineScope.launch { gridState.scrollToItem(targetIndex) }
                    }
                }
        )
    }
}

@Composable
fun DraggableStaggeredGridScrollbar(gridState: LazyStaggeredGridState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val totalItems = remember { derivedStateOf { gridState.layoutInfo.totalItemsCount } }
    val visibleItems = remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo } }
    if (totalItems.value == 0 || visibleItems.value.isEmpty()) return

    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }

    val showFull by remember { derivedStateOf { isDragging || isScrolling } }
    val width by animateDpAsState(targetValue = if (showFull) 12.dp else 4.dp, label = "width")
    val alphaVal by animateFloatAsState(targetValue = if (showFull) 0.9f else 0.3f, label = "alpha")

    val scrollbarInfo by remember {
        derivedStateOf {
            val total = totalItems.value
            val visible = visibleItems.value.size
            val first = gridState.firstVisibleItemIndex
            val ratio = (visible.toFloat() / total.toFloat()).coerceIn(0.1f, 1.0f)
            val thumbHeightPx = trackHeightPx * ratio
            val maxScroll = (total - visible).coerceAtLeast(1)
            val progress = (first.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
            val thumbOffsetPx = (trackHeightPx - thumbHeightPx) * progress
            Triple(thumbHeightPx, thumbOffsetPx, ratio)
        }
    }
    val (thumbHeightPx, thumbOffsetPx, ratio) = scrollbarInfo
    if (ratio >= 1.0f) return

    val currentThumbOffsetPx by rememberUpdatedState(thumbOffsetPx)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentTotalItems by rememberUpdatedState(totalItems.value)
    val currentVisibleSize by rememberUpdatedState(visibleItems.value.size)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .alpha(alphaVal)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(width / 2))
    ) {
        Box(
            modifier = Modifier
                .offset(y = with(density) { (thumbOffsetPx / density.density).dp })
                .fillMaxWidth()
                .height(with(density) { (thumbHeightPx / density.density).dp })
                .clip(RoundedCornerShape(width / 2))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaY = dragAmount.y
                        val newOffset = (currentThumbOffsetPx + deltaY).coerceIn(0f, currentTrackHeightPx - currentThumbHeightPx)
                        val maxScroll = (currentTotalItems - currentVisibleSize).coerceAtLeast(1)
                        val progress = newOffset / (currentTrackHeightPx - currentThumbHeightPx).coerceAtLeast(1f)
                        val targetIndex = (progress * maxScroll).toInt().coerceIn(0, currentTotalItems - 1)
                        coroutineScope.launch { gridState.scrollToItem(targetIndex) }
                    }
                }
        )
    }
}

@Composable
fun ScanStatusCard(
    scanProgress: String?,
    isScanActive: Boolean,
    isScanPaused: Boolean,
    onTogglePause: () -> Unit,
    onStopScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (scanProgress == null) return

    var dotCount by remember { mutableIntStateOf(0) }
    if (isScanActive && !isScanPaused) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(400.milliseconds)
                dotCount = (dotCount + 1) % 4
            }
        }
    }
    val animatedDots = ".".repeat(dotCount)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scanProgress + if (isScanActive && !isScanPaused) animatedDots else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isScanActive) {
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isScanPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onStopScan,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
