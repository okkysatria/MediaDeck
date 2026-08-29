package com.mediadeck.app.ui.screens.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
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
    val currentPath by settingsViewModel.currentBrowserPath.collectAsState()
    val browserItems by settingsViewModel.browserItems.collectAsState()
    val isLoading by settingsViewModel.isBrowserLoading.collectAsState()
    val error by settingsViewModel.browserError.collectAsState()
    val needsAuthentication by settingsViewModel.browserNeedsAuthentication.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Samba Network Explorer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            currentPath.ifEmpty { t("Connecting...", "Menghubungkan...") },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showAuthForm || needsAuthentication) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(t("Samba Authorization Required", "Otorisasi Samba Diperlukan"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            }
                            
                            if (error != null) {
                                Text("Detail: $error", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(t("Server restricted access without valid credentials.", "Server membatasi akses tanpa kredensial yang valid."), fontSize = 11.sp)
                            }

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(t("Username", "Username")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(t("Password", "Password")) },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = domain,
                                onValueChange = { domain = it },
                                label = { Text(t("Domain (Workgroup)", "Domain (Workgroup)")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAuthForm = false }) {
                                    Text(t("Cancel", "Batal"))
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
                                    enabled = !isLoading
                                ) {
                                    Text(t("Connect", "Hubungkan"))
                                }
                            }
                        }
                    }
                }

                if (error != null && !needsAuthentication) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(t("Samba Connection Failed", "Koneksi Samba Gagal"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(error.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Button(onClick = settingsViewModel::retryBrowserLoading, enabled = !isLoading) {
                                Text(t("Retry", "Coba Lagi"))
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
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(t("Loading Network Folders...", "Membaca Folder Jaringan..."), fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
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
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(t("Empty Directory or Non-readable", "Folder Kosong / Tidak Terlihat"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(t("Make sure this folder has compatible shares and is readable.", "Pastikan folder ini berisi berkas dan bukan folder tersembunyi."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(browserItems.size) { index ->
                                val item = browserItems[index]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (item.isDirectory) {
                                                settingsViewModel.navigateIntoDirectory(item)
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.isDirectory) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = if (item.isDirectory) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (item.isDirectory) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                            if (!item.isDirectory) {
                                                Text(
                                                    android.text.format.Formatter.formatShortFileSize(context, item.size),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            } else {
                                                Text(
                                                    t("Shared Directory", "Direktori Bersama"),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
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

            val browseShareBySession by settingsViewModel.browseShareBySession.collectAsState()
            val browseSubpathBySession by settingsViewModel.browseSubpathBySession.collectAsState()

            val activeShare = browseShareBySession.ifEmpty { settings.smbShare.trim().trim('/') }

            if (activeShare.isNotEmpty() && !isLoading) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .padding(bottom = 8.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                t("Current Location: ", "Lokasi Sekarang: "),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            "$activeShare${if (browseSubpathBySession.isNotEmpty()) "/" + browseSubpathBySession else ""}",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        
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
                                scannerViewModel.runFolderScan(fullUrl, type)
                                onBack()
                            }
                        }

                        Button(
                            onClick = { 
                                triggerScan(when(scanType) {
                                    "comics" -> "comics"
                                    "movies" -> "movies"
                                    else -> "gallery"
                                })
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isNavigatingAway,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            val icon = when(scanType) {
                                "comics" -> Icons.Default.AutoStories
                                "movies" -> Icons.Default.Movie
                                else -> Icons.Default.Collections
                            }
                            val label = when(scanType) {
                                "comics" -> t("Scan Comics Here", "Pindai Komik di Sini")
                                "movies" -> t("Scan Movies Here", "Pindai Film di Sini")
                                else -> t("Scan Gallery Here", "Pindai Galeri di Sini")
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
