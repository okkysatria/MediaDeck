package com.mediadeck.app.ui.screens.settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaFormatters
import com.mediadeck.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
fun SystemSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showClearComicsDialog by remember { mutableStateOf(false) }
    var showClearGalleryDialog by remember { mutableStateOf(false) }
    var showClearMoviesDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearAllHistoryDialog by remember { mutableStateOf(false) }
    var currentCacheSizeBytes by remember { mutableLongStateOf(0L) }
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val isClearingData by viewModel.isClearingData.collectAsState()
    val cacheClearCompleted by viewModel.cacheClearCompleted.collectAsState()
    val isBusy = isClearingCache || isClearingData
    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    fun refreshCacheSize() {
        coroutineScope.launch(Dispatchers.IO) {
            val prefixes = listOf("smb_", "zip_pages_", "thumbnails_", "smb_files_")
            val cacheSize = context.cacheDir?.listFiles { _, name ->
                prefixes.any { name.startsWith(it) }
            }?.sumOf { file ->
                if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
            } ?: 0L
            val thumbnailFolder = java.io.File(context.filesDir, "thumbnails")
            val thumbnailSize = if (thumbnailFolder.exists()) {
                thumbnailFolder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
            withContext(Dispatchers.Main) {
                currentCacheSizeBytes = cacheSize + thumbnailSize
            }
        }
    }
    LaunchedEffect(Unit) {
        refreshCacheSize()
    }
    LaunchedEffect(cacheClearCompleted) {
        if (cacheClearCompleted > 0) refreshCacheSize()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsSectionHeader(
            title = t("Database Management", "Manajemen Database"),
            icon = Icons.Default.WarningAmber,
            accent = SettingsAccent.Error,
            subtitle = t("These actions cannot be undone", "Tindakan ini tidak dapat dibatalkan"),
        )
        SettingsCard {
            SettingsDangerRow(
                title = t("Reset Comics", "Hapus Data Komik"),
                icon = Icons.Default.AutoStories,
                onClick = { showClearComicsDialog = true },
                enabled = !isBusy,
                testTag = "clear_comics_button",
            )
            SettingsDivider()
            SettingsDangerRow(
                title = t("Reset Gallery", "Hapus Data Galeri"),
                icon = Icons.Default.Image,
                onClick = { showClearGalleryDialog = true },
                enabled = !isBusy,
                testTag = "clear_gallery_button",
            )
            SettingsDivider()
            SettingsDangerRow(
                title = t("Reset Movies", "Hapus Data Video"),
                icon = Icons.Default.Movie,
                onClick = { showClearMoviesDialog = true },
                enabled = !isBusy,
                testTag = "clear_movies_button",
            )
            SettingsDivider()
            SettingsDangerRow(
                title = t("Clear All History", "Hapus Semua Riwayat"),
                icon = Icons.Default.History,
                onClick = { showClearAllHistoryDialog = true },
                enabled = !isBusy,
            )
        }
        SettingsSectionHeader(t("Storage Management", "Manajemen Penyimpanan"))
        SettingsCard {
            SettingsToggleRow(
                title = t("Automatic Cleanup", "Pembersihan Otomatis"),
                icon = Icons.AutoMirrored.Filled.Sort,
                checked = settings.autoManageCache,
                onCheckedChange = viewModel::setAutoManageCache,
            )
            if (!settings.autoManageCache) {
                SettingsDivider()
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Max Cache Size: ", "Batas Maks Cache: ") + "${settings.cacheSizeLimitMB} MB", fontSize = 13.sp, color = DeckTextPrimary, modifier = Modifier.weight(1f))
                            Slider(
                                value = settings.cacheSizeLimitMB.toFloat(),
                                onValueChange = { viewModel.setCacheSizeLimit(it.toInt()) },
                                valueRange = 50f..1000f,
                                steps = 10,
                                modifier = Modifier.weight(1.5f),
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val purgeTarget = settings.cachePurgeTargetMB.coerceAtMost(settings.cacheSizeLimitMB)
                            Text(t("Cleanup Target: ", "Target Pembersihan: ") + "$purgeTarget MB", fontSize = 13.sp, color = DeckTextPrimary, modifier = Modifier.weight(1f))
                            Slider(
                                value = purgeTarget.toFloat(),
                                onValueChange = { viewModel.setCachePurgeTarget(it.toInt()) },
                                valueRange = 20f..settings.cacheSizeLimitMB.toFloat(),
                                modifier = Modifier.weight(1.5f),
                            )
                        }
                    }
                }
            }
            SettingsDivider(inset = false)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t("Cache utilization", "Penggunaan cache"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DeckAccentBlue)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        MediaFormatters.fileSize(currentCacheSizeBytes, unknownValue = "0 B"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckTextPrimary,
                    )
                }
                Button(
                    onClick = { showClearCacheDialog = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeckBadgeRed.copy(alpha = 0.22f),
                        contentColor = DeckBadgeRed,
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("Clean", "Bersihkan"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showClearAllHistoryDialog) {
        SettingsConfirmDialog(
            title = t("Clear All History?", "Hapus Semua Riwayat?"),
            message = t(
                "This will reset all reading and watch progress across all comics and videos. Your files will NOT be deleted.",
                "Ini akan menghapus semua progres baca dan tonton di seluruh komik dan video. File Anda TIDAK akan dihapus.",
            ),
            confirmLabel = t("Clear", "Hapus"),
            icon = Icons.Default.History,
            onConfirm = {
                viewModel.resetAllHistory()
                showClearAllHistoryDialog = false
            },
            onDismiss = { showClearAllHistoryDialog = false },
        )
    }
    if (showClearComicsDialog) {
        SettingsConfirmDialog(
            title = t("Reset Comics?", "Hapus Data Komik?"),
            message = t(
                "All comic data, favorites, and reading progress will be removed from the app. Your files will not be deleted.",
                "Semua data komik, favorit, dan progres membaca akan dihapus dari aplikasi. File Anda tidak akan dihapus.",
            ),
            confirmLabel = t("Reset", "Hapus"),
            onConfirm = {
                viewModel.clearComicsData()
                showClearComicsDialog = false
            },
            onDismiss = { showClearComicsDialog = false },
        )
    }
    if (showClearGalleryDialog) {
        SettingsConfirmDialog(
            title = t("Reset Gallery?", "Hapus Data Galeri?"),
            message = t(
                "All scanned gallery metadata (images and videos) will be removed from the app. Your files will not be deleted.",
                "Semua data galeri hasil pindai (gambar dan video) akan dihapus dari aplikasi. File Anda tidak akan dihapus.",
            ),
            confirmLabel = t("Reset", "Hapus"),
            onConfirm = {
                viewModel.clearGalleryData()
                showClearGalleryDialog = false
            },
            onDismiss = { showClearGalleryDialog = false },
        )
    }
    if (showClearMoviesDialog) {
        SettingsConfirmDialog(
            title = t("Reset Movies?", "Hapus Data Film?"),
            message = t(
                "All movie data, bookmarks, and watch history will be removed from the app. Your files will not be deleted.",
                "Semua data film, bookmark, dan riwayat tontonan akan dihapus dari aplikasi. File Anda tidak akan dihapus.",
            ),
            confirmLabel = t("Reset", "Hapus"),
            onConfirm = {
                viewModel.clearMoviesData()
                showClearMoviesDialog = false
            },
            onDismiss = { showClearMoviesDialog = false },
        )
    }
    if (showClearCacheDialog) {
        SettingsConfirmDialog(
            title = t("Clear Cache?", "Hapus Cache?"),
            message = t(
                "This will delete all temporary files, thumbnails, and SMB stream caches. It will free up space but might make browsing slower initially.",
                "Ini akan menghapus semua file sementara, thumbnail, dan cache stream SMB. Ruang penyimpanan akan bertambah tapi navigasi mungkin akan sedikit lebih lambat di awal.",
            ),
            confirmLabel = t("Clear", "Hapus"),
            icon = Icons.Default.DeleteOutline,
            confirmEnabled = !isClearingCache,
            onConfirm = {
                viewModel.clearAllCaches()
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false },
        )
    }
    if (isBusy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(t("Processing...", "Sedang memproses..."), fontSize = 16.sp)
                }
            },
            text = {
                Text(t("Please wait while we update the database.", "Mohon tunggu sementara kami memperbarui database."))
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}
