package com.mediadeck.app.util.zip

import android.util.Log
import com.mediadeck.app.util.cache.CacheEntryLock
import com.mediadeck.app.util.cache.DiskLruCache
import java.io.File
import java.io.IOException
import java.security.MessageDigest


sealed class ExtractResult {
    data class Success(val file: File) : ExtractResult()
    object NotFound : ExtractResult()
    object SourceMissing : ExtractResult()
    object Corrupted : ExtractResult()
    data class Error(val message: String) : ExtractResult()
}


class PageExtractor(
    private val cache: DiskLruCache,
    private val lock: CacheEntryLock
) {
    private val TAG = "PageExtractor"

    suspend fun extractPage(zipPath: String, entryName: String): ExtractResult {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            Log.w(TAG, "Source ZIP missing: $zipPath")
            return ExtractResult.SourceMissing
        }

        val cacheKey = generateCacheKey(zipPath, entryName)

        cache.get(cacheKey)?.let {
            if (it.exists() && it.length() > 0L) {
                return ExtractResult.Success(it)
            }
        }

        return lock.withLock(cacheKey) {
            cache.get(cacheKey)?.let {
                if (it.exists() && it.length() > 0L) {
                    return@withLock ExtractResult.Success(it)
                } else if (it.exists()) {
                    Log.w(TAG, "Cache file corrupted (0 bytes), invalidating: $cacheKey")
                    cache.remove(cacheKey)
                }
            }

            Log.d(TAG, "Extracting page: $entryName from $zipPath")
            try {
                val reader = ZipEntryReader(zipFile)
                val cachedFile = cache.put(cacheKey) { output ->
                    reader.readEntry(entryName).use { input ->
                        input.copyTo(output)
                    }
                }

                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L) {
                    Log.i(TAG, "Successfully extracted to cache: $cacheKey")
                    ExtractResult.Success(cachedFile)
                } else {
                    Log.e(TAG, "Failed to write to cache for key: $cacheKey")
                    ExtractResult.Error("Failed to save to cache")
                }
            } catch (e: ZipEntryNotFoundException) {
                Log.e(TAG, "Entry not found in ZIP: $entryName")
                ExtractResult.NotFound
            } catch (e: ZipCorruptedException) {
                Log.e(TAG, "Zip corrupted: $zipPath")
                ExtractResult.Corrupted
            } catch (e: IOException) {
                Log.e(TAG, "IO Error extracting $entryName: ${e.message}")
                ExtractResult.Error(e.message ?: "Unknown IO error")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error extracting $entryName", e)
                ExtractResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun generateCacheKey(path: String, entry: String): String {
        val input = "$path|$entry"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "fallback_${input.hashCode()}"
        }
    }
}
