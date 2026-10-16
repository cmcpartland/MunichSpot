package de.munichspot.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

const val LATEST_POIS_URL = "https://cmcpartland.github.io/MunichSpot/pois.geojson"

private const val MIN_LATEST_POIS = 1000

private val LEGACY_POST_BOX_NAME = Regex("^Briefkasten \\((.+)\\)$")

private val client by lazy { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 10_000 } } }

// Recycling islands: Landeshauptstadt München, AWM, opendata.muenchen.de (dl-by-de/2.0)
// Mailboxes: © OpenStreetMap contributors (ODbL)
// Clothes containers: derived from the recycling islands
// Drinking fountains, public toilets: Landeshauptstadt München, opendata.muenchen.de (dl-by-de/2.0)
data class PoiData(val pois: List<Poi>, val generated: Instant?)

sealed interface FetchResult {
    data class Updated(val data: PoiData) : FetchResult
    data object Unchanged : FetchResult
    data object Failed : FetchResult
}

// The cached copy of the last download if it is valid, otherwise null (a bad cache is deleted).
suspend fun loadStartupPois(): PoiData? = withContext(Dispatchers.Default) {
    val text = readCachedPoisText() ?: return@withContext null
    val cached = try {
        parsePoiData(text).takeIf { isPlausible(it.pois) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    cached ?: run { deleteCachedPois(); null }
}

// With [conditional] and a cached download, asks the server only for newer data (304 -> Unchanged).
suspend fun fetchLatestPois(conditional: Boolean): FetchResult = try {
    val etag = if (conditional) readCachedEtag() else null
    val response = client.get(LATEST_POIS_URL) { if (etag != null) header(HttpHeaders.IfNoneMatch, etag) }
    when {
        etag != null && response.status == HttpStatusCode.NotModified -> FetchResult.Unchanged
        !response.status.isSuccess() -> FetchResult.Failed
        else -> {
            val text = response.bodyAsText()
            val newEtag = response.headers[HttpHeaders.ETag]
            withContext(Dispatchers.Default) {
                parsePoiData(text).takeIf { isPlausible(it.pois) }
                    ?.also { saveCachedPois(text, newEtag) }
                    ?.let { FetchResult.Updated(it) }
                    ?: FetchResult.Failed
            }
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    FetchResult.Failed
}

private fun isPlausible(pois: List<Poi>) =
    pois.size >= MIN_LATEST_POIS && PoiType.entries.all { type -> pois.any { it.type == type } }

// Clothes containers are derived from the recycling islands that accept clothing.
internal fun parsePoiData(geoJson: String): PoiData {
    val root = Json.parseToJsonElement(geoJson).jsonObject
    val generated = root["generated"]?.jsonPrimitive?.contentOrNull
        ?.let { try { Instant.parse(it) } catch (e: IllegalArgumentException) { null } }
    return PoiData(parseTypedPois(root), generated)
}

private fun parseTypedPois(root: JsonObject): List<Poi> = parseFeatures(root).flatMap { poi ->
    if (poi.type != PoiType.RECYCLING || Material.CLOTHING !in poi.materials) {
        listOf(poi)
    } else {
        val rest = poi.materials - Material.CLOTHING
        listOfNotNull(
            poi.copy(materials = rest).takeIf { rest.isNotEmpty() },
            poi.copy(id = "${poi.id}-clothes", type = PoiType.CLOTHES, materials = emptyList()),
        )
    }
}

private fun parseFeatures(root: JsonObject): List<Poi> =
    root.getValue("features").jsonArray.mapNotNull { f ->
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

/** dd.MM.yyyy in UTC. */
internal fun Instant.toGermanDate(): String {
    // Days since 1970-01-01 to a civil date (Howard Hinnant's algorithm).
    val z = epochSeconds.floorDiv(86_400L) + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "${day.toString().padStart(2, '0')}.${month.toString().padStart(2, '0')}.$year"
}

internal val STALE_AFTER = 30.days
internal val VERY_STALE_AFTER = 365.days

/** Subtle note for the attribution bar when the data is old; null when it is recent or its age is unknown. */
internal fun freshnessHint(generated: Instant?, now: Instant): String? {
    val age = now - (generated ?: return null)
    return when {
        age > VERY_STALE_AFTER -> "Daten seit über einem Jahr nicht aktualisiert"
        age > STALE_AFTER -> "Datenstand: ${generated.toGermanDate()}"
        else -> null
    }
}
