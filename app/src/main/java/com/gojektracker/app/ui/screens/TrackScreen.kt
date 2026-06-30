package com.gojektracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gojektracker.app.ui.track.PosterTheme
import com.gojektracker.app.ui.track.RouteMapPreview
import com.gojektracker.app.ui.track.RoutePosterOptions
import com.gojektracker.app.ui.track.RoutePosterStats
import com.gojektracker.app.ui.track.generateAndShareRoutePoster
import com.gojektracker.app.ui.theme.GojekGreen
import com.gojektracker.app.viewmodel.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar "Rute": menyusun ringkasan order satu hari jadi poster yang bisa dibagikan.
 * Dibangun ulang dari nol — hanya bagian yang esensial: pilih tanggal & jenis order,
 * pratinjau rute di peta, ringkasan statistik, pilih tema, lalu simpan/bagikan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val orders by viewModel.allOrders.collectAsState()

    val availableDates = remember(orders) { orders.map { it.tanggal }.distinct().sortedDescending() }
    val defaultDate = remember(availableDates) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (availableDates.contains(today)) today else availableDates.firstOrNull() ?: today
    }

    var selectedDate by remember(defaultDate) { mutableStateOf(defaultDate) }
    var orderTypeFilter by remember { mutableStateOf("Semua") }
    var selectedTheme by remember { mutableStateOf(PosterTheme.GOJEK_EMERALD) }
    var driverName by remember { mutableStateOf(TextFieldValue("")) }

    var showDistance by remember { mutableStateOf(true) }
    var showEarnings by remember { mutableStateOf(true) }
    var showDuration by remember { mutableStateOf(true) }
    var showTrips by remember { mutableStateOf(true) }

    val dayOrders = remember(orders, selectedDate, orderTypeFilter) {
        orders.filter { o ->
            o.tanggal == selectedDate &&
                (orderTypeFilter == "Semua" || o.jenisOrder.equals(orderTypeFilter, ignoreCase = true) ||
                    (orderTypeFilter == "Makanan" && o.jenisOrder.equals("Food", ignoreCase = true)))
        }
    }

    val routeTrackPoints = remember(dayOrders) {
        dayOrders.flatMap { it.getTrackPoints() }
    }

    val stats = remember(dayOrders) {
        RoutePosterStats(
            totalDistanceKm = dayOrders.sumOf { it.jarakTempuh },
            totalEarnings = dayOrders.sumOf { it.pendapatan },
            totalDurationMinutes = dayOrders.sumOf { it.durasi },
            tripsCount = dayOrders.size
        )
    }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply { maximumFractionDigits = 0 }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Rute & Ringkasan",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Buat poster rute hari kerja Anda untuk dibagikan",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Pilih tanggal ──
        if (availableDates.isEmpty()) {
            EmptyStateCard()
            return@Column
        }

        SectionLabel("Tanggal")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableDates.take(30).forEach { date ->
                FilterChipItem(
                    label = date,
                    selected = date == selectedDate,
                    onClick = { selectedDate = date }
                )
            }
        }

        // ── Filter jenis order ──
        SectionLabel("Jenis Order")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Semua", "Penumpang", "Makanan", "Paket").forEach { type ->
                FilterChipItem(
                    label = type,
                    selected = type == orderTypeFilter,
                    onClick = { orderTypeFilter = type }
                )
            }
        }

        // ── Pratinjau peta ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp)
        ) {
            RouteMapPreview(
                trackPoints = routeTrackPoints,
                accentColor = selectedTheme.accent,
                showMapTiles = selectedTheme.useMapTiles,
                mapSource = viewModel.mapSource,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Ringkasan statistik ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBlock("Jarak", String.format(Locale.US, "%.1f km", stats.totalDistanceKm))
                StatBlock("Pendapatan", currencyFormatter.format(stats.totalEarnings))
                StatBlock("Durasi", "${stats.totalDurationMinutes / 60}j ${stats.totalDurationMinutes % 60}m")
                StatBlock("Trip", "${stats.tripsCount}")
            }
        }

        // ── Pilih tema ──
        SectionLabel("Tema Poster")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PosterTheme.entries.forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = theme == selectedTheme,
                    onClick = { selectedTheme = theme }
                )
            }
        }

        // ── Nama driver (opsional) ──
        SectionLabel("Nama (opsional, tampil di poster)")
        OutlinedTextField(
            value = driverName,
            onValueChange = { driverName = it },
            placeholder = { Text("Nama Anda") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Toggle tampilan statistik ──
        SectionLabel("Tampilkan di Poster")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleRow("Jarak tempuh", showDistance) { showDistance = it }
            ToggleRow("Pendapatan", showEarnings) { showEarnings = it }
            ToggleRow("Durasi", showDuration) { showDuration = it }
            ToggleRow("Jumlah trip", showTrips) { showTrips = it }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Tombol aksi ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    generateAndShareRoutePoster(
                        context = context,
                        trackPoints = routeTrackPoints,
                        theme = selectedTheme,
                        stats = stats,
                        options = RoutePosterOptions(showDistance, showEarnings, showDuration, showTrips, driverName.text),
                        selectedDate = selectedDate,
                        shareAfterSave = false
                    )
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simpan")
            }

            Button(
                onClick = {
                    generateAndShareRoutePoster(
                        context = context,
                        trackPoints = routeTrackPoints,
                        theme = selectedTheme,
                        stats = stats,
                        options = RoutePosterOptions(showDistance, showEarnings, showDuration, showTrips, driverName.text),
                        selectedDate = selectedDate,
                        shareAfterSave = true
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagikan", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) GojekGreen else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeSwatch(theme: PosterTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(theme.background)
                .then(
                    if (selected) Modifier.border(3.dp, theme.accent, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(theme.accent))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(theme.displayName, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GojekGreen)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text("Belum ada order tercatat", fontWeight = FontWeight.Bold)
            Text(
                "Rekam order dulu di tab Perekam, lalu kembali ke sini untuk membuat poster rute.",
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
