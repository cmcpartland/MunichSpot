package de.munichspot.data

import android.content.Context

private var cacheDir: String? = null

fun initPoiCache(context: Context) {
    cacheDir = context.applicationContext.filesDir.absolutePath
}

internal actual fun poiCacheDir(): String? = cacheDir
