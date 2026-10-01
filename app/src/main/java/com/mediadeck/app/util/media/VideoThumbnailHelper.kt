package com.mediadeck.app.util.media
import android.content.Context
import android.graphics.*
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.os.SystemClock
import com.mediadeck.app.BuildConfig
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.cache.CacheEntryLock
import com.mediadeck.app.util.smb.SmbConnectionManager
import org.codelibs.jcifs.smb.impl.SmbFile
import org.codelibs.jcifs.smb.SmbRandomAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
object VideoThumbnailHelper {
    private const val MAX_THUMB_DIMENSION = 640     
    private const val EXPLORE_THUMB_DIMENSION = MAX_THUMB_DIMENSION
    const val THUMBNAIL_JPEG_QUALITY = 78
    private val entryLock = CacheEntryLock()
    private val thumbnailGenerationVersions = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    private val thumbnailProcessingStates = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val folderThumbnailProcessingStates = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    fun folderThumbnailProcessingState(folderKey: String): StateFlow<Boolean> =
        folderThumbnailProcessingStates.computeIfAbsent(folderKey) { MutableStateFlow(false) }.asStateFlow()
    fun setFolderThumbnailProcessing(folderKey: String, processing: Boolean) {
        folderThumbnailProcessingStates.computeIfAbsent(folderKey) { MutableStateFlow(false) }.value = processing
    }
    fun thumbnailProcessingState(mediaId: Long, variant: String = "explore"): StateFlow<Boolean> =
        thumbnailProcessingStates.computeIfAbsent("$variant:$mediaId") { MutableStateFlow(false) }.asStateFlow()
    fun setThumbnailProcessing(mediaId: Long, variant: String, processing: Boolean) {
        thumbnailProcessingStates.computeIfAbsent("$variant:$mediaId") { MutableStateFlow(false) }.value = processing
    }
    fun mediaThumbnailVersion(mediaId: Long, variant: String = "explore"): StateFlow<Long> =
        versionFlow("media:$variant:$mediaId").asStateFlow()
    fun folderThumbnailVersion(folderKey: String): StateFlow<Long> = versionFlow("folder:$folderKey").asStateFlow()
    fun notifyThumbnailReady(mediaId: Long, variant: String = "explore") {
        thumbnailGenerationVersions["media:$variant:$mediaId"]?.let { it.value += 1L }
    }
    fun notifyFolderThumbnailReady(folderKey: String) {
        thumbnailGenerationVersions["folder:$folderKey"]?.let { it.value += 1L }
    }
    private fun versionFlow(key: String): MutableStateFlow<Long> =
        thumbnailGenerationVersions.computeIfAbsent(key) { MutableStateFlow(0L) }
    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).toInt().coerceAtLeast(8 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val localImageSemaphore = Semaphore(3)
    private val localVideoSemaphore = Semaphore(2)
    private val smbSemaphore = Semaphore(5)
    private fun downscaleBitmap(src: Bitmap, maxDimension: Int): Bitmap {
        val w = src.width
        val h = src.height
        if ((w <= maxDimension && h <= maxDimension) || w <= 0 || h <= 0) return src
        val scale = maxDimension.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return try {
            val scaled = Bitmap.createScaledBitmap(src, newW, newH, true)
            if (scaled !== src) src.recycle()
            scaled
        } catch (e: Exception) {
            src
        }
    }
    fun getCacheFilename(mediaId: Long, variant: String = "explore"): String =
        if (variant == "explore") "vthumb_v2_$mediaId.jpg" else "vthumb_v3_${variant}_$mediaId.jpg"
    fun getFolderCacheFilename(folderName: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(folderName.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "fthumb_v1_$hash.jpg"
    }
    fun getLegacyFolderCacheFilename(folderName: String): String {
        val hash = (folderName.hashCode() and 0x7FFFFFFF).toString()
        return "fthumb_v1_$hash.jpg"
    }
    fun hasCachedThumbnail(context: Context, mediaId: Long, variant: String = "explore"): Boolean {
        if (mediaId <= 0L) return false
        val file = File(context.filesDir, "thumbnails/${getCacheFilename(mediaId, variant)}")
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val isValid = options.outWidth > 0 && options.outHeight > 0
        if (!isValid) file.delete()
        return isValid
    }
    fun hasCachedFolderThumbnail(context: Context, folderKey: String, legacyFolderName: String? = null): Boolean {
        val folder = File(context.filesDir, "thumbnails")
        val candidates = buildList {
            add(File(folder, getFolderCacheFilename(folderKey)))
            if (!legacyFolderName.isNullOrBlank()) add(File(folder, getLegacyFolderCacheFilename(legacyFolderName)))
        }
        return candidates.any { file ->
            if (!file.isFile || file.length() <= 0L) return@any false
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val valid = options.outWidth > 0 && options.outHeight > 0
            if (!valid) file.delete()
            valid
        }
    }
    fun clearMemoryCache() {
        memoryCache.evictAll()
    }
    fun createFolderMosaic(bitmaps: List<Bitmap>, dimension: Int = EXPLORE_THUMB_DIMENSION): Bitmap {
        val result = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val half = dimension / 2
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(Color.DKGRAY)
        bitmaps.take(4).forEachIndexed { index, bitmap ->
            val left = (index % 2) * half
            val top = (index / 2) * half
            val bW = bitmap.width
            val bH = bitmap.height
            val scale = Math.max(half.toFloat() / bW, half.toFloat() / bH)
            val scaledW = (bW * scale).toInt()
            val scaledH = (bH * scale).toInt()
            val dx = (half - scaledW) / 2
            val dy = (half - scaledH) / 2
            val dst = Rect(left + dx, top + dy, left + dx + scaledW, top + dy + scaledH)
            canvas.save()
            canvas.clipRect(left, top, left + half, top + half)
            canvas.drawBitmap(bitmap, null, dst, paint)
            canvas.restore()
        }
        return result
    }
    private fun decodeThumbnailFile(file: File, maxDimension: Int = MAX_THUMB_DIMENSION): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension, maxDimension)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }
    private fun getThumbnailFolder(context: Context): File {
        val folder = File(context.filesDir, "thumbnails")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }
    suspend fun loadThumbnail(
        context: Context,
        uriString: String,
        mediaId: Long,
        settings: AppSettings? = null,
        variant: String = "explore",
        durationMs: Long = 0L,
        onVideoMetadata: ((durationMs: Long, width: Int, height: Int) -> Unit)? = null,
        onImageMetadata: ((width: Int, height: Int) -> Unit)? = null,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val maxDimension = MAX_THUMB_DIMENSION
        val cacheKey = "v3|$variant|$mediaId"
        memoryCache.get(cacheKey)?.let { return@withContext it }
        val cacheFilename = getCacheFilename(mediaId, variant)
        val thumbFolder = getThumbnailFolder(context)
        val cacheFile = File(thumbFolder, cacheFilename)
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            val bmp = decodeThumbnailFile(cacheFile, maxDimension)
            if (bmp != null) {
                memoryCache.put(cacheKey, bmp)
                return@withContext bmp
            } else {
                cacheFile.delete()
            }
        }
        return@withContext entryLock.withLock(cacheFilename) {
            memoryCache.get(cacheKey)?.let { return@withLock it }
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                decodeThumbnailFile(cacheFile, maxDimension)?.let {
                    memoryCache.put(cacheKey, it)
                    return@withLock it
                }
            }
            val isSmb = MediaUtils.isSmbUri(uriString)
            val isImage = com.mediadeck.app.util.media.MediaUtils.isImageMime(context.contentResolver.getType(Uri.parse(uriString))) ||
                com.mediadeck.app.util.media.MediaUtils.isImageExt(com.mediadeck.app.util.media.MediaUtils.getFileExt(uriString))
            val generationSemaphore = when {
                isSmb -> smbSemaphore
                isImage -> localImageSemaphore
                else -> localVideoSemaphore
            }
            generationSemaphore.withPermit {
                yield()
                val generatedRaw = if (isImage) {
                    generateImageThumbnail(context, uriString, isSmb, maxDimension, settings, onImageMetadata)
                } else {
                    generateVideoThumbnail(context, uriString, isSmb, maxDimension, settings, durationMs, onVideoMetadata)
                }
                val generatedBmp = generatedRaw?.let { downscaleBitmap(it, maxDimension) }
                if (generatedBmp != null) {
                    val tempFile = File(thumbFolder, "$cacheFilename.tmp")
                    var saved = false
                    val saveStartedAt = SystemClock.elapsedRealtime()
                    try {
                        FileOutputStream(tempFile).use { out ->
                            if (!generatedBmp.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, out)) {
                                throw java.io.IOException("Gagal mengompres thumbnail")
                            }
                        }
                        if (cacheFile.exists() && !cacheFile.delete()) {
                            throw java.io.IOException("Gagal mengganti thumbnail lama")
                        }
                        if (!tempFile.renameTo(cacheFile)) {
                            throw java.io.IOException("Gagal memindahkan thumbnail sementara")
                        }
                        saved = cacheFile.isFile && cacheFile.length() > 0L
                        if (!saved) throw java.io.IOException("File thumbnail kosong")
                    } catch (e: Exception) {
                        Log.e("VideoThumbnailHelper", "Gagal menyimpan thumbnail $uriString", e)
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                        if (isSmb) logSmbStage("compress+save", saveStartedAt)
                    }
                    if (saved) {
                        memoryCache.put(cacheKey, generatedBmp)
                        notifyThumbnailReady(mediaId, variant)
                    } else {
                        generatedBmp.recycle()
                        throw IOException("Thumbnail file could not be persisted")
                    }
                }
                generatedBmp
            }
        }
    }
    private suspend fun generateImageThumbnail(
        context: Context,
        uriString: String,
        isSmb: Boolean,
        maxDimension: Int,
        settings: AppSettings?,
        onImageMetadata: ((width: Int, height: Int) -> Unit)?,
    ): Bitmap? {
        val bmp = try {
            if (isSmb) {
                val smbUrl = MediaUtils.getSmbUrlFromUri(uriString) ?: return null
                val fileStartedAt = SystemClock.elapsedRealtime()
                val smbFile = try {
                    SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                } finally {
                    logSmbStage("image getSmbFile", fileStartedAt)
                }
                var smbBytes = 0L
                val decodeStartedAt = SystemClock.elapsedRealtime()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                smbFile.getInputStream().use { raw ->
                    BufferedInputStream(countingInputStream(raw) { smbBytes += it }, 1 shl 20).use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }
                if (options.outWidth > 0 && options.outHeight > 0) onImageMetadata?.invoke(options.outWidth, options.outHeight)
                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension, maxDimension)
                }
                val decodedBmp = smbFile.getInputStream().use { raw ->
                    BufferedInputStream(countingInputStream(raw) { smbBytes += it }, 1 shl 20).use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }
                logSmbStage("image bounds+decode", decodeStartedAt)
                if (BuildConfig.DEBUG) Log.d(TAG, "SMB image I/O uri=$uriString bytes=$smbBytes")
                decodedBmp
            } else {
                val uri = Uri.parse(uriString)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    BufferedInputStream(raw, 1 shl 20).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                }
                if (options.outWidth > 0 && options.outHeight > 0) onImageMetadata?.invoke(options.outWidth, options.outHeight)
                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension, maxDimension)
                }
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    BufferedInputStream(raw, 1 shl 20).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                }
            }
        } catch (e: Exception) {
            throw e
        } ?: return null
        return downscaleBitmap(bmp, maxDimension)
    }
    private suspend fun generateVideoThumbnail(
        context: Context,
        uriString: String,
        isSmb: Boolean,
        maxDimension: Int,
        settings: AppSettings?,
        durationMs: Long = 0L,
        onVideoMetadata: ((durationMs: Long, width: Int, height: Int) -> Unit)? = null,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            return if (isSmb) {
                val smbUrl = MediaUtils.getSmbUrlFromUri(uriString) ?: return null
                val fileStartedAt = SystemClock.elapsedRealtime()
                val smbFile = try {
                    SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                } finally {
                    logSmbStage("video getSmbFile", fileStartedAt)
                }
                val sourceStartedAt = SystemClock.elapsedRealtime()
                val smbSource = try {
                    SmbMediaDataSource(smbFile, SmbConnectionManager.openRandomAccess(smbFile))
                } finally {
                    logSmbStage("video open random-access source", sourceStartedAt)
                }
                try {
                    val setDataSourceStartedAt = SystemClock.elapsedRealtime()
                    try {
                        retriever.setDataSource(smbSource)
                    } finally {
                        logSmbStage("video setDataSource", setDataSourceStartedAt)
                    }
                    val metadataStartedAt = SystemClock.elapsedRealtime()
                    val finalDurationUs = try {
                        readVideoMetadata(retriever, durationMs, onVideoMetadata)
                    } finally {
                        logSmbStage("video read metadata", metadataStartedAt)
                    }
                    val frameStartedAt = SystemClock.elapsedRealtime()
                    val frame = try {
                        extractFrame(retriever, maxDimension, finalDurationUs)
                    } finally {
                        logSmbStage("video getScaledFrameAtTime", frameStartedAt)
                    }
                    frame
                } finally {
                    if (BuildConfig.DEBUG) Log.d(TAG, smbSource.ioSummary(uriString))
                    smbSource.close()
                }
            } else {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        val finalDurationUs = readVideoMetadata(retriever, durationMs, onVideoMetadata)
                        extractFrame(retriever, maxDimension, finalDurationUs)
                    }
                } else {
                    retriever.setDataSource(context, uri)
                    val finalDurationUs = readVideoMetadata(retriever, durationMs, onVideoMetadata)
                    extractFrame(retriever, maxDimension, finalDurationUs)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailHelper", "Gagal extract data video: $uriString", e)
            throw e
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
    private fun readVideoMetadata(
        retriever: MediaMetadataRetriever,
        knownDurationMs: Long,
        onVideoMetadata: ((durationMs: Long, width: Int, height: Int) -> Unit)?,
    ): Long {
        val durationMs = if (knownDurationMs > 0L && onVideoMetadata == null) {
            knownDurationMs
        } else {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: knownDurationMs
        }
        if (onVideoMetadata != null) {
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            onVideoMetadata(durationMs, width, height)
        }
        return durationMs * 1000L
    }
    private fun extractFrame(retriever: MediaMetadataRetriever, maxDimension: Int, durationUs: Long = 0L): Bitmap? {
        val timeUs = if (durationUs > 0) (durationUs * 0.4).toLong() else 10_000_000L
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
                    ?: retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }
    suspend fun extractDurationOnly(context: Context, uriString: String, settings: AppSettings? = null): Long = withContext(Dispatchers.IO) {
        val isSmb = MediaUtils.isSmbUri(uriString)
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        try {
            if (isSmb) {
                val smbUrl = MediaUtils.getSmbUrlFromUri(uriString)
                if (smbUrl != null) {
                    val smbFile = SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                    val smbSource = SmbMediaDataSource(smbFile, SmbConnectionManager.openRandomAccess(smbFile))
                    try {
                        retriever.setDataSource(smbSource)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        smbSource.close()
                    }
                }
            } else {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }
                } else {
                    retriever.setDataSource(context, uri)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailHelper", "Gagal extract durasi: $uriString", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        durationMs
    }
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    suspend fun getSmbFileForUri(context: Context, uriString: String, providedSettings: AppSettings? = null): SmbFile? {
        val smbUrl = MediaUtils.getSmbUrlFromUri(uriString) ?: return null
        return try {
            SmbConnectionManager.getSmbFile(context, smbUrl, providedSettings)
        } catch (e: Exception) {
            null
        }
    }
    class SmbMediaDataSource(private val smbFile: SmbFile, private val raf: SmbRandomAccess) : MediaDataSource() {
        private val size = smbFile.length()
        private val lock = Any()
        private val blockSize = 256 * 1024
        private val maxCachedBlocks = 16
        private val blocks = object : LinkedHashMap<Long, ByteArray>(maxCachedBlocks, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean =
                size > maxCachedBlocks
        }
        private var fetchedBlocks = 0L
        private var fetchedBytes = 0L
        private var closed = false
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int = synchronized(lock) {
            if (closed) throw IOException("SMB data source is closed")
            if (position < 0L || offset < 0 || size < 0 || offset > buffer.size - size) {
                throw IndexOutOfBoundsException("Invalid SMB read range")
            }
            if (size == 0) return 0
            if (position >= this.size) return -1
            val requestedBytes = minOf(size.toLong(), this.size - position).toInt()
            var copied = 0
            while (copied < requestedBytes) {
                val currentPosition = position + copied
                val blockStart = (currentPosition / blockSize) * blockSize
                val block = blocks[blockStart] ?: fetchBlock(blockStart).also { blocks[blockStart] = it }
                val inBlockOffset = (currentPosition - blockStart).toInt()
                val copyCount = minOf(requestedBytes - copied, block.size - inBlockOffset)
                if (copyCount <= 0) break
                System.arraycopy(block, inBlockOffset, buffer, offset + copied, copyCount)
                copied += copyCount
            }
            if (copied == 0) -1 else copied
        }
        private fun fetchBlock(blockStart: Long): ByteArray {
            val expectedBytes = minOf(blockSize.toLong(), size - blockStart).toInt()
            if (expectedBytes <= 0) return ByteArray(0)
            val data = ByteArray(expectedBytes)
            raf.seek(blockStart)
            var totalRead = 0
            while (totalRead < expectedBytes) {
                val read = raf.read(data, totalRead, expectedBytes - totalRead)
                if (read < 0) throw IOException("Short SMB block read at $blockStart: $totalRead/$expectedBytes")
                if (read == 0) throw IOException("SMB read made no progress at $blockStart")
                totalRead += read
                fetchedBytes += read
            }
            fetchedBlocks++
            return data
        }
        fun ioSummary(uri: String): String =
            "SMB thumbnail I/O uri=$uri blocks=$fetchedBlocks bytes=$fetchedBytes blockSize=$blockSize cacheBlocks=${blocks.size}"
        override fun getSize(): Long = size
        override fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                blocks.clear()
                try { raf.close() } catch (_: Exception) { }
            }
        }
    }
    private const val TAG = "VideoThumbnailHelper"
    private fun logSmbStage(stage: String, startedAt: Long) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$stage took ${SystemClock.elapsedRealtime() - startedAt}ms")
    }
    private fun countingInputStream(input: InputStream, onBytesRead: (Long) -> Unit): InputStream =
        object : FilterInputStream(input) {
            override fun read(): Int = super.read().also { if (it >= 0) onBytesRead(1L) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) onBytesRead(it.toLong()) }
        }
}
