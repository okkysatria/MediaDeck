package com.mediadeck.app.util.cache

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.IOException

interface DiskLruCache {
    fun get(key: String): File?
    fun put(key: String, writer: (OutputStream) -> Unit): File?
    fun remove(key: String)
    fun clear()
    fun prune(limitBytes: Long, targetBytes: Long)
}

class SimpleDiskLruCache(
    private val cacheDir: File,
    private val namespace: String
) : DiskLruCache {
    private val TAG = "SimpleDiskLruCache"

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getFile(key: String): File {
        return File(cacheDir, "${namespace}_$key")
    }

    override fun get(key: String): File? {
        val file = getFile(key)
        if (file.exists() && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        return null
    }

    override fun put(key: String, writer: (OutputStream) -> Unit): File? {
        val finalFile = getFile(key)
        @Suppress("DEPRECATION")
        val tempFile = File(cacheDir, "${namespace}_$key.tmp.${Thread.currentThread().id}.${System.nanoTime()}")

        try {
            FileOutputStream(tempFile).use { output ->
                writer(output)
            }
            
            if (tempFile.exists()) {
                if (tempFile.renameTo(finalFile)) {
                    return finalFile
                } else {
                    Log.e(TAG, "Failed to rename temp file to final file: $key")
                }
            }
            return null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override fun remove(key: String) {
        getFile(key).delete()
    }

    override fun clear() {
        val files = cacheDir.listFiles { _, name -> name.startsWith("${namespace}_") }
        files?.forEach { it.delete() }
    }

    override fun prune(limitBytes: Long, targetBytes: Long) {
        val files = cacheDir.listFiles { _, name -> name.startsWith("${namespace}_") } ?: return
        var currentSize = files.sumOf { it.length() }

        if (currentSize > limitBytes) {
            val sorted = files.sortedBy { it.lastModified() }
            for (file in sorted) {
                if (currentSize <= targetBytes) break
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                }
            }
        }
    }
}
