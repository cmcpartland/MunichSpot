package de.munichspot.map

import androidx.compose.ui.platform.UriHandler
import de.munichspot.data.Poi

internal actual fun openDirections(poi: Poi, uriHandler: UriHandler) =
    uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=${poi.lat},${poi.lon}")
