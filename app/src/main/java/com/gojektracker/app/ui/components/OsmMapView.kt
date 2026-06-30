package com.gojektracker.app.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gojektracker.app.data.OrderRecord
import com.gojektracker.app.data.TrackPoint
import com.gojektracker.app.ui.theme.GojekGreen
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView as OsmMapViewNative
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import java.io.File
import java.util.Locale

@Composable
fun OsmMapView(
    latitude: Double,
    longitude: Double,
    isDarkMode: Boolean,
    currentPathPoints: List<TrackPoint>,
    historicalOrders: List<OrderRecord>,
    modifier: Modifier = Modifier,
    mapSource: String = "osm",
    importedPbfFileName: String? = null,
    importedPbfUri: String? = null
) {
    val context = LocalContext.current

    // ── Controls State ────────────────────────────────────────────
    var isAutoCentered by remember { mutableStateOf(true) }
    var selectedOrderHotspot by remember { mutableStateOf<OrderRecord?>(null) }

    // ── Color palettes ────────────────────────────────────────────
    val themeBgColor = if (isDarkMode) Color(0xFF0F1319) else Color(0xFFEFF1F4)

    // ── Build osmdroid MapView (Flicker-Free, single instantiation) ──
    val osmMapView = remember {
        object : OsmMapViewNative(context) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
                if (ev?.action == android.view.MotionEvent.ACTION_DOWN || 
                    ev?.action == android.view.MotionEvent.ACTION_MOVE) {
                    isAutoCentered = false
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            Configuration.getInstance().userAgentValue = context.packageName
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.5)
        }
    }

    Box(
        modifier = modifier
            .background(themeBgColor)
            .testTag("surabaya_vector_map")
    ) {
        // ── Native Map Integration ────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("native_osmdroid_map"),
            factory = { osmMapView },
            update = { view ->
                // 1. Dynamic Tile Source Handling
                // Tag includes mapSource value so different sources trigger reload
                val isOffline = mapSource == "manual_pbf" && importedPbfUri != null
                val isManualSurabaya = mapSource == "manual_surabaya"
                val expectedTag = when {
                    isOffline -> "offline_${importedPbfUri}"
                    isManualSurabaya -> "manual_surabaya_${if (isDarkMode) "dark" else "light"}"
                    else -> "online_${mapSource}"
                }
                
                if (view.tag != expectedTag) {
                    view.tag = expectedTag
                    when {
                        isOffline -> {
                            val tilesDir = File(context.getExternalFilesDir(null), "osmdroid/tiles")
                            val mapFile = tilesDir.listFiles()?.firstOrNull { it.name.startsWith("offline_map") }
                            if (mapFile != null && mapFile.exists()) {
                                try {
                                    val offlineTileProvider = MapTileProviderBasic(context)
                                    offlineTileProvider.tileSource = XYTileSource(
                                        "OfflineMap", 1, 19, 256, ".png", arrayOf()
                                    )
                                    view.setTileProvider(offlineTileProvider)
                                } catch (e: Exception) {
                                    view.setTileSource(TileSourceFactory.MAPNIK)
                                }
                            } else {
                                view.setTileSource(TileSourceFactory.MAPNIK)
                            }
                        }
                        isManualSurabaya -> {
                            try {
                                val sourceName = if (isDarkMode) "ManualSurabayaDark" else "ManualSurabayaLight"
                                val offlineTileProvider = MapTileProviderBasic(context)
                                offlineTileProvider.tileSource = XYTileSource(
                                    sourceName, 12, 15, 256, ".png", arrayOf()
                                )
                                view.setTileProvider(offlineTileProvider)
                                // Standard center on Surabaya
                                view.controller.setZoom(13.5)
                                view.controller.setCenter(org.osmdroid.util.GeoPoint(-7.2754, 112.7938))
                            } catch (e: Exception) {
                                view.setTileSource(TileSourceFactory.MAPNIK)
                            }
                        }
                        else -> {
                            // Always recreate online provider when source tag changes
                            val onlineProvider = MapTileProviderBasic(context)
                            onlineProvider.tileSource = TileSourceFactory.MAPNIK
                            view.setTileProvider(onlineProvider)
                        }
                    }
                }

                // 2. Dynamic Dark Mode Styling
                val colorFilter = if (isDarkMode && !isManualSurabaya) {
                    val m = ColorMatrix()
                    m.set(floatArrayOf(
                        -0.65f, 0f, 0f, 0f, 210f,
                        0f, -0.65f, 0f, 0f, 210f,
                        0f, 0f, -0.65f, 0f, 210f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    ColorMatrixColorFilter(m)
                } else {
                    null
                }
                view.overlayManager.tilesOverlay.setColorFilter(colorFilter)

                // 3. Clear and Render Overlays Dynamically
                view.overlays.clear()

                // Center camera if locked
                if (isAutoCentered) {
                    view.controller.animateTo(GeoPoint(latitude, longitude))
                }

                // Active GPS Track Polyline
                if (currentPathPoints.size >= 2) {
                    val polyline = Polyline(view).apply {
                        outlinePaint.apply {
                            color = android.graphics.Color.argb(230, 0, 200, 20)
                            strokeWidth = 12f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            setShadowLayer(8f, 0f, 0f, android.graphics.Color.argb(120, 0, 255, 30))
                            isAntiAlias = true
                        }
                        setPoints(currentPathPoints.map { GeoPoint(it.lat, it.lng) })
                    }
                    view.overlays.add(polyline)
                }

                // Hotspot Order Markers & Heatmap Translucent Overlays (Up to 30 elements)
                historicalOrders.take(30).forEach { order ->
                    if (order.latitudeAwal != 0.0) {
                        val intensity = order.durasi.toInt().coerceIn(10, 100)

                        val marker = Marker(view).apply {
                            position = GeoPoint(order.latitudeAwal, order.longitudeAwal)
                            title = "🔥 Potensi: $intensity% • Rp ${order.pendapatan.toLong()}"
                            subDescription = "${order.jenisOrder} • ${order.alamatAwal.take(45)}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = createMarkerIcon(context, order.jenisOrder)
                            setOnMarkerClickListener { m, _ ->
                                selectedOrderHotspot = order
                                m.showInfoWindow()
                                true
                            }
                        }
                        view.overlays.add(marker)
                    }
                }

                // Live Driver Location Marker (Distinct white-bordered blue dot with pulsing ring)
                val driverMarker = Marker(view).apply {
                    position = GeoPoint(latitude, longitude)
                    title = "📍 Lokasi Anda"
                    subDescription = "GPS Live Tracker"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createDriverIcon(context)
                }
                view.overlays.add(driverMarker)

                view.invalidate()
            }
        )

        // ── Floating Action Buttons (Top-Right controls) ───────────
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lock GPS
            MapFab(
                icon = Icons.Default.GpsFixed,
                active = isAutoCentered,
                tag = "map_lock_gps_button",
                onClick = {
                    isAutoCentered = true
                    selectedOrderHotspot = null
                }
            )
            // Zoom In
            MapFab(
                icon = Icons.Default.Add,
                active = false,
                tag = "map_zoom_in_button",
                onClick = {
                    isAutoCentered = false
                    try {
                        osmMapView.controller.zoomIn()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            )
            // Zoom Out
            MapFab(
                icon = Icons.Default.Remove,
                active = false,
                tag = "map_zoom_out_button",
                onClick = {
                    isAutoCentered = false
                    try {
                        osmMapView.controller.zoomOut()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            )
        }

        // ── Hotspot Info Card (Bottom floating overlay) ────────────
        AnimatedVisibility(
            visible = selectedOrderHotspot != null,
            enter = fadeIn() + scaleIn(initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            selectedOrderHotspot?.let { order ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF151C26) else Color(0xFFFFFFFF)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, GojekGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.shadow(24.dp, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Order Historis #${order.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "💰 Rp ${order.pendapatan.toLong()} • ${order.jenisOrder}",
                                fontSize = 12.sp,
                                color = GojekGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = order.alamatAwal,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { selectedOrderHotspot = null },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(if (active) 8.dp else 4.dp, RoundedCornerShape(12.dp))
            .background(
                if (active) GojekGreen else Color(0xCC1A2130),
                RoundedCornerShape(12.dp)
            )
            .border(
                BorderStroke(1.dp, if (active) GojekGreen else Color.White.copy(alpha = 0.08f)),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Dynamically draws marker icons for hotspots with distinct color categories and letter labels.
 */
private fun createMarkerIcon(context: android.content.Context, type: String): android.graphics.drawable.Drawable {
    val sizePx = 86
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Color categorization based on service type
    val color = when (type.lowercase(Locale.ROOT)) {
        "food", "gofood", "makanan" -> android.graphics.Color.rgb(230, 126, 34) // Orange (Food)
        "paket", "gosend", "barang" -> android.graphics.Color.rgb(41, 128, 185)  // Blue (Package)
        else -> android.graphics.Color.rgb(0, 170, 19)                         // Gojek Green (Ride)
    }

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }

    // Shadow
    paint.color = android.graphics.Color.argb(70, 0, 0, 0)
    canvas.drawCircle(sizePx / 2f, sizePx * 0.85f, 12f, paint)

    // Pin shape
    paint.color = color
    val path = android.graphics.Path().apply {
        val cx = sizePx / 2f
        val cy = sizePx * 0.35f
        val r = sizePx * 0.3f
        moveTo(cx, sizePx * 0.9f)
        lineTo(cx - r * 0.75f, cy + r * 0.6f)
        arcTo(
            android.graphics.RectF(cx - r, cy - r, cx + r, cy + r),
            145f,
            250f,
            false
        )
        close()
    }
    canvas.drawPath(path, paint)

    // Outer white dot in center
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(sizePx / 2f, sizePx * 0.35f, sizePx * 0.20f, paint)

    // Inner custom letter representing service
    paint.color = color
    paint.textSize = sizePx * 0.26f
    paint.isFakeBoldText = true
    paint.textAlign = android.graphics.Paint.Align.CENTER
    
    val letter = when (type.lowercase(Locale.ROOT)) {
        "food", "gofood", "makanan" -> "F"
        "paket", "gosend", "barang" -> "P"
        else -> "R"
    }
    
    val fm = paint.fontMetrics
    val yOffset = (fm.descent - fm.ascent) / 2f - fm.descent
    canvas.drawText(letter, sizePx / 2f, sizePx * 0.35f + yOffset, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/**
 * Dynamically draws the driver's current position icon (Gojek-style green helmet marker).
 */
private fun createDriverIcon(context: android.content.Context): android.graphics.drawable.Drawable {
    val sizePx = 96
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply { isAntiAlias = true }

    // 1. Shadow
    paint.color = android.graphics.Color.argb(80, 0, 0, 0)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f + 4f, sizePx * 0.4f, paint)

    // 2. Outer glowing ring (White)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.4f, paint)

    // 3. Inner circle (Gojek Green)
    paint.color = android.graphics.Color.rgb(0, 170, 19)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.34f, paint)

    // 4. Helmet Dome (White/Silver)
    paint.color = android.graphics.Color.WHITE
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx * 0.18f
    canvas.drawCircle(cx, cy - 2f, r, paint)

    // 5. Visor (Black)
    paint.color = android.graphics.Color.BLACK
    val visorPath = android.graphics.Path().apply {
        moveTo(cx - r * 0.9f, cy - 2f)
        lineTo(cx + r * 0.9f, cy - 2f)
        lineTo(cx + r * 0.7f, cy + r * 0.4f)
        lineTo(cx - r * 0.7f, cy + r * 0.4f)
        close()
    }
    canvas.drawPath(visorPath, paint)

    // 6. Visor Reflection (White gloss line)
    paint.color = android.graphics.Color.argb(150, 255, 255, 255)
    paint.strokeWidth = 2f
    canvas.drawLine(cx - r * 0.5f, cy + 2f, cx - r * 0.2f, cy + 4f, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
