package com.mediadeck.app.util.smb
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.codelibs.jcifs.smb.impl.SmbFile
import org.codelibs.jcifs.smb.SmbRandomAccess
import kotlinx.coroutines.*
import java.io.IOException
@OptIn(UnstableApi::class)
class SmbDataSource(private val context: Context) : BaseDataSource(true) {
    private var randomAccessFile: SmbRandomAccess? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var totalLength: Long = 0
    private var opened = false
    private var activeSmbUrl: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val INITIAL_BUFFER_SIZE = 256 * 1024
    private val STEADY_BUFFER_SIZE = 2 * 1024 * 1024
    private var nextBufferSize = INITIAL_BUFFER_SIZE
    private var currentBuffer = ByteArray(INITIAL_BUFFER_SIZE)
    private var currentBufferStart = -1L
    private var currentBufferLength = 0
    private var prefetchJob: Deferred<FetchResult?>? = null
    private var prefetchBuffer = ByteArray(INITIAL_BUFFER_SIZE)
    private var prefetchStartPos = -1L
    data class FetchResult(val data: ByteArray, val length: Int, val startPos: Long)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val uriStr = dataSpec.uri.toString()
        val smbUrl = com.mediadeck.app.util.media.MediaUtils.getSmbUrlFromUri(uriStr)
            ?: throw IOException("Invalid SMB URI: $uriStr")
        try {
            activeSmbUrl = smbUrl
            val file = runBlocking { SmbConnectionManager.getSmbFile(context, smbUrl) }
            val raf = file.openRandomAccess("r")
            randomAccessFile = raf
            totalLength = file.length()
            if (dataSpec.position > totalLength) {
                throw IOException("Position ${dataSpec.position} out of bounds")
            }
            raf.seek(dataSpec.position)
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                totalLength - dataSpec.position
            }
            currentBufferStart = -1L
            currentBufferLength = 0
            nextBufferSize = INITIAL_BUFFER_SIZE
            cancelPrefetch()
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            close()
            throw IOException("Failed to open SMB: ${e.message}", e)
        }
    }
    override fun read(targetBuffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened || length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val currentPos = try { raf.filePointer } catch (e: Exception) { return C.RESULT_END_OF_INPUT }
        if (currentPos < currentBufferStart || currentPos >= currentBufferStart + currentBufferLength) {
            val pfResult = runBlocking { prefetchJob?.await() }
            if (pfResult != null && currentPos == pfResult.startPos) {
                val oldBuffer = currentBuffer
                currentBuffer = pfResult.data
                currentBufferStart = pfResult.startPos
                currentBufferLength = pfResult.length
                prefetchBuffer = oldBuffer
                prefetchJob = null
            } else {
                cancelPrefetch()
                val readSize = Math.min(nextBufferSize.toLong(), minOf(totalLength - currentPos, bytesRemaining)).toInt()
                if (readSize <= 0) return C.RESULT_END_OF_INPUT
                if (currentBuffer.size < readSize) currentBuffer = ByteArray(readSize)
                raf.seek(currentPos)
                val read = try { raf.read(currentBuffer, 0, readSize) } catch (e: Exception) { -1 }
                if (read == -1) return C.RESULT_END_OF_INPUT
                currentBufferStart = currentPos
                currentBufferLength = read
                nextBufferSize = STEADY_BUFFER_SIZE
            }
        }
        val offsetInBuffer = (currentPos - currentBufferStart).toInt()
        val availableInBuffer = currentBufferLength - offsetInBuffer
        val bytesToCopy = Math.min(length, availableInBuffer)
        System.arraycopy(currentBuffer, offsetInBuffer, targetBuffer, offset, bytesToCopy)
        raf.seek(currentPos + bytesToCopy)
        if (prefetchJob == null && (offsetInBuffer + bytesToCopy) > currentBufferLength / 2) {
            triggerPrefetch(currentBufferStart + currentBufferLength)
        }
        bytesRemaining -= bytesToCopy
        bytesTransferred(bytesToCopy)
        return bytesToCopy
    }
    private fun triggerPrefetch(startPos: Long) {
        if (startPos >= totalLength || prefetchJob != null) return
        val smbUrl = activeSmbUrl ?: return
        val chunkSize = minOf(nextBufferSize.toLong(), totalLength - startPos).toInt()
        if (chunkSize <= 0) return
        if (prefetchBuffer.size < chunkSize) prefetchBuffer = ByteArray(chunkSize)
        prefetchStartPos = startPos
        prefetchJob = scope.async {
            try {
                val smbFile = SmbConnectionManager.getSmbFile(context, smbUrl)
                smbFile.openRandomAccess("r").use { raf ->
                    raf.seek(startPos)
                    val read = raf.read(prefetchBuffer, 0, chunkSize)
                    if (read != -1) FetchResult(prefetchBuffer, read, startPos) else null
                }
            } catch (e: Exception) {
                Log.e("SmbDataSource", "Prefetch failed at $startPos", e)
                null
            }
        }
    }
    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }
    override fun getUri(): Uri? = uri
    override fun close() {
        opened = false
        uri = null
        cancelPrefetch()
        try {
            scope.coroutineContext.cancelChildren()
        } catch (_: Exception) {}
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {
        } finally {
            randomAccessFile = null
            transferEnded()
        }
    }
}