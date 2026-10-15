package de.munichspot.map

import de.munichspot.data.Poi

actual fun navigationUri(poi: Poi): String =
    "https://maps.apple.com/?daddr=${poi.lat},${poi.lon}"
