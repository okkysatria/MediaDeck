package com.mediadeck.app.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.ui.screens.settings.GeneralSection
import com.mediadeck.app.ui.screens.settings.ScanSection
import com.mediadeck.app.ui.screens.settings.SmbExplorer
import com.mediadeck.app.ui.screens.settings.SystemSection
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    scannerViewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {

    val settings by settingsViewModel.appSettings.collectAsState(AppSettings())
    val scrollState = rememberScrollState()

    LaunchedEffect(settingsViewModel.scrollToTopEvent) {
        settingsViewModel.scrollToTopEvent.collect {
            scrollState.animateScrollTo(0)
        }
    }

    val currentBrowserPath by settingsViewModel.currentBrowserPath.collectAsState()
    var smbScanTypeForSession by rememberSaveable { mutableStateOf("comics") }

    if (currentBrowserPath.isNotEmpty()) {
        SmbExplorer(
            settingsViewModel = settingsViewModel,
            scannerViewModel = scannerViewModel,
            scanType = smbScanTypeForSession,
            onBack = settingsViewModel::clearBrowserState,
        )
    } else {
        val selectedTabIndex by settingsViewModel.selectedSettingsTabIndex.collectAsState()
        val tabTitles = listOf(
            t("General", "Umum"),
            t("Scan", "Pindai"),
            t("Data", "Data"),
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            t("Settings", "Pengaturan"),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            },
            modifier = modifier,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                SettingsPillTabBar(
                    titles = tabTitles,
                    selectedIndex = selectedTabIndex,
                    onSelect = settingsViewModel::setSelectedSettingsTabIndex,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    ) {
                        when (selectedTabIndex) {
                            0 -> GeneralSection(
                                viewModel = settingsViewModel,
                                settings = settings,
                            )
                            1 -> ScanSection(
                                scannerViewModel = scannerViewModel,
                                settingsViewModel = settingsViewModel,
                                settings = settings,
                            ) { scanType ->
                                smbScanTypeForSession = scanType
                                settingsViewModel.startBrowsingServer()
                            }
                            2 -> SystemSection(
                                viewModel = settingsViewModel,
                                settings = settings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPillTabBar(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
