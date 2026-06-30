package com.gojektracker.app.ui.components

import android.content.Context
import android.graphics.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

object SurabayaMapGenerator {

    private const val TAG = "SurabayaMapGenerator"

    data class PointD(val lat: Double, val lng: Double)
    data class MapRoad(val name: String, val points: List<PointD>, val isHighway: Boolean)
    data class MapWater(val name: String, val points: List<PointD>)
    data class MapZone(val name: String, val points: List<PointD>, val colorLight: Int, val colorDark: Int)
    data class MapLandmark(val name: String, val lat: Double, val lng: Double, val type: String)

    // Slippy Map math
    fun tile2lon(x: Int, z: Int): Double = x.toDouble() / 2.0.pow(z) * 360.0 - 180.0

    fun tile2lat(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y.toDouble() / 2.0.pow(z)
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }

    fun lon2tile(lon: Double, zoom: Int): Int = ((lon + 180.0) / 360.0 * 2.0.pow(zoom)).toInt()

    fun lat2tile(lat: Double, zoom: Int): Int =
        ((1.0 - ln(tan(lat * PI / 180.0) + 1.0 / cos(lat * PI / 180.0)) / PI) / 2.0 * 2.0.pow(zoom)).toInt()

    // Map definition data of Surabaya
    val roads = listOf(
        // High Speed Toll lines
        MapRoad(
            "Tol Surabaya - Gempol",
            listOf(PointD(-7.3550, 112.7100), PointD(-7.3100, 112.7120), PointD(-7.2750, 112.7150), PointD(-7.2500, 112.7160), PointD(-7.2210, 112.7230)),
            isHighway = true
        ),
        // South corridor entrance
        MapRoad(
            "Jl. Ahmad Yani",
            listOf(PointD(-7.3551, 112.7270), PointD(-7.3480, 112.7290), PointD(-7.3320, 112.7305), PointD(-7.3100, 112.7330), PointD(-7.3000, 112.7350)),
            isHighway = true
        ),
        // Central axis
        MapRoad(
            "Jl. Raya Darmo",
            listOf(PointD(-7.3000, 112.7350), PointD(-7.2913, 112.7375), PointD(-7.2800, 112.7390), PointD(-7.2710, 112.7402)),
            isHighway = true
        ),
        MapRoad(
            "Jl. Ngagel",
            listOf(PointD(-7.3010, 112.7440), PointD(-7.2882, 112.7455), PointD(-7.2720, 112.7460)),
            isHighway = false
        ),
        MapRoad(
            "Jl. Basuki Rahmat",
            listOf(PointD(-7.2710, 112.7402), PointD(-7.2625, 112.7385), PointD(-7.2550, 112.7390)),
            isHighway = true
        ),
        MapRoad(
            "Jl. Tunjungan",
            listOf(PointD(-7.2550, 112.7390), PointD(-7.2482, 112.7360), PointD(-7.2410, 112.7345)),
            isHighway = true
        ),
        // East bypass (MERR)
        MapRoad(
            "MERR Jl. Ir. H. Soekarno",
            listOf(PointD(-7.3420, 112.7808), PointD(-7.3150, 112.7825), PointD(-7.2950, 112.7836), PointD(-7.2750, 112.7845), PointD(-7.2520, 112.7845)),
            isHighway = true
        ),
        // Connecting roads
        MapRoad(
            "Jl. Dharmahusada",
            listOf(PointD(-7.2700, 112.7400), PointD(-7.2705, 112.7600), PointD(-7.2710, 112.7836), PointD(-7.2721, 112.7930)),
            isHighway = false
        ),
        MapRoad(
            "Jl. Manyar Kertoarjo",
            listOf(PointD(-7.2820, 112.7441), PointD(-7.2815, 112.7620), PointD(-7.2800, 112.7836)),
            isHighway = false
        ),
        MapRoad(
            "Jl. Kertajaya Indah",
            listOf(PointD(-7.2721, 112.7930), PointD(-7.2796, 112.7975), PointD(-7.2880, 112.8020)),
            isHighway = false
        ),
        MapRoad(
            "Jl. HR Muhammad",
            listOf(PointD(-7.2882, 1112.7375), PointD(-7.2882, 112.7150), PointD(-7.2885, 112.6950), PointD(-7.2890, 112.6700)), // Wait, 1112.7375 correction
            isHighway = true
        ),
        MapRoad(
            "Jl. Kenjeran",
            listOf(PointD(-7.2410, 112.7345), PointD(-7.2420, 112.7550), PointD(-7.2450, 112.7845), PointD(-7.2470, 112.8010)),
            isHighway = false
        ),
        MapRoad(
            "Jl. Raya Kupang Indah",
            listOf(PointD(-7.2882, 112.7150), PointD(-7.2750, 112.7210), PointD(-7.2710, 112.7402)),
            isHighway = false
        ),
        MapRoad(
            "Jembatan Suramadu",
            listOf(PointD(-7.1950, 112.7820), PointD(-7.1850, 112.7850), PointD(-7.1650, 112.7915), PointD(-7.1450, 112.8000)),
            isHighway = true
        )
    ).map { road ->
        // Protect against any typos in lng, like 1112.7375
        val correctedPts = road.points.map { pt ->
            if (pt.lng > 180.0) PointD(pt.lat, pt.lng - 1000.0) else pt
        }
        road.copy(points = correctedPts)
    }

    val waterBodies = listOf(
        MapWater(
            "Sungai Kalimas",
            listOf(
                PointD(-7.3150, 112.7390), PointD(-7.3000, 112.7400), PointD(-7.2882, 112.7431),
                PointD(-7.2690, 112.7450), PointD(-7.2550, 112.7420), PointD(-7.2350, 112.7380),
                PointD(-7.2100, 112.7350), PointD(-7.1900, 112.7320)
            )
        )
    )

    val zones = listOf(
        MapZone(
            "Kampus ITS Sukolilo",
            listOf(
                PointD(-7.2740, 112.7925),
                PointD(-7.2721, 112.8020),
                PointD(-7.2825, 112.8010),
                PointD(-7.2820, 112.7920)
            ),
            colorLight = 0xFFD3F4D9.toInt(),
            colorDark = 0xFF0D331B.toInt()
        ),
        MapZone(
            "Kampus B UNAIR",
            listOf(
                PointD(-7.2660, 112.7562),
                PointD(-7.2650, 112.7610),
                PointD(-7.2690, 112.7610),
                PointD(-7.2700, 112.7562)
            ),
            colorLight = 0xFFDCF2F9.toInt(),
            colorDark = 0xFF142F3E.toInt()
        )
    )

    val landmarks = listOf(
        MapLandmark("Tunjungan Plaza", -7.2625, 112.7385, "landmark"),
        MapLandmark("Galaxy Mall", -7.2678, 112.7818, "landmark"),
        MapLandmark("Pakuwon City Mall", -7.2770, 112.8080, "landmark"),
        MapLandmark("Stasiun Gubeng", -7.2653, 112.7519, "station"),
        MapLandmark("Stasiun Pasar Turi", -7.2483, 112.7303, "station"),
        MapLandmark("Tanjung Perak", -7.2040, 112.7330, "port"),
        MapLandmark("Taman Bungkul", -7.2913, 112.7375, "park"),
        MapLandmark("Rektorat ITS", -7.2796, 112.7975, "univ"),
        MapLandmark("Pasar Wonokromo", -7.3010, 112.7370, "landmark")
    )

    fun isGenerated(context: Context): Boolean {
        val rootDir = File(context.getExternalFilesDir(null), "osmdroid/tiles")
        val lightDir = File(rootDir, "ManualSurabayaLight")
        val darkDir = File(rootDir, "ManualSurabayaDark")
        return lightDir.exists() && darkDir.exists() && (lightDir.list()?.isNotEmpty() ?: false)
    }

    /**
     * Pre-generates stylized light and dark mode Surabaya map tiles programmatically.
     * This ensures high-speed, zero-asset, 100% offline maps.
     */
    fun generateAllTiles(context: Context, onProgress: (Float) -> Unit = {}): Boolean {
        try {
            val rootDir = File(context.getExternalFilesDir(null), "osmdroid/tiles").apply { mkdirs() }
            val lightDir = File(rootDir, "ManualSurabayaLight").apply { mkdirs() }
            val darkDir = File(rootDir, "ManualSurabayaDark").apply { mkdirs() }

            val zoomLevels = 12..15
            var totalCount = 0
            for (z in zoomLevels) {
                val xMin = lon2tile(112.65, z)
                val xMax = lon2tile(112.83, z)
                val yMin = lat2tile(-7.20, z)
                val yMax = lat2tile(-7.35, z)
                totalCount += (xMax - xMin + 1) * (yMax - yMin + 1)
            }

            var processed = 0
            for (z in zoomLevels) {
                val xMin = lon2tile(112.65, z)
                val xMax = lon2tile(112.83, z)
                val yMin = lat2tile(-7.20, z)
                val yMax = lat2tile(-7.35, z)

                for (x in xMin..xMax) {
                    for (y in yMin..yMax) {
                        // Generate Light Mode Tile
                        generateTileImage(lightDir, z, x, y, isDarkMode = false)
                        // Generate Dark Mode Tile
                        generateTileImage(darkDir, z, x, y, isDarkMode = true)

                        processed++
                        onProgress(processed.toFloat() / totalCount)
                    }
                }
            }
            Log.d(TAG, "Successfully generated $processed tile pairs for Surabaya offline map.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error generating map tiles: ${e.message}", e)
            return false
        }
    }

    private fun generateTileImage(parentDir: File, z: Int, x: Int, y: Int, isDarkMode: Boolean) {
        val zDir = File(parentDir, z.toString()).apply { mkdir() }
        val xDir = File(zDir, x.toString()).apply { mkdir() }
        val tileFile = File(xDir, "$y.png")

        val width = 256
        val height = 256
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Tile Bounding Box in coordinates
        val westLng = tile2lon(x, z)
        val eastLng = tile2lon(x + 1, z)
        val northLat = tile2lat(y, z)
        val southLat = tile2lat(y + 1, z)

        val latRange = northLat - southLat
        val lngRange = eastLng - westLng

        // Translation Lambdas
        val toPxX = { lng: Double -> ((lng - westLng) / lngRange * width).toFloat() }
        val toPxY = { lat: Double -> ((northLat - lat) / latRange * height).toFloat() }

        // Styles Palette
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = if (isDarkMode) Color.parseColor("#121822") else Color.parseColor("#F4F6FB")
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Grid lines for high-tech look in Dark Mode
        if (isDarkMode) {
            val gridPaint = Paint().apply {
                color = Color.parseColor("#152C1F")
                strokeWidth = 0.5f
                style = Paint.Style.STROKE
            }
            val step = 32f
            var i = 0f; while (i < width) { canvas.drawLine(i, 0f, i, height.toFloat(), gridPaint); i += step }
            var j = 0f; while (j < height) { canvas.drawLine(0f, j, width.toFloat(), j, gridPaint); j += step }
        }

        // 1. Draw Zones (ITS Campus, etc.)
        zones.forEach { zone ->
            val path = Path()
            var hasPoints = false
            zone.points.forEachIndexed { idx, pt ->
                val px = toPxX(pt.lng)
                val py = toPxY(pt.lat)
                if (idx == 0) {
                    path.moveTo(px, py)
                    hasPoints = true
                } else {
                    path.lineTo(px, py)
                }
            }
            if (hasPoints) {
                path.close()
                val zonePaint = Paint().apply {
                    style = Paint.Style.FILL
                    color = if (isDarkMode) zone.colorDark else zone.colorLight
                    isAntiAlias = true
                }
                canvas.drawPath(path, zonePaint)

                // Outline
                val outlinePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = if (isDarkMode) Color.parseColor("#185A2E") else Color.parseColor("#B3E2BE")
                    isAntiAlias = true
                }
                canvas.drawPath(path, outlinePaint)
            }
        }

        // 2. Draw Waters (Kalimas River)
        waterBodies.forEach { water ->
            val path = Path()
            var hasPoints = false
            water.points.forEachIndexed { idx, pt ->
                val px = toPxX(pt.lng)
                val py = toPxY(pt.lat)
                if (idx == 0) {
                    path.moveTo(px, py)
                    hasPoints = true
                } else {
                    path.lineTo(px, py)
                }
            }
            if (hasPoints) {
                val waterPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (z >= 14) 8f else 4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = if (isDarkMode) Color.parseColor("#1B2F44") else Color.parseColor("#AEE3FE")
                    isAntiAlias = true
                }
                canvas.drawPath(path, waterPaint)
            }
        }

        // Draw Selat Madura (Sea) polygon on East & North East
        val seaPath = Path()
        val p1 = PointD(-7.2200, 112.8150)
        val p2 = PointD(-7.2200, 112.8500)
        val p3 = PointD(-7.1400, 112.8500)
        val p4 = PointD(-7.1400, 112.7750)
        val p5 = PointD(-7.1850, 112.7750)
        
        seaPath.moveTo(toPxX(p1.lng), toPxY(p1.lat))
        seaPath.lineTo(toPxX(p2.lng), toPxY(p2.lat))
        seaPath.lineTo(toPxX(p3.lng), toPxY(p3.lat))
        seaPath.lineTo(toPxX(p4.lng), toPxY(p4.lat))
        seaPath.lineTo(toPxX(p5.lng), toPxY(p5.lat))
        seaPath.close()

        val seaPaint = Paint().apply {
            style = Paint.Style.FILL
            color = if (isDarkMode) Color.parseColor("#152538") else Color.parseColor("#BFE2F8")
            isAntiAlias = true
        }
        canvas.drawPath(seaPath, seaPaint)

        // 3. Draw Roads (Minor roads first)
        roads.filter { !it.isHighway }.forEach { road ->
            val path = Path()
            var hasPoints = false
            road.points.forEachIndexed { idx, pt ->
                val px = toPxX(pt.lng)
                val py = toPxY(pt.lat)
                if (idx == 0) {
                    path.moveTo(px, py)
                    hasPoints = true
                } else {
                    path.lineTo(px, py)
                }
            }
            if (hasPoints) {
                val roadColor = if (isDarkMode) Color.parseColor("#252E3C") else Color.parseColor("#FFFFFF")
                val roadOutlineColor = if (isDarkMode) Color.parseColor("#1B222D") else Color.parseColor("#E0E4EB")
                
                // Draw outline
                val paintOutline = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (z >= 14) 7f else 4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = roadOutlineColor
                    isAntiAlias = true
                }
                canvas.drawPath(path, paintOutline)

                // Draw core
                val paintCore = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (z >= 14) 4f else 2f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = roadColor
                    isAntiAlias = true
                }
                canvas.drawPath(path, paintCore)
            }
        }

        // Draw Highways (Arteries with green/orange colors)
        roads.filter { it.isHighway }.forEach { road ->
            val path = Path()
            var hasPoints = false
            road.points.forEachIndexed { idx, pt ->
                val px = toPxX(pt.lng)
                val py = toPxY(pt.lat)
                if (idx == 0) {
                    path.moveTo(px, py)
                    hasPoints = true
                } else {
                    path.lineTo(px, py)
                }
            }
            if (hasPoints) {
                val highwayCoreColor = if (isDarkMode) Color.parseColor("#00AA13") else Color.parseColor("#00C213") // Gojek Green!
                val highwayOutlineColor = if (isDarkMode) Color.parseColor("#1D3322") else Color.parseColor("#AEE2B5")

                // Outline
                val paintOutline = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (z >= 14) 10f else 6f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = highwayOutlineColor
                    isAntiAlias = true
                }
                canvas.drawPath(path, paintOutline)

                // Core
                val paintCore = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (z >= 14) 6f else 3f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = highwayCoreColor
                    isAntiAlias = true
                }
                canvas.drawPath(path, paintCore)
            }
        }

        // 4. Draw Labels & Text (Only at zoom levels 13+)
        if (z >= 13) {
            val textPaint = Paint().apply {
                color = if (isDarkMode) Color.parseColor("#A0ABC0") else Color.parseColor("#34495E")
                textSize = if (z == 15) 12f else 10f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val shadowPaint = Paint().apply {
                color = if (isDarkMode) Color.parseColor("#E0000000") else Color.parseColor("#E0FFFFFF")
                textSize = if (z == 15) 12f else 10f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

            // Draw Zone Labels
            zones.forEach { zone ->
                // Calculate average center
                val avgLat = zone.points.map { it.lat }.average()
                val avgLng = zone.points.map { it.lng }.average()

                val px = toPxX(avgLng)
                val py = toPxY(avgLat)

                if (px in 10f..(width - 10f) && py in 10f..(height - 10f)) {
                    val label = zone.name
                    canvas.drawText(label, px, py, shadowPaint)
                    canvas.drawText(label, px, py, textPaint.apply {
                        color = if (isDarkMode) Color.parseColor("#80C392") else Color.parseColor("#1E5F2F")
                    })
                }
            }

            // Draw Landmark Labels
            landmarks.forEach { l ->
                val px = toPxX(l.lng)
                val py = toPxY(l.lat)

                if (px in 10f..(width - 10f) && py in 10f..(height - 10f)) {
                    // Circle indicator
                    val pinPaint = Paint().apply {
                        color = if (l.type == "station") Color.parseColor("#3498DB") else Color.parseColor("#E74C3C")
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawCircle(px, py, 4f, pinPaint)
                    canvas.drawCircle(px, py, 5f, Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                        isAntiAlias = true
                    })

                    // Custom label text
                    val label = l.name
                    canvas.drawText(label, px, py - 8f, shadowPaint)
                    canvas.drawText(label, px, py - 8f, textPaint.apply {
                        color = if (isDarkMode) Color.WHITE else Color.parseColor("#2C3E50")
                    })
                }
            }
        }

        // Save tile to file
        FileOutputStream(tileFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
    }
}
