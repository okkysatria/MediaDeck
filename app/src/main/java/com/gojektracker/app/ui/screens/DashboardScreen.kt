package com.gojektracker.app.ui.screens

import androidx.compose.animation.*
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gojektracker.app.data.OrderRecord
import com.gojektracker.app.ui.theme.*
import com.gojektracker.app.viewmodel.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class JasaJenisItem(val name: String, val sum: Double, val pct: Double, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val orders by viewModel.allOrders.collectAsState()

    val localeID = Locale.forLanguageTag("id-ID")
    val formatter = NumberFormat.getCurrencyInstance(localeID).apply {
        maximumFractionDigits = 0
    }

    val context = LocalContext.current

    // Calculations based on calendar
    val calendar = Calendar.getInstance()
    val todayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDateStr = remember { todayFormatter.format(Date()) }

    // Init values for dynamic date range selection
    val initialStartStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6) // default is 7 days up to today
        todayFormatter.format(cal.time)
    }
    var reportStartDate by remember { mutableStateOf(initialStartStr) }

    // reportEndDate is automatically calculated as 6 days after reportStartDate (spanning 7 days total)
    val reportEndDate = remember(reportStartDate) {
        val cal = Calendar.getInstance()
        try {
            todayFormatter.parse(reportStartDate)?.let {
                cal.time = it
                cal.add(Calendar.DAY_OF_YEAR, 6)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        todayFormatter.format(cal.time)
    }

    // Month Report variables
    val monthNamesIndo = remember {
        listOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )
    }
    val currentTodayCal = Calendar.getInstance()
    var selectedReportMonth by remember { mutableStateOf(currentTodayCal.get(Calendar.MONTH)) }
    var selectedReportYear by remember { mutableStateOf(currentTodayCal.get(Calendar.YEAR)) }
    var isShowMonthPickerDialog by remember { mutableStateOf(false) }

    // FILTER STATES
    var filterTanggal by remember { mutableStateOf("") }
    var filterJenisOrder by remember { mutableStateOf("Semua") }
    var filterJarakOperator by remember { mutableStateOf("Semua") }
    var filterJarakValue by remember { mutableStateOf("") }
    var filterPendapatanOperator by remember { mutableStateOf("Semua") }
    var filterPendapatanValue by remember { mutableStateOf("") }
    var showFilterPanel by remember { mutableStateOf(false) }

    // SORT STATES
    var sortBy by remember { mutableStateOf("tanggal") }
    var sortAscending by remember { mutableStateOf(false) }

    // Reset to current month on page view/recomposition
    LaunchedEffect(Unit) {
        val resetCal = Calendar.getInstance()
        selectedReportMonth = resetCal.get(Calendar.MONTH)
        selectedReportYear = resetCal.get(Calendar.YEAR)
    }

    // Dynamic Lists of dates in the selected range
    val reportDates = remember(reportStartDate, reportEndDate) {
        val list = mutableListOf<Pair<String, String>>()
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()
        try {
            todayFormatter.parse(reportStartDate)?.let { startCal.time = it }
            todayFormatter.parse(reportEndDate)?.let { endCal.time = it }
            
            val formatLabel = SimpleDateFormat("dd MMM", Locale.forLanguageTag("id-ID"))
            val currentCal = startCal.clone() as Calendar
            var loops = 0
            while (!currentCal.after(endCal) && loops < 12) {
                list.add(Pair(todayFormatter.format(currentCal.time), formatLabel.format(currentCal.time)))
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
                loops++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            list.add(Pair(todayDateStr, "Hari Ini"))
        }
        list
    }

    // Filtered orders and stats for selected range
    val reportOrdersInRange = remember(orders, reportStartDate, reportEndDate) {
        orders.filter {
            it.tanggal >= reportStartDate && it.tanggal <= reportEndDate
        }
    }
    val totalReportRevenue = remember(reportOrdersInRange) { reportOrdersInRange.sumOf { it.pendapatan } }
    val totalReportCount = reportOrdersInRange.size

    // Pair of (Revenue, OrderCount) per label
    val reportDailyStats = remember(orders, reportDates) {
        val map = mutableMapOf<String, Pair<Double, Int>>()
        for (item in reportDates) {
            val dayOrders = orders.filter { it.tanggal == item.first }
            val rev = dayOrders.sumOf { it.pendapatan }
            val count = dayOrders.size
            map[item.second] = Pair(rev, count)
        }
        map
    }

    // Helper Lambdas to launch calendar pickers
    val showStartDatePicker = {
        val cal = Calendar.getInstance()
        try {
            todayFormatter.parse(reportStartDate)?.let { cal.time = it }
        } catch (e: Exception) {}
        android.app.DatePickerDialog(
            context,
            { _, y: Int, m: Int, dOfMonth: Int ->
                val selectCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, dOfMonth)
                }
                reportStartDate = todayFormatter.format(selectCal.time)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val chipDateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID")) }
    val displayStartDate = remember(reportStartDate) {
        try {
            val d = todayFormatter.parse(reportStartDate) ?: Date()
            chipDateFormatter.format(d)
        } catch (e: Exception) {
            reportStartDate
        }
    }
    val displayEndDate = remember(reportEndDate) {
        try {
            val d = todayFormatter.parse(reportEndDate) ?: Date()
            chipDateFormatter.format(d)
        } catch (e: Exception) {
            reportEndDate
        }
    }

    // TODAY STATS
    val todayOrders = remember(orders, todayDateStr) { orders.filter { it.tanggal == todayDateStr } }
    val todayCount = todayOrders.size
    val todayRevenue = todayOrders.sumOf { it.pendapatan }
    val todayDistance = todayOrders.sumOf { it.jarakTempuh }
    val todayKePickup = todayOrders.sumOf { it.jarakKePickup }
    val todayKeTujuan = todayOrders.sumOf { it.jarakKeTujuan }
    val todayDuration = todayOrders.sumOf { it.durasi }

    // WEEKLY STATS (Last 7 Days)
    val weekOrders = remember(orders) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val limit = cal.time
        orders.filter {
            try {
                val date = todayFormatter.parse(it.tanggal) ?: Date()
                date.after(limit) || it.tanggal == todayDateStr
            } catch (e: Exception) {
                false
            }
        }
    }
    val weekCount = weekOrders.size
    val weekRevenue = weekOrders.sumOf { it.pendapatan }

    // MONTHLY STATS (Selected Month & Year)
    val monthOrders = remember(orders, selectedReportMonth, selectedReportYear) {
        orders.filter {
            try {
                val date = todayFormatter.parse(it.tanggal)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    cal.get(Calendar.MONTH) == selectedReportMonth && cal.get(Calendar.YEAR) == selectedReportYear
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
    val monthCount = monthOrders.size
    val monthRevenue = monthOrders.sumOf { it.pendapatan }
    val monthDistance = monthOrders.sumOf { it.jarakTempuh }
    val monthKePickup = monthOrders.sumOf { it.jarakKePickup }
    val monthKeTujuan = monthOrders.sumOf { it.jarakKeTujuan }

    val selectedReportOrders = remember(reportOrdersInRange, filterTanggal) {
        if (filterTanggal.isNotEmpty() && filterTanggal.lowercase() != "semua" && filterTanggal.lowercase() != "hari ini") {
            reportOrdersInRange.filter { it.tanggal == filterTanggal }
        } else if (filterTanggal.lowercase() == "hari ini") {
            reportOrdersInRange.filter { it.tanggal == todayDateStr }
        } else {
            reportOrdersInRange
        }
    }

    // PENDAPATAN PER JENIS ORDER (Penumpang, Food, Paket)
    val jenisRevenueMap = remember(selectedReportOrders) {
        val penump = selectedReportOrders.filter { it.jenisOrder == "Penumpang" }.sumOf { it.pendapatan }
        val food = selectedReportOrders.filter { it.jenisOrder == "Food" || it.jenisOrder == "Makanan" }.sumOf { it.pendapatan }
        val paket = selectedReportOrders.filter { it.jenisOrder == "Paket" }.sumOf { it.pendapatan }
        
        val total = (penump + food + paket).coerceAtLeast(1.0)
        Triple(
            Pair(penump, penump / total),
            Pair(food, food / total),
            Pair(paket, paket / total)
        )
    }

    // JAM PRODUKTIF
    val jamProduktifList = remember(orders) {
        val Map = mutableMapOf<Int, Double>()
        for (o in orders) {
            try {
                val hrStr = o.jamMulai.split(":")[0]
                val hr = hrStr.toInt()
                Map[hr] = (Map[hr] ?: 0.0) + o.pendapatan
            } catch (e: Exception) {
                // ignore format errors
            }
        }
        Map.entries.sortedByDescending { it.value }.take(3)
    }

    // AREA PRODUKTIF
    val areaProduktifList = remember(orders) {
        val Map = mutableMapOf<String, Double>()
        for (o in orders) {
            val addr = o.alamatAwal
            if (addr.isNotEmpty()) {
                Map[addr] = (Map[addr] ?: 0.0) + o.pendapatan
            }
        }
        Map.entries.sortedByDescending { it.value }.take(3)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dashboard Pendapatan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ringkasan performa dan riwayat mengemudi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }



        // METRICS SUMMARIES ROW (TODAY, WEEK, MONTH)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // HARI INI LARGEST HEADER CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Ringkasan Hari Ini",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = GojekGreen
                    )
                    Text(
                        text = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID")).format(Date()) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatter.format(todayRevenue),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Laba Bersih ($todayCount Order Selesai)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Jarak Tempuh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(todayDistance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Jarak Jemput", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(todayKePickup), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Jarak Antar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(todayKeTujuan), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                             Text("Durasi Mengemudi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             Text("$todayDuration Min", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // MONTHLY REPORT CARD (Removes the weekly card completely as requested)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isShowMonthPickerDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, GojekGreen.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Kalender",
                                tint = GojekGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Laporan Bulanan",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Current display of month-year
                        Surface(
                            color = GojekGreen,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${monthNamesIndo[selectedReportMonth]} $selectedReportYear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Month",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatter.format(monthRevenue),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = GojekGreen
                            )
                            Text(
                                text = "Total Akumulasi Laba Pendapatan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GojekGreen.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$monthCount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GojekGreen
                                )
                                Text(
                                    text = "Order Selesai",
                                    fontSize = 10.sp,
                                    color = GojekGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Jarak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(monthDistance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Jemput", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(monthKePickup), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total Antar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(monthKeTujuan), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "*Ketuk untuk mengubah periode laporan",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // MONTH PICKER DIALOG IN COMPOSE (Extremely safe, robust, localized in Indonesian)
            if (isShowMonthPickerDialog) {
                AlertDialog(
                    onDismissRequest = { isShowMonthPickerDialog = false },
                    title = {
                        Text(
                            text = "Pilih Periode Laporan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Tahun",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (y in 2024..2027) {
                                    val isSelected = selectedReportYear == y
                                    Surface(
                                        color = if (isSelected) GojekGreen else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.clickable { selectedReportYear = y }
                                    ) {
                                        Text(
                                            text = y.toString(),
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Bulan",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val monthNamesAbbr = listOf(
                                "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
                                "Jul", "Agt", "Sep", "Okt", "Nov", "Des"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (rowIdx in 0..3) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (colIdx in 0..2) {
                                            val mIdx = rowIdx * 3 + colIdx
                                            val isSelected = selectedReportMonth == mIdx
                                            Surface(
                                                color = if (isSelected) GojekGreen else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedReportMonth = mIdx }
                                            ) {
                                                Text(
                                                    text = monthNamesAbbr[mIdx],
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { isShowMonthPickerDialog = false }
                        ) {
                            Text("Selesai", fontWeight = FontWeight.Bold, color = GojekGreen)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                val current = Calendar.getInstance()
                                selectedReportMonth = current.get(Calendar.MONTH)
                                selectedReportYear = current.get(Calendar.YEAR)
                                isShowMonthPickerDialog = false
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                )
            }
        }

        // DATE RANGE PICKER SECTION: SINGLE SELECTION (Auto-calculate 7 days ahead)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendar Period",
                        tint = GojekGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ringkasan Mingguan (7 Hari)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Date Button (Now the primary & only date trigger)
                    Button(
                        onClick = { showStartDatePicker() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GojekGreen.copy(alpha = 0.12f),
                            contentColor = GojekGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = GojekGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("PILIH TANGGAL MULAI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GojekGreen.copy(alpha = 0.8f))
                                Text(displayStartDate, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = GojekGreen)
                            }
                        }
                    }
                }
                
                // Show Calculated Range
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$displayStartDate s/d $displayEndDate",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = GojekGreen
                        )
                    }
                }
            }
        }

        // CHART STATS #1: HUB PENDAPATAN MINGGUAN (Indonesian Local Name requested)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Grafik Pendapatan Mingguan",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pendapatan harian dan jumlah order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Small visual indicator badge
                    Surface(
                        color = GojekGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "7 Hari",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GojekGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                val maxDayRev = reportDailyStats.values.map { it.first }.maxOrNull()?.coerceAtLeast(1000.0) ?: 1000.0

                // Row containing the columns
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    reportDates.forEach { dateItem ->
                        val lbl = dateItem.second
                        val stats = reportDailyStats[lbl] ?: Pair(0.0, 0)
                        val value = stats.first
                        val orderCount = stats.second
                        val ratio = (value / maxDayRev).toFloat()
                        
                        val isSelected = filterTanggal == dateItem.first
                        val isAnySelected = filterTanggal.isNotEmpty() && filterTanggal.lowercase() != "semua" && filterTanggal.lowercase() != "hari ini"
                        val opacity = if (isAnySelected && !isSelected) 0.35f else 1.0f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer(alpha = opacity)
                                .clickable {
                                    filterTanggal = if (filterTanggal == dateItem.first) "" else dateItem.first
                                }
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // 1. Revenue Text at top of pillar
                            Text(
                                text = if (value > 0) "${(value / 1000).roundToInt()}k" else "-",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (value > 0) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 2. Fixed-height bar container area (110.dp) -> Solves the "bar kepanjangan" bug perfectly
                            Box(
                                modifier = Modifier
                                    .height(110.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                val barHeightPct = ratio.coerceIn(0.02f, 1.0f)
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(barHeightPct)
                                        .background(
                                            color = if (value > 0) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                        )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 3. Daily Order Count under the pillar
                            Surface(
                                color = if (orderCount > 0) GojekGreen.copy(alpha = 0.12f) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (orderCount > 0) "${orderCount}x" else "0",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (orderCount > 0) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            // 4. Day label
                            Text(
                                text = lbl,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Add Divider and beautiful Stats total breakdown at the bottom of the Card ("atur posisi jumlah pendapatan mingguan dan orderan di bagian bawah pendapatan hub")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Pendapatan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatter.format(totalReportRevenue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = GojekGreen
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Order",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$totalReportCount Order Selesai",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // CHART STATS #2: PENDAPATAN PER JENIS ORDER (Penumpang, Food, Paket)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(
                        text = "Pendapatan per Layanan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    
                    val activePeriodText = if (filterTanggal.isNotEmpty() && filterTanggal.lowercase() != "semua" && filterTanggal.lowercase() != "hari ini") {
                        "Periode terpilih: $filterTanggal"
                    } else if (filterTanggal.lowercase() == "hari ini") {
                        "Periode terpilih: Hari Ini ($todayDateStr)"
                    } else {
                        "Periode: 7 Hari Terakhir ($reportStartDate s/d $reportEndDate)"
                    }
                    Text(
                        text = activePeriodText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Layout bar per type using root-level JasaJenisItem data class
                val items = listOf(
                    JasaJenisItem("Penumpang (GoRide)", jenisRevenueMap.first.first, jenisRevenueMap.first.second, GojekGreen),
                    JasaJenisItem("Makanan (GoFood)", jenisRevenueMap.second.first, jenisRevenueMap.second.second, GojekRed),
                    JasaJenisItem("Paket (GoSend)", jenisRevenueMap.third.first, jenisRevenueMap.third.second, GojekBlue)
                )

                for (item in items) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val mappedJenis = when {
                                    item.name.contains("Penumpang") -> "Penumpang"
                                    item.name.contains("Makanan") || item.name.contains("Food") -> "Makanan"
                                    else -> "Paket"
                                }
                                filterJenisOrder = mappedJenis
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("${formatter.format(item.sum)} (${(item.pct * 100).roundToInt()}%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = item.color)
                        }
                        LinearProgressIndicator(
                            progress = { item.pct.toFloat() },
                            color = item.color,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        // STATE VARIABLE FOR DETAILED DIALOG
        var activeDetailOrder by remember { mutableStateOf<OrderRecord?>(null) }

        val filteredOrders = remember(orders, filterTanggal, filterJenisOrder, filterJarakOperator, filterJarakValue, filterPendapatanOperator, filterPendapatanValue) {
            orders.filter { order ->
                val matchTanggal = when {
                    filterTanggal.isEmpty() || filterTanggal.lowercase() == "semua" -> true
                    filterTanggal.lowercase() == "hari ini" -> order.tanggal == todayDateStr
                    else -> order.tanggal.contains(filterTanggal, ignoreCase = true) || order.hari.contains(filterTanggal, ignoreCase = true)
                }
                
                val matchJenis = when {
                    filterJenisOrder == "Semua" -> true
                    filterJenisOrder == "Makanan" -> order.jenisOrder == "Makanan" || order.jenisOrder == "Food"
                    else -> order.jenisOrder == filterJenisOrder
                }
                
                val matchJarak = when (filterJarakOperator) {
                    "Semua" -> true
                    ">=" -> {
                        val valDouble = filterJarakValue.toDoubleOrNull() ?: 0.0
                        order.jarakTempuh >= valDouble
                    }
                    "<=" -> {
                        val valDouble = filterJarakValue.toDoubleOrNull() ?: Float.MAX_VALUE.toDouble()
                        order.jarakTempuh <= valDouble
                    }
                    else -> true
                }
                
                val matchPendapatan = when (filterPendapatanOperator) {
                    "Semua" -> true
                    ">=" -> {
                        val valDouble = filterPendapatanValue.toDoubleOrNull() ?: 0.0
                        order.pendapatan >= valDouble
                    }
                    "<=" -> {
                        val valDouble = filterPendapatanValue.toDoubleOrNull() ?: Float.MAX_VALUE.toDouble()
                        order.pendapatan <= valDouble
                    }
                    else -> true
                }
                
                matchTanggal && matchJenis && matchJarak && matchPendapatan
            }
        }

        val sortedOrders = remember(filteredOrders, sortBy, sortAscending) {
            val comparator = when (sortBy) {
                "pendapatan" -> compareBy<OrderRecord> { it.pendapatan }
                "jarak" -> compareBy<OrderRecord> { it.jarakTempuh }
                "waktu" -> compareBy<OrderRecord> { it.durasi }
                else -> compareBy<OrderRecord> { it.tanggal }.thenBy { it.id }
            }
            if (sortAscending) {
                filteredOrders.sortedWith(comparator)
            } else {
                filteredOrders.sortedWith(comparator.reversed())
            }
        }

        // DETAILED TRANSACTIONS LIST
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("riwayat_order_section"),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "History", tint = GojekGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Riwayat Order", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("Rincian riwayat perjalanan", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showFilterPanel = !showFilterPanel },
                            modifier = Modifier.testTag("filter_toggle_button").size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (showFilterPanel) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                contentDescription = "Toggle Filter",
                                tint = if (showFilterPanel) GojekRed else GojekGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Surface(
                            color = GojekGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (filteredOrders.size == orders.size) "${orders.size} Selesai" else "${filteredOrders.size}/${orders.size}",
                                color = GojekGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // SORTING PANEL (Scrollable chips + direction toggle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Urutkan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Urutkan:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sortOptions = listOf(
                            Pair("tanggal", "Tanggal"),
                            Pair("pendapatan", "Pendapatan"),
                            Pair("jarak", "Jarak"),
                            Pair("waktu", "Durasi")
                        )
                        sortOptions.forEach { opt ->
                            val isSelected = sortBy == opt.first
                            Surface(
                                color = if (isSelected) GojekGreen.copy(alpha = 0.12f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) GojekGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable {
                                    if (sortBy == opt.first) {
                                        sortAscending = !sortAscending
                                    } else {
                                        sortBy = opt.first
                                        sortAscending = false
                                    }
                                }
                            ) {
                                Text(
                                    text = opt.second,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        onClick = { sortAscending = !sortAscending },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (sortAscending) "Terkecil ke Terbesar" else "Terbesar ke Terkecil",
                            tint = GojekGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Expandable Filter Panel UI
                AnimatedVisibility(visible = showFilterPanel) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filter Riwayat",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = {
                                        filterTanggal = ""
                                        filterJenisOrder = "Semua"
                                        filterJarakOperator = "Semua"
                                        filterJarakValue = ""
                                        filterPendapatanOperator = "Semua"
                                        filterPendapatanValue = ""
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset Filter", fontSize = 11.sp, color = GojekRed)
                                }
                            }
                            
                            // 1. Pilih Tanggal Filter
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Pilih Tanggal", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isSemuaSet = filterTanggal.isEmpty()
                                    // SEMUA BUTTON
                                    FilterChip(
                                        selected = isSemuaSet,
                                        onClick = { filterTanggal = "" },
                                        label = { Text("Semua", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GojekGreen.copy(alpha = 0.2f),
                                            selectedLabelColor = GojekGreen
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    // CHOOSE FROM CALENDAR
                                    FilterChip(
                                        selected = !isSemuaSet,
                                        onClick = {
                                            val cal = Calendar.getInstance()
                                            try {
                                                todayFormatter.parse(filterTanggal)?.let { cal.time = it }
                                            } catch (e: Exception) {}
                                            android.app.DatePickerDialog(
                                                context,
                                                { _, y: Int, m: Int, dOfMonth: Int ->
                                                    val selectCal = Calendar.getInstance().apply {
                                                        set(Calendar.YEAR, y)
                                                        set(Calendar.MONTH, m)
                                                        set(Calendar.DAY_OF_MONTH, dOfMonth)
                                                    }
                                                    filterTanggal = todayFormatter.format(selectCal.time)
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DateRange,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = if (!isSemuaSet) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = if (isSemuaSet) "Pilih Tanggal" else filterTanggal,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GojekGreen.copy(alpha = 0.2f),
                                            selectedLabelColor = GojekGreen
                                        ),
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                            }

                            // 2. Jenis Layanan Select
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Jenis Layanan", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Semua", "Penumpang", "Makanan", "Paket").forEach { jenis ->
                                        val isSelected = filterJenisOrder == jenis
                                        val chipColor = when (jenis) {
                                            "Penumpang" -> GojekGreen
                                            "Makanan" -> GojekRed
                                            "Paket" -> GojekBlue
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { filterJenisOrder = jenis },
                                            label = { Text(jenis, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = chipColor.copy(alpha = 0.15f),
                                                selectedLabelColor = chipColor,
                                                selectedLeadingIconColor = chipColor
                                            )
                                        )
                                    }
                                }
                            }

                            // 3 & 4. Jarak & Pendapatan Sections side-by-side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Jarak Column
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Jarak", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Semua", ">=", "<=").forEach { op ->
                                            val isSel = filterJarakOperator == op
                                            Surface(
                                                color = if (isSel) GojekGreen.copy(alpha = 0.15f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSel) GojekGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { 
                                                        filterJarakOperator = op
                                                        if (op == "Semua") filterJarakValue = ""
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(op, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                    if (filterJarakOperator != "Semua") {
                                        OutlinedTextField(
                                            value = filterJarakValue,
                                            onValueChange = { filterJarakValue = it },
                                            placeholder = { Text("KM") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GojekGreen,
                                                focusedLabelColor = GojekGreen,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent
                                            )
                                        )
                                    }
                                }

                                // Pendapatan Column
                                Column(
                                    modifier = Modifier.weight(1.1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Pendapatan Bersih", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Semua", ">=", "<=").forEach { op ->
                                            val isSel = filterPendapatanOperator == op
                                            Surface(
                                                color = if (isSel) GojekGreen.copy(alpha = 0.15f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSel) GojekGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { 
                                                        filterPendapatanOperator = op
                                                        if (op == "Semua") filterPendapatanValue = ""
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(op, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                    if (filterPendapatanOperator != "Semua") {
                                        OutlinedTextField(
                                            value = filterPendapatanValue,
                                            onValueChange = { filterPendapatanValue = it },
                                            placeholder = { Text("Val (Rp)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GojekGreen,
                                                focusedLabelColor = GojekGreen,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // APPLY FILTER BUTTON
                            Button(
                                onClick = { showFilterPanel = false },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Terapkan Filter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                if (orders.isEmpty()) {
                    Text(
                        text = "Belum ada riwayat order. Silakan lakukan perekaman rute terlebih dahulu.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (filteredOrders.isEmpty()) {
                    Text(
                        text = "Tidak ada riwayat order yang sesuai dengan kriteria filter.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sortedOrders.forEach { order ->
                            val colorScheme = when (order.jenisOrder) {
                                "Penumpang" -> GojekGreen
                                "Food", "Makanan" -> GojekRed
                                else -> GojekBlue
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeDetailOrder = order }
                                    .testTag("historical_record_card_${order.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = colorScheme,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = order.jenisOrder,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "ORD-${order.id}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "${order.hari}, ${order.tanggal}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Jemput: ${order.alamatAwal}",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Antar: ${order.alamatAkhir ?: "-"}",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = formatter.format(order.pendapatan),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = GojekGreen
                                        )
                                        Text(
                                            text = "Ke Jemput: %.1f km\nKe Antar: %.1f km".format(order.jarakKePickup, order.jarakKeTujuan),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.End,
                                            lineHeight = 12.sp
                                        )
                                        Text(
                                            text = "Total %.2f KM | %d m".format(order.jarakTempuh, order.durasi),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Detail >",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GojekGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DETAILED DIALOG IMPLEMENTATION
        if (activeDetailOrder != null) {
            val order = activeDetailOrder!!
            var showRawGpsPoints by remember { mutableStateOf(false) }
            val points = remember(order) { order.getTrackPoints() }

            AlertDialog(
                onDismissRequest = { activeDetailOrder = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Rincian Order",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kode Transaksi: ORD-${order.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { activeDetailOrder = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Summary Metadata Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Pendapatan Bersih", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = formatter.format(order.pendapatan),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = GojekGreen
                                        )
                                    }
                                    Surface(
                                        color = when (order.jenisOrder) {
                                            "Penumpang" -> GojekGreen
                                            "Food", "Makanan" -> GojekRed
                                            else -> GojekBlue
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = order.jenisOrder,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.1f)) {
                                            Text("Hari & Tanggal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${order.hari}, ${order.tanggal}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                                            Text("Durasi Kerja", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("%d Menit".format(order.durasi), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Rincian Jarak Tempuh", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Jarak Jemput", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("%.2f KM".format(order.jarakKePickup), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Jarak Antar", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("%.2f KM".format(order.jarakKeTujuan), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                                                Text("Total Jarak", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("%.2f KM".format(order.jarakTempuh), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = GojekGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // TIMELINE FLOW
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Riwayat Rute",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Step 1: Start Address
                            TimelineElement(
                                icon = Icons.Default.PlayArrow,
                                iconColor = GojekGreen,
                                stepTitle = "Titik Awal",
                                timeText = order.jamMulai,
                                addressText = order.alamatAwal,
                                coordsText = "%.5f, %.5f".format(order.latitudeAwal, order.longitudeAwal),
                                isLast = (order.jamPickup == null && order.alamatPickup.isNullOrEmpty())
                            )

                            // Step 2: Pickup Address (optional)
                            if (order.jamPickup != null || !order.alamatPickup.isNullOrEmpty()) {
                                TimelineElement(
                                    icon = Icons.Default.Navigation,
                                    iconColor = GojekYellow,
                                    stepTitle = "Titik Jemput",
                                    timeText = order.jamPickup ?: "-",
                                    addressText = order.alamatPickup ?: "Tidak terekam",
                                    coordsText = if (order.latitudePickup != null) "%.5f, %.5f".format(order.latitudePickup, order.longitudePickup) else "Tidak tersedia",
                                    isLast = false
                                )
                            }

                            // Step 3: Finish Address
                            TimelineElement(
                                icon = Icons.Default.CheckCircle,
                                iconColor = GojekRed,
                                stepTitle = "Titik Tujuan",
                                timeText = order.jamSelesai ?: "-",
                                addressText = order.alamatAkhir ?: "Tidak terekam",
                                coordsText = if (order.latitudeAkhir != null) "%.5f, %.5f".format(order.latitudeAkhir, order.longitudeAkhir) else "Tidak tersedia",
                                isLast = true
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Notes Field
                        if (!order.catatan.isNullOrEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Catatan Perjalanan:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = order.catatan,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        // GPS Track points collapsible log list
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Riwayat Rute",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = { showRawGpsPoints = !showRawGpsPoints }) {
                                    Text(if (showRawGpsPoints) "Sembunyikan" else "Buka Log (${points.size} Titik)")
                                }
                            }

                            if (showRawGpsPoints) {
                                if (points.isEmpty()) {
                                    Text("Tidak ada titik GPS terekam di orderan ini.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .heightIn(max = 180.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            points.forEachIndexed { index, pt ->
                                                val timestampStr = remember(pt.timestamp) {
                                                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pt.timestamp))
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "[#${index + 1}] $timestampStr",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GojekGreen
                                                    )
                                                    Text(
                                                        text = "Lat: %.5f, Lng: %.5f".format(pt.lat, pt.lng),
                                                        fontSize = 10.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { activeDetailOrder = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                    ) {
                        Text("Tutup")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun TimelineElement(
    icon: ImageVector,
    iconColor: Color,
    stepTitle: String,
    timeText: String,
    addressText: String,
    coordsText: String,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(iconColor.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stepTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = iconColor)
                Text(text = timeText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = addressText, fontSize = 12.sp, fontWeight = FontWeight.Normal)
            Text(text = "GPS: $coordsText", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
