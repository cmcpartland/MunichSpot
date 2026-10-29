package de.publicpoint

import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSLog

// Kotlin strings can't be passed as NSLog varargs, so the message is the (escaped) format itself.
private fun log(msg: String) = NSLog(msg.replace("%", "%%"))

@OptIn(ExperimentalNativeApi::class)
internal actual object Log {
    actual fun d(tag: String, msg: String) {
        if (Platform.isDebugBinary) log("$tag: $msg")
    }

    actual fun w(tag: String, msg: String, t: Throwable?) {
        log("$tag: $msg" + (t?.let { " (${it::class.simpleName}: ${it.message})" } ?: ""))
    }
}
