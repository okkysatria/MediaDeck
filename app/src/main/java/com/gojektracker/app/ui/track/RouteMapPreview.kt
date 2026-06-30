package com.gojektracker.app.ui.track

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.gojektracker.app.data.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Pratinjau rute sederhana untuk satu hari: garis polyline + titik mulai/selesai,
 * mengikuti sumber peta yang sedang aktif di aplikasi (online OSM / offline).
 * Lebih ringan dari implementasi lama: tanpa color-matrix manual, tanpa mode vektor terpisah.
 */
@Composable
fun RouteMapPreview(
    trackPoints: List<TrackPoint>,
    accentColor: Color,
    showMapTiles: Boolean,
    mapSource: String,
    modifier: Modifier = Modifier,
    onMapViewReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current

    if (trackPoints.size < 2) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tidak ada data rute untuk tanggal ini", color = Color.Gray)
        }
        return
    }

    val osmMapView = remember(mapSource) {
        MapView(context).apply {
            Configuration.getInstance().userAgentValue = context.packageName
            if (mapSource == "manual_pbf") {
                val tilesDir = File(context.getExternalFilesDir(null), "osmdroid/tiles")
                val mapFile = tilesDir.listFiles()?.firstOrNull { it.name.startsWith("offline_map") }
                if (mapFile != null && mapFile.exists()) {
                    try {
                        val provider = MapTileProviderBasic(context)
                        provider.tileSource = XYTileSource("OfflineMap", 1, 19, 256, ".png", arrayOf())
                        setTileProvider(provider)
                    } catch (_: Exception) {
                        setTileSource(TileSourceFactory.MAPNIK)
                    }
                } else {
                    setTileSource(TileSourceFactory.MAPNIK)
                }
            } else {
                setTileSource(TileSourceFactory.MAPNIK)
            }
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { osmMapView },
        update = { view ->
            view.overlayManager.tilesOverlay.isEnabled = showMapTiles
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            view.overlays.clear()
            val geoPoints = trackPoints.map { GeoPoint(it.lat, it.lng) }

            view.overlays.add(
                Polyline(view).apply {
                    outlinePaint.color = accentColor.toArgb()
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    setPoints(geoPoints)
                }
            )
            view.overlays.add(
                Marker(view).apply {
                    position = geoPoints.first()
                    title = "Mulai"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            )
            view.overlays.add(
                Marker(view).apply {
                    position = geoPoints.last()
                    title = "Selesai"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            )

            val lats = trackPoints.map { it.lat }
            val lngs = trackPoints.map { it.lng }
            val box = BoundingBox(
                lats.max() + 0.003, lngs.max() + 0.003,
                lats.min() - 0.003, lngs.min() - 0.003
            )
            view.post { view.zoomToBoundingBox(box, false, 60) }
            view.invalidate()
            onMapViewReady(view)
        }
    )
}
