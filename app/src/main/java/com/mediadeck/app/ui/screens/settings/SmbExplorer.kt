package com.mediadeck.app.ui.screens.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckBackground
import com.mediadeck.app.ui.theme.DeckBadgeRed
import com.mediadeck.app.ui.theme.DeckOnAccent
import com.mediadeck.app.ui.theme.DeckSurface
import com.mediadeck.app.ui.theme.DeckSurfaceVariant
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbExplorer(
    settingsViewModel: SettingsViewModel,
    scannerViewModel: ScannerViewModel,
    scanType: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsViewModel.appSettings.collectAsState()
    val browserItems by settingsViewModel.browserItems.collectAsState()
    val isLoading by settingsViewModel.isBrowserLoading.collectAsState()
    val error by settingsViewModel.browserError.collectAsState()
    val needsAuthentication by settingsViewModel.browserNeedsAuthentication.collectAsState()
    val browseShareBySession by settingsViewModel.browseShareBySession.collectAsState()
    val browseSubpathBySession by settingsViewModel.browseSubpathBySession.collectAsState()
    val activeShare = browseShareBySession.ifEmpty { settings.smbShare.trim().trim('/') }
    val displayLocation = listOf(settings.smbHost.trim(), activeShare, browseSubpathBySession)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val savedPassword by settingsViewModel.smbPassword.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var showAuthForm by remember { mutableStateOf(false) }
    var isNavigatingAway by remember { mutableStateOf(false) }
    LaunchedEffect(settings.smbUser, savedPassword, settings.smbDomain) {
        username = settings.smbUser
        password = savedPassword
        domain = settings.smbDomain
    }
    LaunchedEffect(browserItems, isLoading) {
        if (!isLoading && browserItems.isNotEmpty()) {
            showAuthForm = false
        }
    }
    androidx.activity.compose.BackHandler {
        settingsViewModel.navigateUpBrowser()
    }
    LaunchedEffect(needsAuthentication) {
        if (needsAuthentication) {
            showAuthForm = true
        }
    }

    val atRoot = activeShare.isEmpty()
    val subtitle = when {
        !atRoot -> displayLocation
        settings.smbHost.isBlank() -> t("Connecting...", "Menghubungkan...")
        else -> t("Choose a share", "Pilih share")
    }
    val crumbs = buildList {
        add(settings.smbHost.trim())
        if (activeShare.isNotEmpty()) add(activeShare)
        addAll(browseSubpathBySession.split('/').filter { it.isNotBlank() })
    }.filter { it.isNotBlank() }
    val folders = browserItems.filter { it.isDirectory }
    val files = browserItems.filterNot { it.isDirectory }

    Scaffold(
        containerColor = DeckBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            t("Select SMB Folder", "Pilih Folder SMB"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DeckTextPrimary,
                        )
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = DeckTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Kembali"))
                    }
                },
                actions = {
                    IconButton(onClick = { settingsViewModel.retryBrowserLoading() }) {
                        Icon(Icons.Default.Refresh, contentDescription = t("Refresh", "Muat Ulang"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeckBackground,
                    navigationIconContentColor = DeckTextPrimary,
                    actionIconContentColor = DeckTextPrimary,
                ),
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                if (crumbs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        crumbs.forEachIndexed { index, crumb ->
                            val isLast = index == crumbs.lastIndex
                            if (index > 0) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = DeckTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isLast) DeckAccentBlue.copy(alpha = 0.16f) else DeckSurfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    crumb,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isLast) DeckAccentBlue else DeckTextPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                if (showAuthForm) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeckSurface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = DeckBadgeRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(t("Samba Authorization Required", "Otorisasi Samba Diperlukan"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeckBadgeRed)
                            }
                            if (error != null) {
                                Text("Detail: $error", fontSize = 12.sp, color = DeckBadgeRed)
                            } else {
                                Text(t("Server restricted access without valid credentials.", "Server membatasi akses tanpa kredensial yang valid."), fontSize = 12.sp, color = DeckTextSecondary)
                            }
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(t("Username", "Username")) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(t("Password", "Password")) },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = domain,
                                onValueChange = { domain = it },
                                label = { Text(t("Domain (Workgroup)", "Domain (Workgroup)")) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAuthForm = false }) {
                                    Text(t("Cancel", "Batal"), color = DeckTextPrimary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        settingsViewModel.updateSmbPreferencesAndBrowse(
                                            host = settings.smbHost,
                                            share = settings.smbShare,
                                            user = username,
                                            pass = password,
                                            domain = domain,
                                            port = settings.smbPort,
                                            enableSMB2 = settings.smbEnableSMB2,
                                            disableSMB1 = settings.smbDisableSMB1,
                                            connTimeout = settings.smbConnTimeout,
                                            soTimeout = settings.smbSoTimeout,
                                            isGuest = false,
                                        )
                                        showAuthForm = false
                                    },
                                    enabled = !isLoading,
                                    shape = CircleShape,
                                    modifier = Modifier.height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeckAccentBlue,
                                        contentColor = DeckOnAccent,
                                    ),
                                ) {
                                    Text(t("Connect", "Hubungkan"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                if (error != null && !showAuthForm) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeckSurface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(t("Samba Connection Failed", "Koneksi Samba Gagal"), fontWeight = FontWeight.Bold, color = DeckBadgeRed)
                            Text(error.orEmpty(), fontSize = 12.sp, color = DeckBadgeRed)
                            Button(
                                onClick = settingsViewModel::retryBrowserLoading,
                                enabled = !isLoading,
                                shape = CircleShape,
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeckAccentBlue,
                                    contentColor = DeckOnAccent,
                                ),
                            ) {
                                Text(t("Retry", "Coba Lagi"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = DeckAccentBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(t("Loading Network Folders...", "Membaca Folder Jaringan..."), fontSize = 14.sp, color = DeckTextSecondary)
                        }
                    } else if (browserItems.isEmpty() && error == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = DeckTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(t("Empty Directory or Non-readable", "Folder Kosong / Tidak Terlihat"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeckTextPrimary)
                            Text(t("Make sure this folder has compatible shares and is readable.", "Pastikan folder ini berisi berkas dan bukan folder tersembunyi."), fontSize = 12.sp, color = DeckTextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 168.dp)
                        ) {
                            items(folders.size) { index ->
                                val item = folders[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DeckSurface)
                                        .clickable { settingsViewModel.navigateIntoDirectory(item) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DeckAccentBlue.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (atRoot) Icons.Default.Dns else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = DeckAccentBlue,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeckTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (atRoot) t("Share", "Share") else t("Folder", "Folder"),
                                            fontSize = 13.sp,
                                            color = DeckTextSecondary,
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DeckTextSecondary)
                                }
                            }
                            if (files.isNotEmpty()) {
                                item {
                                    Text(
                                        t("Files · can't be selected", "Berkas · tidak bisa dipilih").uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = DeckTextSecondary,
                                        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                }
                                items(files.size) { index ->
                                    val item = files[index]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(0.55f)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = DeckTextSecondary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = DeckTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                android.text.format.Formatter.formatShortFileSize(context, item.size),
                                                fontSize = 12.sp,
                                                color = DeckTextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isLoading) {
                Surface(
                    color = DeckSurfaceVariant,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(22.dp), tint = DeckAccentBlue)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                displayLocation.ifEmpty { activeShare },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = DeckTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val icon = when (scanType) {
                            "comics" -> Icons.Default.AutoStories
                            "movies" -> Icons.Default.Movie
                            else -> Icons.Default.Image
                        }
                        if (atRoot) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(icon, contentDescription = null, tint = DeckTextSecondary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    t("Open a folder to scan", "Buka folder untuk dipindai"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeckTextSecondary,
                                )
                            }
                        } else {
                            val triggerScan = { type: String ->
                                if (!isNavigatingAway) {
                                    isNavigatingAway = true
                                    val fullUrl = com.mediadeck.app.util.smb.SmbScanner.buildSmbUrl(
                                        host = settings.smbHost,
                                        port = settings.smbPort,
                                        share = activeShare,
                                        subpath = browseSubpathBySession
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        (if (settings.language == "en") "Scanning: " else "Memindai: ") + activeShare,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    scannerViewModel.saveLibraryFolder(context, fullUrl, type)
                                    settingsViewModel.clearBrowserState()
                                    scannerViewModel.runFolderScan(fullUrl, type)
                                }
                            }
                            val label = when (scanType) {
                                "comics" -> t("Scan Comics Here", "Pindai Komik di Sini")
                                "movies" -> t("Scan Movies Here", "Pindai Film di Sini")
                                else -> t("Scan Gallery Here", "Pindai Galeri di Sini")
                            }
                            Button(
                                onClick = {
                                    triggerScan(
                                        when (scanType) {
                                            "comics" -> "comics"
                                            "movies" -> "movies"
                                            else -> "gallery"
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = CircleShape,
                                enabled = !isNavigatingAway,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeckAccentBlue,
                                    contentColor = DeckOnAccent,
                                ),
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
