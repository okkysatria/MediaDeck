package com.mediadeck.app.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mediadeck.app.data.settings.AppSettings
import androidx.compose.ui.graphics.luminance
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.ui.theme.DeckDivider
import com.mediadeck.app.ui.theme.DeckSurface
import com.mediadeck.app.ui.theme.DeckSurfaceVariant
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.util.i18n.LocalLanguage
import com.mediadeck.app.util.i18n.translate
import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.scan.ScanProgressFormatter
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val FolderPlaceholderBrush = Brush.linearGradient(listOf(Color(0xFF2C3037), Color(0xFF1F222A)))
private val HeroPlaceholderBrush = Brush.linearGradient(listOf(Color(0xFF4A5A8F), Color(0xFF9A6AB0)))
private val HeroScrimBrush = Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9131417)), startY = 60f)
private val SoftWhiteText = Color(0xFFD6DAE2)
private val NotProcessing: Flow<Boolean> = MutableStateFlow(false)

@Composable
private fun DeckProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    square: Boolean = false,
) {
    val shape = if (square) RectangleShape else RoundedCornerShape(height / 2)
    val accentColor = DeckAccentBlue
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(trackColor)
    ) {
        if (fraction != null) {
            val progress = animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(accentColor, size = Size(size.width * progress.value, size.height))
                    }
            )
        } else {
            val phase = rememberInfiniteTransition(label = "indeterminate").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
                label = "phase"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = size.width * 0.35f
                        drawRect(
                            accentColor,
                            topLeft = Offset((size.width + w) * phase.value - w, 0f),
                            size = Size(w, size.height)
                        )
                    }
            )
        }
    }
}

private class ThumbnailSource(val uri: Uri, val cacheKey: String)

@Composable
private fun rememberThumbnailSource(
    mediaId: Long,
    hasThumbnail: Boolean,
    generationVersion: Long,
    variant: String,
): ThumbnailSource? {
    val context = LocalContext.current
    return remember(mediaId, hasThumbnail, generationVersion, variant) {
        val filename = VideoThumbnailHelper.getCacheFilename(mediaId, variant)
        val file = File(context.filesDir, "thumbnails/$filename")
        val length = file.length()
        if (length > 0L && file.isFile) {
            ThumbnailSource(
                uri = Uri.fromFile(file),
                cacheKey = "${file.absolutePath}:${file.lastModified()}:$length:$generationVersion:$hasThumbnail"
            )
        } else null
    }
}

@Composable
fun rememberThumbnailUri(
    mediaId: Long,
    hasThumbnail: Boolean,
    generationVersion: Long,
    variant: String = "explore",
): Uri? = rememberThumbnailSource(mediaId, hasThumbnail, generationVersion, variant)?.uri

@Suppress("UNUSED_PARAMETER")
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
    val thumbnailVersion by remember(mediaId, thumbnailVariant) {
        VideoThumbnailHelper.mediaThumbnailVersion(mediaId, thumbnailVariant)
    }.collectAsState()
    val source = rememberThumbnailSource(mediaId, hasThumbnail, thumbnailVersion, thumbnailVariant)

    val request = remember(source) {
        source?.let {
            ImageRequest.Builder(context)
                .data(it.uri)
                .crossfade(false)
                .memoryCacheKey(it.cacheKey)
                .diskCacheKey(it.cacheKey)
                .build()
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = onSuccess,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showProcessingIndicator) {
            ThumbnailProcessingBadge(mediaId, thumbnailVariant)
        }
    }
}

@Composable
private fun BoxScope.ThumbnailProcessingBadge(mediaId: Long, variant: String) {
    val processing by remember(mediaId, variant) {
        VideoThumbnailHelper.thumbnailProcessingState(mediaId, variant)
    }.collectAsState()
    if (!processing) return
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(30.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp,
            color = Color.White,
        )
    }
}

@Composable
fun rememberFolderThumbnailUri(folderKey: String, legacyFolderName: String? = null): Uri? {
    val context = LocalContext.current
    val generationVersion by remember(folderKey) {
        VideoThumbnailHelper.folderThumbnailVersion(folderKey)
    }.collectAsState()
    return remember(folderKey, legacyFolderName, generationVersion) {
        val dir = File(context.filesDir, "thumbnails")
        val file = File(dir, VideoThumbnailHelper.getFolderCacheFilename(folderKey))
        val legacyFile = legacyFolderName?.let {
            File(dir, VideoThumbnailHelper.getLegacyFolderCacheFilename(it))
        }
        val cachedFile = file.takeIf { it.exists() && it.length() > 0 }
            ?: legacyFile?.takeIf { it.exists() && it.length() > 0 }
        cachedFile?.let(Uri::fromFile)
    }
}

@Composable
private fun rememberFolderProcessing(processingKey: String?): Boolean {
    val flow: Flow<Boolean> = remember(processingKey) {
        processingKey?.let { VideoThumbnailHelper.folderThumbnailProcessingState(it) } ?: NotProcessing
    }
    val processing by flow.collectAsState(initial = false)
    return processing
}


@Composable
fun FolderGroupCard(
    folderKey: String,
    folderName: String,
    countLabel: String,
    legacyFolderName: String? = null,
    showThumbnail: Boolean = true,
    processingKey: String? = null,
    progressPercent: Int? = null,
    progressRatioText: String? = null,
    isWaiting: Boolean = false,
    onClick: () -> Unit,
) {
    val lang = LocalLanguage.current
    val mosaicUri = rememberFolderThumbnailUri(folderKey, legacyFolderName)
    val isProcessing = rememberFolderProcessing(processingKey)

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(shape)
            .background(DeckSurface)
            .clickable(onClick = onClick),
    ) {
        if (mosaicUri != null && showThumbnail) {
            AsyncImage(
                model = mosaicUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
            val placeholderBrush = if (isLight) {
                Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
            } else {
                FolderPlaceholderBrush
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = DeckAccentBlue,
                    modifier = Modifier.size(36.dp),
                )
            }
        }


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = folderName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(0f, 1f),
                            blurRadius = 3f,
                        ),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = countLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.9f),
                                offset = Offset(0f, 1f),
                                blurRadius = 3f,
                            ),
                        ),
                    )
                    if (isProcessing && progressPercent != null) {
                        Text(
                            text = "· $progressPercent%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeckAccentBlue,
                        )
                    } else if (isWaiting) {
                        Text(
                            text = "· " + translate(lang, "waiting", "menunggu"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99131417)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = DeckAccentBlue
                        )
                        Text(
                            text = translate(lang, "Generating thumbnails", "Membuat thumbnail"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (progressRatioText != null) {
                            Text(
                                text = progressRatioText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SoftWhiteText
                            )
                        }
                    }
                }
                if (progressPercent != null) {
                    DeckProgressBar(
                        fraction = progressPercent / 100f,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        square = true
                    )
                }
            }
        }
    }

@Composable
fun FolderGroupListItem(
    folderName: String,
    countLabel: String,
    processingKey: String? = null,
    progressRatioText: String? = null,
    progressPercent: Int? = null,
    isWaiting: Boolean = false,
    onClick: () -> Unit,
) {
    val lang = LocalLanguage.current
    val isProcessing = rememberFolderProcessing(processingKey)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeckSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeckSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = DeckAccentBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = DeckAccentBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = folderName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isProcessing && progressRatioText != null) {
                    Text(
                        text = translate(lang, "Generating thumbnails", "Membuat thumbnail") + " · $progressRatioText",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckAccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    DeckProgressBar(
                        fraction = progressPercent?.let { it / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        height = 3.dp,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    Text(
                        text = if (isWaiting) "$countLabel · " + translate(lang, "waiting", "menunggu") else countLabel,
                        fontSize = 13.sp,
                        color = DeckTextSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DeckTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmText: String = translate(LocalLanguage.current, "Delete", "Hapus"),
    dismissText: String = translate(LocalLanguage.current, "Cancel", "Batal")
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
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


@Stable
private interface ScrollbarSource {
    val totalItems: Int
    val visibleCount: Int
    val firstVisibleIndex: Int
    val isScrolling: Boolean
    suspend fun scrollTo(index: Int)
}

private val ScrollbarTouchWidth = 24.dp


@Composable
private fun DraggableScrollbar(source: ScrollbarSource, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val canScroll by remember(source) {
        derivedStateOf { source.visibleCount in 1 until source.totalItems }
    }
    if (!canScroll) return

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val isActive by remember(source) { derivedStateOf { isDragging || source.isScrolling } }
    val pillWidth by animateDpAsState(if (isActive) 12.dp else 4.dp, label = "scrollbarWidth")
    val pillAlpha = animateFloatAsState(if (isActive) 0.9f else 0.3f, label = "scrollbarAlpha")

    val thumbFraction by remember(source) {
        derivedStateOf { (source.visibleCount.toFloat() / source.totalItems).coerceIn(0.1f, 1f) }
    }
    val scrollFraction by remember(source) {
        derivedStateOf {
            val maxScroll = (source.totalItems - source.visibleCount).coerceAtLeast(1)
            (source.firstVisibleIndex.toFloat() / maxScroll).coerceIn(0f, 1f)
        }
    }

    fun thumbHeightPx(): Int =
        (trackHeightPx * thumbFraction).roundToInt().coerceIn(1, trackHeightPx.roundToInt().coerceAtLeast(1))

    fun travelPx(): Float = (trackHeightPx - thumbHeightPx()).coerceAtLeast(0f)

    fun currentFraction(): Float = if (isDragging) dragFraction else scrollFraction

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrollbarTouchWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
    ) {

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(pillWidth)
                .fillMaxHeight()
                .graphicsLayer { alpha = pillAlpha.value }
                .clip(RoundedCornerShape(pillWidth / 2))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        )

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (currentFraction() * travelPx()).roundToInt()) }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(constraints.maxWidth, thumbHeightPx())
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .pointerInput(source) {
                    detectDragGestures(
                        onDragStart = {
                            dragFraction = scrollFraction
                            isDragging = true
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val travel = travelPx()
                        if (travel <= 0f) return@detectDragGestures
                        dragFraction = (dragFraction + dragAmount.y / travel).coerceIn(0f, 1f)
                        val maxScroll = (source.totalItems - source.visibleCount).coerceAtLeast(1)
                        val target = (dragFraction * maxScroll).roundToInt()
                            .coerceIn(0, (source.totalItems - 1).coerceAtLeast(0))
                        if (target != source.firstVisibleIndex) {
                            scope.launch { source.scrollTo(target) }
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = pillAlpha.value }
                    .clip(RoundedCornerShape(pillWidth / 2))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun DraggableGridScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier) {
    val source = remember(gridState) {
        object : ScrollbarSource {
            override val totalItems: Int get() = gridState.layoutInfo.totalItemsCount
            override val visibleCount: Int get() = gridState.layoutInfo.visibleItemsInfo.size
            override val firstVisibleIndex: Int get() = gridState.firstVisibleItemIndex
            override val isScrolling: Boolean get() = gridState.isScrollInProgress
            override suspend fun scrollTo(index: Int) = gridState.scrollToItem(index)
        }
    }
    DraggableScrollbar(source, modifier)
}

@Composable
fun DraggableStaggeredGridScrollbar(gridState: LazyStaggeredGridState, modifier: Modifier = Modifier) {
    val source = remember(gridState) {
        object : ScrollbarSource {
            override val totalItems: Int get() = gridState.layoutInfo.totalItemsCount
            override val visibleCount: Int get() = gridState.layoutInfo.visibleItemsInfo.size
            override val firstVisibleIndex: Int get() = gridState.firstVisibleItemIndex
            override val isScrolling: Boolean get() = gridState.isScrollInProgress
            override suspend fun scrollTo(index: Int) = gridState.scrollToItem(index)
        }
    }
    DraggableScrollbar(source, modifier)
}


@Composable
fun ScanStatusCard(
    scanProgress: String?,
    isScanActive: Boolean,
    isScanPaused: Boolean,
    onTogglePause: () -> Unit,
    onStopScan: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (scanProgress == null) return
    val lang = LocalLanguage.current
    fun t(en: String, id: String) = translate(lang, en, id)
    val location = remember(scanProgress) { ScanProgressFormatter.location(scanProgress) }
    val isRunning = isScanActive && !isScanPaused

    val statusText = when {
        isScanActive && isScanPaused -> t("Scan paused", "Scan dijeda")
        isScanActive -> t("Scanning", "Memindai")
        else -> t("Scan complete", "Scan selesai")
    }
    val detail = location ?: ScanProgressFormatter.compact(scanProgress)
    val displayText = if (!isScanActive) {
        if (detail.isNotBlank()) detail else statusText
    } else if (detail.isNotBlank() && !detail.equals(statusText, ignoreCase = true)) {
        "$statusText • $detail"
    } else {
        statusText
    }

    Surface(
        color = DeckSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DeckDivider),
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = onOpenDetail != null) { onOpenDetail?.invoke() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = DeckAccentBlue,
                    )
                } else {
                    Icon(
                        imageVector = if (isScanActive) Icons.Default.Pause else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isScanActive) DeckAccentBlue else Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp),
                    )
                }

                Text(
                    text = displayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DeckTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (onOpenDetail != null) {
                    IconButton(
                        onClick = onOpenDetail,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = t("Detail", "Detail"),
                            tint = DeckTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (isScanActive) {
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (isScanPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isScanPaused) t("Resume", "Lanjutkan") else t("Pause", "Jeda"),
                            tint = DeckAccentBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onStopScan,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = t("Dismiss", "Tutup"),
                        tint = DeckTextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (isRunning) {
                DeckProgressBar(
                    fraction = null,
                    modifier = Modifier.fillMaxWidth(),
                    height = 2.dp,
                    trackColor = DeckDivider,
                    square = true,
                )
            }
        }
    }
}

@Composable
private fun ScanStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeckTextPrimary)
        Text(label, fontSize = 12.sp, color = DeckTextSecondary)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailBottomSheet(
    scanProgress: String?,
    isScanActive: Boolean,
    isScanPaused: Boolean,
    onTogglePause: () -> Unit,
    onStopScan: () -> Unit,
    onDismiss: () -> Unit,
    foundCount: Int? = null,
    newCount: Int? = null,
    skippedCount: Int? = null,
    progressFraction: Float? = null,
    categoryLabel: String? = null,
) {
    val lang = LocalLanguage.current
    fun t(en: String, id: String) = translate(lang, en, id)
    val location = remember(scanProgress) {
        scanProgress?.let { ScanProgressFormatter.location(it) }.orEmpty()
    }
    val isRunning = isScanActive && !isScanPaused

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeckSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DeckTextSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = DeckAccentBlue
                        )
                    } else {
                        Icon(
                            imageVector = if (isScanActive) Icons.Default.Pause else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = DeckAccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = t("Library Scan", "Pemindaian Pustaka"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckTextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = t("Close", "Tutup"),
                        tint = DeckTextPrimary
                    )
                }
            }

            if (location.isNotEmpty()) {
                Surface(
                    color = DeckSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = t("Now scanning", "Sedang memindai"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeckTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = location,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeckTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (foundCount != null || newCount != null || skippedCount != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ScanStat(foundCount?.toString() ?: "–", t("Found", "Ditemukan"))
                    ScanStat(newCount?.toString() ?: "–", t("New", "Baru"))
                    ScanStat(skippedCount?.toString() ?: "–", t("Skipped", "Dilewati"))
                }
            }

            if (progressFraction != null || isRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categoryLabel ?: t("Progress", "Progres"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeckTextPrimary
                        )
                        if (progressFraction != null) {
                            Text(
                                text = "${(progressFraction.coerceIn(0f, 1f) * 100).roundToInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeckAccentBlue
                            )
                        }
                    }
                    DeckProgressBar(
                        fraction = progressFraction,
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isScanActive) {
                    OutlinedButton(
                        onClick = onTogglePause,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DeckTextPrimary),
                        border = BorderStroke(1.dp, DeckDivider),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isScanPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isScanPaused) t("Resume", "Lanjutkan") else t("Pause", "Jeda"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onStopScan,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeckBadgeRed),
                    border = BorderStroke(1.dp, DeckBadgeRed),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = DeckBadgeRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScanActive) t("Cancel scan", "Batal pemindaian") else t("Close", "Tutup"),
                        fontWeight = FontWeight.Bold,
                        color = DeckBadgeRed
                    )
                }
            }
        }
    }
}


@Composable
fun rememberBottomBarPadding(extra: Dp = 16.dp): Dp {
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return 76.dp + navBarsBottom + extra
}

@Composable
fun MediaDeckHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
) {
    val lang = LocalLanguage.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBackClick != null) 6.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackClick != null) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = translate(lang, "Back", "Kembali"),
                    tint = DeckTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeckAccentBlue,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DeckTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = DeckTextSecondary,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = translate(lang, "Search", "Cari"),
                tint = DeckTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = translate(lang, "More", "Lainnya"),
                tint = DeckTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ContinueHeroCard(
    title: String,
    progressText: String,
    progressFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUri: Uri? = null,
    categoryLabel: String? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeckSurfaceVariant),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HeroPlaceholderBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = SoftWhiteText,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }


            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            0.68f to Color(0x75000000),
                            1.0f to Color(0xEB101216)
                        )
                    )
            )


            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (progressFraction > 0f) {
                DeckProgressBar(
                    fraction = progressFraction,
                    height = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    square = true,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun RedesignedComicCard(
    title: String,
    pageCountText: String,
    isFavorite: Boolean,
    progressFraction: Float = 0f,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverContent: @Composable BoxScope.() -> Unit,
) {
    val lang = LocalLanguage.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(DeckSurface)
        ) {
            coverContent()

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = translate(lang, "Favorite", "Favorit"),
                    tint = if (isFavorite) DeckBadgeRed else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (pageCountText.isNotEmpty()) {
                Text(
                    text = pageCountText,
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
                DeckProgressBar(
                    fraction = progressFraction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    square = true
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeckTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = translate(lang, "Menu", "Menu"),
                    tint = DeckTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
