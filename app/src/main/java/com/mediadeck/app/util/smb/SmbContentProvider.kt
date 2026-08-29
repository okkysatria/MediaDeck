package com.mediadeck.app.util.smb

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.util.cache.SimpleDiskLruCache
import jcifs.smb.SmbFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SmbContentProvider : ContentProvider() {

    private lateinit var cache: SimpleDiskLruCache
    private lateinit var repository: AppRepository
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val AUTHORITY = "com.mediadeck.app.smbprovider"
        private val isPruning = AtomicBoolean(false)
        private val openFileSemaphore = kotlinx.coroutines.sync.Semaphore(16)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun appRepository(): AppRepository
        fun settingsDao(): com.mediadeck.app.data.settings.SettingsDao
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        cache = SimpleDiskLruCache(context.cacheDir, "smb_files")
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ProviderEntryPoint::class.java)
        repository = entryPoint.appRepository()
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null

    override fun getType(uri: Uri): String? {
        val path = uri.path?.lowercase() ?: ""
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".mov") -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sa: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        try {
            val encodedPath = uri.encodedPath ?: return null
            var path = encodedPath.trimStart('/')
            if (path.isEmpty()) return null

            val atIndex = path.indexOf('@')
            val firstSlash = path.indexOf('/')
            if (atIndex != -1 && (firstSlash == -1 || atIndex < firstSlash)) {
                path = path.substring(atIndex + 1)
            }
            
            val smbUrl = "smb://$path"
            Log.d("SmbContentProvider", "Opening: $smbUrl")

            val cacheKey = "smb_" + uri.toString().hashCode()
            
            val cachedFile = cache.get(cacheKey)
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L) {
                return ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            pruneCacheIfNecessary()

            val settings = runBlocking { repository.getSettingsDirect() }

            return runBlocking {
                openFileSemaphore.withPermit {
                    try {
                        val smbFile = SmbConnectionManager.getSmbFile(context, smbUrl, settings)
                        if (!smbFile.exists()) {
                            Log.w("SmbContentProvider", "File not found: $smbUrl")
                            return@withPermit null
                        }
                        
                        val fileSize = smbFile.length()
                        val isVideo = getType(uri)?.startsWith("video") == true
                        
                        if (fileSize > 50 * 1024 * 1024L && isVideo) {
                            Log.w("SmbContentProvider", "File too large for provider: $fileSize bytes")
                            return@withPermit null
                        }

                        val resultFile = cache.put(cacheKey) { output ->
                            smbFile.inputStream.use { input ->
                                input.copyTo(output)
                            }
                        }

                        if (resultFile != null && resultFile.exists()) {
                            ParcelFileDescriptor.open(resultFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("SmbContentProvider", "Error reading $smbUrl: ${e.message}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmbContentProvider", "Error opening SMB file", e)
            return null
        }
    }


    private fun pruneCacheIfNecessary() {
        if (!isPruning.compareAndSet(false, true)) return
        
        scope.launch {
            try {
                val settings = repository.getCacheSettingsSync()
                val limit = settings.limitMB * 1024 * 1024L
                val target = settings.targetMB * 1024 * 1024L
                cache.prune(limit, target)
            } catch (e: Exception) {
                Log.e("SmbContentProvider", "Failed to prune cache", e)
            } finally {
                isPruning.set(false)
            }
        }
    }
}
