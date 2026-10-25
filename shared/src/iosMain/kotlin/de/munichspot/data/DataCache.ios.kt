package de.munichspot.data

import de.munichspot.Log
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

private val dir: String? by lazy { createDir()?.also(::migrateFromCaches) }

internal actual fun cacheDir(): String? = dir

@OptIn(ExperimentalForeignApi::class)
private fun userDir(directory: ULong) =
    NSFileManager.defaultManager.URLsForDirectory(directory, NSUserDomainMask).firstOrNull() as? NSURL

// Application Support is not purged by the system like Caches; the data is re-downloadable, so it is excluded from backups.
@OptIn(ExperimentalForeignApi::class)
private fun createDir(): String? {
    val url = userDir(NSApplicationSupportDirectory) ?: return null
    val path = url.path ?: return null
    if (!NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)) {
        Log.w("DataCache", "cannot create cache directory")
        return null
    }
    if (!url.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)) {
        Log.w("DataCache", "cannot exclude cache directory from backup")
    }
    return path
}

@OptIn(ExperimentalForeignApi::class)
private fun migrateFromCaches(target: String) {
    val old = userDir(NSCachesDirectory)?.path ?: return
    val fm = NSFileManager.defaultManager
    fun move(name: String): Boolean {
        val from = "$old/$name"
        val to = "$target/$name"
        if (!fm.fileExistsAtPath(from)) return false
        if (fm.fileExistsAtPath(to)) return true
        return fm.moveItemAtPath(from, toPath = to, error = null).also { if (!it) Log.w("DataCache", "migration failed: $name") }
    }
    for ((data, etag) in listOf("pois-latest.geojson" to "pois-latest.etag", "places-latest.json" to "places-latest.etag")) {
        if (move(data)) move(etag)
    }
}
