package de.munichspot.map

import de.munichspot.data.Poi

actual fun navigationUri(poi: Poi): String {
    val label = poi.name.map { c ->
        if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") c.toString()
        else c.toString().encodeToByteArray().joinToString("") { "%" + (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }
    }.joinToString("")
    return "geo:0,0?q=${poi.lat},${poi.lon}($label)"
}
