package de.munichspot.data

import de.munichspot.shared.resources.Res
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

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

const val PLACES_URL = "https://cmcpartland.github.io/MunichSpot/places.json"

private const val MIN_PLACES = 5000

private val cache = DataCache("places-latest.json")

class PlaceData(val places: List<Place>, val generated: Instant?) {
    val index = PlaceIndex(places)
}

// Streets, parks, squares, stations, landmarks and districts: © OpenStreetMap contributors (ODbL), built by tools/data/build_places.py
// The bundled copy is the base; a downloaded copy replaces it only if its `generated` is strictly newer.
suspend fun loadStartupPlaces(): PlaceData = withContext(Dispatchers.Default) {
    val bundled = try {
        parsePlaceData(Res.readBytes("files/places.json").decodeToString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PlaceData(emptyList(), null)
    }
    loadCached(cache) { text ->
        parsePlaceData(text).takeIf { it.isPlausible() && it.isNewerThan(bundled.generated) }
    } ?: bundled
}

/** Silent: a failed refresh just keeps the current data. */
suspend fun fetchLatestPlaces(current: Instant?): FetchResult<PlaceData> =
    fetchConditional(PLACES_URL, cache, conditional = true, isNewer = { it.isNewerThan(current) }) { text ->
        parsePlaceData(text).takeIf { it.isPlausible() }
    }

private fun PlaceData.isPlausible() = places.size >= MIN_PLACES

private fun PlaceData.isNewerThan(other: Instant?) = generated != null && (other == null || generated > other)

// Accepts `{"generated": ..., "places": [...]}` and, defensively, a bare array.
internal fun parsePlaceData(json: String): PlaceData {
    val root = Json.parseToJsonElement(json)
    val rows = if (root is JsonArray) root else root.jsonObject.getValue("places").jsonArray
    val generated = (root as? JsonObject)?.get("generated")?.jsonPrimitive?.contentOrNull
        ?.let { try { Instant.parse(it) } catch (e: IllegalArgumentException) { null } }
    val places = rows.mapNotNull { row ->
        val a = row.jsonArray
        val kind = PlaceKind.entries.firstOrNull { it.name == a[1].jsonPrimitive.content.uppercase() } ?: return@mapNotNull null
        Place(a[0].jsonPrimitive.content, kind, a[2].jsonPrimitive.doubleOrNull ?: return@mapNotNull null, a[3].jsonPrimitive.doubleOrNull ?: return@mapNotNull null)
    }
    return PlaceData(places, generated)
}
