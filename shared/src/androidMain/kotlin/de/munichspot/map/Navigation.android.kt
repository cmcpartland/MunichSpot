package de.munichspot.map

import androidx.compose.ui.platform.UriHandler
import de.munichspot.Log
import de.munichspot.data.Poi

internal actual fun openDirections(poi: Poi, uriHandler: UriHandler, onFailed: () -> Unit) {
    try {
        uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=${poi.lat},${poi.lon}")
    } catch (e: Exception) {
        Log.w("Navigation", "no app can open directions (${e::class.simpleName})")
        onFailed()
    }
}
