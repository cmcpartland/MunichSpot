package de.munichspot.data

import de.munichspot.shared.resources.Res
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

enum class PlaceKind(val label: String) {
    STREET("Straße"),
    PARK("Park"),
    SQUARE("Platz"),
    STATION("Bahnhof"),
    LANDMARK("Sehenswürdigkeit"),
    DISTRICT("Stadtteil"),
}

data class Place(val name: String, val kind: PlaceKind, val lat: Double, val lon: Double)

internal val PlaceSaver = listSaver<Place?, Any>(
    save = { p -> if (p == null) emptyList() else listOf(p.name, p.kind.name, p.lat, p.lon) },
    restore = { l -> if (l.isEmpty()) null else Place(l[0] as String, PlaceKind.valueOf(l[1] as String), l[2] as Double, l[3] as Double) },
)

// Streets, parks, squares, stations, landmarks and districts: © OpenStreetMap contributors (ODbL), built by tools/data/build_places.py
suspend fun loadPlaces(): List<Place> = try {
    val text = Res.readBytes("files/places.json").decodeToString()
    withContext(Dispatchers.Default) {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { row ->
            val a = row.jsonArray
            val kind = PlaceKind.entries.firstOrNull { it.name == a[1].jsonPrimitive.content.uppercase() } ?: return@mapNotNull null
            Place(a[0].jsonPrimitive.content, kind, a[2].jsonPrimitive.doubleOrNull ?: return@mapNotNull null, a[3].jsonPrimitive.doubleOrNull ?: return@mapNotNull null)
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}
