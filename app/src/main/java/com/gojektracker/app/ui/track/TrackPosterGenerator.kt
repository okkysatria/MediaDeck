package com.gojektracker.app.ui.track

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.toArgb
import com.gojektracker.app.data.TrackPoint
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale

/** Statistik ringkas yang ditampilkan di poster rute. */
data class RoutePosterStats(
    val totalDistanceKm: Double,
    val totalEarnings: Double,
    val totalDurationMinutes: Long,
    val tripsCount: Int
)

/** Toggle bagian mana yang mau ditampilkan di poster. */
data class RoutePosterOptions(
    val showDistance: Boolean = true,
    val showEarnings: Boolean = true,
    val showDuration: Boolean = true,
    val showTrips: Boolean = true,
    val driverName: String = ""
)

private const val CANVAS_W = 1080
private const val CANVAS_H = 1350

/**
 * Menggambar poster rute ke Bitmap lalu menyimpannya ke galeri, dan membagikannya
 * lewat share sheet bila diminta. Disederhanakan dari versi lama: rute selalu digambar
 * sebagai vektor di atas latar bertema (bukan screenshot tile peta asli), sehingga
 * tidak bergantung pada native map capture yang rawan gagal.
 */
fun generateAndShareRoutePoster(
    context: Context,
    trackPoints: List<TrackPoint>,
    theme: PosterTheme,
    stats: RoutePosterStats,
    options: RoutePosterOptions,
    selectedDate: String,
    shareAfterSave: Boolean
) {
    try {
        val bitmap = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, theme)
        drawRoute(canvas, trackPoints, theme)
        drawHeader(canvas, theme, selectedDate, options.driverName)
        drawStats(canvas, theme, stats, options)

        val fileName = "rute_$selectedDate.png"
        val savedUri = saveBitmapToGallery(context, bitmap, fileName)

        if (savedUri == null) {
            Toast.makeText(context, "Gagal menyimpan poster.", Toast.LENGTH_LONG).show()
            return
        }

        if (!shareAfterSave) {
            Toast.makeText(context, "Poster rute tersimpan di galeri.", Toast.LENGTH_LONG).show()
            return
        }

        shareBitmap(context, bitmap, selectedDate)
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membuat poster: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}

private fun drawBackground(canvas: Canvas, theme: PosterTheme) {
    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, CANVAS_H.toFloat(),
            theme.background.toArgb(),
            theme.cardBackground.toArgb(),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, CANVAS_W.toFloat(), CANVAS_H.toFloat(), bgPaint)
}

private fun drawRoute(canvas: Canvas, trackPoints: List<TrackPoint>, theme: PosterTheme) {
    val mapArea = RectF(60f, 220f, CANVAS_W - 60f, 980f)

    val cardPaint = Paint().apply {
        color = theme.cardBackground.toArgb()
        isAntiAlias = true
    }
    canvas.drawRoundRect(mapArea, 32f, 32f, cardPaint)

    if (trackPoints.size < 2) {
        val emptyPaint = Paint().apply {
            color = theme.textPrimary.toArgb()
            alpha = 120
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Tidak ada data rute", mapArea.centerX(), mapArea.centerY(), emptyPaint)
        return
    }

    val lats = trackPoints.map { it.lat }
    val lngs = trackPoints.map { it.lng }
    val minLat = lats.min(); val maxLat = lats.max()
    val minLng = lngs.min(); val maxLng = lngs.max()
    val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
    val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)

    val padding = 80f
    val drawableW = mapArea.width() - padding * 2
    val drawableH = mapArea.height() - padding * 2

    fun project(lat: Double, lng: Double): android.graphics.PointF {
        val x = mapArea.left + padding + ((lng - minLng) / lngSpan * drawableW).toFloat()
        // lat dibalik karena makin besar lat = makin ke atas di peta, sedang Y kanvas tumbuh ke bawah
        val y = mapArea.top + padding + ((maxLat - lat) / latSpan * drawableH).toFloat()
        return android.graphics.PointF(x, y)
    }

    val points = trackPoints.map { project(it.lat, it.lng) }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (p in points.drop(1)) lineTo(p.x, p.y)
    }

    val glowPaint = Paint().apply {
        color = theme.accent.toArgb()
        alpha = 80
        strokeWidth = 22f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    canvas.drawPath(path, glowPaint)

    val routePaint = Paint().apply {
        color = theme.accent.toArgb()
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    canvas.drawPath(path, routePaint)

    // Titik mulai (hijau) & selesai (merah)
    val startDot = Paint().apply { color = 0xFF4CAF50.toInt(); isAntiAlias = true }
    val finishDot = Paint().apply { color = 0xFFE53935.toInt(); isAntiAlias = true }
    val innerDot = Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true }
    canvas.drawCircle(points.first().x, points.first().y, 16f, startDot)
    canvas.drawCircle(points.first().x, points.first().y, 7f, innerDot)
    canvas.drawCircle(points.last().x, points.last().y, 16f, finishDot)
    canvas.drawCircle(points.last().x, points.last().y, 7f, innerDot)
}

private fun drawHeader(canvas: Canvas, theme: PosterTheme, selectedDate: String, driverName: String) {
    val titlePaint = Paint().apply {
        color = theme.textPrimary.toArgb()
        textSize = 48f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText(if (driverName.isNotBlank()) driverName else "Rute Hari Ini", 60f, 110f, titlePaint)

    val datePaint = Paint().apply {
        color = theme.textPrimary.toArgb()
        alpha = 160
        textSize = 30f
        isAntiAlias = true
    }
    canvas.drawText(selectedDate, 60f, 160f, datePaint)
}

private fun drawStats(canvas: Canvas, theme: PosterTheme, stats: RoutePosterStats, options: RoutePosterOptions) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
        maximumFractionDigits = 0
    }

    val statEntries = mutableListOf<Pair<String, String>>()
    if (options.showDistance) statEntries.add("JARAK" to String.format(Locale.US, "%.1f km", stats.totalDistanceKm))
    if (options.showEarnings) statEntries.add("PENDAPATAN" to currencyFormatter.format(stats.totalEarnings))
    if (options.showDuration) {
        val h = stats.totalDurationMinutes / 60
        val m = stats.totalDurationMinutes % 60
        statEntries.add("DURASI" to "${h}j ${m}m")
    }
    if (options.showTrips) statEntries.add("TRIP" to "${stats.tripsCount}")

    if (statEntries.isEmpty()) return

    val statsTop = 1020f
    val statsBottom = 1290f
    val cellWidth = (CANVAS_W - 120f) / statEntries.size

    for ((index, entry) in statEntries.withIndex()) {
        val cx = 60f + index * cellWidth + cellWidth / 2f

        val labelPaint = Paint().apply {
            color = theme.textPrimary.toArgb()
            alpha = 140
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
            isAntiAlias = true
        }
        canvas.drawText(entry.first, cx, statsTop + 40f, labelPaint)

        val valuePaint = Paint().apply {
            color = theme.accent.toArgb()
            textSize = 42f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(entry.second, cx, statsTop + 95f, valuePaint)
    }

    val dividerPaint = Paint().apply {
        color = theme.textPrimary.toArgb()
        alpha = 30
        strokeWidth = 2f
    }
    canvas.drawLine(60f, statsBottom, CANVAS_W - 60f, statsBottom, dividerPaint)
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GojekTracker")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.also {
            context.contentResolver.openOutputStream(it)?.use { os: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
        }
    } else {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GojekTracker")
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap, selectedDate: String) {
    val cacheDir = File(context.cacheDir, "shared_images")
    cacheDir.mkdirs()
    val cacheFile = File(cacheDir, "rute_share_$selectedDate.png")
    FileOutputStream(cacheFile).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
    val shareUri: Uri = FileProvider.getUriForFile(context, "com.gojektracker.app.fileprovider", cacheFile)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, shareUri)
        type = "image/png"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rute"))
}
