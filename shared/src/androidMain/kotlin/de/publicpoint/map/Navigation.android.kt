package de.publicpoint.map

import androidx.compose.ui.platform.UriHandler
import de.publicpoint.Log
import de.publicpoint.data.Poi

internal actual fun openDirections(poi: Poi, uriHandler: UriHandler, onFailed: () -> Unit) {
    try {
        uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=${poi.lat},${poi.lon}")
    } catch (e: Exception) {
        Log.w("Navigation", "no app can open directions (${e::class.simpleName})")
        onFailed()
    }
}
