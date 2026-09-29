package com.mediadeck.app.util.media
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
object ThumbnailEventBus {
    private val _mediaThumbnailEvents = MutableSharedFlow<Long>(extraBufferCapacity = 128)
    val mediaThumbnailEvents: SharedFlow<Long> = _mediaThumbnailEvents.asSharedFlow()
    private val _folderThumbnailEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val folderThumbnailEvents: SharedFlow<String> = _folderThumbnailEvents.asSharedFlow()
    fun notifyThumbnailReady(mediaId: Long) {
        if (mediaId > 0L) {
            _mediaThumbnailEvents.tryEmit(mediaId)
        }
    }
    fun notifyFolderThumbnailReady(folderName: String) {
        if (folderName.isNotEmpty()) {
            _folderThumbnailEvents.tryEmit(folderName)
        }
    }
}