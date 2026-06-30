package com.gojektracker.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.LocalPostOffice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gojektracker.app.data.OrderRecord
import com.gojektracker.app.ui.theme.*
import com.gojektracker.app.viewmodel.MainViewModel
import com.gojektracker.app.viewmodel.WorkflowState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekamOrderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val orders by viewModel.allOrders.collectAsState()
    
    // Calculate today's stats
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val todayStrFull = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID")).format(Date()) }
    val todayOrders = remember(orders, todayStr) { orders.filter { it.tanggal == todayStr } }
    val todayRevenue = remember(todayOrders) { todayOrders.sumOf { it.pendapatan } }
    val todayCount = todayOrders.size
    val todayDistance = remember(todayOrders) { todayOrders.sumOf { it.jarakTempuh } }
    val todayKePickup = remember(todayOrders) { todayOrders.sumOf { it.jarakKePickup } }
    val todayKeTujuan = remember(todayOrders) { todayOrders.sumOf { it.jarakKeTujuan } }

    // Dialog input rates
    var selectedJenis by remember { mutableStateOf("Penumpang") }
    var incomeInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    // Observe showSaveDialogDirectly from view model to reset inputs
    LaunchedEffect(viewModel.showSaveDialogDirectly) {
        if (viewModel.showSaveDialogDirectly) {
            incomeInput = ""
            notesInput = ""
            selectedJenis = "Penumpang"
        }
    }

    val localeID = Locale.forLanguageTag("id-ID")
    val formatter = NumberFormat.getCurrencyInstance(localeID).apply {
        maximumFractionDigits = 0
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // RINGKASAN HARI INI CARD
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.currentScreen = "dashboard_pendapatan"
                    }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Ringkasan Hari Ini",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = todayStrFull,
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
                                text = "Pendapatan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatter.format(todayRevenue),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = GojekGreen
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Order",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$todayCount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Jarak Tempuh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(todayDistance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Jarak Jemput", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(todayKePickup), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Jarak Antar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f KM".format(todayKeTujuan), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Removed Strava promotional banner shortcut as requested

            // GPS LOCATION STATUS BANNER
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.currentScreen = "smart_heatmap"
                    },
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "GPS",
                        tint = GojekGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Lokasi GPS",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = viewModel.currentAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                    }
                }
            }

            // RUNNING TRACK STATS (VISIBLE ONLY WHEN TRACKING IS ACTIVE)
            AnimatedVisibility(
                visible = viewModel.workflowState != WorkflowState.IDLE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = GojekBlue.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Perekaman Aktif",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GojekBlue
                            )
                            Surface(
                                color = GojekBlue,
                                shape = CircleShape,
                                modifier = Modifier.size(12.dp)
                            ) {}
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "Jarak",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.2f KM".format(viewModel.activeDistanceKm),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Durasi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${viewModel.activeDurationMinutes} Menit",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "Jarak Jemput",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.2f KM".format(viewModel.activeJarakKePickup),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GojekGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Jarak Antar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.2f KM".format(viewModel.activeJarakKeTujuan),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GojekRed
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        // Show coordinates count
                        Text(
                            text = "Koordinat GPS: ${viewModel.activeTrackPoints.size} titik (Interval ${viewModel.trackingIntervalSec}s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            // STATE MACHINE GIANT BUTTON
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val btnColor = when (viewModel.workflowState) {
                    WorkflowState.IDLE -> GojekGreen
                    WorkflowState.STARTED -> GojekYellow
                    WorkflowState.PICKED_UP -> GojekRed
                }
                
                val btnText = when (viewModel.workflowState) {
                    WorkflowState.IDLE -> "Mulai Order"
                    WorkflowState.STARTED -> "Konfirmasi Jemput"
                    WorkflowState.PICKED_UP -> "Selesai Order"
                }

                val btnIcon = when (viewModel.workflowState) {
                    WorkflowState.IDLE -> Icons.Default.PlayArrow
                    WorkflowState.STARTED -> Icons.Default.Navigation
                    WorkflowState.PICKED_UP -> Icons.Default.CheckCircle
                }

                Button(
                    onClick = {
                        when (viewModel.workflowState) {
                            WorkflowState.IDLE -> viewModel.startOrder()
                            WorkflowState.STARTED -> viewModel.pickupOrder()
                            WorkflowState.PICKED_UP -> {
                                viewModel.completeOrder()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("action_workflow_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = btnIcon, contentDescription = btnText, tint = Color.White)
                        Text(
                            text = btnText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }

                // If active, show dynamic cancel button
                if (viewModel.workflowState != WorkflowState.IDLE) {
                    TextButton(
                        onClick = { viewModel.cancelActiveOrder() },
                        modifier = Modifier.testTag("cancel_workflow_button")
                    ) {
                        Text(
                            text = "Batalkan Order",
                            color = GojekRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // ORDER COMPLETION MODAL SHEET DIALOG
    if (viewModel.showSaveDialogDirectly) {
        AlertDialog(
            onDismissRequest = { /* Do not close on outside touch to prevent lose tracking */ },
            title = {
                Text(
                    text = "Simpan Order",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Summary values
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Jarak", style = MaterialTheme.typography.bodySmall)
                                Text("%.2f KM".format(viewModel.activeDistanceKm), fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Durasi", style = MaterialTheme.typography.bodySmall)
                                Text("${viewModel.activeDurationMinutes} Menit", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Jenis Order Selector
                    Text("Jenis Layanan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Penumpang", "Makanan", "Paket").forEach { jenis ->
                            val isSelected = selectedJenis == jenis
                            val itemColor = when (jenis) {
                                "Penumpang" -> GojekGreen
                                "Makanan" -> GojekRed
                                else -> GojekBlue
                            }
                            
                            val itemIcon = when (jenis) {
                                "Penumpang" -> Icons.Outlined.TwoWheeler
                                "Makanan" -> Icons.Outlined.Fastfood
                                else -> Icons.Outlined.LocalPostOffice
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedJenis = jenis }
                                    .testTag("jenis_${jenis.lowercase()}_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) itemColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (isSelected) BorderStroke(2.dp, itemColor) else null,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = itemIcon,
                                        contentDescription = jenis,
                                        tint = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = jenis,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Income Input
                    OutlinedTextField(
                        value = incomeInput,
                        onValueChange = { input ->
                            // Allow digits only
                            if (input.all { it.isDigit() }) {
                                incomeInput = input
                            }
                        },
                        label = { Text("Pendapatan Bersih") },
                        prefix = { Text("Rp ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("income_text_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GojekGreen,
                            focusedLabelColor = GojekGreen,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    // Optional Notes
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Catatan (Opsional)") },
                        modifier = Modifier.fillMaxWidth().testTag("notes_text_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GojekGreen,
                            focusedLabelColor = GojekGreen,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalIncome = incomeInput.toDoubleOrNull() ?: 0.0
                        viewModel.saveCompletedOrder(
                            jenis = selectedJenis,
                            pendapatan = finalIncome,
                            catatan = notesInput
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                    modifier = Modifier.testTag("dialog_save_button")
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelActiveOrder()
                    },
                    modifier = Modifier.testTag("dialog_cancel_button")
                ) {
                    Text("Batal", color = MaterialTheme.colorScheme.outline)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
