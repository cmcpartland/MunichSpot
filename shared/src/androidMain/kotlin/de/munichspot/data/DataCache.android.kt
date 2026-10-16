package de.munichspot.data

import android.content.Context

private var dir: String? = null

fun initDataCache(context: Context) {
    dir = context.applicationContext.filesDir.absolutePath
}

internal actual fun cacheDir(): String? = dir
