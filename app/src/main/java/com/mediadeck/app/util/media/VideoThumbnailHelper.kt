package com.mediadeck.app.util.media

import android.content.Context
import android.graphics.*
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.cache.CacheEntryLock
import com.mediadeck.app.util.smb.SmbConnectionManager

import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream

object VideoThumbnailHelper {

    private const val MAX_THUMB_DIMENSION = 640     
    private const val EXPLORE_THUMB_DIMENSION = 480  
    private val entryLock = CacheEntryLock()

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).toInt().coerceAtLeast(8 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val cores = Runtime.getRuntime().availableProcessors()
    private val generationSemaphore = Semaphore(cores.coerceIn(8, 24))
    private val smbSemaphore = Semaphore((cores * 2).coerceIn(10, 24))

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

    fun getCacheFilename(mediaId: Long, variant: String = "explore"): String {
        return when (variant) {
            "view" -> "vthumb_v1_view_$mediaId.jpg"
            else   -> "vthumb_v1_$mediaId.jpg"
        }
    }

    fun getFolderCacheFilename(folderName: String): String {
        val hash = (folderName.hashCode() and 0x7FFFFFFF).toString()
        return "fthumb_v1_$hash.jpg"
    }

    fun hasCachedThumbnail(context: Context, mediaId: Long): Boolean {
        if (mediaId <= 0L) return false
        val file = File(context.filesDir, "thumbnails/${getCacheFilename(mediaId, "explore")}")
        return file.isFile && file.length() > 0L
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
    ): Bitmap? = withContext(Dispatchers.IO) {
        val maxDimension = if (variant == "view") MAX_THUMB_DIMENSION else EXPLORE_THUMB_DIMENSION
        val cacheKey = "v1|$mediaId|$variant"

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

        if (variant == "view") {
            val exploreFilename = getCacheFilename(mediaId, "explore")
            val exploreCacheFile = File(thumbFolder, exploreFilename)
            if (exploreCacheFile.exists() && exploreCacheFile.length() > 0L) {
                val fallbackKey = "v1|$mediaId|explore"
                val bmp = memoryCache.get(fallbackKey) ?: decodeThumbnailFile(exploreCacheFile, EXPLORE_THUMB_DIMENSION)?.also {
                    memoryCache.put(fallbackKey, it)
                }
                if (bmp != null) return@withContext bmp
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

            generationSemaphore.withPermit {
                yield()

                val isSmb = uriString.startsWith("smb://") || uriString.startsWith("content://${com.mediadeck.app.util.smb.SmbContentProvider.AUTHORITY}")
                val isImage = com.mediadeck.app.util.media.MediaUtils.isImageMime(context.contentResolver.getType(Uri.parse(uriString))) ||
                               com.mediadeck.app.util.media.MediaUtils.isImageExt(com.mediadeck.app.util.media.MediaUtils.getFileExt(uriString))

                val generatedBmp = if (isImage) {
                    generateImageThumbnail(context, uriString, isSmb, maxDimension, settings)
                } else {
                    generateVideoThumbnail(context, uriString, isSmb, maxDimension, settings, durationMs = 0L)
                }

                if (generatedBmp != null) {
                    memoryCache.put(cacheKey, generatedBmp)
                    val tempFile = File(thumbFolder, "$cacheFilename.tmp")
                    try {
                        FileOutputStream(tempFile).use { out ->
                            val quality = if (variant == "view") 92 else 88
                            generatedBmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        }
                        if (cacheFile.exists()) cacheFile.delete()
                        tempFile.renameTo(cacheFile)
                    } catch (e: Exception) {
                        Log.e("VideoThumbnailHelper", "Gagal menyimpan thumbnail $uriString", e)
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
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
    ): Bitmap? {
        val bmp = try {
            if (isSmb) {
                smbSemaphore.withPermit {
                    val smbUrl = MediaUtils.getSmbUrlFromUri(uriString)
                    if (smbUrl != null) {
                        val smbFile = SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        smbFile.getInputStream().use { inputStream ->
                            BitmapFactory.decodeStream(inputStream, null, options)
                        }
                        options.apply {
                            inJustDecodeBounds = false
                            inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension, maxDimension)
                        }
                        smbFile.getInputStream().use { inputStream ->
                            BitmapFactory.decodeStream(inputStream, null, options)
                        }
                    } else null
                }
            } else {
                val uri = Uri.parse(uriString)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension, maxDimension)
                }
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val scaled = downscaleBitmap(bmp, maxDimension)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    private suspend fun generateVideoThumbnail(
        context: Context,
        uriString: String,
        isSmb: Boolean,
        maxDimension: Int,
        settings: AppSettings?,
        durationMs: Long = 0L,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            return if (isSmb) {
                smbSemaphore.withPermit {
                    val smbUrl = MediaUtils.getSmbUrlFromUri(uriString) ?: return@withPermit null
                    val smbFile = SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                    val smbSource = SmbMediaDataSource(smbFile)
                    try {
                        retriever.setDataSource(smbSource)
                        val finalDurationUs = if (durationMs > 0) durationMs * 1000 else {
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it * 1000 } ?: 0L
                        }
                        extractFrame(retriever, maxDimension, finalDurationUs)
                    } finally {
                        smbSource.close()
                    }
                }
            } else {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        val finalDurationUs = if (durationMs > 0) durationMs * 1000 else {
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it * 1000 } ?: 0L
                        }
                        extractFrame(retriever, maxDimension, finalDurationUs)
                    }
                } else {
                    retriever.setDataSource(context, uri)
                    val finalDurationUs = if (durationMs > 0) durationMs * 1000 else {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it * 1000 } ?: 0L
                    }
                    extractFrame(retriever, maxDimension, finalDurationUs)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailHelper", "Gagal extract data video: $uriString", e)
            return null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
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
        val isSmb = uriString.startsWith("smb://") || uriString.startsWith("content://${com.mediadeck.app.util.smb.SmbContentProvider.AUTHORITY}")
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L

        try {
            if (isSmb) {
                val smbUrl = MediaUtils.getSmbUrlFromUri(uriString)
                if (smbUrl != null) {
                    val smbFile = SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                    val smbSource = SmbMediaDataSource(smbFile)
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

    class SmbMediaDataSource(private val smbFile: SmbFile) : MediaDataSource() {
        private val raf = SmbRandomAccessFile(smbFile, "r")
        private val size = smbFile.length()
        private val lock = Any()

        private val bufferSize = 2 * 1024 * 1024 
        private val internalBuffer = ByteArray(bufferSize)
        private var bufferStartPosition: Long = -1
        private var bufferEndPosition: Long = -1

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int = synchronized(lock) {
            if (position >= this.size) return -1

            val bytesToReadActual = minOf(size.toLong(), this.size - position).toInt()
            if (bytesToReadActual <= 0) return -1

            if (bufferStartPosition != -1L && position >= bufferStartPosition && position + bytesToReadActual <= bufferEndPosition) {
                val bufferOffset = (position - bufferStartPosition).toInt()
                System.arraycopy(internalBuffer, bufferOffset, buffer, offset, bytesToReadActual)
                return bytesToReadActual
            }

            if (bytesToReadActual > bufferSize) {
                raf.seek(position)
                var totalReadDirect = 0
                while (totalReadDirect < bytesToReadActual) {
                    val read = try { raf.read(buffer, offset + totalReadDirect, bytesToReadActual - totalReadDirect) } catch(_: Exception) { -1 }
                    if (read == -1) break
                    totalReadDirect += read
                }
                return if (totalReadDirect > 0) totalReadDirect else -1
            }

            raf.seek(position)
            val refillSize = minOf(bufferSize.toLong(), this.size - position).toInt()
            var totalRefilled = 0
            while (totalRefilled < refillSize) {
                val read = try { raf.read(internalBuffer, totalRefilled, refillSize - totalRefilled) } catch(_: Exception) { -1 }
                if (read == -1) break
                totalRefilled += read
            }

            if (totalRefilled <= 0) return -1

            bufferStartPosition = position
            bufferEndPosition = position + totalRefilled

            val finalCopySize = minOf(bytesToReadActual, totalRefilled)
            System.arraycopy(internalBuffer, 0, buffer, offset, finalCopySize)
            return finalCopySize
        }

        override fun getSize(): Long = size

        override fun close() {
            try {
                raf.close()
            } catch (_: Exception) { }
        }
    }
}
