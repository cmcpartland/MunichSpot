package de.munichspot.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun cacheDir(): String? =
    (NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).firstOrNull() as? NSURL)?.path
