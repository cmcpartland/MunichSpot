package de.munichspot.map

import androidx.compose.ui.platform.UriHandler
import de.munichspot.data.Poi

/** Opens directions to [poi]: Google Maps URL on Android; on iOS the default navigation app, then Apple Maps, Google Maps, the web. */
internal expect fun openDirections(poi: Poi, uriHandler: UriHandler)
