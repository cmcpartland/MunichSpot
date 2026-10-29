package de.publicpoint.data

import android.content.Context
import android.content.pm.ApplicationInfo
import de.publicpoint.debuggable

private var dir: String? = null

fun initDataCache(context: Context) {
    dir = context.applicationContext.filesDir.absolutePath
    debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

internal actual fun cacheDir(): String? = dir
