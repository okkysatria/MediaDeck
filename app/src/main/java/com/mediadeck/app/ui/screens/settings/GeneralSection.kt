package com.mediadeck.app.ui.screens.settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.SettingsViewModel
import com.mediadeck.app.ui.theme.AmberGold
import com.mediadeck.app.ui.theme.MediaBlue
import com.mediadeck.app.ui.theme.MidnightNavy
import com.mediadeck.app.ui.theme.MidnightPurple
import kotlin.time.Duration.Companion.milliseconds
@Composable
fun GeneralSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsSectionHeader(t("Language", "Bahasa"), Icons.Default.Language, SettingsAccent.Primary)
        SettingsCard(SettingsAccent.Primary) {
            SettingsPickerRow(
                title = t("Display Language", "Bahasa Tampilan"),
                selectedLabel = if (settings.language == "en") "English" else "Bahasa Indonesia",
                icon = Icons.Default.Language,
                accent = SettingsAccent.Primary,
            ) { dismiss ->
                DropdownMenuItem(text = { Text("English") }, onClick = { viewModel.setLanguage("en"); dismiss() })
                DropdownMenuItem(text = { Text("Bahasa Indonesia") }, onClick = { viewModel.setLanguage("id"); dismiss() })
            }
        }
        SettingsSectionHeader(
            t("Appearance", "Tampilan"),
            Icons.Default.Palette,
            SettingsAccent.Secondary,
        )
        SettingsCard(SettingsAccent.Secondary) {
            SettingsPickerRow(
                title = t("Visual Theme", "Tema Visual"),
                selectedLabel = when (settings.theme) {
                    "light" -> t("Light", "Terang")
                    "dark" -> t("Dark", "Gelap")
                    "orange" -> t("Orange", "Oranye")
                    "purple" -> t("Purple", "Ungu")
                    else -> t("System Default", "Default Sistem")
                },
                icon = Icons.Default.Palette,
                accent = SettingsAccent.Secondary,
            ) { dismiss ->
                DropdownMenuItem(leadingIcon = { ThemeSwatch(MaterialTheme.colorScheme.primary) }, text = { Text(t("System Default", "Default Sistem")) }, onClick = { viewModel.updateTheme("system"); dismiss() })
                DropdownMenuItem(leadingIcon = { ThemeSwatch(MidnightNavy) }, text = { Text(t("Light", "Terang")) }, onClick = { viewModel.updateTheme("light"); dismiss() })
                DropdownMenuItem(leadingIcon = { ThemeSwatch(MediaBlue) }, text = { Text(t("Dark", "Gelap")) }, onClick = { viewModel.updateTheme("dark"); dismiss() })
                DropdownMenuItem(leadingIcon = { ThemeSwatch(AmberGold) }, text = { Text(t("Orange", "Oranye")) }, onClick = { viewModel.updateTheme("orange"); dismiss() })
                DropdownMenuItem(leadingIcon = { ThemeSwatch(MidnightPurple) }, text = { Text(t("Purple", "Ungu")) }, onClick = { viewModel.updateTheme("purple"); dismiss() })
            }
        }
        SettingsSectionHeader(t("Library View", "Tampilan Pustaka"), Icons.Default.GridView, SettingsAccent.Secondary)
        SettingsCard(SettingsAccent.Secondary) {
            SettingsPickerRow(
                title = t("Display Mode", "Mode Tampilan"),
                selectedLabel = if (settings.layoutMode == "title_only") t("Title Only", "Hanya Judul") else t("Grid View", "Tampilan Grid"),
                icon = Icons.Default.GridView,
                accent = SettingsAccent.Secondary,
            ) { dismiss ->
                DropdownMenuItem(text = { Text(t("Grid (Thumbnails)", "Grid (Gambar Mini)")) }, onClick = { viewModel.setLayoutMode("grid"); dismiss() })
                DropdownMenuItem(text = { Text(t("Title Only (List)", "Hanya Judul (Daftar)")) }, onClick = { viewModel.setLayoutMode("title_only"); dismiss() })
            }
            if (settings.layoutMode == "grid") {
                SettingsDivider()
                SettingsPickerRow(
                    title = t("Grid Columns", "Kolom Grid"),
                    selectedLabel = when (settings.gridColumns) {
                        1 -> t("Large (1 Col)", "Besar (1 Kol)")
                        2 -> t("Standard (2 Col)", "Standar (2 Kol)")
                        else -> t("Adaptive", "Adaptif")
                    },
                    icon = Icons.Default.AspectRatio,
                    accent = SettingsAccent.Secondary,
                ) { dismiss ->
                    DropdownMenuItem(text = { Text(t("Adaptive (Auto)", "Adaptif (Otomatis)")) }, onClick = { viewModel.setGridColumns(0); dismiss() })
                    DropdownMenuItem(text = { Text(t("Large (1 Column)", "Besar (1 Kolom)")) }, onClick = { viewModel.setGridColumns(1); dismiss() })
                    DropdownMenuItem(text = { Text(t("Standard (2 Columns)", "Standar (2 Kolom)")) }, onClick = { viewModel.setGridColumns(2); dismiss() })
                }
                SettingsDivider()
                SettingsPickerRow(
                    title = t("Gallery Grid", "Grid Galeri"),
                    selectedLabel = if (settings.galleryGridType == "staggered") t("Staggered", "Staggered") else t("Uniform (Square)", "Kotak (Rapi)"),
                    icon = Icons.Default.GridView,
                    accent = SettingsAccent.Secondary,
                ) { dismiss ->
                    DropdownMenuItem(text = { Text(t("Staggered (Dynamic)", "Staggered (Dinamis)")) }, onClick = { viewModel.setGalleryGridType("staggered"); dismiss() })
                    DropdownMenuItem(text = { Text(t("Uniform (Square)", "Rapi (Kotak)")) }, onClick = { viewModel.setGalleryGridType("uniform"); dismiss() })
                }
                SettingsDivider()
                SettingsToggleRow(
                    title = t("Auto-open 1-Video Folders", "Buka Otomatis Folder 1 Video"),
                    icon = Icons.Default.FolderOpen,
                    accent = SettingsAccent.Secondary,
                    checked = settings.autoUnwrapSingleVideoFolders,
                    onCheckedChange = viewModel::setAutoUnwrapSingleVideoFolders,
                )
            }
        }
        SettingsSectionHeader(t("App Behavior", "Perilaku Aplikasi"), Icons.Default.Tune, SettingsAccent.Tertiary)
        SettingsCard(SettingsAccent.Tertiary) {
            SettingsToggleRow(
                title = t("Stay Awake", "Layar Tetap Aktif"),
                icon = Icons.Default.LightMode,
                accent = SettingsAccent.Tertiary,
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
                testTag = "keep_screen_on_switch",
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Show Side Scrollbar", "Tampilkan Scrollbar Samping"),
                icon = Icons.Default.FormatLineSpacing,
                accent = SettingsAccent.Tertiary,
                checked = settings.showSideScrollbar,
                onCheckedChange = viewModel::setShowSideScrollbar,
            )
        }
        SettingsSectionHeader(t("Comic Reader", "Pembaca Komik"), Icons.Default.ImportContacts, SettingsAccent.Primary)
        SettingsCard(SettingsAccent.Primary) {
            SettingsPickerRow(
                title = t("Reading Mode", "Mode Membaca"),
                selectedLabel = if (settings.defaultReaderMode == "vertical") t("Vertical", "Vertikal") else t("Horizontal", "Horizontal"),
                icon = Icons.Default.ImportContacts,
                accent = SettingsAccent.Primary,
            ) { dismiss ->
                DropdownMenuItem(text = { Text(t("Vertical (Webtoon)", "Vertikal (Webtoon)")) }, onClick = { viewModel.setDefaultReaderMode("vertical"); dismiss() })
                DropdownMenuItem(text = { Text(t("Horizontal (Manga)", "Horizontal (Manga)")) }, onClick = { viewModel.setDefaultReaderMode("horizontal"); dismiss() })
            }
            SettingsPickerRow(
                title = t("Page Transition", "Transisi Halaman"),
                selectedLabel = when (settings.comicPageTransition) {
                    "slide" -> "Slide"
                    "fade" -> "Fade"
                    else -> "Page Curl"
                },
                icon = Icons.Default.ImportContacts,
                accent = SettingsAccent.Primary,
            ) { dismiss ->
                listOf("curl" to "Page Curl", "slide" to "Slide", "fade" to "Fade").forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { viewModel.setComicPageTransition(value); dismiss() },
                    )
                }
            }
            SettingsDivider()
            SettingsPickerRow(
                title = t("Page Spacing", "Jarak Halaman"),
                selectedLabel = settings.verticalPageGap.replaceFirstChar { it.uppercase() },
                icon = Icons.Default.FormatLineSpacing,
                accent = SettingsAccent.Primary,
            ) { dismiss ->
                listOf("none", "small", "medium").forEach { gap ->
                    DropdownMenuItem(
                        text = { Text(gap.replaceFirstChar { it.uppercase() }) },
                        onClick = { viewModel.setVerticalPageGap(gap); dismiss() },
                    )
                }
            }
            SettingsDivider()
            SettingsToggleRow(
                title = t("Auto-hide UI", "Sembunyikan UI Otomatis"),
                icon = Icons.Default.VisibilityOff,
                accent = SettingsAccent.Primary,
                checked = settings.autoHideReaderUi,
                onCheckedChange = viewModel::setAutoHideReaderUi,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Volume Key Navigation", "Navigasi Tombol Volume"),
                icon = Icons.Default.VolumeUp,
                accent = SettingsAccent.Primary,
                checked = settings.readerVolumeKeysNavigation,
                onCheckedChange = viewModel::setReaderVolumeKeysNavigation,
            )
        }
        SettingsSectionHeader(t("Video Player", "Pemutar Video"), Icons.Default.AspectRatio, SettingsAccent.Secondary)
        SettingsCard(SettingsAccent.Secondary) {
            SettingsPickerRow(
                title = t("Fit Mode", "Mode Ukuran"),
                selectedLabel = when (settings.defaultVideoZoomMode) {
                    1 -> t("Fill", "Isi")
                    2 -> t("Zoom", "Perbesar")
                    else -> t("Fit", "Pas")
                },
                icon = Icons.Default.AspectRatio,
                accent = SettingsAccent.Secondary,
            ) { dismiss ->
                DropdownMenuItem(text = { Text(t("Fit", "Pas")) }, onClick = { viewModel.setDefaultVideoZoomMode(0); dismiss() })
                DropdownMenuItem(text = { Text(t("Fill", "Isi")) }, onClick = { viewModel.setDefaultVideoZoomMode(1); dismiss() })
                DropdownMenuItem(text = { Text(t("Zoom", "Perbesar")) }, onClick = { viewModel.setDefaultVideoZoomMode(2); dismiss() })
            }
            SettingsDivider()
            SettingsPickerRow(
                title = t("Default Orientation", "Orientasi Standar"),
                selectedLabel = when (settings.defaultVideoOrientation) {
                    1 -> t("Landscape", "Lanskap")
                    2 -> t("Portrait", "Potret")
                    else -> t("Auto", "Otomatis")
                },
                icon = Icons.Default.ScreenRotation,
                accent = SettingsAccent.Secondary,
            ) { dismiss ->
                DropdownMenuItem(text = { Text(t("Auto (Sensor)", "Otomatis (Sensor)")) }, onClick = { viewModel.setDefaultVideoOrientation(0); dismiss() })
                DropdownMenuItem(text = { Text(t("Landscape Only", "Hanya Lanskap")) }, onClick = { viewModel.setDefaultVideoOrientation(1); dismiss() })
                DropdownMenuItem(text = { Text(t("Portrait Only", "Hanya Potret")) }, onClick = { viewModel.setDefaultVideoOrientation(2); dismiss() })
            }
            SettingsDivider()
            SettingsPickerRow(
                title = t("Skip Interval", "Interval Lompat"),
                selectedLabel = "${settings.videoSkipInterval}s",
                icon = Icons.Default.Forward10,
                accent = SettingsAccent.Secondary,
            ) { dismiss ->
                listOf(5, 10, 15, 30, 60).forEach { sec ->
                    DropdownMenuItem(text = { Text("${sec}s") }, onClick = { viewModel.setVideoSkipInterval(sec); dismiss() })
                }
            }
            SettingsDivider()
            SettingsToggleRow(
                title = t("Enable PiP Mode", "Aktifkan Mode PiP"),
                icon = Icons.Default.AspectRatio,
                accent = SettingsAccent.Secondary,
                checked = settings.enablePiP,
                onCheckedChange = viewModel::setEnablePiP,
            )
        }
        SettingsSectionHeader(
            t("Filename Parsing", "Format Nama Berkas"),
            Icons.Default.Tune,
            SettingsAccent.Tertiary,
        )
        SettingsCard(SettingsAccent.Tertiary) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var showFormatMenu by remember { mutableStateOf(false) }
                val customFormatSelected = settings.scanFormat.startsWith(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)
                var customFormat by remember {
                    mutableStateOf(
                        settings.scanFormat.removePrefix(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)
                            .takeIf { customFormatSelected }
                            ?: AppSettings.CUSTOM_SCAN_FORMAT_DEFAULT,
                    )
                }
                LaunchedEffect(settings.scanFormat) {
                    if (settings.scanFormat.startsWith(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)) {
                        customFormat = settings.scanFormat.removePrefix(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)
                    }
                }
                LaunchedEffect(customFormat, customFormatSelected) {
                    if (customFormatSelected && customFormat.isNotBlank()) {
                        kotlinx.coroutines.delay(500.milliseconds)
                        viewModel.setScanFormat(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX + customFormat)
                    }
                }
                Column {
                    Text(
                        t("Name Extraction Pattern", "Pola Ekstraksi Nama"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { showFormatMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            Text(
                                if (customFormatSelected) t("Custom pattern", "Pola kustom") else settings.scanFormat,
                                fontSize = 14.sp,
                            )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        val customPatternOption = t("Custom pattern...", "Pola kustom...")
                        DropdownMenu(
                            expanded = showFormatMenu,
                            onDismissRequest = { showFormatMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            listOf(
                                "[Bracket] tags - Title",
                                "ID Title - tags",
                                "Title - tags",
                                AppSettings.TITLE_EPISODE_TAGS_FORMAT,
                                "ID tags - Title",
                                "tags - Title",
                                customPatternOption,
                            ).forEach { formatOption ->
                                DropdownMenuItem(
                                    text = { Text(formatOption) },
                                    onClick = {
                                        if (formatOption == customPatternOption) {
                                            customFormat = AppSettings.CUSTOM_SCAN_FORMAT_DEFAULT
                                            viewModel.setScanFormat(
                                                AppSettings.CUSTOM_SCAN_FORMAT_PREFIX + customFormat,
                                            )
                                        } else {
                                            viewModel.setScanFormat(formatOption)
                                        }
                                        showFormatMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (customFormatSelected) {
                    OutlinedTextField(
                        value = customFormat,
                        onValueChange = { customFormat = it.take(160) },
                        label = { Text(t("Custom pattern", "Pola kustom")) },
                        placeholder = { Text(AppSettings.CUSTOM_SCAN_FORMAT_DEFAULT) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var sepLocal by remember { mutableStateOf(settings.tagSeparator) }
                    var delimLocal by remember { mutableStateOf(settings.tagDelimiter) }
                    LaunchedEffect(settings.tagSeparator, settings.tagDelimiter) {
                        if (sepLocal.isEmpty()) sepLocal = settings.tagSeparator
                        if (delimLocal.isEmpty()) delimLocal = settings.tagDelimiter
                    }
                    LaunchedEffect(sepLocal) {
                        if (sepLocal == settings.tagSeparator) return@LaunchedEffect
                        kotlinx.coroutines.delay(500.milliseconds)
                        viewModel.setTagSeparator(sepLocal)
                    }
                    LaunchedEffect(delimLocal) {
                        if (delimLocal == settings.tagDelimiter) return@LaunchedEffect
                        kotlinx.coroutines.delay(500.milliseconds)
                        viewModel.setTagDelimiter(delimLocal)
                    }
                    OutlinedTextField(
                        value = sepLocal,
                        onValueChange = { if (it.length <= 3) sepLocal = it },
                        label = { Text(t("Primary Separator", "Pemisah Utama"), fontSize = 12.sp) },
                        placeholder = { Text("-") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    OutlinedTextField(
                        value = delimLocal,
                        onValueChange = { if (it.length <= 3) delimLocal = it },
                        label = { Text(t("Tag Separator", "Pemisah Tag"), fontSize = 12.sp) },
                        placeholder = { Text(",") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                }
            }
            SettingsDivider()
            SettingsToggleRow(
                title = t("Remove Numeric IDs", "Hapus ID Angka"),
                icon = Icons.Default.Tag,
                accent = SettingsAccent.Tertiary,
                checked = settings.stripNumericId,
                onCheckedChange = viewModel::setStripNumericId,
                testTag = "strip_numeric_id_switch",
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Lowercase Tags", "Tag Huruf Kecil"),
                icon = Icons.Default.TextFormat,
                accent = SettingsAccent.Tertiary,
                checked = settings.lowercaseTags,
                onCheckedChange = viewModel::setLowercaseTags,
                testTag = "lowercase_tags_switch",
            )
        }
        SettingsSectionHeader(t("Scanning", "Pemindaian"), Icons.Default.Refresh, SettingsAccent.Primary)
        SettingsCard(SettingsAccent.Primary) {
            SettingsToggleRow(
                title = t("Scan on Startup", "Pindai Saat Buka"),
                icon = Icons.Default.Refresh,
                accent = SettingsAccent.Primary,
                checked = settings.autoScanOnStart,
                onCheckedChange = viewModel::setAutoScanOnStart,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Skip Duplicates", "Lewati Duplikat"),
                icon = Icons.Default.ContentCopy,
                accent = SettingsAccent.Primary,
                checked = settings.skipDuplicateScan,
                onCheckedChange = viewModel::setSkipDuplicateScan,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Video Thumbnails", "Thumbnail Video"),
                icon = Icons.Default.Movie,
                accent = SettingsAccent.Primary,
                checked = settings.videoThumbnails,
                onCheckedChange = viewModel::setVideoThumbnails,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Floating Scan Indicator", "Indikator Pindai Melayang"),
                icon = Icons.Default.NotificationsActive,
                accent = SettingsAccent.Primary,
                checked = settings.floatingScanStatus,
                onCheckedChange = viewModel::setFloatingScanStatus,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Local Scan Priority", "Prioritas Pindai Lokal"),
                icon = Icons.Default.PhoneAndroid,
                accent = SettingsAccent.Primary,
                checked = settings.prioritizeLocalScan,
                onCheckedChange = viewModel::setPrioritizeLocalScan,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Hide Offline SMB", "Sembunyikan SMB Offline"),
                icon = Icons.Default.CloudOff,
                accent = SettingsAccent.Primary,
                checked = settings.hideOfflineSmb,
                onCheckedChange = viewModel::setHideOfflineSmb,
            )
        }
        SettingsSectionHeader(t("Network Streaming (SMB)", "Streaming Jaringan (SMB)"), Icons.Default.Tune, SettingsAccent.Tertiary)
        SettingsCard(SettingsAccent.Tertiary) {
            SettingsPickerRow(
                title = t("Network Buffer", "Buffer Jaringan"),
                selectedLabel = "${settings.smbNetworkCachingMs / 1000}s (${settings.smbNetworkCachingMs} ms)",
                icon = Icons.Default.Tune,
                accent = SettingsAccent.Tertiary,
            ) { dismiss ->
                listOf(1000, 2000, 3000, 5000, 10000, 20000, 30000).forEach { ms ->
                    DropdownMenuItem(
                        text = { Text("${ms / 1000}s (${ms} ms)") },
                        onClick = {
                            viewModel.setSmbNetworkCachingMs(ms)
                            dismiss()
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun ThemeSwatch(color: Color) {
    Surface(
        modifier = Modifier.size(18.dp),
        shape = CircleShape,
        color = color,
        content = {},
    )
}