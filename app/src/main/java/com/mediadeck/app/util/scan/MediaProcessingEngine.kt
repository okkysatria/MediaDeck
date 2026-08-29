package com.mediadeck.app.util.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.media.VideoThumbnailHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object MediaProcessingEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskChannel = Channel<ProcessingTask>(Channel.BUFFERED)
    private val priorityChannel = Channel<ProcessingTask>(Channel.BUFFERED)
    private val folderChannel = Channel<FolderMosaicTask>(Channel.BUFFERED)

    private val maxParallelism = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(4).coerceAtMost(12)
    private val concurrencySemaphore = Semaphore(maxParallelism)

    init {
        repeat(maxParallelism) {
            engineScope.launch {
                while (true) {
                    if (ScannerStateManager.isMediaActive.value) {
                        ScannerStateManager.isMediaActive.first { !it }
                    }

                    try {
                        val task: Any = priorityChannel.tryReceive().getOrNull() 
                            ?: folderChannel.tryReceive().getOrNull()
                            ?: select {
                                priorityChannel.onReceive { it as Any }
                                folderChannel.onReceive { it as Any }
                                taskChannel.onReceive { it as Any }
                            }
                        
                        when (task) {
                            is ProcessingTask -> processTask(task)
                            is FolderMosaicTask -> processFolderTask(task)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("MediaProcessingEngine", "Worker error", e)
                        continue
                    }
                }
            }
        }
    }

    data class ProcessingTask(
        val context: Context,
        val uri: String,
        val mediaId: Long,
        val settings: AppSettings,
        val skipThumbnail: Boolean = false,
        val onComplete: (resolution: String) -> Unit,
    )

    data class FolderMosaicTask(
        val context: Context,
        val folderName: String,
        val mediaItems: List<Pair<String, Long>>,
        val settings: AppSettings
    )

    suspend fun enqueue(task: ProcessingTask) {
        taskChannel.send(task)
    }

    suspend fun enqueuePriority(task: ProcessingTask) {
        priorityChannel.send(task)
    }

    suspend fun enqueueFolderMosaic(task: FolderMosaicTask) {
        folderChannel.send(task)
    }

    private suspend fun processTask(task: ProcessingTask) {
        concurrencySemaphore.withPermit {
            var resolution = ""
            var duration = 0L
            var width = 0
            var height = 0
            var hasThumbnail = false

            try {
                val isSmb = task.uri.startsWith("smb://") || task.uri.contains("smbprovider")
                val isImage = com.mediadeck.app.util.media.MediaUtils.isImageMime(task.context.contentResolver.getType(Uri.parse(task.uri))) ||
                               com.mediadeck.app.util.media.MediaUtils.isImageExt(com.mediadeck.app.util.media.MediaUtils.getFileExt(task.uri))

                if (isImage) {
                    if (!task.skipThumbnail) {
                        val thumb = VideoThumbnailHelper.loadThumbnail(task.context, task.uri, task.mediaId, task.settings, "explore")
                        if (thumb != null) {
                            hasThumbnail = true
                            VideoThumbnailHelper.loadThumbnail(task.context, task.uri, task.mediaId, task.settings, "view")
                        }
                    }

                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    if (isSmb) {
                        val smbUrl = com.mediadeck.app.util.media.MediaUtils.getSmbUrlFromUri(task.uri)
                        if (smbUrl != null) {
                            val smbFile = com.mediadeck.app.util.smb.SmbConnectionManager.getSmbFile(task.context, smbUrl, task.settings)
                            smbFile.getInputStream().use { input ->
                                BitmapFactory.decodeStream(input, null, options)
                            }
                        }
                    } else {
                        task.context.contentResolver.openInputStream(Uri.parse(task.uri))?.use { input ->
                            BitmapFactory.decodeStream(input, null, options)
                        }
                    }
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        width = options.outWidth
                        height = options.outHeight
                        resolution = "${width}x${height}"
                    }
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        if (isSmb) {
                            val smbFile = VideoThumbnailHelper.getSmbFileForUri(task.context, task.uri, task.settings)
                            if (smbFile != null) {
                                val smbSource = VideoThumbnailHelper.SmbMediaDataSource(smbFile)
                                try {
                                    retriever.setDataSource(smbSource)
                                    val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                    val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    width = wStr?.toIntOrNull() ?: 0
                                    height = hStr?.toIntOrNull() ?: 0
                                    duration = durStr?.toLongOrNull() ?: 0L
                                    resolution = if (width > 0 && height > 0) "${width}x$height" else ""
                                    if (!task.skipThumbnail) {
                                        val durationUs = duration * 1000
                                        val generatedBmp = extractFrameFromRetriever(retriever, 480, durationUs)
                                        if (generatedBmp != null) {
                                            saveThumbnailToDisk(task.context, generatedBmp, task.mediaId, "explore")
                                            hasThumbnail = true
                                            val viewBmp = extractFrameFromRetriever(retriever, 640, durationUs)
                                            if (viewBmp != null) saveThumbnailToDisk(task.context, viewBmp, task.mediaId, "view")
                                        }
                                    }
                                } finally {
                                    smbSource.close()
                                }
                            }
                        } else {
                            val uri = Uri.parse(task.uri)
                            if (uri.scheme == "content") {
                                task.context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                    retriever.setDataSource(pfd.fileDescriptor)
                                }
                            } else {
                                retriever.setDataSource(task.context, uri)
                            }
                            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            width = wStr?.toIntOrNull() ?: 0
                            height = hStr?.toIntOrNull() ?: 0
                            duration = durStr?.toLongOrNull() ?: 0L
                            resolution = if (width > 0 && height > 0) "${width}x$height" else ""
                            if (!task.skipThumbnail) {
                                val durationUs = duration * 1000
                                val generatedBmp = extractFrameFromRetriever(retriever, 480, durationUs)
                                if (generatedBmp != null) {
                                    saveThumbnailToDisk(task.context, generatedBmp, task.mediaId, "explore")
                                    hasThumbnail = true
                                    val viewBmp = extractFrameFromRetriever(retriever, 640, durationUs)
                                    if (viewBmp != null) saveThumbnailToDisk(task.context, viewBmp, task.mediaId, "view")
                                }
                            }
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
                
                val repository = com.mediadeck.app.di.RepositoryEntryPoint.get(task.context).repository()
                if (isImage) {
                    repository.updateGalleryMetadata(task.uri, 0L, width, height, hasThumbnail)
                } else {
                    repository.updateMovieMetadata(task.uri, duration, resolution, hasThumbnail)
                    repository.updateGalleryMetadata(task.uri, duration, width, height, hasThumbnail)
                }
            } catch (e: Exception) {
                Log.e("MediaProcessingEngine", "Gagal memproses ${task.uri}", e)
            } finally {
                task.onComplete(resolution)
            }
        }
    }

    private suspend fun processFolderTask(task: FolderMosaicTask) {
        concurrencySemaphore.withPermit {
            try {
                Log.d("MediaProcessingEngine", "Generating mosaic for folder: ${task.folderName}")
                val bitmaps = mutableListOf<android.graphics.Bitmap>()
                for (item in task.mediaItems.take(4)) {
                    val bmp = VideoThumbnailHelper.loadThumbnail(task.context, item.first, item.second, task.settings, "explore")
                    if (bmp != null) bitmaps.add(bmp)
                }

                if (bitmaps.isNotEmpty()) {
                    val mosaic = VideoThumbnailHelper.createFolderMosaic(bitmaps)
                    saveFolderThumbnailToDisk(task.context, mosaic, task.folderName)
                    Log.d("MediaProcessingEngine", "Saved mosaic for folder: ${task.folderName}")
                }
            } catch (e: Exception) {
                Log.e("MediaProcessingEngine", "Gagal memproses mosaic folder ${task.folderName}", e)
            }
        }
    }

    private fun extractFrameFromRetriever(retriever: MediaMetadataRetriever, maxDimension: Int, durationUs: Long): android.graphics.Bitmap? {
        val timeUs = if (durationUs > 0) (durationUs * 0.4).toLong() else 10_000_000L
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
                    ?: retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveThumbnailToDisk(context: Context, bmp: android.graphics.Bitmap, mediaId: Long, variant: String) {
        val folder = java.io.File(context.filesDir, "thumbnails")
        if (!folder.exists()) folder.mkdirs()
        val filename = VideoThumbnailHelper.getCacheFilename(mediaId, variant)
        val file = java.io.File(folder, filename)
        val tempFile = java.io.File(folder, "$filename.tmp")
        try {
            java.io.FileOutputStream(tempFile).use { out ->
                val quality = if (variant == "view") 92 else 88
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            }
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    Log.e("MediaProcessingEngine", "Failed to rename temp thumbnail to $filename")
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessingEngine", "Gagal simpan thumbnail $mediaId", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun saveFolderThumbnailToDisk(context: Context, bmp: android.graphics.Bitmap, folderName: String) {
        val folder = java.io.File(context.filesDir, "thumbnails")
        if (!folder.exists()) folder.mkdirs()
        val filename = VideoThumbnailHelper.getFolderCacheFilename(folderName)
        val file = java.io.File(folder, filename)
        val tempFile = java.io.File(folder, "$filename.tmp")
        try {
            java.io.FileOutputStream(tempFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    Log.e("MediaProcessingEngine", "Failed to rename temp mosaic to $filename")
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessingEngine", "Gagal simpan mosaic folder $folderName", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
