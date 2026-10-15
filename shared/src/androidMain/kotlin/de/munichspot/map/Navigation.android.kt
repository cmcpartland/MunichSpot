package de.munichspot.map

import de.munichspot.data.Poi

actual fun navigationUri(poi: Poi): String =
    "https://www.google.com/maps/dir/?api=1&destination=${poi.lat},${poi.lon}"
