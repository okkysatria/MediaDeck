package com.gojektracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gojektracker.app.ui.screens.*
import com.gojektracker.app.ui.theme.*
import com.gojektracker.app.viewmodel.MainViewModel
import com.gojektracker.app.viewmodel.WorkflowState

class MainActivity : ComponentActivity() {

    // Mutable state so that Compose can observe intent changes reactively
    private val currentIntent = mutableStateOf<Intent?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            // ViewModel will set up location on next resume
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize osmdroid configuration
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
        org.osmdroid.config.Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )

        enableEdgeToEdge()
        checkAndRequestLocationPermissions()

        // Capture the launch intent so Compose can observe it
        currentIntent.value = intent

        setContent {
            val vModel: MainViewModel = viewModel()
            GojekTrackerTheme(darkTheme = vModel.isDarkMode) {
                // Observe intent changes (from onNewIntent) reactively inside Compose
                val latestIntent by currentIntent
                LaunchedEffect(latestIntent) {
                    val intent = latestIntent ?: return@LaunchedEffect
                    val action = intent.action

                    // Trigger ViewModel actions based on intent action
                    when (action) {
                        "com.gojektracker.app.ACTION_START_ORDER" -> {
                            vModel.startOrder()
                        }
                        "com.gojektracker.app.ACTION_PICKUP" -> {
                            vModel.pickupOrder()
                        }
                        "com.gojektracker.app.ACTION_COMPLETE" -> {
                            // completeOrder() sets showSaveDialogDirectly=true internally
                            vModel.completeOrder()
                            // Navigate to recording screen so save dialog appears
                            vModel.currentScreen = "rekam_order"
                        }
                        "com.gojektracker.app.ACTION_CANCEL" -> {
                            vModel.cancelActiveOrder()
                        }
                    }

                    val nav = intent.getStringExtra("NAVIGATE_TO")
                    if (nav != null) {
                        vModel.currentScreen = nav
                    }
                    val showDialog = intent.getBooleanExtra("SHOW_SAVE_DIALOG", false)
                    if (showDialog) {
                        vModel.showSaveDialogDirectly = true
                    }
                    // Clear so it doesn't re-fire on recomposition
                    currentIntent.value = null
                }

                // Setup location updates when lifecycle resumes
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            vModel.setupLocationUpdates()
                        } else if (event == Lifecycle.Event.ON_PAUSE) {
                            if (vModel.workflowState == WorkflowState.IDLE) {
                                vModel.stopLocationUpdates()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                MainAppScaffold(vModel)
            }
        }
    }

    /**
     * Called when the activity is already running (singleTop launchMode) and receives
     * a new intent — e.g. user taps a notification action while app is in background.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update the observable state so Compose LaunchedEffect fires
        currentIntent.value = intent
    }

    private fun checkAndRequestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GojekSolvLogo()
                        Column {
                            Text(
                                text = "Driver Tracker",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Pencatat Rute & Pendapatan",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.isDarkMode = !viewModel.isDarkMode
                            viewModel.saveLocalSettings()
                        },
                        modifier = Modifier.testTag("theme_quick_toggle")
                    ) {
                        Icon(
                            imageVector = if (viewModel.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Ubah Tema",
                            tint = GojekGreen
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                val screens = listOf(
                    NavigationItem("rekam_order", "Perekam", Icons.Default.PlayArrow, "navigation_rekam"),
                    NavigationItem("smart_heatmap", "Radar", Icons.Default.Map, "navigation_heatmap"),
                    NavigationItem("strava_share", "Rute", Icons.Default.Share, "navigation_strava_share"),
                    NavigationItem("dashboard_pendapatan", "Laporan", Icons.Default.Assessment, "navigation_dashboard"),
                    NavigationItem("pengaturan", "Pengaturan", Icons.Default.Settings, "navigation_settings")
                )

                screens.forEach { item ->
                    val isSelected = viewModel.currentScreen == item.key
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.currentScreen = item.key },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = GojekGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = GojekGreen
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (viewModel.currentScreen) {
                "rekam_order" -> RekamOrderScreen(viewModel)
                "smart_heatmap" -> SmartHeatmapScreen(viewModel)
                "dashboard_pendapatan" -> DashboardScreen(viewModel)
                "pengaturan" -> SettingsScreen(viewModel)
                "strava_share" -> TrackScreen(viewModel)
            }
        }
    }
}

data class NavigationItem(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)

@Composable
fun GojekSolvLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(GojekGreen, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val center = size.width / 2f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center, size.height)
                cubicTo(
                    0f, size.height * 0.4f,
                    0f, 0f,
                    center, 0f
                )
                cubicTo(
                    size.width, 0f,
                    size.width, size.height * 0.4f,
                    center, size.height
                )
                close()
            }
            drawPath(
                path = path,
                color = Color.White
            )
            drawCircle(
                color = GojekGreen,
                radius = size.width / 5f,
                center = androidx.compose.ui.geometry.Offset(center, size.height * 0.35f)
            )
        }
    }
}
