package de.publicpoint

internal var debuggable = false

internal actual object Log {
    actual fun d(tag: String, msg: String) {
        if (debuggable) android.util.Log.d(tag, msg)
    }

    actual fun w(tag: String, msg: String, t: Throwable?) {
        android.util.Log.w(tag, msg, t)
    }
}
