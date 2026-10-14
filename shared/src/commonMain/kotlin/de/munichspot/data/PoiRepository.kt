package de.munichspot.data

import de.munichspot.shared.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val LATEST_POIS_URL = "https://cmcpartland.github.io/MunichSpot/pois.geojson"

private const val MIN_LATEST_POIS = 1000

private val LEGACY_POST_BOX_NAME = Regex("^Briefkasten \\((.+)\\)$")

private val client by lazy { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 10_000 } } }

// Recycling islands: Landeshauptstadt München, AWM, opendata.muenchen.de (dl-by-de/2.0)
// Mailboxes: © OpenStreetMap contributors (ODbL)
// Drinking fountains, public toilets: Landeshauptstadt München, opendata.muenchen.de (dl-by-de/2.0)
suspend fun loadBundledPois(): List<Poi> =
    Res.readBytes("files/pois.geojson").decodeToString().let { withContext(Dispatchers.Default) { parsePois(it) } }

// Cached copy of the last download if it is valid, otherwise the bundled data.
suspend fun loadStartupPois(): List<Poi> = withContext(Dispatchers.Default) {
    val text = readCachedPoisText() ?: return@withContext loadBundledPois()
    val cached = try {
        parsePois(text).takeIf(::isPlausible)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    cached ?: loadBundledPois().also { deleteCachedPois() }
}

// Returns null on any failure or implausible data, so callers keep what they have.
suspend fun fetchLatestPois(): List<Poi>? = try {
    val response = client.get(LATEST_POIS_URL)
    if (!response.status.isSuccess()) null
    else {
        val text = response.bodyAsText()
        withContext(Dispatchers.Default) { parsePois(text).takeIf(::isPlausible)?.also { saveCachedPois(text) } }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

private fun isPlausible(pois: List<Poi>) =
    pois.size >= MIN_LATEST_POIS && PoiType.entries.all { type -> pois.any { it.type == type } }

internal fun parsePois(geoJson: String): List<Poi> =
    Json.parseToJsonElement(geoJson).jsonObject.getValue("features").jsonArray.mapNotNull { f ->
        val feature = f.jsonObject
        val properties = feature.getValue("properties").jsonObject
        val type = PoiType.entries.firstOrNull { it.name == properties.getValue("type").jsonPrimitive.content }
            ?: return@mapNotNull null
        val coordinates = feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
        val rawName = properties.getValue("name").jsonPrimitive.content
        val legacyTimes = if (type == PoiType.POST_BOX) LEGACY_POST_BOX_NAME.matchEntire(rawName)?.groupValues?.get(1) else null
        Poi(
            id = feature.getValue("id").jsonPrimitive.content,
            type = type,
            name = if (legacyTimes != null) "Briefkasten" else rawName,
            lat = coordinates[1].jsonPrimitive.content.toDouble(),
            lon = coordinates[0].jsonPrimitive.content.toDouble(),
            materials = properties["materials"]?.jsonArray.orEmpty().mapNotNull { m ->
                Material.entries.firstOrNull { it.name == m.jsonPrimitive.content }
            },
            note = properties["note"]?.jsonPrimitive?.content,
            toilet = if (type == PoiType.TOILET) {
                fun text(key: String) = properties[key]?.jsonPrimitive?.contentOrNull
                fun flag(key: String) = properties[key]?.jsonPrimitive?.booleanOrNull == true
                ToiletInfo(
                    address = text("address"),
                    hours = text("hours"),
                    floor = text("floor"),
                    price = properties["price"]?.jsonPrimitive?.doubleOrNull,
                    accessible = flag("accessible"),
                    changing = flag("changing"),
                    euroKey = flag("euroKey"),
                    rooms = properties["rooms"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                    status = ToiletStatus.entries.firstOrNull { it.name == text("status") },
                )
            } else {
                null
            },
            postBox = if (type == PoiType.POST_BOX) {
                PostBoxInfo(
                    address = properties["address"]?.jsonPrimitive?.contentOrNull,
                    collectionTimes = properties["collectionTimes"]?.jsonPrimitive?.contentOrNull ?: legacyTimes,
                    indoor = properties["indoor"]?.jsonPrimitive?.booleanOrNull == true,
                )
            } else {
                null
            },
        )
    }
