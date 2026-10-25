package de.munichspot

internal expect object Log {
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String, t: Throwable? = null)
}
