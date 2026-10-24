package com.mediadeck.app.ui.screens.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.theme.DeckSurfaceVariant
import com.mediadeck.app.ui.theme.DeckTextPrimary
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.SettingsViewModel
import kotlin.time.Duration.Companion.milliseconds

private data class PatternExample(val format: String, val sample: String, val result: String)

@Composable
private fun patternExamples(): List<PatternExample> {
    val title = t("Title", "Judul")
    val tags = t("Tags", "Tag")
    val episode = t("Episode", "Episode")
    return listOf(
        PatternExample("[Bracket] tags - Title", "[action, drama] - My Title", "$title: My Title · $tags: action, drama"),
        PatternExample("ID Title - tags", "1042 My Title - action, drama", "ID: 1042 · $title: My Title · $tags: action, drama"),
        PatternExample("Title - tags", "My Title - action, drama", "$title: My Title · $tags: action, drama"),
        PatternExample(AppSettings.TITLE_EPISODE_TAGS_FORMAT, "My Title - Episode 01 [action, drama]", "$title: My Title · $episode: 01 · $tags: action, drama"),
        PatternExample("ID tags - Title", "1042 action, drama - My Title", "ID: 1042 · $tags: action, drama · $title: My Title"),
        PatternExample("tags - Title", "action, drama - My Title", "$tags: action, drama · $title: My Title"),
    )
}

@Composable
fun GeneralSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        SettingsSectionHeader(t("Display", "Tampilan"))
        SettingsCard {
            SettingsPickerRow(
                title = t("Display Language", "Bahasa Tampilan"),
                selectedLabel = if (settings.language == "en") "English" else "Bahasa Indonesia",
                icon = Icons.Default.Language,
                options = listOf(
                    PickerOption("English", settings.language == "en", { viewModel.setLanguage("en") }),
                    PickerOption("Bahasa Indonesia", settings.language != "en", { viewModel.setLanguage("id") }),
                ),
            )
            SettingsDivider()
            SettingsPickerRow(
                title = t("Visual Theme", "Tema Visual"),
                selectedLabel = when (settings.theme) {
                    "light" -> t("Light", "Terang")
                    "dark" -> t("Dark", "Gelap")
                    else -> t("System default", "Default sistem")
                },
                icon = Icons.Default.Palette,
                options = listOf(
                    PickerOption(t("System default", "Default sistem"), settings.theme != "light" && settings.theme != "dark", { viewModel.updateTheme("system") }),
                    PickerOption(t("Light", "Terang"), settings.theme == "light", { viewModel.updateTheme("light") }),
                    PickerOption(t("Dark", "Gelap"), settings.theme == "dark", { viewModel.updateTheme("dark") }),
                ),
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Stay Awake", "Layar Tetap Aktif"),
                icon = Icons.Default.LightMode,
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
                testTag = "keep_screen_on_switch",
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Show Side Scrollbar", "Tampilkan Scrollbar Samping"),
                icon = Icons.Default.FormatLineSpacing,
                checked = settings.showSideScrollbar,
                onCheckedChange = viewModel::setShowSideScrollbar,
            )
            SettingsDivider()
            SettingsPickerRow(
                title = t("Network Buffer", "Buffer Jaringan"),
                selectedLabel = "${settings.smbNetworkCachingMs / 1000}s (${settings.smbNetworkCachingMs} ms)",
                icon = Icons.Default.Tune,
                options = listOf(1000, 2000, 3000, 5000, 10000, 20000, 30000).map { ms ->
                    PickerOption("${ms / 1000}s ($ms ms)", settings.smbNetworkCachingMs == ms, { viewModel.setSmbNetworkCachingMs(ms) })
                },
            )
        }

        SettingsSectionHeader(t("Comic Reader", "Pembaca Komik"))
        SettingsCard {
            SettingsPickerRow(
                title = t("Reading Mode", "Mode Membaca"),
                selectedLabel = if (settings.defaultReaderMode == "vertical") t("Vertical", "Vertikal") else t("Horizontal", "Horizontal"),
                icon = Icons.Default.ImportContacts,
                options = listOf(
                    PickerOption(t("Vertical (Webtoon)", "Vertikal (Webtoon)"), settings.defaultReaderMode == "vertical", { viewModel.setDefaultReaderMode("vertical") }),
                    PickerOption(t("Horizontal (Manga)", "Horizontal (Manga)"), settings.defaultReaderMode != "vertical", { viewModel.setDefaultReaderMode("horizontal") }),
                ),
            )
            SettingsDivider()
            SettingsPickerRow(
                title = t("Page Transition", "Transisi Halaman"),
                selectedLabel = when (settings.comicPageTransition) {
                    "slide" -> "Slide"
                    "fade" -> "Fade"
                    else -> "Page Curl"
                },
                icon = Icons.Default.ViewCarousel,
                options = listOf("curl" to "Page Curl", "slide" to "Slide", "fade" to "Fade").map { (value, label) ->
                    val selected = when (settings.comicPageTransition) {
                        "slide", "fade" -> settings.comicPageTransition == value
                        else -> value == "curl"
                    }
                    PickerOption(label, selected, { viewModel.setComicPageTransition(value) })
                },
            )
            SettingsDivider()
            SettingsPickerRow(
                title = t("Page Spacing", "Jarak Halaman"),
                selectedLabel = settings.verticalPageGap.replaceFirstChar { it.uppercase() },
                icon = Icons.Default.FormatLineSpacing,
                options = listOf("none", "small", "medium").map { gap ->
                    PickerOption(gap.replaceFirstChar { it.uppercase() }, settings.verticalPageGap == gap, { viewModel.setVerticalPageGap(gap) })
                },
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Auto-hide UI", "Sembunyikan UI Otomatis"),
                icon = Icons.Default.VisibilityOff,
                checked = settings.autoHideReaderUi,
                onCheckedChange = viewModel::setAutoHideReaderUi,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Volume Key Navigation", "Navigasi Tombol Volume"),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = settings.readerVolumeKeysNavigation,
                onCheckedChange = viewModel::setReaderVolumeKeysNavigation,
            )
        }

        SettingsSectionHeader(t("Gallery View", "Tampilan Galeri"))
        SettingsCard {
            SettingsPickerRow(
                title = t("Display Mode", "Mode Tampilan"),
                selectedLabel = if (settings.layoutMode == "title_only") t("Title Only", "Hanya Judul") else t("Grid View", "Tampilan Grid"),
                icon = Icons.Default.GridView,
                options = listOf(
                    PickerOption(t("Grid (Thumbnails)", "Grid (Gambar Mini)"), settings.layoutMode != "title_only", { viewModel.setLayoutMode("grid") }),
                    PickerOption(t("Title Only (List)", "Hanya Judul (Daftar)"), settings.layoutMode == "title_only", { viewModel.setLayoutMode("title_only") }),
                ),
            )
            if (settings.layoutMode == "grid") {
                SettingsDivider()
                SettingsPickerRow(
                    title = t("Grid Columns", "Kolom Grid"),
                    selectedLabel = when (settings.gridColumns) {
                        1 -> t("Large (1 Col)", "Besar (1 Kol)")
                        2 -> t("Standard (2 Col)", "Standar (2 Kol)")
                        else -> t("Adaptive", "Adaptif")
                    },
                    icon = Icons.Default.CropFree,
                    options = listOf(
                        PickerOption(t("Adaptive (Auto)", "Adaptif (Otomatis)"), settings.gridColumns != 1 && settings.gridColumns != 2, { viewModel.setGridColumns(0) }),
                        PickerOption(t("Large (1 Column)", "Besar (1 Kolom)"), settings.gridColumns == 1, { viewModel.setGridColumns(1) }),
                        PickerOption(t("Standard (2 Columns)", "Standar (2 Kolom)"), settings.gridColumns == 2, { viewModel.setGridColumns(2) }),
                    ),
                )
                SettingsDivider()
                SettingsPickerRow(
                    title = t("Gallery Grid", "Grid Galeri"),
                    selectedLabel = if (settings.galleryGridType == "staggered") t("Staggered", "Staggered") else t("Uniform (Square)", "Kotak (Rapi)"),
                    icon = Icons.Default.GridView,
                    options = listOf(
                        PickerOption(t("Staggered (Dynamic)", "Staggered (Dinamis)"), settings.galleryGridType == "staggered", { viewModel.setGalleryGridType("staggered") }),
                        PickerOption(t("Uniform (Square)", "Rapi (Kotak)"), settings.galleryGridType != "staggered", { viewModel.setGalleryGridType("uniform") }),
                    ),
                )
                SettingsDivider()
                SettingsPickerRow(
                    title = t("Gallery Transition", "Transisi Galeri"),
                    selectedLabel = when (settings.galleryTransition) {
                        "fade" -> t("Fade", "Pudar")
                        "curl" -> t("Page Curl", "Lipat Halaman")
                        else -> t("Slide (Default)", "Geser (Default)")
                    },
                    icon = Icons.Default.ViewCarousel,
                    options = listOf(
                        PickerOption(t("Slide (Default)", "Geser (Default)"), settings.galleryTransition != "fade" && settings.galleryTransition != "curl", { viewModel.setGalleryTransition("slide") }),
                        PickerOption(t("Fade", "Pudar"), settings.galleryTransition == "fade", { viewModel.setGalleryTransition("fade") }),
                        PickerOption(t("Page Curl", "Lipat Halaman"), settings.galleryTransition == "curl", { viewModel.setGalleryTransition("curl") }),
                    ),
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = t("Auto-open 1-Video Folders", "Buka Otomatis Folder 1 Video"),
                    icon = Icons.Default.FolderOpen,
                    checked = settings.autoUnwrapSingleVideoFolders,
                    onCheckedChange = viewModel::setAutoUnwrapSingleVideoFolders,
                )
            }
        }

        SettingsSectionHeader(t("Video Player", "Pemutar Video"))
        SettingsCard {
            SettingsPickerRow(
                title = t("Fit Mode", "Mode Ukuran"),
                selectedLabel = when (settings.defaultVideoZoomMode) {
                    1 -> t("Fill", "Isi")
                    2 -> t("Zoom", "Perbesar")
                    else -> t("Fit", "Pas")
                },
                icon = Icons.Default.CropFree,
                options = listOf(
                    PickerOption(t("Fit", "Pas"), settings.defaultVideoZoomMode != 1 && settings.defaultVideoZoomMode != 2, { viewModel.setDefaultVideoZoomMode(0) }),
                    PickerOption(t("Fill", "Isi"), settings.defaultVideoZoomMode == 1, { viewModel.setDefaultVideoZoomMode(1) }),
                    PickerOption(t("Zoom", "Perbesar"), settings.defaultVideoZoomMode == 2, { viewModel.setDefaultVideoZoomMode(2) }),
                ),
            )
            SettingsDivider()
            SettingsPickerRow(
                title = t("Default Orientation", "Orientasi Standar"),
                selectedLabel = when (settings.defaultVideoOrientation) {
                    1 -> t("Landscape", "Lanskap")
                    2 -> t("Portrait", "Potret")
                    else -> t("Auto", "Otomatis")
                },
                icon = Icons.Default.ScreenRotation,
                options = listOf(
                    PickerOption(t("Auto (Sensor)", "Otomatis (Sensor)"), settings.defaultVideoOrientation != 1 && settings.defaultVideoOrientation != 2, { viewModel.setDefaultVideoOrientation(0) }),
                    PickerOption(t("Landscape Only", "Hanya Lanskap"), settings.defaultVideoOrientation == 1, { viewModel.setDefaultVideoOrientation(1) }),
                    PickerOption(t("Portrait Only", "Hanya Potret"), settings.defaultVideoOrientation == 2, { viewModel.setDefaultVideoOrientation(2) }),
                ),
            )
            SettingsDivider()
            SettingsPickerRow(
                title = t("Skip Interval", "Interval Lompat"),
                selectedLabel = "${settings.videoSkipInterval}s",
                icon = Icons.Default.Forward10,
                options = listOf(5, 10, 15, 30, 60).map { sec ->
                    PickerOption("${sec}s", settings.videoSkipInterval == sec, { viewModel.setVideoSkipInterval(sec) })
                },
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Enable PiP Mode", "Aktifkan Mode PiP"),
                icon = Icons.Default.PictureInPictureAlt,
                checked = settings.enablePiP,
                onCheckedChange = viewModel::setEnablePiP,
            )
        }

        SettingsSectionHeader(t("Scanner", "Pemindaian"))
        SettingsCard {
            SettingsToggleRow(
                title = t("Scan on Startup", "Pindai Saat Buka"),
                icon = Icons.Default.Refresh,
                checked = settings.autoScanOnStart,
                onCheckedChange = viewModel::setAutoScanOnStart,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Skip Duplicates", "Lewati Duplikat"),
                icon = Icons.Default.ContentCopy,
                checked = settings.skipDuplicateScan,
                onCheckedChange = viewModel::setSkipDuplicateScan,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Video Thumbnails", "Thumbnail Video"),
                icon = Icons.Default.Movie,
                checked = settings.videoThumbnails,
                onCheckedChange = viewModel::setVideoThumbnails,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Floating Scan Indicator", "Indikator Pindai Melayang"),
                icon = Icons.Default.NotificationsActive,
                checked = settings.floatingScanStatus,
                onCheckedChange = viewModel::setFloatingScanStatus,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Local Scan Priority", "Prioritas Pindai Lokal"),
                icon = Icons.Default.PhoneAndroid,
                checked = settings.prioritizeLocalScan,
                onCheckedChange = viewModel::setPrioritizeLocalScan,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Hide Offline SMB", "Sembunyikan SMB Offline"),
                icon = Icons.Default.CloudOff,
                checked = settings.hideOfflineSmb,
                onCheckedChange = viewModel::setHideOfflineSmb,
            )
        }

        SettingsSectionHeader(t("Filename Parsing", "Format Nama Berkas"))
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var showFormatSheet by remember { mutableStateOf(false) }
                val examples = patternExamples()
                val customFormatSelected = settings.scanFormat.startsWith(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)
                var customFormat by remember {
                    mutableStateOf(
                        settings.scanFormat.removePrefix(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)
                            .takeIf { customFormatSelected }
                            ?: AppSettings.CUSTOM_SCAN_FORMAT_DEFAULT,
                    )
                }
                LaunchedEffect(customFormatSelected) {
                    if (customFormatSelected) {
                        customFormat = settings.scanFormat.removePrefix(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX)
                    }
                }
                LaunchedEffect(customFormat, customFormatSelected) {
                    if (customFormatSelected && customFormat.isNotBlank()) {
                        val target = AppSettings.CUSTOM_SCAN_FORMAT_PREFIX + customFormat
                        if (target == settings.scanFormat) return@LaunchedEffect
                        kotlinx.coroutines.delay(500.milliseconds)
                        viewModel.setScanFormat(target)
                    }
                }
                Column {
                    Text(
                        t("Name extraction pattern", "Pola ekstraksi nama"),
                        fontSize = 12.sp,
                        color = DeckTextSecondary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val fieldShape = RoundedCornerShape(16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .border(1.dp, DeckTextSecondary.copy(alpha = 0.5f), fieldShape)
                            .clickable { showFormatSheet = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (customFormatSelected) t("Custom pattern", "Pola kustom") else settings.scanFormat,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeckTextPrimary,
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DeckTextPrimary)
                    }
                }
                if (showFormatSheet) {
                    SettingsBottomSheet(
                        title = t("Name extraction pattern", "Pola ekstraksi nama"),
                        icon = Icons.Default.Tune,
                        onDismiss = { showFormatSheet = false },
                    ) {
                        examples.forEach { example ->
                            SettingsOptionRow(
                                label = example.format,
                                subtitle = example.sample,
                                selected = !customFormatSelected && settings.scanFormat == example.format,
                                onClick = {
                                    viewModel.setScanFormat(example.format)
                                    showFormatSheet = false
                                },
                            )
                        }
                        SettingsOptionRow(
                            label = t("Custom pattern...", "Pola kustom..."),
                            subtitle = t("Define your own pattern", "Tentukan pola sendiri"),
                            selected = customFormatSelected,
                            icon = Icons.Default.Tune,
                            onClick = {
                                if (!customFormatSelected) {
                                    customFormat = AppSettings.CUSTOM_SCAN_FORMAT_DEFAULT
                                    viewModel.setScanFormat(AppSettings.CUSTOM_SCAN_FORMAT_PREFIX + customFormat)
                                }
                                showFormatSheet = false
                            },
                        )
                        val active = examples.firstOrNull { !customFormatSelected && it.format == settings.scanFormat }
                        if (active != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DeckSurfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    t("Result for", "Hasil untuk") + " “${active.sample}”",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeckTextSecondary,
                                )
                                Text(
                                    active.result,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeckTextPrimary,
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
                        shape = RoundedCornerShape(16.dp),
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
                        if (sepLocal.isEmpty() || sepLocal == settings.tagSeparator) return@LaunchedEffect
                        kotlinx.coroutines.delay(500.milliseconds)
                        viewModel.setTagSeparator(sepLocal)
                    }
                    LaunchedEffect(delimLocal) {
                        if (delimLocal.isEmpty() || delimLocal == settings.tagDelimiter) return@LaunchedEffect
                        kotlinx.coroutines.delay(500.milliseconds)
                        viewModel.setTagDelimiter(delimLocal)
                    }
                    OutlinedTextField(
                        value = sepLocal,
                        onValueChange = { if (it.length <= 3) sepLocal = it },
                        label = { Text(t("Primary separator", "Pemisah utama"), fontSize = 12.sp) },
                        placeholder = { Text("-") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    OutlinedTextField(
                        value = delimLocal,
                        onValueChange = { if (it.length <= 3) delimLocal = it },
                        label = { Text(t("Tag separator", "Pemisah tag"), fontSize = 12.sp) },
                        placeholder = { Text(",") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                }
            }
            SettingsDivider()
            SettingsToggleRow(
                title = t("Remove Numeric IDs", "Hapus ID Angka"),
                icon = Icons.Default.Tag,
                checked = settings.stripNumericId,
                onCheckedChange = viewModel::setStripNumericId,
                testTag = "strip_numeric_id_switch",
            )
            SettingsDivider()
            SettingsToggleRow(
                title = t("Lowercase Tags", "Tag Huruf Kecil"),
                icon = Icons.Default.TextFormat,
                checked = settings.lowercaseTags,
                onCheckedChange = viewModel::setLowercaseTags,
                testTag = "lowercase_tags_switch",
            )
        }
    }
}
