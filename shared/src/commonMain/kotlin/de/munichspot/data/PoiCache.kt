package de.munichspot.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

private const val CACHE_FILE = "pois-latest.geojson"
private const val ETAG_FILE = "pois-latest.etag"

internal expect fun poiCacheDir(): String?

private fun cachePath(suffix: String = "") = poiCacheDir()?.let { Path(it, CACHE_FILE + suffix) }

private fun etagPath() = poiCacheDir()?.let { Path(it, ETAG_FILE) }

private fun deleteQuietly(path: Path?) {
    try {
        path?.let { SystemFileSystem.delete(it, mustExist = false) }
    } catch (e: Exception) {
    }
}

// Failures are ignored: the cache is only an optimisation.
// The etag is written only after the cache write succeeded, so an etag never outlives or precedes its data.
internal fun saveCachedPois(text: String, etag: String?) {
    deleteQuietly(etagPath())
    try {
        val target = cachePath() ?: return
        val tmp = cachePath(".tmp") ?: return
        SystemFileSystem.sink(tmp).buffered().use { it.writeString(text) }
        SystemFileSystem.atomicMove(tmp, target)
        val etagFile = etagPath()
        if (etag != null && etagFile != null) SystemFileSystem.sink(etagFile).buffered().use { it.writeString(etag) }
    } catch (e: Exception) {
        deleteQuietly(etagPath())
    }
}

/** The etag of the cached download, or null if there is none or no cache file to go with it. */
internal fun readCachedEtag(): String? = try {
    val cache = cachePath()?.takeIf { SystemFileSystem.exists(it) }
    etagPath()?.takeIf { cache != null && SystemFileSystem.exists(it) }
        ?.let { SystemFileSystem.source(it).buffered().use { s -> s.readString() } }
        ?.takeIf { it.isNotBlank() }
} catch (e: Exception) {
    null
}

internal fun readCachedPoisText(): String? = try {
    cachePath()?.takeIf { SystemFileSystem.exists(it) }?.let { SystemFileSystem.source(it).buffered().use { s -> s.readString() } }
} catch (e: Exception) {
    null
}

internal fun deleteCachedPois() {
    deleteQuietly(cachePath())
    deleteQuietly(etagPath())
}
