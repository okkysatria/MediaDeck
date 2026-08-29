package com.mediadeck.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
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
import com.mediadeck.app.util.i18n.t
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

    var showClearComicsThumbDialog by remember { mutableStateOf(false) }
    var showClearGalleryThumbDialog by remember { mutableStateOf(false) }
    var showClearMoviesThumbDialog by remember { mutableStateOf(false) }

    var currentCacheSizeBytes by remember { mutableLongStateOf(0L) }

    val comicsThumbSize by viewModel.comicsThumbSize.collectAsState()
    val galleryThumbSize by viewModel.galleryThumbSize.collectAsState()
    val moviesThumbSize by viewModel.moviesThumbSize.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val isClearingData by viewModel.isClearingData.collectAsState()
    val cacheClearCompleted by viewModel.cacheClearCompleted.collectAsState()

    val isBusy = isClearingCache || isClearingData

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
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
        viewModel.refreshThumbnailsSize()
    }

    LaunchedEffect(cacheClearCompleted) {
        if (cacheClearCompleted > 0) refreshCacheSize()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsSectionHeader(
            t("Database Management", "Manajemen Database"),
            Icons.Default.WarningAmber,
            SettingsAccent.Error,
            subtitle = t("These actions cannot be undone", "Tindakan ini tidak dapat dibatalkan"),
        )
        SettingsCard(SettingsAccent.Error) {
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
                icon = Icons.Default.DeleteSweep,
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

        SettingsSectionHeader(t("Storage Management", "Manajemen Penyimpanan"), Icons.Default.Storage, SettingsAccent.Secondary)
        SettingsCard(SettingsAccent.Secondary) {
            SettingsToggleRow(
                title = t("Automatic Cleanup", "Pembersihan Otomatis"),
                icon = Icons.Default.AutoDelete,
                accent = SettingsAccent.Secondary,
                checked = settings.autoManageCache,
                onCheckedChange = viewModel::setAutoManageCache,
            )

            if (!settings.autoManageCache) {
                SettingsDivider()

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Max Cache Size: ", "Batas Maks Cache: ") + "${settings.cacheSizeLimitMB} MB", fontSize = 13.sp, modifier = Modifier.weight(1f))
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
                            Text(t("Cleanup Target: ", "Target Pembersihan: ") + "${settings.cachePurgeTargetMB} MB", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Slider(
                                value = settings.cachePurgeTargetMB.coerceAtMost(settings.cacheSizeLimitMB).toFloat(),
                                onValueChange = { viewModel.setCachePurgeTarget(it.toInt()) },
                                valueRange = 20f..settings.cacheSizeLimitMB.toFloat(),
                                modifier = Modifier.weight(1.5f),
                            )
                        }
                    }
                }
            }

            SettingsDivider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t("Cache Utilization", "Penggunaan Cache"), fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatSize(currentCacheSizeBytes),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = { showClearCacheDialog = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("Clean", "Bersihkan"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showClearAllHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllHistoryDialog = false },
            title = { Text(t("Clear All History?", "Hapus Semua Riwayat?"), fontWeight = FontWeight.Bold) },
            text = { Text(t("This will reset all reading and watch progress across all comics and videos. Your files will NOT be deleted.", "Ini akan menghapus semua progres baca dan tonton di seluruh komik dan video. File Anda TIDAK akan dihapus.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllHistory()
                        showClearAllHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(t("Yes, Clear All", "Ya, Hapus Semua"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllHistoryDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearComicsDialog) {
        AlertDialog(
            onDismissRequest = { showClearComicsDialog = false },
            title = { Text(t("Delete Comic Data?", "Hapus Data Komik?"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = { Text(t("This action will erase all cached comic entries, reading progress, and cover arts from the offline database. Your original image files in storage WILL NOT be modified or deleted.", "Tindakan ini akan menghapus seluruh data komik, progress membaca, dan cover art cache yang terdaftar dari database offline. File gambar asli Anda di folder storage TIDAK akan terpengaruh.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearComicsData()
                        showClearComicsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Clear Comics", "Ya, Hapus Komik"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearComicsDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearGalleryDialog) {
        AlertDialog(
            onDismissRequest = { showClearGalleryDialog = false },
            title = { Text(t("Delete Gallery Data?", "Hapus Data Galeri?"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = { Text(t("This action will erase all scanned media metadata (images and videos) from the offline database. Your original file directories WILL NOT be modified or deleted.", "Tindakan ini akan menghapus seluruh data scan item media galeri (gambar & video) dari database offline. File asli Anda di folder storage TIDAK akan terpengaruh.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearGalleryData()
                        showClearGalleryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Clear Gallery", "Ya, Hapus Galeri"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearGalleryDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearMoviesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMoviesDialog = false },
            title = { Text(t("Delete Movies Data?", "Hapus Data Film?"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = { Text(t("This action will erase all indexed movies database records, bookmarks, and play histories. Your original file directories WILL NOT be modified or deleted.", "Tindakan ini akan menghapus seluruh data film dan riwayat tontonan dari database offline. File asli Anda di folder storage TIDAK akan terpengaruh.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearMoviesData()
                        showClearMoviesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Clear Movies", "Ya, Hapus Film"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMoviesDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearComicsThumbDialog) {
        AlertDialog(
            onDismissRequest = { showClearComicsThumbDialog = false },
            title = { Text(t("Delete Comics Thumbnails?", "Hapus Thumbnail Komik?"), fontWeight = FontWeight.Bold) },
            text = { Text(t("This will delete all cover thumbnails for comics. They will be regenerated when you browse them.", "Ini akan menghapus semua thumbnail sampul untuk komik. Thumbnail akan dibuat ulang saat Anda membukanya.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearPermanentThumbnails("comics")
                        showClearComicsThumbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Delete", "Ya, Hapus"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearComicsThumbDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearGalleryThumbDialog) {
        AlertDialog(
            onDismissRequest = { showClearGalleryThumbDialog = false },
            title = { Text(t("Delete Gallery Thumbnails?", "Hapus Thumbnail Galeri?"), fontWeight = FontWeight.Bold) },
            text = { Text(t("This will delete all thumbnails for gallery images and videos.", "Ini akan menghapus semua thumbnail untuk gambar dan video galeri.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearPermanentThumbnails("gallery")
                        showClearGalleryThumbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Delete", "Ya, Hapus"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearGalleryThumbDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearMoviesThumbDialog) {
        AlertDialog(
            onDismissRequest = { showClearMoviesThumbDialog = false },
            title = { Text(t("Delete Movies Thumbnails?", "Hapus Thumbnail Film?"), fontWeight = FontWeight.Bold) },
            text = { Text(t("This will delete all thumbnails for movies.", "Ini akan menghapus semua thumbnail untuk film.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearPermanentThumbnails("movies")
                        showClearMoviesThumbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Delete", "Ya, Hapus"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMoviesThumbDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(t("Clear Cache?", "Hapus Cache?"), fontWeight = FontWeight.Bold) },
            text = { Text(t("This will delete all temporary files, thumbnails, and SMB stream caches. It will free up space but might make browsing slower initially.", "Ini akan menghapus semua file sementara, thumbnail, dan cache stream SMB. Ruang penyimpanan akan bertambah tapi navigasi mungkin akan sedikit lebih lambat di awal.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllCaches()
                        showClearCacheDialog = false
                    },
                    enabled = !isClearingCache,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(t("Yes, Clear", "Ya, Hapus"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(t("Cancel", "Batal"))
                }
            },
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
            shape = RoundedCornerShape(12.dp)
        )
    }
}
