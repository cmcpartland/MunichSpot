package de.munichspot.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

private const val CACHE_FILE = "pois-latest.geojson"

internal expect fun poiCacheDir(): String?

private fun cachePath(suffix: String = "") = poiCacheDir()?.let { Path(it, CACHE_FILE + suffix) }

// Failures are ignored: the cache is only an optimisation.
internal fun saveCachedPois(text: String) {
    try {
        val target = cachePath() ?: return
        val tmp = cachePath(".tmp") ?: return
        SystemFileSystem.sink(tmp).buffered().use { it.writeString(text) }
        SystemFileSystem.atomicMove(tmp, target)
    } catch (e: Exception) {
    }
}

internal fun readCachedPoisText(): String? = try {
    cachePath()?.takeIf { SystemFileSystem.exists(it) }?.let { SystemFileSystem.source(it).buffered().use { s -> s.readString() } }
} catch (e: Exception) {
    null
}

internal fun deleteCachedPois() {
    try {
        cachePath()?.let { SystemFileSystem.delete(it, mustExist = false) }
    } catch (e: Exception) {
    }
}
