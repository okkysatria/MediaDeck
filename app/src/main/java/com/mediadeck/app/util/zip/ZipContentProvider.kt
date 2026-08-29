package com.mediadeck.app.util.zip

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.util.cache.CacheEntryLock
import com.mediadeck.app.util.cache.SimpleDiskLruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ZipContentProvider : ContentProvider() {

    private lateinit var pageExtractor: PageExtractor
    private lateinit var repository: AppRepository
    private lateinit var cache: SimpleDiskLruCache
    private val scope = CoroutineScope(Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun appRepository(): AppRepository
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        
        cache = SimpleDiskLruCache(context.cacheDir, "zip_pages")
        pageExtractor = PageExtractor(cache, CacheEntryLock())
        
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ProviderEntryPoint::class.java)
        repository = entryPoint.appRepository()
        
        return true
    }

    override fun getType(uri: Uri): String? {
        val entry = uri.getQueryParameter("entry") ?: ""
        return when {
            entry.endsWith(".png", true) -> "image/png"
            entry.endsWith(".webp", true) -> "image/webp"
            entry.endsWith(".gif", true) -> "image/gif"
            else -> "image/jpeg"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val zipFilePath = uri.path ?: return null
        val entryName = uri.getQueryParameter("entry") ?: return null

        if (zipFilePath.contains("..")) {
            Log.e("ZipContentProvider", "Security violation: path contains '..'")
            return null
        }

        pruneCacheIfNecessary()

        return runBlocking {
            val result = pageExtractor.extractPage(zipFilePath, entryName)
            when (result) {
                is ExtractResult.Success -> {
                    ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                is ExtractResult.NotFound -> {
                    Log.e("ZipContentProvider", "Entry not found: $entryName in $zipFilePath")
                    null
                }
                is ExtractResult.SourceMissing -> {
                    Log.e("ZipContentProvider", "Zip file missing: $zipFilePath")
                    null
                }
                is ExtractResult.Corrupted -> {
                    Log.e("ZipContentProvider", "Zip or entry corrupted: $entryName")
                    null
                }
                is ExtractResult.Error -> {
                    Log.e("ZipContentProvider", "Extraction error: ${result.message}")
                    null
                }
            }
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
                Log.e("ZipContentProvider", "Failed to prune cache", e)
            } finally {
                isPruning.set(false)
            }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sa: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.mediadeck.app.zipreader"
        private val isPruning = AtomicBoolean(false)

        fun buildUri(zipPath: String, entryName: String): String {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(zipPath)
                .appendQueryParameter("entry", entryName)
                .build()
                .toString()
        }
    }
}
