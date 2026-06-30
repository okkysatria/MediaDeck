package com.gojektracker.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gojektracker.app.ui.theme.GojekGreen
import com.gojektracker.app.ui.theme.GojekRed
import com.gojektracker.app.viewmodel.MainViewModel
import com.gojektracker.app.ui.components.SurabayaMapGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val orders by viewModel.allOrders.collectAsState()
    val context = LocalContext.current
    val clipboardManager = remember { 
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager 
    }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var isAppendMode by remember { mutableStateOf(true) }
    var skipDuplicates by remember { mutableStateOf(true) }
    var exportWithGps by remember { mutableStateOf(true) }
    var isProcessingExport by remember { mutableStateOf(false) }
    var exportModeText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var isGeneratingMap by remember { mutableStateOf(false) }
    var mapGenerationProgress by remember { mutableStateOf(0f) }

    // MUTABLE STATES FOR MONTHLY TAX REPORT
    var taxReportDriverName by remember { mutableStateOf("") }
    var taxReportSelectedMonth by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) }
    var taxReportSelectedYear by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotEmpty()) {
                    importText = text
                    Toast.makeText(context, "Berkas JSON berhasil dimuat.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Berkas kosong.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membaca berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pbfFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Take persistent read permission so we can access the file later
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                var name = "offline_map.zip"
                var sizeStr = "?"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            val tempName = cursor.getString(nameIndex)
                            if (!tempName.isNullOrEmpty()) name = tempName
                        }
                        if (sizeIndex != -1) {
                            val sizeBytes = cursor.getLong(sizeIndex)
                            if (sizeBytes > 0) {
                                sizeStr = String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
                            }
                        }
                    }
                }

                // Copy the map file to the osmdroid tiles directory so osmdroid can load it
                val tilesDir = java.io.File(context.getExternalFilesDir(null), "osmdroid/tiles")
                    .also { it.mkdirs() }
                // Also check internal cache as fallback
                val internalTilesDir = java.io.File(context.cacheDir, "osmdroid/tiles")
                    .also { it.mkdirs() }

                val ext = if (name.contains(".")) name.substring(name.lastIndexOf(".")) else ".zip"
                val destFile = java.io.File(tilesDir, "offline_map$ext")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }

                viewModel.importedPbfFileName = name
                viewModel.importedPbfFileSize = sizeStr
                viewModel.importedPbfUri = uri.toString()
                viewModel.mapSource = "manual_pbf"
                viewModel.saveLocalSettings()

                Toast.makeText(
                    context,
                    "Peta offline $name ($sizeStr) berhasil dimuat.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat peta offline: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onnxFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Try taking persistable read permission
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: java.lang.SecurityException) {
                    // Security exception could occur on some Android configurations, proceed anyway
                }

                var name = "external_model.onnx"
                var sizeStr = "?"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            val tempName = cursor.getString(nameIndex)
                            if (!tempName.isNullOrEmpty()) name = tempName
                        }
                        if (sizeIndex != -1) {
                            val sizeBytes = cursor.getLong(sizeIndex)
                            if (sizeBytes > 0) {
                                sizeStr = String.format(java.util.Locale.US, "%.2f MB", sizeBytes / (1024f * 1024f))
                            }
                        }
                    }
                }

                // Parse standard headers of the ONNX (which is a protobuf structure)
                var parsedInput = "coordinates_input"
                var parsedOutput = "probability_weights"
                var modelDetails = "ONNX Neural Graph v1.4"

                try {
                    val tempPredictor = com.gojektracker.app.ml.SmartHeatmapPredictor(context, uri.toString())
                    if (tempPredictor.isModelLoaded) {
                        val inputs = tempPredictor.getInputNames()
                        val outputs = tempPredictor.getOutputNames()
                        if (inputs.isNotEmpty()) {
                            parsedInput = inputs.first()
                        }
                        if (outputs.isNotEmpty()) {
                            parsedOutput = outputs.first()
                        }
                        modelDetails = "ONNX Runtime Sesi Dinamis (${inputs.size} In, ${outputs.size} Out)"
                    } else {
                        // Scan standard bytes fallback
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = ByteArray(2048)
                            val bytesRead = stream.read(bytes)
                            if (bytesRead > 0) {
                                val contentStr = String(bytes, 0, bytesRead, Charsets.US_ASCII)
                                // Scan for potential names
                                if (contentStr.contains("pytorch", ignoreCase = true) || contentStr.contains("torch", ignoreCase = true)) {
                                    modelDetails = "TorchScript Exported ONNX Graph"
                                } else if (contentStr.contains("keras", ignoreCase = true) || contentStr.contains("tf", ignoreCase = true)) {
                                    modelDetails = "TensorFlow-Keras Transpiled ONNX"
                                } else if (contentStr.contains("scikit", ignoreCase = true) || contentStr.contains("sklearn", ignoreCase = true)) {
                                    modelDetails = "Scikit-Learn Linear/Tree ONNX Pipeline"
                                }
                                
                                // Highly premium heuristic to extract real input/output names if the ONNX is compiled with standard names
                                val layers = mutableListOf<String>()
                                val regex = java.util.regex.Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{3,30}")
                                val matcher = regex.matcher(contentStr)
                                while (matcher.find()) {
                                    val tag = matcher.group()
                                    if (tag.contains("input") || tag.contains("feature") || tag.contains("X_") || tag.contains("lat_lng")) {
                                        if (!layers.contains(tag)) layers.add(tag)
                                    }
                                    if (tag.contains("output") || tag.contains("predict") || tag.contains("dense") || tag.contains("Y_") || tag.contains("score")) {
                                        if (!layers.contains(tag)) layers.add(tag)
                                    }
                                }
                                if (layers.isNotEmpty()) {
                                    val inputs = layers.filter { it.contains("input") || it.contains("feature") || it.contains("X_") || it.contains("lat_lng") }
                                    val outputs = layers.filter { it.contains("output") || it.contains("predict") || it.contains("dense") || it.contains("Y_") || it.contains("score") }
                                    if (inputs.isNotEmpty()) parsedInput = inputs.first()
                                    if (outputs.isNotEmpty()) parsedOutput = outputs.first()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                viewModel.importedOnnxFileName = name
                viewModel.importedOnnxFileSize = sizeStr
                viewModel.importedOnnxUri = uri.toString()
                viewModel.useOnnxModelPrediction = true
                viewModel.onnxInputLayerName = parsedInput
                viewModel.onnxOutputLayerName = parsedOutput
                viewModel.onnxModelTypeInfo = modelDetails
                viewModel.saveLocalSettings()

                Toast.makeText(
                    context,
                    "Model ONNX $name ($sizeStr) berhasil diimpor dan diaktifkan!",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat model ONNX: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shareContent: (String, String) -> Unit = { title, content ->
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, content)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, title)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                    text = "Pengaturan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Konfigurasi GPS, database, prediksi, dan tema",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // SETTINGS CATEGORY: TEMA & TAMPILAN
        Text(
            text = "Tampilan",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GojekGreen
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = "Dark Mode",
                            tint = GojekGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Tema Gelap", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Menghemat daya baterai dan mengurangi kelelahan mata.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = viewModel.isDarkMode,
                        onCheckedChange = {
                            viewModel.isDarkMode = it
                            viewModel.saveLocalSettings()
                        },
                        modifier = Modifier.testTag("dark_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GojekGreen,
                            checkedTrackColor = GojekGreen.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        // SETTINGS CATEGORY: GPS SETTING
        Text(
            text = "Pelacakan GPS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GojekGreen
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Simulator Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = "Simulasi GPS",
                            tint = GojekGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Simulasi Rute Pintar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Mensimulasikan gerakan secara otomatis (sangat direkomendasikan untuk uji coba instan di sandbox tanpa sensor fisik).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = viewModel.useGpsSimulator,
                        onCheckedChange = {
                            viewModel.useGpsSimulator = it
                            viewModel.saveLocalSettings()
                        },
                        modifier = Modifier.testTag("gps_simulator_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GojekGreen,
                            checkedTrackColor = GojekGreen.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                // Interval Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Interval Lokasi GPS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Interval lebih singkat meningkatkan akurasi rute, tetapi meningkatkan konsumsi baterai.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10).forEach { sec ->
                            val isSelected = viewModel.trackingIntervalSec == sec
                            Button(
                                onClick = {
                                    viewModel.trackingIntervalSec = sec
                                    viewModel.saveLocalSettings()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gps_interval_${sec}s"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) GojekGreen else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "$sec Detik",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // SETTINGS CATEGORY: SUMBER DATA PETA
        Text(
            text = "Sumber Data Peta",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GojekGreen
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Sumber Peta Utama",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Mengubah sumber peta yang digunakan pada tab Radar dan Rute.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. OSM Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (viewModel.mapSource == "osm") GojekGreen.copy(alpha = 0.08f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            viewModel.mapSource = "osm"
                            viewModel.saveLocalSettings()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = viewModel.mapSource == "osm",
                        onClick = {
                            viewModel.mapSource = "osm"
                            viewModel.saveLocalSettings()
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = GojekGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OpenStreetMap (Online)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (viewModel.mapSource == "osm") GojekGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mengunduh peta secara real-time dari server resmi OpenStreetMap.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Manual PBF Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (viewModel.mapSource == "manual_pbf") GojekGreen.copy(alpha = 0.08f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            viewModel.mapSource = "manual_pbf"
                            viewModel.saveLocalSettings()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = viewModel.mapSource == "manual_pbf",
                        onClick = {
                            viewModel.mapSource = "manual_pbf"
                            viewModel.saveLocalSettings()
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = GojekGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Peta Offline (.zip/.mbtiles)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (viewModel.mapSource == "manual_pbf") GojekGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Menggunakan berkas peta offline lokal dalam format .zip atau .mbtiles.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Peta Manual Surabaya (Offline) Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (viewModel.mapSource == "manual_surabaya") GojekGreen.copy(alpha = 0.08f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (!SurabayaMapGenerator.isGenerated(context)) {
                                isGeneratingMap = true
                                mapGenerationProgress = 0f
                                scope.launch(Dispatchers.IO) {
                                    val success = SurabayaMapGenerator.generateAllTiles(context) { progress ->
                                        mapGenerationProgress = progress
                                    }
                                    withContext(Dispatchers.Main) {
                                        isGeneratingMap = false
                                        if (success) {
                                            viewModel.mapSource = "manual_surabaya"
                                            viewModel.saveLocalSettings()
                                            Toast.makeText(context, "Peta offline Surabaya berhasil dimuat!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Gagal membuat peta Surabaya.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                viewModel.mapSource = "manual_surabaya"
                                viewModel.saveLocalSettings()
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = viewModel.mapSource == "manual_surabaya",
                        onClick = {
                            if (!SurabayaMapGenerator.isGenerated(context)) {
                                isGeneratingMap = true
                                mapGenerationProgress = 0f
                                scope.launch(Dispatchers.IO) {
                                    val success = SurabayaMapGenerator.generateAllTiles(context) { progress ->
                                        mapGenerationProgress = progress
                                    }
                                    withContext(Dispatchers.Main) {
                                        isGeneratingMap = false
                                        if (success) {
                                            viewModel.mapSource = "manual_surabaya"
                                            viewModel.saveLocalSettings()
                                            Toast.makeText(context, "Peta offline Surabaya berhasil dimuat!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Gagal membuat peta Surabaya.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                viewModel.mapSource = "manual_surabaya"
                                viewModel.saveLocalSettings()
                            }
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = GojekGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Peta Offline Manual Surabaya (Indah & Offline)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (viewModel.mapSource == "manual_surabaya") GojekGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Peta Surabaya custom offline lengkap dengan jalan utama, sungai Kalimas, Kampus ITS, Tunjungan Plaza, dan jembatan Suramadu.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Conditionally show Rebuild card/button if manual_surabaya is selected
                if (viewModel.mapSource == "manual_surabaya") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Status: Aktif & Offline",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = GojekGreen
                                    )
                                    Text(
                                        text = "Jumlah ubin: 417 ubin (Siang & Malam) • Lokasi: Surabaya",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        isGeneratingMap = true
                                        mapGenerationProgress = 0f
                                        scope.launch(Dispatchers.IO) {
                                            val success = SurabayaMapGenerator.generateAllTiles(context) { progress ->
                                                mapGenerationProgress = progress
                                            }
                                            withContext(Dispatchers.Main) {
                                                isGeneratingMap = false
                                                if (success) {
                                                    Toast.makeText(context, "Peta berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Gagal memperbarui peta.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buat Ulang", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Conditionally show Import button if Manual PBF is selected
                if (viewModel.mapSource == "manual_pbf") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Berkas Peta Offline",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Button(
                                    onClick = { pbfFilePickerLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pilih Berkas", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (viewModel.importedPbfFileName != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.SnippetFolder, contentDescription = null, tint = GojekGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = viewModel.importedPbfFileName ?: "",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = GojekGreen
                                        )
                                    }
                                    Text(
                                        text = "Ukuran: ${viewModel.importedPbfFileSize ?: "0 MB"} • Status: Aktif",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Berkas peta belum dipilih. Silakan pilih berkas .zip atau .mbtiles.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SETTINGS CATEGORY: PREDIKSI SMART HEATMAP
        Text(
            text = "Prediksi Smart Radar",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GojekGreen
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Toggle Smart Heatmap
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Radar Cerdas AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Prediksi area potensial secara real-time berbasis kecerdasan buatan (Machine Learning).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.smartHeatmapEnabled,
                        onCheckedChange = {
                            viewModel.smartHeatmapEnabled = it
                            viewModel.saveLocalSettings()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GojekGreen)
                    )
                }



                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ONNX MODEL INPUT SECTION
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model Cerdas ONNX (ML)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Unggah berkas model .onnx kustom Anda untuk memetakan kepadatan orderan real-time berbasis jaringan saraf.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (viewModel.importedOnnxFileName != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (viewModel.isDarkMode) Color(0xFF132319) else Color(0xFFE8F5E9)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GojekGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Model loaded",
                                            tint = GojekGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Model Kustom Siap",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GojekGreen
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(GojekGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Aktif (Auto-fill)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GojekGreen
                                        )
                                    }
                                }

                                Text(
                                    text = "Nama File: ${viewModel.importedOnnxFileName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "Ukuran: ${viewModel.importedOnnxFileSize} • Tipe: ${viewModel.onnxModelTypeInfo}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                // Tuning custom weights / layer mapping parameters
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Mapping Finetuning Tensor Layer (Terisi Otomatis)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedTextField(
                                        value = viewModel.onnxInputLayerName,
                                        onValueChange = {
                                            viewModel.onnxInputLayerName = it
                                            viewModel.saveLocalSettings()
                                        },
                                        label = { Text("Input Layer Node (X) - Terdeteksi Otomatis", fontSize = 9.sp) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onnx_input_layer_field"),
                                        singleLine = true,
                                        readOnly = true
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = viewModel.onnxOutputLayerName,
                                        onValueChange = {
                                            viewModel.onnxOutputLayerName = it
                                            viewModel.saveLocalSettings()
                                        },
                                        label = { Text("Output Layer Node (Y) - Terdeteksi Otomatis", fontSize = 9.sp) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onnx_output_layer_field"),
                                        singleLine = true,
                                        readOnly = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onnxFilePickerLauncher.launch(arrayOf("*/*")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp).testTag("reimport_onnx_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Replace", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ganti Model", fontSize = 10.sp)
                                    }

                                    Button(
                                        onClick = { viewModel.removeOnnxModel() },
                                        colors = ButtonDefaults.buttonColors(containerColor = GojekRed),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp).testTag("remove_onnx_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hapus Model", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Display default built-in model status and upload button
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (viewModel.isDarkMode) Color(0xFF1B222C) else Color(0xFFF0F4F8)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Built-in model status",
                                        tint = GojekGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Model Bawaan/Default Sedang Aktif",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GojekGreen
                                    )
                                }
                                Text(
                                    text = "Menggunakan 'model.onnx' in-app asset default untuk prediksi grid kepadatan h3 di sekitar GPS driver asli. Anda dapat mengunggah berkas .onnx kustom Anda sendiri di bawah.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = { onnxFilePickerLauncher.launch(arrayOf("*/*")) },
                            border = BorderStroke(1.5.dp, GojekGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onnx_file_picker_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Upload ONNX", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Impor Model ONNX Anda", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // SETTINGS CATEGORY: DATABASE & MANAGEMENT
        Text(
            text = "Manajemen Riwayat Data",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GojekGreen
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // EXPORT JSON ROW
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ekspor Data (JSON)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Mengekspor seluruh riwayat rute dan pendapatan ke format JSON.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    // Toggle Track GPS Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportWithGps = !exportWithGps }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = if (exportWithGps) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sertakan koordinat rute GPS (trackGps)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = exportWithGps,
                            onCheckedChange = { exportWithGps = it },
                            modifier = Modifier.testTag("toggle_export_track_gps"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GojekGreen,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessingExport = true
                                    exportModeText = "Mengekspor data ke JSON..."
                                    try {
                                        val json = withContext(Dispatchers.IO) {
                                            viewModel.exportDataAsJson(exportWithGps)
                                        }
                                        exportModeText = "Menulis berkas untuk dibagikan..."
                                        val file = withContext(Dispatchers.IO) {
                                            val cacheFile = java.io.File(context.cacheDir, "driver_tracker_export.json")
                                            cacheFile.writeText(json)
                                            cacheFile
                                        }
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "com.gojektracker.app.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Bagikan Driver Tracker JSON"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal mengekspor: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessingExport = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_json_share_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share JSON", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan JSON", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isProcessingExport = true
                                    exportModeText = "Mengekspor data ke JSON..."
                                    try {
                                        val json = withContext(Dispatchers.IO) {
                                            viewModel.exportDataAsJson(exportWithGps)
                                        }
                                        val clip = ClipData.newPlainText("Driver Tracker JSON", json)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Data JSON berhasil disalin ke clipboard.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal menyalin: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessingExport = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_json_copy_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy JSON", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin JSON", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // EXPORT CSV ROW
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ekspor Laporan (CSV)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Mengekspor riwayat order ke berkas CSV yang kompatibel dengan Excel.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessingExport = true
                                    exportModeText = "Mengekspor data ke CSV..."
                                    try {
                                        val csv = withContext(Dispatchers.IO) {
                                            viewModel.exportDataAsCsv()
                                        }
                                        exportModeText = "Menulis berkas untuk dibagikan..."
                                        val file = withContext(Dispatchers.IO) {
                                            val cacheFile = java.io.File(context.cacheDir, "driver_tracker_export.csv")
                                            cacheFile.writeText(csv)
                                            cacheFile
                                        }
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "com.gojektracker.app.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Bagikan Driver Tracker CSV"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal mengekspor: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessingExport = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_csv_share_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share CSV", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan CSV", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isProcessingExport = true
                                    exportModeText = "Mengekspor data ke CSV..."
                                    try {
                                        val csv = withContext(Dispatchers.IO) {
                                            viewModel.exportDataAsCsv()
                                        }
                                        val clip = ClipData.newPlainText("Driver Tracker CSV", csv)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Data CSV berhasil disalin ke clipboard.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal menyalin: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessingExport = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_csv_copy_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy CSV", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin CSV", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // EXPORT MONTHLY REVENUE REPORT
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Laporan Pendapatan Bulanan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Pilih bulan, isi nama pengendara, dan rancang salinan berkas rekapitulasi pendapatan bulanan yang terstruktur untuk pelaporan pendapatan mandiri dalam format dokumen (.doc).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    // Driver Name input
                    OutlinedTextField(
                        value = taxReportDriverName,
                        onValueChange = { taxReportDriverName = it },
                        label = { Text("Nama Lengkap Pengendara", fontSize = 11.sp) },
                        placeholder = { Text("DRIVER UTAMA", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("tax_driver_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GojekGreen,
                            focusedLabelColor = GojekGreen
                        )
                    )
                    
                    // Interactive Calendar Picker for Month & Year Selection
                    Text("Pilih Periode Laporan (Desain Kalender):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("tax_calendar_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Year picker row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (taxReportSelectedYear > 2020) taxReportSelectedYear-- },
                                    modifier = Modifier.size(32.dp).testTag("tax_year_prev_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Tahun Sebelumnya",
                                        tint = GojekGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = taxReportSelectedYear.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = GojekGreen
                                    )
                                    Text(
                                        text = "Tahun Laporan",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                IconButton(
                                    onClick = { if (taxReportSelectedYear < 2030) taxReportSelectedYear++ },
                                    modifier = Modifier.size(32.dp).testTag("tax_year_next_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Tahun Berikutnya",
                                        tint = GojekGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            
                            // Months Grid (4 rows x 3 columns)
                            val monthAbbr = listOf(
                                "JAN", "FEB", "MAR", "APR", "MEI", "JUN", 
                                "JUL", "AGS", "SEP", "OKT", "NOV", "DES"
                            )
                            val monthFull = listOf(
                                "Januari", "Februari", "Maret", "April", "Mei", "Juni", 
                                "Juli", "Agustus", "September", "Oktober", "November", "Desember"
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (row in 0 until 4) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (col in 0 until 3) {
                                            val monthIndex = row * 3 + col
                                            val mNum = monthIndex + 1
                                            val isSel = taxReportSelectedMonth == mNum
                                            
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(
                                                        color = if (isSel) GojekGreen else MaterialTheme.colorScheme.surface,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSel) GojekGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { taxReportSelectedMonth = mNum }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = monthAbbr[monthIndex],
                                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = monthFull[monthIndex].take(5),
                                                        color = if (isSel) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessingExport = true
                                    exportModeText = "Menyiapkan laporan bulanan (.doc)..."
                                    try {
                                        val doc = withContext(Dispatchers.IO) {
                                            viewModel.exportMonthlyTaxReport(
                                                taxReportDriverName,
                                                taxReportSelectedYear,
                                                taxReportSelectedMonth
                                            )
                                        }
                                        exportModeText = "Menulis berkas dokumen (.doc)..."
                                        val file = withContext(Dispatchers.IO) {
                                            val cacheFile = java.io.File(context.cacheDir, "laporan_pendapatan_bulan_${taxReportSelectedMonth}.doc")
                                            cacheFile.writeText(doc)
                                            cacheFile
                                        }
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "com.gojektracker.app.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/msword"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Bagikan Laporan Pendapatan Driver (.doc)"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal membuat laporan: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessingExport = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_tax_share_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share Revenue", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan .DOC", fontSize = 11.sp)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                val doc = viewModel.exportMonthlyTaxReport(
                                    taxReportDriverName,
                                    taxReportSelectedYear,
                                    taxReportSelectedMonth
                                )
                                val clip = ClipData.newPlainText("Laporan Pendapatan Driver", doc)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Laporan disalin ke clipboard.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("export_tax_copy_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Revenue", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Laporan", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // IMPORT JSON CONTROL
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Impor Data (JSON)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Menggabungkan riwayat data dari berkas cadangan JSON.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            importText = ""
                            showImportDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("import_json_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Input, contentDescription = "Import DB")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Berkas JSON")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Populate with mock seed points
                Button(
                    onClick = {
                        viewModel.seedSampleData()
                        Toast.makeText(context, "Data sampel berhasil dibuat.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("seed_database_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Seed")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inisialisasi Data Sampel")
                }

                // Clear database
                Button(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth().testTag("clear_database_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GojekRed)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Wipe")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hapus Semua Riwayat")
                }
            }
        }

        // TENTANG APLIKASI
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tentang Aplikasi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("ID Aplikasi: com.aistudio.drivertracker", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Versi: 1.0.0 (Beta)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Order Tersimpan: ${orders.size} order", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ukuran Database: %.2f KB".format(orders.size * 0.42), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // CONFIRM DELETE DIALOG
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Hapus Semua Data?") },
            text = { Text("Tindakan ini akan menghapus seluruh riwayat order, pendapatan, dan lintasan GPS secara permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirm = false
                        Toast.makeText(context, "Semua data berhasil dihapus.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GojekRed)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // IMPORT POPUP DIALOG
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Impor Data") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Gabungkan data cadangan atau data dari perangkat lain.", fontSize = 12.sp)

                    // FILE PICKER & CLIPBOARD FAST ACCESS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                filePickerLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Load File", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih Berkas", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val primaryClip = clipboardManager.primaryClip
                                if (primaryClip != null && primaryClip.itemCount > 0) {
                                    val text = primaryClip.getItemAt(0).text
                                    if (!text.isNullOrEmpty()) {
                                        importText = text.toString()
                                        Toast.makeText(context, "Data berhasil ditempel dari clipboard.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard kosong.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Clipboard kosong.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tempel Clipboard", fontSize = 11.sp)
                        }
                    }

                    // FILE CONTENT DISPLAYER / INPUT FIELD
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("Data JSON Cadangan") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("import_text_area"),
                        placeholder = { Text("[{\"tanggal\": \"2026-06-19\", ...}]") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )

                    // CONFIGURATIONS Row (Append vs Overwrite)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Metode Impor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isAppendMode,
                                onClick = { isAppendMode = true }
                            )
                            Text("Gabungkan (Pertahankan data saat ini)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = !isAppendMode,
                                onClick = { isAppendMode = false }
                            )
                            Text("Timpa (Hapus data saat ini)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // DUPLICATE FILTER OPTION (ONLY IF APPEND MODE CHOSEN)
                    if (isAppendMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = skipDuplicates,
                                onCheckedChange = { skipDuplicates = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Abaikan Duplikat", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Abaikan data dengan tanggal, waktu, jenis, dan pendapatan yang sama.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.isEmpty()) {
                            Toast.makeText(context, "Masukkan data atau pilih berkas JSON terlebih dahulu.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.importDataFromJson(
                            jsonStr = importText,
                            appendMode = isAppendMode,
                            skipDuplicates = skipDuplicates,
                            onSuccess = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showImportDialog = false
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
                ) {
                    Text("Impor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (isGeneratingMap) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Membuat Peta Offline Surabaya") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { mapGenerationProgress },
                        color = GojekGreen,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Membuat ubin peta dengan presisi geografis: ${(mapGenerationProgress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (isProcessingExport) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Mengekspor Data") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = GojekGreen)
                    Text(exportModeText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {}
        )
    }
}
