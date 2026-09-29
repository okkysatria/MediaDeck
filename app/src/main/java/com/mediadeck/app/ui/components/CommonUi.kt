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
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.i18n.LocalLanguage
import com.mediadeck.app.util.i18n.translate
import coil3.request.crossfade
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
@Composable
fun rememberThumbnailUri(
    mediaId: Long,
    hasThumbnail: Boolean,
    generationVersion: Long,
    variant: String = "explore",
): Uri? {
    val context = LocalContext.current
    return remember(mediaId, hasThumbnail, generationVersion, variant) {
        val filename = com.mediadeck.app.util.media.VideoThumbnailHelper.getCacheFilename(mediaId, variant)
        File(context.filesDir, "thumbnails/$filename")
            .takeIf { it.isFile && it.length() > 0L }
            ?.let(Uri::fromFile)
    }
}
@Composable
fun AsyncThumbnailImage(
    uriString: String,
    mediaId: Long,
    hasThumbnail: Boolean,
    settings: AppSettings,
    thumbnailVariant: String = "explore",
    showProcessingIndicator: Boolean = false,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onSuccess: ((coil3.compose.AsyncImagePainter.State.Success) -> Unit)? = null
) {
    val context = LocalContext.current
    val thumbnailVersion by com.mediadeck.app.util.media.VideoThumbnailHelper.mediaThumbnailVersion(mediaId, thumbnailVariant).collectAsState()
    val isThumbnailProcessing by com.mediadeck.app.util.media.VideoThumbnailHelper.thumbnailProcessingState(mediaId, thumbnailVariant).collectAsState()
    val thumbnailUri = rememberThumbnailUri(mediaId, hasThumbnail, thumbnailVersion, thumbnailVariant)
    val thumbnailFile = thumbnailUri?.path?.let(::File)
    val thumbnailCacheKey = thumbnailFile?.let {
        "${it.absolutePath}:${it.lastModified()}:${it.length()}:$thumbnailVersion:$hasThumbnail"
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        coil3.compose.AsyncImage(
            model = coil3.request.ImageRequest.Builder(context)
            .data(thumbnailUri)
            .crossfade(false)
            .apply {
                if (thumbnailCacheKey != null) {
                    memoryCacheKey(thumbnailCacheKey)
                    diskCacheKey(thumbnailCacheKey)
                }
            }
            .build(),
            contentDescription = null,
            contentScale = contentScale,
            onSuccess = { onSuccess?.invoke(it) },
            modifier = Modifier.fillMaxSize()
        )
        if (showProcessingIndicator && isThumbnailProcessing) {
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.Center)
                    .size(30.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                    .padding(5.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}
@Composable
fun rememberFolderThumbnailUri(folderKey: String, legacyFolderName: String? = null): Uri? {
    val context = LocalContext.current
    val generationVersion by com.mediadeck.app.util.media.VideoThumbnailHelper.folderThumbnailVersion(folderKey).collectAsState()
    return remember(folderKey, legacyFolderName, generationVersion) {
        val cacheFilename = com.mediadeck.app.util.media.VideoThumbnailHelper.getFolderCacheFilename(folderKey)
        val file = File(context.filesDir, "thumbnails/$cacheFilename")
        val legacyFile = legacyFolderName?.let {
            File(context.filesDir, "thumbnails/${com.mediadeck.app.util.media.VideoThumbnailHelper.getLegacyFolderCacheFilename(it)}")
        }
        val cachedFile = file.takeIf { it.exists() && it.length() > 0 }
            ?: legacyFile?.takeIf { it.exists() && it.length() > 0 }
        cachedFile?.let(Uri::fromFile)
    }
}
@Composable
fun FolderGroupCard(
    folderKey: String,
    folderName: String,
    countLabel: String,
    legacyFolderName: String? = null,
    showThumbnail: Boolean = true,
    processingKey: String? = null,
    onClick: () -> Unit,
) {
    val mosaicUri = rememberFolderThumbnailUri(folderKey, legacyFolderName)
    val isProcessing by (processingKey?.let {
        com.mediadeck.app.util.media.VideoThumbnailHelper.folderThumbnailProcessingState(it)
    } ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val hasMosaic = mosaicUri != null && showThumbnail
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (hasMosaic) {
                coil3.compose.AsyncImage(
                    model = mosaicUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(112.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.28f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.34f),
                            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f),
                        )),
                )
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.Center).offset(y = (-16).dp).size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    folderName,
                    color = if (hasMosaic) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    countLabel,
                    color = if (hasMosaic) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Text(translate(LocalLanguage.current, "Generating thumbnails", "Membuat thumbnail"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}
@Composable
fun FolderGroupListItem(
    folderName: String,
    countLabel: String,
    processingKey: String? = null,
    onClick: () -> Unit,
) {
    val isProcessing by (processingKey?.let {
        com.mediadeck.app.util.media.VideoThumbnailHelper.folderThumbnailProcessingState(it)
    } ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folderName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(countLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isProcessing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text(
                        translate(LocalLanguage.current, "Generating thumbnails", "Membuat thumbnail"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
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
    val lang = LocalLanguage.current
    fun t(en: String, id: String) = translate(lang, en, id)
    val location = com.mediadeck.app.util.scan.ScanProgressFormatter.location(scanProgress)
    val isRunning = isScanActive && !isScanPaused
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            location != null && isScanPaused -> t("Scan paused", "Pemindaian dijeda")
                            location != null && isScanActive -> t("Scanning", "Memindai")
                            else -> com.mediadeck.app.util.scan.ScanProgressFormatter.compact(scanProgress)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (location != null) {
                        Text(
                            text = location,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isScanActive) {
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isScanPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isScanPaused) t("Resume scan", "Lanjutkan pemindaian") else t("Pause scan", "Jeda pemindaian"),
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
                            contentDescription = t("Stop scan", "Hentikan pemindaian"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (isRunning) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
            }
        }
    }
}