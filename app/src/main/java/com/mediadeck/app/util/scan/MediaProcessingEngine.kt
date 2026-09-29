package com.mediadeck.app.util.scan
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.media.MediaUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
object MediaProcessingEngine {
    private const val MAX_PROCESS_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 500L
    private const val FOLDER_BATCH_WORKERS = 5
    private const val SMB_WORKERS = 5
    private const val FAILED_TASK_TTL_MS = 10 * 60 * 1000L
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskChannel = Channel<ProcessingTask>(64)
    private val smbTaskChannel = Channel<ProcessingTask>(64)
    private val priorityChannel = Channel<ProcessingTask>(16)
    private val smbPriorityChannel = Channel<ProcessingTask>(32)
    private val folderChannel = Channel<FolderMosaicTask>(16)
    private val smbFolderChannel = Channel<FolderMosaicTask>(16)
    private val folderMediaBatchChannel = Channel<FolderMediaBatchTask>(Channel.UNLIMITED)
    private val smbFolderMediaBatchChannel = Channel<FolderMediaBatchTask>(Channel.UNLIMITED)
    private val queuedNormalUris = ConcurrentHashMap.newKeySet<String>()
    private val queuedPriorityUris = ConcurrentHashMap.newKeySet<String>()
    private val inFlightMediaUris = ConcurrentHashMap.newKeySet<String>()
    private val priorityCompletedUris = ConcurrentHashMap.newKeySet<String>()
    private val failedMediaUntil = ConcurrentHashMap<String, Long>()
    private val activeFolderKeys = ConcurrentHashMap.newKeySet<String>()
    private val activeFolderMediaBatchKeys = ConcurrentHashMap.newKeySet<String>()
    private fun mediaTaskKey(task: ProcessingTask): String = "${task.thumbnailVariant}|${task.uri}"
    private val maxParallelism = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 3)
    private val localSemaphore = Semaphore(maxParallelism)
    private val smbSemaphore = Semaphore(SMB_WORKERS)
    init {
        launchFolderBatchConsumer(folderMediaBatchChannel, maxParallelism)
        launchFolderBatchConsumer(smbFolderMediaBatchChannel, FOLDER_BATCH_WORKERS)
        repeat(maxParallelism) {
            engineScope.launch { runLocalWorker() }
        }
        repeat(SMB_WORKERS) {
            engineScope.launch { runSmbWorker() }
        }
    }
    private suspend fun runLocalWorker() {
        while (true) {
            if (ScannerStateManager.isMediaActive.value) ScannerStateManager.isMediaActive.first { !it }
            try {
                select<Unit> {
                    priorityChannel.onReceive { processQueuedTask(it, priority = true) }
                    folderChannel.onReceive { task ->
                        try { processFolderTask(task) } finally { activeFolderKeys.remove(task.folderKey) }
                    }
                    taskChannel.onReceive { processQueuedTask(it, priority = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MediaProcessingEngine", "Local worker error", e)
            }
        }
    }
    private suspend fun runSmbWorker() {
        while (true) {
            if (ScannerStateManager.isMediaActive.value) ScannerStateManager.isMediaActive.first { !it }
            try {
                select<Unit> {
                    smbPriorityChannel.onReceive { processQueuedTask(it, priority = true) }
                    smbFolderChannel.onReceive { task ->
                        try { processFolderTask(task) } finally { activeFolderKeys.remove(task.folderKey) }
                    }
                    smbTaskChannel.onReceive { processQueuedTask(it, priority = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MediaProcessingEngine", "SMB worker error", e)
            }
        }
    }
    private fun launchFolderBatchConsumer(channel: Channel<FolderMediaBatchTask>, workerCount: Int) {
        engineScope.launch {
            for (batch in channel) {
                try {
                    VideoThumbnailHelper.setFolderThumbnailProcessing(batch.folderKey, true)
                    val taskChannel = Channel<ProcessingTask>(Channel.UNLIMITED)
                    for (task in batch.tasks) taskChannel.send(task)
                    taskChannel.close()
                    coroutineScope {
                        repeat(workerCount) {
                            launch {
                                for (task in taskChannel) processFolderMediaTask(task)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MediaProcessingEngine", "Folder media batch failed: ${batch.folderKey}", e)
                } finally {
                    activeFolderMediaBatchKeys.remove(batch.folderKey)
                    VideoThumbnailHelper.setFolderThumbnailProcessing(batch.folderKey, false)
                }
            }
        }
    }
    private suspend fun processQueuedTask(task: ProcessingTask, priority: Boolean) {
        val key = mediaTaskKey(task)
        if (!priority) awaitFolderBatchTurn(task.thumbnailVariant)
        if (priority) queuedPriorityUris.remove(key) else queuedNormalUris.remove(key)
        if (!priority && priorityCompletedUris.remove(key)) return
        if (isSuppressed(task, force = false)) return
        if (!task.skipThumbnail && VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)) return
        if (!inFlightMediaUris.add(key)) return
        try {
            processTask(task, priority)
        } finally {
            inFlightMediaUris.remove(key)
        }
    }
    private suspend fun processFolderMediaTask(task: ProcessingTask) {
        while (true) {
            if (isSuppressed(task, force = false)) return
            if (ScannerStateManager.isMediaActive.value) {
                ScannerStateManager.isMediaActive.first { !it }
            }
            if (VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)) return
            val taskKey = mediaTaskKey(task)
            if (inFlightMediaUris.add(taskKey)) {
                try {
                    processTask(task)
                } finally {
                    inFlightMediaUris.remove(taskKey)
                }
                return
            }
            if (taskKey in queuedNormalUris) enqueuePriority(task)
            kotlinx.coroutines.delay(100L)
        }
    }
    data class ProcessingTask(
        val context: Context,
        val uri: String,
        val mediaId: Long,
        val settings: AppSettings,
        val skipThumbnail: Boolean = false,
        val thumbnailVariant: String = "explore",
        val onComplete: (resolution: String) -> Unit,
    )
    data class FolderMosaicTask(
        val context: Context,
        val folderKey: String,
        val mediaItems: List<Triple<String, Long, String>>,
        val settings: AppSettings
    )
    data class FolderMediaBatchTask(
        val folderKey: String,
        val tasks: List<ProcessingTask>,
    )
    private fun isSuppressed(task: ProcessingTask, force: Boolean): Boolean {
        val key = mediaTaskKey(task)
        val now = System.currentTimeMillis()
        val failedUntil = failedMediaUntil[key] ?: return false
        if (force || now >= failedUntil) {
            failedMediaUntil.remove(key, failedUntil)
            return false
        }
        return true
    }
    suspend fun enqueue(task: ProcessingTask, force: Boolean = false) {
        val taskKey = mediaTaskKey(task)
        if (isSuppressed(task, force) || taskKey in inFlightMediaUris || taskKey in queuedPriorityUris) return
        if (!queuedNormalUris.add(taskKey)) return
        priorityCompletedUris.remove(taskKey)
        try { channelFor(task).send(task) } catch (e: Exception) {
            queuedNormalUris.remove(taskKey)
            throw e
        }
    }
    suspend fun enqueuePriority(task: ProcessingTask, force: Boolean = false) {
        val taskKey = mediaTaskKey(task)
        if (isSuppressed(task, force) || taskKey in inFlightMediaUris || !queuedPriorityUris.add(taskKey)) return
        priorityCompletedUris.remove(taskKey)
        if (force) failedMediaUntil.remove(taskKey)
        try { priorityChannelFor(task).send(task) } catch (e: Exception) { queuedPriorityUris.remove(taskKey); throw e }
    }
    private fun channelFor(task: ProcessingTask): Channel<ProcessingTask> =
        if (MediaUtils.isSmbUri(task.uri)) smbTaskChannel else taskChannel
    private fun priorityChannelFor(task: ProcessingTask): Channel<ProcessingTask> =
        if (MediaUtils.isSmbUri(task.uri)) smbPriorityChannel else priorityChannel
    suspend fun enqueueFolderMosaic(task: FolderMosaicTask) {
        if (!activeFolderKeys.add(task.folderKey)) return
        val channel = if (task.mediaItems.firstOrNull()?.first?.let { MediaUtils.isSmbUri(it) } == true) smbFolderChannel else folderChannel
        try { channel.send(task) } catch (e: Exception) { activeFolderKeys.remove(task.folderKey); throw e }
    }
    suspend fun enqueueFolderMediaBatch(folderKey: String, tasks: List<ProcessingTask>, force: Boolean = false) {
        if (tasks.isEmpty()) return
        val firstTask = tasks.firstOrNull()
        val batchKey = "${firstTask?.thumbnailVariant ?: "explore"}|$folderKey"
        if (!activeFolderMediaBatchKeys.add(batchKey)) return
        try {
            if (force) tasks.forEach { failedMediaUntil.remove(mediaTaskKey(it)) }
            val eligibleTasks = if (force) tasks else tasks.filterNot { isSuppressed(it, false) }
            if (eligibleTasks.isEmpty()) {
                activeFolderMediaBatchKeys.remove(batchKey)
                return
            }
            val batch = FolderMediaBatchTask(batchKey, eligibleTasks)
            val channel = if (firstTask != null && MediaUtils.isSmbUri(firstTask.uri)) smbFolderMediaBatchChannel else folderMediaBatchChannel
            channel.send(batch)
        } catch (e: Exception) {
            activeFolderMediaBatchKeys.remove(batchKey)
            VideoThumbnailHelper.setFolderThumbnailProcessing("${tasks.firstOrNull()?.thumbnailVariant ?: "explore"}|$folderKey", false)
            throw e
        }
    }
    private suspend fun processTask(task: ProcessingTask, priority: Boolean = false) {
        if (!task.skipThumbnail) {
            VideoThumbnailHelper.setThumbnailProcessing(task.mediaId, task.thumbnailVariant, true)
        }
        try {
            var attempt = 0
            while (true) {
                try {
                    val permit = if (MediaUtils.isSmbUri(task.uri)) smbSemaphore else localSemaphore
                    val result = permit.withPermit { processTaskAttempt(task) }
                    runCatching { task.onComplete(result) }
                        .onFailure { Log.e("MediaProcessingEngine", "Callback gagal: ${task.uri}", it) }
                    failedMediaUntil.remove(mediaTaskKey(task))
                    if (priority) priorityCompletedUris.add(mediaTaskKey(task))
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val canRetry = isTransientIoFailure(e) && attempt < MAX_PROCESS_ATTEMPTS - 1
                    if (!canRetry) {
                        Log.e("MediaProcessingEngine", "Gagal permanen memproses ${task.uri}", e)
                        if (!task.skipThumbnail) {
                            failedMediaUntil[mediaTaskKey(task)] = System.currentTimeMillis() + FAILED_TASK_TTL_MS
                        }
                        runCatching { task.onComplete("") }
                        return
                    }
                    attempt++
                    kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                }
            }
        } finally {
            if (!task.skipThumbnail) {
                VideoThumbnailHelper.setThumbnailProcessing(task.mediaId, task.thumbnailVariant, false)
            }
        }
    }
    private fun isTransientIoFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is java.io.IOException || current is java.net.SocketTimeoutException ||
                current.javaClass.simpleName in setOf("SmbException", "TransportException")) return true
            current = current.cause
        }
        return false
    }
    private class PermanentThumbnailException(message: String) : Exception(message)
    private suspend fun processTaskAttempt(task: ProcessingTask): String {
            var resolution = ""
            var duration = 0L
            var width = 0
            var height = 0
            var hasThumbnail = task.skipThumbnail && VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)
            try {
                val isSmb = MediaUtils.isSmbUri(task.uri)
                val isImage = com.mediadeck.app.util.media.MediaUtils.isImageMime(task.context.contentResolver.getType(Uri.parse(task.uri))) ||
                               com.mediadeck.app.util.media.MediaUtils.isImageExt(com.mediadeck.app.util.media.MediaUtils.getFileExt(task.uri))
                if (isImage) {
                    if (!task.skipThumbnail) {
                        val thumb = VideoThumbnailHelper.loadThumbnail(
                            context = task.context,
                            uriString = task.uri,
                            mediaId = task.mediaId,
                            settings = task.settings,
                            variant = task.thumbnailVariant,
                            onImageMetadata = { imageWidth, imageHeight ->
                                width = imageWidth
                                height = imageHeight
                                resolution = "${width}x$height"
                            },
                        ) ?: throw PermanentThumbnailException("Image decoder returned no thumbnail")
                        hasThumbnail = VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)
                    }
                } else if (!task.skipThumbnail && !VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)) {
                    val bitmap = VideoThumbnailHelper.loadThumbnail(
                        context = task.context,
                        uriString = task.uri,
                        mediaId = task.mediaId,
                        settings = task.settings,
                        variant = task.thumbnailVariant,
                        onVideoMetadata = { durationMs, videoWidth, videoHeight ->
                            duration = durationMs
                            width = videoWidth
                            height = videoHeight
                            resolution = if (width > 0 && height > 0) "${width}x$height" else ""
                        },
                    )
                    if (bitmap == null) throw PermanentThumbnailException("Video decoder returned no thumbnail")
                    hasThumbnail = VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)
                    if (duration <= 0L || width <= 0 || height <= 0) {
                        val metadata = readVideoMetadata(task)
                        if (duration <= 0L) duration = metadata.first
                        if (resolution.isBlank()) resolution = metadata.second
                    }
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        if (isSmb) {
                            val smbFile = VideoThumbnailHelper.getSmbFileForUri(task.context, task.uri, task.settings)
                            if (smbFile == null) throw java.io.IOException("SMB video URI tidak valid")
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
                                } finally {
                                    smbSource.close()
                                }
                            }
                        } else {
                            val uri = Uri.parse(task.uri)
                            if (uri.scheme == "content") {
                                val descriptor = task.context.contentResolver.openFileDescriptor(uri, "r")
                                    ?: throw java.io.IOException("Tidak dapat membuka video: $uri")
                                descriptor.use { pfd ->
                                    retriever.setDataSource(pfd.fileDescriptor)
                                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                }
                            } else {
                                retriever.setDataSource(task.context, uri)
                                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            }
                            resolution = if (width > 0 && height > 0) "${width}x$height" else ""
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                    hasThumbnail = VideoThumbnailHelper.hasCachedThumbnail(task.context, task.mediaId, task.thumbnailVariant)
                }
                val repository = com.mediadeck.app.di.RepositoryEntryPoint.get(task.context).repository()
                if (task.thumbnailVariant == "gallery" && width > 0 && height > 0) {
                    repository.updateGalleryMetadata(task.uri, duration, width, height, hasThumbnail)
                } else if (task.thumbnailVariant != "gallery" && (duration > 0L || resolution.isNotBlank())) {
                    repository.updateMovieMetadata(task.uri, duration, resolution, hasThumbnail)
                }
                if (!task.skipThumbnail && !hasThumbnail) throw PermanentThumbnailException("Thumbnail file was not persisted")
                return resolution
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MediaProcessingEngine", "Gagal memproses ${task.uri}", e)
                throw e
            }
    }
    private suspend fun readVideoMetadata(task: ProcessingTask): Pair<Long, String> {
        val retriever = MediaMetadataRetriever()
        try {
            if (MediaUtils.isSmbUri(task.uri)) {
                val smbFile = VideoThumbnailHelper.getSmbFileForUri(task.context, task.uri, task.settings)
                    ?: throw java.io.IOException("SMB video URI tidak valid")
                VideoThumbnailHelper.SmbMediaDataSource(smbFile).use { source ->
                    retriever.setDataSource(source)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    return duration to if (width > 0 && height > 0) "${width}x$height" else ""
                }
            }
            val uri = Uri.parse(task.uri)
            if (uri.scheme == "content") {
                val descriptor = task.context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw java.io.IOException("Tidak dapat membuka video: $uri")
                descriptor.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    return duration to if (width > 0 && height > 0) "${width}x$height" else ""
                }
            }
            retriever.setDataSource(task.context, uri)
            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            return videoDuration to if (videoWidth > 0 && videoHeight > 0) "${videoWidth}x$videoHeight" else ""
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
    private suspend fun processFolderTask(task: FolderMosaicTask) {
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        try {
            task.mediaItems.firstOrNull()?.third?.let { awaitFolderBatchTurn(it) }
            Log.d("MediaProcessingEngine", "Generating mosaic for folder: ${task.folderKey}")
            for (item in task.mediaItems.take(4)) {
                val bmp = VideoThumbnailHelper.loadThumbnail(
                    task.context,
                    item.first,
                    item.second,
                    task.settings,
                    item.third,
                )
                if (bmp != null) bitmaps.add(bmp)
            }
            if (bitmaps.isNotEmpty()) {
                val mosaic = VideoThumbnailHelper.createFolderMosaic(bitmaps)
                try {
                    saveFolderThumbnailToDisk(task.context, mosaic, task.folderKey)
                    VideoThumbnailHelper.notifyFolderThumbnailReady(task.folderKey)
                    Log.d("MediaProcessingEngine", "Saved mosaic for folder: ${task.folderKey}")
                } finally {
                    mosaic.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessingEngine", "Gagal memproses mosaic folder ${task.folderKey}", e)
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
    private suspend fun awaitFolderBatchTurn(variant: String) {
        while (activeFolderMediaBatchKeys.any { it.startsWith("$variant|") }) {
            kotlinx.coroutines.delay(50L)
        }
    }
    private fun saveFolderThumbnailToDisk(context: Context, bmp: android.graphics.Bitmap, folderKey: String) {
        val folder = java.io.File(context.filesDir, "thumbnails")
        if (!folder.exists()) folder.mkdirs()
        val filename = VideoThumbnailHelper.getFolderCacheFilename(folderKey)
        val file = java.io.File(folder, filename)
        val tempFile = java.io.File(folder, "$filename.tmp")
        try {
            java.io.FileOutputStream(tempFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, VideoThumbnailHelper.THUMBNAIL_JPEG_QUALITY, out)
            }
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    Log.e("MediaProcessingEngine", "Failed to rename temp mosaic to $filename")
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessingEngine", "Gagal simpan mosaic folder $folderKey", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
