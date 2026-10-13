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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val LATEST_POIS_URL = "https://cmcpartland.github.io/MunichSpot/pois.geojson"

private const val MIN_LATEST_POIS = 1000

private val client by lazy { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 10_000 } } }

// Recycling islands: Landeshauptstadt München, AWM, opendata.muenchen.de (dl-by-de/2.0)
// Mailboxes: © OpenStreetMap contributors (ODbL)
suspend fun loadBundledPois(): List<Poi> =
    Res.readBytes("files/pois.geojson").decodeToString().let { withContext(Dispatchers.Default) { parsePois(it) } }

// Returns null on any failure or implausible data, so callers keep what they have.
suspend fun fetchLatestPois(): List<Poi>? = try {
    val response = client.get(LATEST_POIS_URL)
    if (!response.status.isSuccess()) null
    else withContext(Dispatchers.Default) { parsePois(response.bodyAsText()) }.takeIf { pois ->
        pois.size >= MIN_LATEST_POIS && PoiType.entries.all { type -> pois.any { it.type == type } }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

internal fun parsePois(geoJson: String): List<Poi> =
    Json.parseToJsonElement(geoJson).jsonObject.getValue("features").jsonArray.map { f ->
        val feature = f.jsonObject
        val coordinates = feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
        val properties = feature.getValue("properties").jsonObject
        Poi(
            id = feature.getValue("id").jsonPrimitive.content,
            type = PoiType.valueOf(properties.getValue("type").jsonPrimitive.content),
            name = properties.getValue("name").jsonPrimitive.content,
            lat = coordinates[1].jsonPrimitive.content.toDouble(),
            lon = coordinates[0].jsonPrimitive.content.toDouble(),
            materials = properties["materials"]?.jsonArray.orEmpty().mapNotNull { m ->
                Material.entries.firstOrNull { it.name == m.jsonPrimitive.content }
            },
        )
    }
