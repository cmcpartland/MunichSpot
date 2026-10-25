package de.munichspot.map

import androidx.compose.ui.platform.UriHandler
import de.munichspot.Log
import de.munichspot.data.Poi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

internal actual fun openDirections(poi: Poi, uriHandler: UriHandler, onFailed: () -> Unit) {
    val c = "${poi.lat},${poi.lon}"
    tryOpen(
        listOf(
            "geo-navigation:///directions?destination=$c",
            "maps://?daddr=$c",
            "comgooglemaps://?daddr=$c",
            "https://maps.apple.com/?daddr=$c",
        ),
        onFailed,
    )
}

private fun tryOpen(urls: List<String>, onFailed: () -> Unit) {
    val url = urls.firstOrNull()?.let { NSURL.URLWithString(it) } ?: run {
        Log.w("Navigation", "no app could open directions")
        return onFailed()
    }
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>()) { ok ->
        if (!ok) tryOpen(urls.drop(1), onFailed)
    }
}
