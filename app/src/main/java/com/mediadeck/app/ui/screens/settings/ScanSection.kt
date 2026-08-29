package com.mediadeck.app.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.layout.PaddingValues

private fun isFolderScanning(folderUri: String, type: String, activeScanTarget: String?): Boolean {
    if (activeScanTarget == null) return false
    val normalizedUri = folderUri.removeSuffix("/")
    val normalizedTarget = activeScanTarget.removeSuffix("/")
    return normalizedTarget == normalizedUri ||
        activeScanTarget == "AUTO_ALL_$type" ||
        activeScanTarget == "AUTO_ALL_"
}

@Composable
fun ScanSection(
    scannerViewModel: ScannerViewModel,
    settingsViewModel: SettingsViewModel,
    settings: AppSettings,
    onStartSmbBrowse: (String) -> Unit,
) {
    val context = LocalContext.current
    var folderToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scanProgress by scannerViewModel.scanProgress.collectAsState()
    val isScanActive by scannerViewModel.isScanActive.collectAsState()
    val isScanPaused by scannerViewModel.isScanPaused.collectAsState()
    val activeScanTarget by scannerViewModel.currentScanTarget.collectAsState()

    val dirLauncherComics = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
            }
            scannerViewModel.saveLibraryFolder(context, it.toString(), "comics")
            scannerViewModel.runFolderScan(it.toString(), "comics")
        }
    }

    val dirLauncherGallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
            }
            scannerViewModel.saveLibraryFolder(context, it.toString(), "gallery")
            scannerViewModel.runFolderScan(it.toString(), "gallery")
        }
    }

    val dirLauncherMovies = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
            }
            scannerViewModel.saveLibraryFolder(context, it.toString(), "movies")
            scannerViewModel.runFolderScan(it.toString(), "movies")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        AnimatedVisibility(visible = scanProgress != null, enter = fadeIn(), exit = fadeOut()) {
            ScanStatusCard(
                scanProgress = scanProgress,
                isScanActive = isScanActive,
                isScanPaused = isScanPaused,
                onTogglePause = { scannerViewModel.togglePauseScan() },
                onStopScan = { scannerViewModel.stopScan() },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            var isManagedFoldersExpanded by remember { mutableStateOf(false) }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isManagedFoldersExpanded = !isManagedFoldersExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsSectionHeader(
                        t("Managed Folders", "Folder Terdaftar"),
                        Icons.Default.Folder,
                        SettingsAccent.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isScanActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        imageVector = if (isManagedFoldersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isManagedFoldersExpanded) t("Collapse", "Tutup") else t("Expand", "Buka"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                val comicFolders by scannerViewModel.comicFolders.collectAsState()
                val galleryFolders by scannerViewModel.galleryFolders.collectAsState()
                val movieFolders by scannerViewModel.movieFolders.collectAsState()

                if (isManagedFoldersExpanded) {
                    if (comicFolders.isEmpty() && galleryFolders.isEmpty() && movieFolders.isEmpty()) {
                        SettingsCard(SettingsAccent.Primary) {
                            Text(
                                t("No folders added yet.", "Belum ada folder yang ditambahkan."),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FolderGroup(t("Comics", "Komik"), comicFolders, Icons.Default.AutoStories, "comics", activeScanTarget) { folderToRemove = it to "comics" }
                            FolderGroup(t("Gallery", "Galeri"), galleryFolders, Icons.Default.Collections, "gallery", activeScanTarget) { folderToRemove = it to "gallery" }
                            FolderGroup(t("Movies", "Film"), movieFolders, Icons.Default.Movie, "movies", activeScanTarget) { folderToRemove = it to "movies" }
                        }
                    }
                }
            }

            SettingsSectionHeader(t("Internal Storage", "Penyimpanan Internal"), Icons.Default.PhoneAndroid, SettingsAccent.Secondary)
            SettingsCard(SettingsAccent.Secondary) {
                SettingsToggleRow(
                    title = t("Hide from System Gallery", "Sembunyikan dari Galeri HP"),
                    subtitle = t("Creates .nomedia file in scanned local folders", "Membuat file .nomedia di folder lokal yang di-scan"),
                    icon = Icons.Default.VisibilityOff,
                    accent = SettingsAccent.Secondary,
                    checked = settings.hideScannedFromGallery,
                    onCheckedChange = {
                        settingsViewModel.setHideScannedFromGallery(it)
                        scannerViewModel.applyGalleryHiding(context, it)
                    },
                )

                SettingsDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = { dirLauncherComics.launch(null) },
                        enabled = !isScanActive,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("Comics", "Komik"), fontSize = 10.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { dirLauncherGallery.launch(null) },
                        enabled = !isScanActive,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("Gallery", "Galeri"), fontSize = 10.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { dirLauncherMovies.launch(null) },
                        enabled = !isScanActive,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("Videos", "Video"), fontSize = 10.sp, maxLines = 1)
                    }
                }

                if (isScanActive) {
                    Text(
                        t("Buttons unlock when the current scan finishes.", "Tombol aktif lagi setelah scan yang berjalan selesai."),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            SettingsSectionHeader(t("Network Storage (Samba)", "Penyimpanan Jaringan (Samba)"), Icons.Default.Dns, SettingsAccent.Tertiary)
            SettingsCard(SettingsAccent.Tertiary) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (settings.smbHost.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Samba: ${settings.smbHost}${if (settings.smbShare.isNotEmpty()) " / " + settings.smbShare else ""}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                t("Samba Server has not been configured yet.", "Server Samba belum dikonfigurasi."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onStartSmbBrowse("comics") },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = settings.smbHost.isNotEmpty() && !isScanActive,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Comics", "Komik"), fontSize = 10.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { onStartSmbBrowse("gallery") },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = settings.smbHost.isNotEmpty() && !isScanActive,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Gallery", "Galeri"), fontSize = 10.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { onStartSmbBrowse("movies") },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = settings.smbHost.isNotEmpty() && !isScanActive,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Videos", "Video"), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                SettingsDivider()

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        t("Samba Server Configuration", "Konfigurasi Server Samba"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    var hostLocal by remember { mutableStateOf(settings.smbHost) }
                    var shareLocal by remember { mutableStateOf(settings.smbShare) }
                    var userLocal by remember { mutableStateOf(settings.smbUser) }
                    val savedPassword by settingsViewModel.smbPassword.collectAsState()
                    var passLocal by remember { mutableStateOf("") }
                    var domainLocal by remember { mutableStateOf(settings.smbDomain) }
                    var portLocal by remember { mutableStateOf(settings.smbPort) }
                    var enableSMB2Local by remember { mutableStateOf(settings.smbEnableSMB2) }
                    var disableSMB1Local by remember { mutableStateOf(settings.smbDisableSMB1) }
                    var isGuestOnly by remember { mutableStateOf(settings.smbIsGuest) }

                    var hasLoadedSmbValues by remember { mutableStateOf(false) }

                    LaunchedEffect(settings.smbHost, settings.smbShare, settings.smbUser, savedPassword, settings.smbDomain, settings.smbPort, settings.smbIsGuest) {
                        if (!hasLoadedSmbValues && (settings.smbHost.isNotEmpty() || settings.smbShare.isNotEmpty() || settings.smbUser.isNotEmpty() || savedPassword.isNotEmpty() || settings.smbDomain.isNotEmpty() || settings.smbIsGuest)) {
                            hostLocal = settings.smbHost
                            shareLocal = settings.smbShare
                            userLocal = settings.smbUser
                            passLocal = savedPassword
                            domainLocal = settings.smbDomain
                            portLocal = settings.smbPort
                            enableSMB2Local = settings.smbEnableSMB2
                            disableSMB1Local = settings.smbDisableSMB1
                            isGuestOnly = settings.smbIsGuest
                            hasLoadedSmbValues = true
                        }
                    }

                    val normalizedPort = portLocal.toIntOrNull()?.takeIf { it in 1..65535 }?.toString()
                    val isPortValid = normalizedPort != null

                    val saveLocally = {
                        settingsViewModel.updateSmbPreferences(
                            host = hostLocal,
                            share = shareLocal,
                            user = userLocal,
                            pass = passLocal,
                            domain = domainLocal,
                            port = normalizedPort ?: portLocal,
                            enableSMB2 = enableSMB2Local,
                            disableSMB1 = disableSMB1Local,
                            connTimeout = settings.smbConnTimeout,
                            soTimeout = settings.smbSoTimeout,
                            isGuest = isGuestOnly,
                        )
                    }

                    val saveAndBrowse = {
                        settingsViewModel.updateSmbPreferencesAndBrowse(
                            host = hostLocal,
                            share = shareLocal,
                            user = userLocal,
                            pass = passLocal,
                            domain = domainLocal,
                            port = normalizedPort ?: portLocal,
                            enableSMB2 = enableSMB2Local,
                            disableSMB1 = disableSMB1Local,
                            connTimeout = settings.smbConnTimeout,
                            soTimeout = settings.smbSoTimeout,
                            isGuest = isGuestOnly,
                        )
                    }

                    LaunchedEffect(hostLocal, portLocal, shareLocal, userLocal, passLocal, domainLocal, isGuestOnly, enableSMB2Local, disableSMB1Local) {
                        if (hostLocal == settings.smbHost &&
                            portLocal == settings.smbPort &&
                            shareLocal == settings.smbShare &&
                            userLocal == settings.smbUser &&
                            passLocal == savedPassword &&
                            domainLocal == settings.smbDomain &&
                            isGuestOnly == settings.smbIsGuest &&
                            enableSMB2Local == settings.smbEnableSMB2 &&
                            disableSMB1Local == settings.smbDisableSMB1
                        ) return@LaunchedEffect

                        if (!isPortValid) return@LaunchedEffect

                        delay(800.milliseconds)
                        saveLocally()
                    }

                    val discoveredSet by com.mediadeck.app.util.smb.SmbDiscoveryManager.discoveredServers.collectAsState()
                    val discoveredIps = remember(discoveredSet) { discoveredSet.toList().sorted() }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(t("Auto Network Scan", "Pindai Jaringan Otomatis"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Button(
                                onClick = { scannerViewModel.startNetworkDiscovery(context) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(t("Scan", "Pindai"), fontSize = 11.sp)
                            }
                        }

                        if (discoveredIps.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(discoveredIps.size) { index ->
                                    val ip = discoveredIps[index]
                                    SuggestionChip(
                                        onClick = { hostLocal = ip },
                                        label = { Text(ip, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = hostLocal,
                            onValueChange = { hostLocal = it },
                            label = { Text(t("Host / IP", "Host / IP"), fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.4f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        OutlinedTextField(
                            value = portLocal,
                            onValueChange = { portLocal = it },
                            label = { Text(t("Port", "Port"), fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !isPortValid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.6f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                    }

                    OutlinedTextField(
                        value = shareLocal,
                        onValueChange = { shareLocal = it },
                        label = { Text(t("Share Name", "Nama Share"), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("Guest Login", "Login Tamu"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isGuestOnly,
                            onCheckedChange = { isGuestOnly = it },
                            thumbContent = if (isGuestOnly) {
                                { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    if (!isGuestOnly) {
                        OutlinedTextField(
                            value = userLocal,
                            onValueChange = { userLocal = it },
                            label = { Text(t("Username", "Username"), fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        OutlinedTextField(
                            value = passLocal,
                            onValueChange = { passLocal = it },
                            label = { Text(t("Password", "Kata Sandi"), fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                    }

                    Button(
                        onClick = { saveAndBrowse() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = hostLocal.trim().isNotEmpty() && isPortValid,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(t("Browse Network Folders", "Jelajahi Folder Jaringan"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val folderRemovalMediaCount by scannerViewModel.folderRemovalMediaCount.collectAsState()

    LaunchedEffect(folderToRemove) {
        folderToRemove?.first?.let(scannerViewModel::previewLibraryFolderRemoval)
    }

    if (folderToRemove != null) {
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(t("Remove folder?", "Hapus folder?")) },
            text = {
                val count = folderRemovalMediaCount
                Text(
                    if (count == null) {
                        t("Calculating indexed media to remove...", "Menghitung media terindeks yang akan dihapus...")
                    } else {
                        t(
                            "This removes the folder, $count indexed media item(s), and their generated thumbnails. Original files will not be changed.",
                            "Ini menghapus folder, $count item media terindeks, dan thumbnail buatannya. File asli tidak akan diubah.",
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(enabled = folderRemovalMediaCount != null, onClick = {
                    folderToRemove?.let { (uri, type) -> scannerViewModel.removeLibraryFolder(context, uri, type) }
                    folderToRemove = null
                }) {
                    Text(t("Remove", "Hapus"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text(t("Cancel", "Batal"))
                }
            },
        )
    }
}

@Composable
private fun FolderGroup(
    title: String,
    folders: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    type: String,
    activeScanTarget: String?,
    onRemove: (String) -> Unit,
) {
    if (folders.isEmpty()) return

    SettingsCard(SettingsAccent.Primary) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }

            folders.forEach { uri ->
                val isActive = isFolderScanning(uri, type, activeScanTarget)
                val borderColor by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    label = "folderBorderColor",
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val friendlyName = if (uri.startsWith("smb://")) {
                                uri.removeSuffix("/").substringAfterLast("/")
                            } else {
                                try {
                                    uri.toUri().lastPathSegment?.substringAfterLast(":") ?: uri
                                } catch (e: Exception) { uri }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(friendlyName, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (isActive) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        t("Scanning…", "Memindai…"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text(
                                uri,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (isActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(
                            onClick = { onRemove(uri) },
                            enabled = !isActive,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = t("Remove folder", "Hapus folder"),
                                tint = if (isActive) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                },
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
