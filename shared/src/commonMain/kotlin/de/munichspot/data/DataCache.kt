package de.munichspot.data

import de.munichspot.Log
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

internal expect fun cacheDir(): String?

/** A downloaded file in the cache directory with its ETag next to it. Failures are ignored: the cache is only an optimisation. */
internal class DataCache(private val fileName: String, private val dir: () -> String? = ::cacheDir) {
    val name = fileName.substringBeforeLast('.')
    private val etagName = "$name.etag"

    private fun path(name: String) = dir()?.let { Path(it, name) }

    private fun deleteQuietly(path: Path?) {
        try {
            path?.let { SystemFileSystem.delete(it, mustExist = false) }
        } catch (e: Exception) {
            Log.w("DataCache", "delete failed: $fileName", e)
        }
    }

    // The etag is written only after the cache write succeeded, so an etag never outlives or precedes its data.
    fun save(text: String, etag: String?) {
        deleteQuietly(path(etagName))
        try {
            val target = path(fileName) ?: return
            val tmp = path("$fileName.tmp") ?: return
            SystemFileSystem.sink(tmp).buffered().use { it.writeString(text) }
            SystemFileSystem.atomicMove(tmp, target)
            val etagFile = path(etagName)
            if (etag != null && etagFile != null) SystemFileSystem.sink(etagFile).buffered().use { it.writeString(etag) }
        } catch (e: Exception) {
            Log.w("DataCache", "write failed: $fileName", e)
            deleteQuietly(path(etagName))
        }
    }

    /** The etag of the cached download, or null if there is none or no cache file to go with it. */
    fun readEtag(): String? = try {
        val cache = path(fileName)?.takeIf { SystemFileSystem.exists(it) }
        path(etagName)?.takeIf { cache != null && SystemFileSystem.exists(it) }
            ?.let { SystemFileSystem.source(it).buffered().use { s -> s.readString() } }
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w("DataCache", "etag read failed: $fileName", e)
        null
    }

    fun read(): String? = try {
        path(fileName)?.takeIf { SystemFileSystem.exists(it) }?.let { SystemFileSystem.source(it).buffered().use { s -> s.readString() } }
    } catch (e: Exception) {
        Log.w("DataCache", "read failed: $fileName", e)
        null
    }

    fun delete() {
        deleteQuietly(path(fileName))
        deleteQuietly(path(etagName))
    }
}
