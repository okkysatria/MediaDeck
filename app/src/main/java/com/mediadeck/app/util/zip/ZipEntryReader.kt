package com.mediadeck.app.util.zip

import com.mediadeck.app.util.scan.NaturalOrderComparator
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ZipEntryNotFoundException(entryName: String) : IOException("Entry not found in ZIP: $entryName")
class ZipCorruptedException(cause: Throwable) : IOException("Zip file appears corrupted", cause)


class ZipEntryReader(private val zipFile: File) {

    fun readEntry(entryName: String): InputStream {
        if (!zipFile.exists()) {
            throw IOException("Zip file not found: ${zipFile.absolutePath}")
        }

        val zip = try {
            ZipFile(zipFile)
        } catch (e: ZipException) {
            throw ZipCorruptedException(e)
        } catch (e: Exception) {
            throw IOException("Failed to open ZipFile: ${e.message}", e)
        }

        try {
            val entry = zip.getEntry(entryName) ?: run {
                zip.close()
                throw ZipEntryNotFoundException(entryName)
            }
            
            return object : InputStream() {
                private val delegate = zip.getInputStream(entry)
                
                override fun read(): Int = delegate.read()
                override fun read(b: ByteArray): Int = delegate.read(b)
                override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
                override fun skip(n: Long): Long = delegate.skip(n)
                override fun available(): Int = delegate.available()
                override fun mark(readlimit: Int) = delegate.mark(readlimit)
                override fun reset() = delegate.reset()
                override fun markSupported(): Boolean = delegate.markSupported()

                override fun close() {
                    try {
                        delegate.close()
                    } finally {
                        zip.close()
                    }
                }
            }
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }
    
    fun listEntries(filter: (String) -> Boolean = { true }): List<String> {
        return try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && filter(it.name) }
                    .map { it.name }
                    .sortedWith(NaturalOrderComparator)
                    .toList()
            }
        } catch (e: ZipException) {
            throw ZipCorruptedException(e)
        }
    }
}
