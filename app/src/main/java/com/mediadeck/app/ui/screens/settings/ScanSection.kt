package com.mediadeck.app.ui.screens.settings
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.components.ScanStatusCard
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.ui.theme.DeckOnAccent
import com.mediadeck.app.ui.theme.DeckSurfaceVariant
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.layout.PaddingValues

private val OnAccent: Color
    @Composable
    get() = DeckOnAccent

private fun isFolderScanning(folderUri: String, type: String, activeScanTarget: String?): Boolean {
    if (activeScanTarget == null) return false
    val normalizedUri = folderUri.removeSuffix("/")
    val normalizedTarget = activeScanTarget.removeSuffix("/")
    return normalizedTarget == normalizedUri ||
            activeScanTarget == "AUTO_ALL_$type" ||
            activeScanTarget == "AUTO_ALL_"
}

@Composable
private fun rememberFolderPicker(
    context: Context,
    scannerViewModel: ScannerViewModel,
    type: String,
) = rememberLauncherForActivityResult(
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
        scannerViewModel.saveLibraryFolder(context, it.toString(), type)
        scannerViewModel.runFolderScan(it.toString(), type)
    }
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
    val dirLauncherComics = rememberFolderPicker(context, scannerViewModel, "comics")
    val dirLauncherGallery = rememberFolderPicker(context, scannerViewModel, "gallery")
    val dirLauncherMovies = rememberFolderPicker(context, scannerViewModel, "movies")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            var isManagedFoldersExpanded by remember { mutableStateOf(false) }
            val comicFolders by scannerViewModel.comicFolders.collectAsState()
            val galleryFolders by scannerViewModel.galleryFolders.collectAsState()
            val movieFolders by scannerViewModel.movieFolders.collectAsState()
            val totalFolders = comicFolders.size + galleryFolders.size + movieFolders.size
            SettingsSectionHeader(t("Managed Folders", "Folder Terdaftar"))
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isManagedFoldersExpanded = !isManagedFoldersExpanded }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeckAccentBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t(
                                "$totalFolders folder${if (totalFolders == 1) "" else "s"}",
                                "$totalFolders folder",
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeckTextPrimary,
                        )
                        val summary = listOf(
                            t("Comics", "Komik") to comicFolders.size,
                            t("Gallery", "Galeri") to galleryFolders.size,
                            t("Movies", "Film") to movieFolders.size,
                        ).filter { it.second > 0 }
                            .joinToString(" · ") { "${it.first} ${it.second}" }
                            .ifEmpty { t("No folders added yet.", "Belum ada folder yang ditambahkan.") }
                        Text(summary, fontSize = 13.sp, color = DeckTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isScanActive) {
                        FolderScanIndicator(modifier = Modifier.size(18.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DeckAccentBlue.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (isManagedFoldersExpanded) t("Hide", "Tutup") else t("Show", "Buka"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeckAccentBlue,
                        )
                        Icon(
                            imageVector = if (isManagedFoldersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isManagedFoldersExpanded) t("Collapse", "Tutup") else t("Expand", "Buka"),
                            tint = DeckAccentBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (isManagedFoldersExpanded && totalFolders > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderGroup(t("Comics", "Komik"), comicFolders, Icons.Default.AutoStories, "comics", activeScanTarget) { folderToRemove = it to "comics" }
                    FolderGroup(t("Gallery", "Galeri"), galleryFolders, Icons.Default.Collections, "gallery", activeScanTarget) { folderToRemove = it to "gallery" }
                    FolderGroup(t("Movies", "Film"), movieFolders, Icons.Default.Movie, "movies", activeScanTarget) { folderToRemove = it to "movies" }
                }
            }


            SettingsSectionHeader(t("Local Folders", "Folder Lokal"))
            SettingsCard {
                SettingsToggleRow(
                    title = t("Hide from System Gallery", "Sembunyikan dari Galeri HP"),
                    icon = Icons.Default.VisibilityOff,
                    checked = settings.hideScannedFromGallery,
                    onCheckedChange = {
                        settingsViewModel.setHideScannedFromGallery(it)
                        scannerViewModel.applyGalleryHiding(context, it)
                    },
                )
                SettingsDivider(inset = false)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        t("Add a folder as", "Tambah folder sebagai"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DeckTextSecondary,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FolderTypeButton(
                            label = t("Comics", "Komik"),
                            icon = Icons.Default.AutoStories,
                            enabled = !isScanActive,
                            onClick = { dirLauncherComics.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                        FolderTypeButton(
                            label = t("Gallery", "Galeri"),
                            icon = Icons.Default.Collections,
                            enabled = !isScanActive,
                            onClick = { dirLauncherGallery.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                        FolderTypeButton(
                            label = t("Videos", "Video"),
                            icon = Icons.Default.Movie,
                            enabled = !isScanActive,
                            onClick = { dirLauncherMovies.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }


            SettingsSectionHeader(t("SMB Folders", "Folder SMB"))
            SettingsCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (settings.smbHost.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "${settings.smbHost}${if (settings.smbShare.isNotEmpty()) " / " + settings.smbShare else ""}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeckTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DeckBadgeRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                t("Samba Server has not been configured yet.", "Server Samba belum dikonfigurasi."),
                                fontSize = 13.sp,
                                color = DeckBadgeRed,
                            )
                        }
                    }
                    val smbReady = settings.smbHost.isNotEmpty() && !isScanActive
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FolderTypeButton(
                            label = t("Comics", "Komik"),
                            icon = Icons.Default.AutoStories,
                            enabled = smbReady,
                            onClick = { onStartSmbBrowse("comics") },
                            modifier = Modifier.weight(1f),
                        )
                        FolderTypeButton(
                            label = t("Gallery", "Galeri"),
                            icon = Icons.Default.Collections,
                            enabled = smbReady,
                            onClick = { onStartSmbBrowse("gallery") },
                            modifier = Modifier.weight(1f),
                        )
                        FolderTypeButton(
                            label = t("Videos", "Video"),
                            icon = Icons.Default.Movie,
                            enabled = smbReady,
                            onClick = { onStartSmbBrowse("movies") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                HorizontalDivider(color = com.mediadeck.app.ui.theme.DeckDivider)
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        t("Samba Server Configuration", "Konfigurasi Server Samba"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckAccentBlue,
                    )
                    var hostLocal by remember { mutableStateOf(settings.smbHost) }
                    var shareLocal by remember { mutableStateOf(settings.smbShare) }
                    var userLocal by remember { mutableStateOf(settings.smbUser) }
                    val savedPassword by settingsViewModel.smbPassword.collectAsState()
                    var passLocal by remember { mutableStateOf("") }
                    var passTouched by remember { mutableStateOf(false) }
                    LaunchedEffect(savedPassword) {
                        if (!passTouched) passLocal = savedPassword
                    }
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(DeckSurfaceVariant)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(t("Auto Network Scan", "Pindai Jaringan Otomatis"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeckTextPrimary)
                            Button(
                                onClick = { scannerViewModel.startNetworkDiscovery(context) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = DeckAccentBlue, contentColor = OnAccent),
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(t("Scan", "Pindai"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (discoveredIps.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(discoveredIps.size) { index ->
                                    val ip = discoveredIps[index]
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(DeckAccentBlue.copy(alpha = 0.16f))
                                            .clickable { hostLocal = ip }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(ip, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DeckAccentBlue)
                                    }
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
                        label = { Text(t("Share name", "Nama share"), fontSize = 12.sp) },
                        placeholder = { Text(t("Share name", "Nama share")) },
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
                        Text(t("Guest Login", "Login Tamu"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary)
                        Switch(
                            checked = isGuestOnly,
                            onCheckedChange = { isGuestOnly = it },
                            colors = settingsSwitchColors(),
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
                            placeholder = { Text(t("Username", "Username")) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        OutlinedTextField(
                            value = passLocal,
                            onValueChange = {
                                passTouched = true
                                passLocal = it
                            },
                            label = { Text(t("Password", "Kata Sandi"), fontSize = 12.sp) },
                            placeholder = { Text(t("Password", "Kata Sandi")) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                    }
                    Button(
                        onClick = { saveAndBrowse() },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = CircleShape,
                        enabled = hostLocal.trim().isNotEmpty() && isPortValid,
                        colors = ButtonDefaults.buttonColors(containerColor = DeckAccentBlue, contentColor = OnAccent),
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
        val count = folderRemovalMediaCount
        SettingsConfirmDialog(
            title = t("Remove folder?", "Hapus folder?"),
            message = if (count == null) {
                t("Calculating indexed media to remove...", "Menghitung media terindeks yang akan dihapus...")
            } else {
                t(
                    "This removes the folder, $count indexed media item(s), and their generated thumbnails. Original files will not be changed.",
                    "Ini menghapus folder, $count item media terindeks, dan thumbnail buatannya. File asli tidak akan diubah.",
                )
            },
            confirmLabel = t("Remove", "Hapus"),
            icon = Icons.Default.Delete,
            confirmEnabled = count != null,
            onConfirm = {
                folderToRemove?.let { (uri, type) -> scannerViewModel.removeLibraryFolder(context, uri, type) }
                folderToRemove = null
            },
            onDismiss = { folderToRemove = null },
        )
    }
}

@Composable
private fun FolderTypeButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DeckSurfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary, maxLines = 1)
    }
}

private fun cleanFolderDisplayPath(uriString: String): String {
    if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(uriString)) {
        val clean = com.mediadeck.app.util.media.MediaUtils.cleanSmbUrl(uriString).trimEnd('/')
        return "smb://$clean"
    }
    return try {
        val uri = uriString.toUri()
        val decoded = android.net.Uri.decode(uriString)
        when {
            uri.scheme == "file" -> uri.path ?: uriString
            "primary:" in decoded -> {
                val path = decoded.substringAfter("primary:").trimStart('/')
                if (path.isNotBlank()) "Internal / $path" else "Internal Storage"
            }
            ":" in decoded && !decoded.startsWith("content:") -> {
                val segment = decoded.substringAfterLast("/tree/").substringAfterLast("/document/")
                val volume = segment.substringBefore(":")
                val path = segment.substringAfter(":").trimStart('/')
                if (volume.equals("primary", ignoreCase = true)) {
                    if (path.isNotBlank()) "Internal / $path" else "Internal Storage"
                } else {
                    if (path.isNotBlank()) "$volume / $path" else volume
                }
            }
            else -> {
                val lastSegment = uri.lastPathSegment
                if (lastSegment != null && ":" in lastSegment) {
                    val volume = lastSegment.substringBefore(":")
                    val path = lastSegment.substringAfter(":").trimStart('/')
                    if (volume.equals("primary", ignoreCase = true)) {
                        if (path.isNotBlank()) "Internal / $path" else "Internal Storage"
                    } else {
                        if (path.isNotBlank()) "$volume / $path" else volume
                    }
                } else {
                    uri.path ?: uriString
                }
            }
        }
    } catch (e: Exception) {
        uriString
    }
}

@Composable
private fun FolderGroup(
    title: String,
    folders: List<String>,
    icon: ImageVector,
    type: String,
    activeScanTarget: String?,
    onRemove: (String) -> Unit,
) {
    if (folders.isEmpty()) return
    SettingsCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DeckAccentBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = DeckAccentBlue)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DeckTextPrimary)
            }
            folders.forEach { uri ->
                val isActive = isFolderScanning(uri, type, activeScanTarget)
                val borderColor by animateColorAsState(
                    targetValue = if (isActive) DeckAccentBlue else Color.Transparent,
                    label = "folderBorderColor",
                )
                val folderShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(folderShape)
                        .background(DeckSurfaceVariant)
                        .border(if (isActive) 1.5.dp else 0.dp, borderColor, folderShape)
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val folderTitle = if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(uri)) {
                            uri.removeSuffix("/").substringAfterLast("/").ifBlank { "SMB Folder" }
                        } else {
                            try {
                                uri.toUri().lastPathSegment?.substringAfterLast(":")?.substringAfterLast("/") ?: uri
                            } catch (e: Exception) { uri }
                        }
                        val cleanPath = cleanFolderDisplayPath(uri)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                folderTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DeckTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isActive) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    t("Scanning", "Memindai"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeckAccentBlue,
                                    maxLines = 1,
                                )
                            }
                        }
                        Text(
                            cleanPath,
                            fontSize = 11.sp,
                            color = DeckTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isActive) {
                        FolderScanIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = { onRemove(uri) },
                        enabled = !isActive,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = t("Remove folder", "Hapus folder"),
                            tint = if (isActive) DeckBadgeRed.copy(alpha = 0.3f) else DeckBadgeRed,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderScanIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "folderScanIndicator")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "folderScanRotation",
    )
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = t("Scanning", "Memindai"),
        tint = DeckAccentBlue,
        modifier = modifier.rotate(rotation),
    )
}
