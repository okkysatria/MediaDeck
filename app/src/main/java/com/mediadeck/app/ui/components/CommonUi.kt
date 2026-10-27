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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mediadeck.app.data.settings.AppSettings
import androidx.compose.ui.graphics.luminance
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckAccentPillBg
import com.mediadeck.app.ui.theme.DeckBackground
import com.mediadeck.app.ui.theme.DeckDivider
import com.mediadeck.app.ui.theme.DeckOnAccent
import com.mediadeck.app.ui.theme.DeckSurface
import com.mediadeck.app.ui.theme.DeckSurfaceVariant
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.util.i18n.LocalLanguage
import com.mediadeck.app.util.i18n.translate
import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.scan.ScanProgressFormatter
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Shared overflow actions and file details for media cards across Gallery, Movies, and Comics. */
@Composable
fun MediaOverflowMenu(
    isFavorite: Boolean,
    infoTitle: String,
    infoRows: List<Pair<String, String>>,
    onFavorite: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onInfoOpened: () -> Unit = {},
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 20.dp,
    iconTint: Color = DeckTextSecondary,
) {
    val lang = LocalLanguage.current
    fun t(en: String, id: String) = translate(lang, en, id)
    var showMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.MoreVert, contentDescription = t("More options", "Opsi lainnya"), tint = iconTint, modifier = Modifier.size(iconSize))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, shape = RoundedCornerShape(16.dp), containerColor = DeckSurface) {
            DropdownMenuItem(
                text = { Text(t("Info", "Info"), color = DeckTextPrimary) },
                leadingIcon = { Icon(Icons.Outlined.Info, null, tint = DeckTextPrimary) },
                onClick = { showMenu = false; onInfoOpened(); showInfo = true },
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) t("Remove from Favorites", "Hapus dari Favorit") else t("Add to Favorites", "Tambah ke Favorit"), color = DeckTextPrimary) },
                leadingIcon = { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) DeckBadgeRed else DeckTextSecondary) },
                onClick = { showMenu = false; onFavorite() },
            )
            if (onDownload != null) {
                DropdownMenuItem(
                    text = { Text(t("Download", "Unduh"), color = DeckTextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Download, null, tint = DeckTextPrimary) },
                    onClick = { showMenu = false; onDownload() },
                )
            }
            DropdownMenuItem(
                text = { Text(t("Delete", "Hapus"), color = DeckBadgeRed) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = DeckBadgeRed) },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }

    if (showInfo) {
        MediaInfoBottomSheet(
            title = infoTitle,
            rows = infoRows,
            onDismiss = { showInfo = false },
            closeLabel = t("Close", "Tutup"),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MediaInfoBottomSheet(
    title: String,
    rows: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    closeLabel: String,
) {
    val lang = LocalLanguage.current
    fun t(en: String, id: String) = translate(lang, en, id)
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val screenHeight = with(LocalDensity.current) { containerHeightPx.toDp() }
    val displayTitle = remember(title) {
        val parsedTitle = title.toUri()
        if (parsedTitle.scheme != null) parsedTitle.lastPathSegment?.takeIf(String::isNotBlank) ?: title else title
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val normalized = rows.map { (label, value) -> label to value }
    fun matches(label: String, vararg terms: String) = terms.any { label.contains(it, ignoreCase = true) }
    fun find(vararg terms: String) = normalized.firstOrNull { (label, value) -> value.isNotBlank() && value != "—" && matches(label, *terms) }

    val type = find("type", "jenis")?.second.orEmpty()
    val isVideo = matches(type, "video")
    val isComic = matches(type, "comic", "komik")
    val isImage = matches(type, "image", "foto", "gambar")
    val pathValue = find("path", "lokasi")?.second.orEmpty()
    val extension = pathValue.substringBefore('?').substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 }.orEmpty()
    val format = find("format")?.second ?: extension.ifBlank { type.substringAfterLast('.', "").takeIf { it.isNotBlank() }.orEmpty() }
    val subline = listOfNotNull(
        type.takeIf { it.isNotBlank() },
        if (isComic) find("total pages", "jumlah halaman")?.second else null,
    ).joinToString(" · ")
    val primaryTerms = listOf("format", "size", "ukuran", "duration", "durasi", "resolution", "resolusi", "added", "ditambahkan", "current page", "halaman saat ini")
    val primaryRows = normalized.filter { (label, value) ->
        value.isNotBlank() && (value != "—" || isComic || isImage) && primaryTerms.any { label.contains(it, ignoreCase = true) }
    }
        .let { existing ->
            if (format.isNotBlank() && existing.none { matches(it.first, "format") }) {
                listOf(t("Format", "Format") to format) + existing
            } else existing
        }
    val tagsRow = normalized.firstOrNull { (label, _) -> matches(label, "tags", "tag") }
    val descriptionRow = find("description", "deskripsi")
    val remainingRows = normalized.filterNot { row ->
        val label = row.first
        row in primaryRows || row == tagsRow || row == descriptionRow ||
            matches(label, "type", "jenis") ||
            (isComic && matches(label, "total pages", "jumlah halaman")) ||
            (isImage && matches(label, "duration", "durasi"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeckBackground,
        contentColor = DeckTextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50)).background(DeckTextSecondary.copy(alpha = 0.45f))
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 360.dp, max = screenHeight * 0.9f),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(DeckAccentPillBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when {
                                isVideo -> Icons.Default.PlayCircle
                                isComic -> Icons.Default.Book
                                isImage -> Icons.Outlined.Image
                                else -> Icons.Outlined.Info
                            },
                            null,
                            tint = DeckAccentBlue,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(displayTitle, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = DeckTextPrimary)
                        if (subline.isNotBlank()) Text(subline, fontSize = 14.sp, color = DeckTextSecondary)
                    }
                }

                if (isVideo) {
                    val formatRow = primaryRows.firstOrNull { matches(it.first, "format") }
                    val sizeRow = primaryRows.firstOrNull { matches(it.first, "size", "ukuran") }
                    val durationRow = primaryRows.firstOrNull { matches(it.first, "duration", "durasi") }
                    val resolutionRow = primaryRows.firstOrNull { matches(it.first, "resolution", "resolusi") }
                    val addedRow = primaryRows.firstOrNull { matches(it.first, "added", "ditambahkan") }
                    val videoSummaryRows = listOfNotNull(formatRow, sizeRow, durationRow)
                    val videoExtraRows = listOfNotNull(resolutionRow, addedRow)
                    if (videoSummaryRows.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            videoSummaryRows.forEach { (label, value) -> InfoValueCard(label, value, Modifier.weight(1f)) }
                        }
                    }
                    if (videoExtraRows.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            videoExtraRows.forEach { (label, value) -> InfoValueCard(label, value, Modifier.weight(1f)) }
                        }
                    }
                } else if (isComic) {
                    val formatRow = primaryRows.firstOrNull { matches(it.first, "format") }
                    val sizeRow = primaryRows.firstOrNull { matches(it.first, "size", "ukuran") }
                    val addedRow = primaryRows.firstOrNull { matches(it.first, "added", "ditambahkan") }
                    val currentPageRow = primaryRows.firstOrNull { matches(it.first, "current page", "halaman saat ini") }
                    val comicFileRows = listOfNotNull(formatRow, sizeRow)
                    if (comicFileRows.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            comicFileRows.forEach { (label, value) -> InfoValueCard(label, value, Modifier.weight(1f)) }
                        }
                    }
                    addedRow?.let { (label, value) -> InfoValueCard(label, value, Modifier.fillMaxWidth()) }
                    currentPageRow?.let { (label, value) -> InfoProgressCard(label, value) }
                } else if (isImage) {
                    val formatRow = primaryRows.firstOrNull { matches(it.first, "format") }
                    val sizeRow = primaryRows.firstOrNull { matches(it.first, "size", "ukuran") }
                    val addedRow = primaryRows.firstOrNull { matches(it.first, "added", "ditambahkan") }
                    val resolutionRow = primaryRows.firstOrNull { matches(it.first, "resolution", "resolusi") }
                    val imageFileRows = listOfNotNull(formatRow, sizeRow)
                    if (imageFileRows.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            imageFileRows.forEach { (label, value) -> InfoValueCard(label, value, Modifier.weight(1f)) }
                        }
                    }
                    addedRow?.let { (label, value) -> InfoValueCard(label, value, Modifier.fillMaxWidth()) }
                    resolutionRow?.let { (label, value) -> InfoValueCard(label, value, Modifier.fillMaxWidth()) }
                } else {
                    primaryRows.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, value) -> InfoValueCard(label, value, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                descriptionRow?.let { (label, value) ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeckSurfaceVariant).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeckTextSecondary)
                        Text(value.ifBlank { "—" }, fontSize = 15.sp, lineHeight = 21.sp, color = DeckTextPrimary)
                    }
                }

                tagsRow?.let { (_, rawTags) ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeckSurfaceVariant).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(t("TAGS", "TAG"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeckTextSecondary)
                        val tagList = rawTags.split(',').map(String::trim).filter(String::isNotBlank)
                        if (tagList.isEmpty()) {
                            Text(t("No tags added", "Belum ada tag"), color = DeckTextSecondary, fontSize = 14.sp)
                        } else {
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tagList.forEach { tag ->
                                    Box(Modifier.clip(CircleShape).background(DeckAccentPillBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(tag, color = DeckAccentBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }

                remainingRows.forEach { (label, value) ->
                    val isPath = matches(label, "path", "lokasi")
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeckSurfaceVariant).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(label.uppercase(), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeckTextSecondary)
                            if (isPath) {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("Path", value)))
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = t("Copy path", "Salin lokasi"), tint = DeckTextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Text(
                            value.ifBlank { "—" },
                            fontSize = if (isPath) 13.sp else 15.sp,
                            lineHeight = 21.sp,
                            fontFamily = if (isPath) FontFamily.Monospace else FontFamily.Default,
                            color = DeckTextPrimary,
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(color = DeckDivider)
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(48.dp).navigationBarsPadding(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeckAccentBlue, contentColor = DeckOnAccent),
            ) {
                Text(closeLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InfoValueCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(DeckSurfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeckTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary)
    }
}

@Composable
private fun InfoProgressCard(label: String, value: String) {
    val progressValues = value.split('/').map { it.trim().toFloatOrNull() }
    val progress = if (progressValues.size == 2 && progressValues[1] != null && progressValues[1]!! > 0f) {
        ((progressValues[0] ?: 0f) / progressValues[1]!!).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeckSurfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeckTextSecondary)
        Text(value, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(DeckDivider)) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(DeckAccentBlue))
        }
    }
}

@Composable
fun MediaEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = DeckTextSecondary, fontSize = 14.sp)
            if (onAction != null) {
                Spacer(Modifier.height(16.dp))
                FloatingActionButton(
                    onClick = onAction,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = actionLabel)
                }
            }
        }
    }
}

@Composable
fun MediaSelectionCountTitle(selectedCount: Int) {
    val lang = LocalLanguage.current
    fun t(en: String, id: String) = translate(lang, en, id)
    Text(t("$selectedCount Selected", "$selectedCount Terpilih"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun MediaSelectionExitButton(onClick: () -> Unit) {
    val lang = LocalLanguage.current
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Close, contentDescription = translate(lang, "Cancel", "Batal"))
    }
}

@Composable
fun MediaSelectionDeleteButton(onClick: () -> Unit, enabled: Boolean = true) {
    val lang = LocalLanguage.current
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.Default.Delete,
            contentDescription = translate(lang, "Delete", "Hapus"),
            tint = if (enabled) DeckBadgeRed else DeckBadgeRed.copy(alpha = 0.35f),
        )
    }
}

private val FolderPlaceholderBrush = Brush.linearGradient(listOf(Color(0xFF2C3037), Color(0xFF1F222A)))
private val HeroPlaceholderBrush = Brush.linearGradient(listOf(Color(0xFF4A5A8F), Color(0xFF9A6AB0)))
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

@Suppress("UNUSED_PARAMETER")
@Composable
fun AsyncThumbnailImage(
    uriString: String,
    mediaId: Long,
    hasThumbnail: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    thumbnailVariant: String = "explore",
    showProcessingIndicator: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable BoxScope.() -> Unit)? = null,
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

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = onSuccess,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            placeholder?.invoke(this)
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
    thumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
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
        if (thumbnailContent != null) {
            thumbnailContent()
        } else if (mosaicUri != null && showThumbnail) {
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
    folderKey: String? = null,
    legacyFolderName: String? = null,
    processingKey: String? = null,
    progressRatioText: String? = null,
    progressPercent: Int? = null,
    isWaiting: Boolean = false,
    supportingLabel: String? = null,
    thumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val lang = LocalLanguage.current
    val isProcessing = rememberFolderProcessing(processingKey)
    val mosaicUri = if (folderKey != null) rememberFolderThumbnailUri(folderKey, legacyFolderName) else null

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
                if (thumbnailContent != null) {
                    thumbnailContent()
                } else if (mosaicUri != null) {
                    AsyncImage(
                        model = mosaicUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (isProcessing) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isWaiting) "$countLabel · " + translate(lang, "waiting", "menunggu") else countLabel,
                            fontSize = 13.sp,
                            color = DeckTextSecondary,
                        )
                        if (!supportingLabel.isNullOrEmpty()) {
                            Text(
                                text = supportingLabel,
                                fontSize = 12.sp,
                                color = DeckTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
fun DraggableListScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val source = remember(listState) {
        object : ScrollbarSource {
            override val totalItems: Int get() = listState.layoutInfo.totalItemsCount
            override val visibleCount: Int get() = listState.layoutInfo.visibleItemsInfo.size
            override val firstVisibleIndex: Int get() = listState.firstVisibleItemIndex
            override val isScrolling: Boolean get() = listState.isScrollInProgress
            override suspend fun scrollTo(index: Int) = listState.scrollToItem(index)
        }
    }
    DraggableScrollbar(source, modifier)
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
    modifier: Modifier = Modifier,
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
        detail.ifBlank { statusText }
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

