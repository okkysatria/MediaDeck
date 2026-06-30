package com.gojektracker.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gojektracker.app.ui.components.OsmMapView
import com.gojektracker.app.ui.theme.*
import com.gojektracker.app.viewmodel.MainViewModel
import com.gojektracker.app.ml.HotspotRecommendation
import com.gojektracker.app.ml.SmartHeatmapPredictor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class RecommendedSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val primaryType: String,
    val avgEarnings: Double,
    val score: Int,
    val reason: String,
    val isFromModel: Boolean = false
)

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHeatmapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val predictor = remember(viewModel.importedOnnxUri) {
        SmartHeatmapPredictor(context, viewModel.importedOnnxUri)
    }

    var sortBy by remember { mutableStateOf("score") } // "score", "distance", "earnings"
    var filterType by remember { mutableStateOf("Semua") } // "Semua", "Penumpang", "Makanan", "Paket"

    // Time & Date formatters
    val currentDay = remember { 
        SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(Date()) 
    }
    val currentHour = remember { 
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) 
    }

    val localeID = remember { Locale.forLanguageTag("id-ID") }
    val formatter = remember(localeID) {
        NumberFormat.getCurrencyInstance(localeID).apply {
            maximumFractionDigits = 0
        }
    }

    // Recommendation logic matching the model setup
    val recommendedSpots = remember(
        viewModel.lastKnownLatitude, 
        viewModel.lastKnownLongitude, 
        sortBy, 
        filterType, 
        viewModel.smartHeatmapEnabled, 
        viewModel.holidayAnalysisEnabled,
        viewModel.onnxInputLayerName,
        viewModel.onnxOutputLayerName
    ) {
        val spots = mutableListOf<RecommendedSpot>()
        if (!viewModel.smartHeatmapEnabled) {
            return@remember spots
        }

        val calendar = Calendar.getInstance()
        val currentHourInt = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeekInt = calendar.get(Calendar.DAY_OF_WEEK)
        
        val isWeekend = if (dayOfWeekInt == Calendar.SATURDAY || dayOfWeekInt == Calendar.SUNDAY) 1 else 0
        val isSchoolHoliday = if (viewModel.holidayAnalysisEnabled) 1 else 0
        
        // Map label filter (Indonesia) ke kategori kanonis yang dipahami predictor & data model
        val canonicalCategory = when (filterType) {
            "Makanan" -> "Food"
            "Paket" -> "Paket"
            "Penumpang" -> "Penumpang"
            else -> "Umum/Lainnya"
        }

        val spotsList = predictor.predictHotspots(
            hour = currentHourInt,
            dayOfWeek = dayOfWeekInt,
            month = calendar.get(Calendar.MONTH) + 1,
            isWeekend = isWeekend,
            isSchoolHoliday = isSchoolHoliday,
            isCollegeHoliday = isSchoolHoliday,
            isRamadhan = 0,
            ramadhanPhase = "AFTER_EID",
            tripCategory = canonicalCategory,
            jenisOrder = if (filterType == "Semua") "RIDE" else canonicalCategory,
            driverLat = viewModel.lastKnownLatitude,
            driverLng = viewModel.lastKnownLongitude,
            inputLayerName = viewModel.onnxInputLayerName,
            outputLayerName = viewModel.onnxOutputLayerName
        )

        val orderTypes = listOf("Penumpang", "Food", "Paket")
        spotsList.forEachIndexed { index, spot ->
            val distance = calculateDistance(viewModel.lastKnownLatitude, viewModel.lastKnownLongitude, spot.latitude, spot.longitude)
            
            // Get clean names or custom descriptions
            val cleanName = spot.keterangan.substringAfter("(").substringBefore(")")
            val displayName = if (cleanName != spot.keterangan) cleanName else "Hexagon H3 Cell"

            // Tentukan kategori order yang sebenarnya untuk spot ini:
            // - Jika user sedang memfilter kategori tertentu, semua hasil prediksi memang relevan ke kategori itu.
            // - Jika "Semua", sebar kategori secara deterministik (berbasis h3Index) supaya filter berikutnya valid.
            val resolvedType = if (filterType != "Semua") {
                canonicalCategory
            } else {
                orderTypes[spot.h3Index.hashCode().let { if (it < 0) -it else it } % orderTypes.size]
            }

            spots.add(
                RecommendedSpot(
                    name = "$displayName [H3:${spot.h3Index.take(6)}]",
                    lat = spot.latitude,
                    lng = spot.longitude,
                    distanceKm = distance,
                    primaryType = resolvedType,
                    avgEarnings = 15000.0 + (spot.skorPotensi * 220),
                    score = spot.skorPotensi,
                    reason = spot.keterangan,
                    isFromModel = spot.isFromModel
                )
            )
        }

        // Filtering
        val filterCanonical = when (filterType) {
            "Makanan" -> "Food"
            else -> filterType
        }
        var filteredList = if (filterType == "Semua") {
            spots
        } else {
            spots.filter { it.primaryType.equals(filterCanonical, ignoreCase = true) }
        }

        // Sorting
        filteredList = when (sortBy) {
            "distance" -> filteredList.sortedBy { it.distanceKm }
            "earnings" -> filteredList.sortedByDescending { it.avgEarnings }
            else -> filteredList.sortedByDescending { it.score }
        }

        filteredList
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── TOP RADAR HEADER ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (viewModel.isDarkMode) {
                            listOf(Color(0xFF0F1E16), MaterialTheme.colorScheme.background)
                        } else {
                            listOf(Color(0xFFE8F5E9), MaterialTheme.colorScheme.background)
                        }
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Radar Hotspot",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (viewModel.isDarkMode) Color.White else Color(0xFF1B5E20)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pantau area order terpadat dan naikkan pendapatan Anda",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Badge sumber data: model ONNX asli vs mode simulasi.
                    // Ditambahkan supaya jelas, bukan cuma tersembunyi di teks kecil tiap kartu.
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (predictor.isModelLoaded) GojekGreen.copy(alpha = 0.15f) else Color(0xFFE67E22).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (predictor.isModelLoaded) Icons.Default.Memory else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (predictor.isModelLoaded) GojekGreen else Color(0xFFE67E22),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (predictor.isModelLoaded) "Model ONNX aktif" else "Mode simulasi (belum ada model ONNX)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (predictor.isModelLoaded) GojekGreen else Color(0xFFE67E22)
                        )
                    }
                }

                // Time Status widget
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.isDarkMode) Color(0xFF1E2822) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GojekGreen.copy(alpha = 0.2f)),
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Waktu",
                            tint = GojekGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = currentDay,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentHour,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── MAP CONTAINER CARD ────────────────────────────────────
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(320.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OsmMapView(
                    latitude = viewModel.lastKnownLatitude,
                    longitude = viewModel.lastKnownLongitude,
                    isDarkMode = viewModel.isDarkMode,
                    currentPathPoints = viewModel.activeTrackPoints.toList(),
                    historicalOrders = recommendedSpots.mapIndexed { idx, spot ->
                        com.gojektracker.app.data.OrderRecord(
                            id = idx + 1000L,
                            tanggal = "",
                            hari = "",
                            jamMulai = "",
                            jamPickup = null,
                            jamSelesai = null,
                            jenisOrder = spot.primaryType,
                            pendapatan = spot.avgEarnings,
                            durasi = spot.score.toLong(),
                            jarakTempuh = spot.distanceKm,
                            latitudeAwal = spot.lat,
                            longitudeAwal = spot.lng,
                            alamatAwal = spot.name,
                            latitudePickup = null,
                            longitudePickup = null,
                            alamatPickup = null,
                            latitudeAkhir = null,
                            longitudeAkhir = null,
                            alamatAkhir = null,
                            catatan = spot.reason,
                            trackGps = "[]"
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    mapSource = viewModel.mapSource,
                    importedPbfFileName = viewModel.importedPbfFileName,
                    importedPbfUri = viewModel.importedPbfUri
                )

                // Top-Left Pulse Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .background(
                            color = if (viewModel.isDarkMode) Color(0xDD121620) else Color(0xDDFFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp, 
                            if (viewModel.isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GojekGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPS Live",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Map Source Label Overlay
                val mapLabel = when (viewModel.mapSource) {
                    "manual_pbf" -> viewModel.importedPbfFileName?.let { "Offline: $it" } ?: "Peta Offline"
                    "manual_surabaya" -> "Offline: Surabaya"
                    else -> "Peta Online"
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = GojekGreen),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = mapLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ── CONTROL & RADAR RECOMENDATION PANEL ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (viewModel.smartHeatmapEnabled) {
                // ── Dynamic Filters Section ──
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Layanan Filter Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = GojekGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Layanan:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val serviceOptions = listOf(
                                Triple("Semua", "Semua", Icons.Default.AllInclusive),
                                Triple("Penumpang", "Penumpang", Icons.Default.TwoWheeler),
                                Triple("Makanan", "Makanan", Icons.Default.Restaurant),
                                Triple("Paket", "Paket", Icons.Default.LocalMall)
                            )

                            serviceOptions.forEach { (key, label, icon) ->
                                val isSelected = filterType == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { filterType = key },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GojekGreen,
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                                        selectedBorderColor = GojekGreen
                                    )
                                )
                            }
                        }

                        // Horizontal Divider
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Sorting Filter Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = null,
                                tint = GojekGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Urutkan:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sortOptions = listOf(
                                Triple("score", "Potensi", Icons.Default.TrendingUp),
                                Triple("distance", "Jarak", Icons.Default.NearMe),
                                Triple("earnings", "Pendapatan", Icons.Default.Payments)
                            )

                            sortOptions.forEach { (key, label, icon) ->
                                val isSelected = sortBy == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { sortBy = key },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GojekGreen,
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                                        selectedBorderColor = GojekGreen
                                    )
                                )
                            }
                        }
                    }
                }

                // ── Hotspots List Section ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = GojekYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rekomendasi Area Terdekat",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (recommendedSpots.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = GojekGreen.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Radar AI Memindai...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tidak ada rekomendasi area aktif saat ini. Radar sedang memindai pola kepadatan orderan, cuaca, dan pola hari libur di sekitar wilayah Anda secara real-time.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recommendedSpots.forEachIndexed { index, spot ->
                            val isTopSpot = index == 0
                            
                            // Top spot has custom golden border in light mode, emerald in dark mode
                            val cardBorderColor = if (isTopSpot) {
                                GojekGreen.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                            val cardBgColor = if (isTopSpot) {
                                if (viewModel.isDarkMode) Color(0xFF16251B) else Color(0xFFF1FDF3)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(if (isTopSpot) 6.dp else 2.dp, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(if (isTopSpot) 2.dp else 1.dp, cardBorderColor)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Header Spot Card
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = spot.name,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isTopSpot) {
                                                    Icon(
                                                        imageVector = Icons.Default.Stars,
                                                        contentDescription = "Saran Terbaik",
                                                        tint = GojekYellow,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            // Service badge
                                            val badgeColor = when (spot.primaryType) {
                                                "Food", "Makanan" -> Color(0xFFE67E22)
                                                "Paket", "GoSend" -> GojekBlue
                                                else -> GojekGreen
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = badgeColor.copy(alpha = 0.12f)
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.wrapContentSize()
                                            ) {
                                                Text(
                                                    text = when(spot.primaryType) {
                                                        "Food", "Makanan" -> "GoFood (Makanan)"
                                                        "Paket", "GoSend" -> "GoSend (Paket)"
                                                        else -> "GoRide (Penumpang)"
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        
                                        // Potency Score Percentage
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Potensi: ${spot.score}%",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (spot.score >= 80) GojekRed else GojekGreen
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            LinearProgressIndicator(
                                                progress = { spot.score / 100f },
                                                color = if (spot.score >= 80) GojekRed else GojekGreen,
                                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .width(64.dp)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Stats & Button Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Navigation, 
                                                    contentDescription = null, 
                                                    modifier = Modifier.size(13.dp), 
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = String.format(Locale.US, "Jarak: %.2f km", spot.distanceKm), 
                                                    fontSize = 11.sp, 
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Payments, 
                                                    contentDescription = null, 
                                                    modifier = Modifier.size(13.dp), 
                                                    tint = GojekGreen
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Rata-rata: ${formatter.format(spot.avgEarnings)}", 
                                                    fontSize = 11.sp, 
                                                    fontWeight = FontWeight.Bold, 
                                                    color = GojekGreen
                                                )
                                            }
                                        }
                                        
                                        Button(
                                            onClick = {
                                                viewModel.lastKnownLatitude = spot.lat
                                                viewModel.lastKnownLongitude = spot.lng
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isTopSpot) GojekGreen else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Map, 
                                                contentDescription = null, 
                                                modifier = Modifier.size(14.dp), 
                                                tint = if (isTopSpot) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Lihat Peta", 
                                                fontSize = 11.sp, 
                                                fontWeight = FontWeight.Bold, 
                                                color = if (isTopSpot) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    // Reason text + chip sumber data (ONNX asli vs simulasi)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (viewModel.isDarkMode) Color(0xFF1E242B) else Color(0xFFF3F4F6)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = spot.reason,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (spot.isFromModel) GojekGreen.copy(alpha = 0.15f) else Color(0xFFE67E22).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (spot.isFromModel) "ONNX" else "Simulasi",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (spot.isFromModel) GojekGreen else Color(0xFFE67E22)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Smart Radar Dinonaktifkan",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Silakan aktifkan fitur Smart Heatmap di halaman Pengaturan untuk melihat analisis radar potensi.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
