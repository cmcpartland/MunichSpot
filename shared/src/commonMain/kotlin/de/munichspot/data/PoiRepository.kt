package de.munichspot.data

import de.munichspot.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

const val LATEST_POIS_URL = "https://cmcpartland.github.io/MunichSpot/pois.geojson"

private const val MIN_LATEST_POIS = 1000

// Placeholder names of older data; the list can go once the nameless-as-"" data is live.
private val OLD_PLACEHOLDER_NAMES = setOf("Wertstoffinsel", "Briefkasten", "Trinkbrunnen", "Öffentliche Toilette")

private val LEGACY_POST_BOX_NAME = Regex("^Briefkasten \\((.+)\\)$")

// Recycling islands: Landeshauptstadt München, AWM, opendata.muenchen.de (dl-by-de/2.0)
// Mailboxes: © OpenStreetMap contributors (ODbL)
// Clothes containers: derived from the recycling islands
// Recycling centres: Landeshauptstadt München, AWM (locations); © OpenStreetMap contributors (names, opening hours)
// Public bookcases: © OpenStreetMap contributors (ODbL)
// Drinking fountains, public toilets: Landeshauptstadt München, opendata.muenchen.de (dl-by-de/2.0)
data class PoiData(val pois: List<Poi>, val generated: Instant?)

private val cache = DataCache("pois-latest.geojson")

// The cached copy of the last download if it is valid, otherwise null (a bad cache is deleted).
suspend fun loadStartupPois(): PoiData? = loadCached(cache, ::parseLatest).also {
    Log.d("PoiData", if (it == null) "no usable cache" else "loaded ${it.pois.size} pois from cache")
}

suspend fun fetchLatestPois(conditional: Boolean): FetchResult<PoiData> =
    fetchConditional(LATEST_POIS_URL, cache, conditional, parse = ::parseLatest)

internal const val POI_SCHEMA_VERSION = 1

// A missing version (files from before the field) is accepted; a newer or non-integer one is not.
internal fun isSupportedSchema(root: JsonObject): Boolean {
    val element = root["schemaVersion"] ?: return true
    val version = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    if (version == null) Log.w("PoiData", "schemaVersion is not an integer: $element")
    else if (version > POI_SCHEMA_VERSION) Log.w("PoiData", "schema version $version is newer than supported ($POI_SCHEMA_VERSION)")
    return version != null && version <= POI_SCHEMA_VERSION
}

internal fun parseLatest(text: String): PoiData? {
    val root = Json.parseToJsonElement(text).jsonObject
    if (!isSupportedSchema(root)) return null
    return parsePoiData(root).takeIf { isPlausible(it.pois) }
}

private val CORE_TYPES = setOf(PoiType.RECYCLING, PoiType.POST_BOX, PoiType.DRINKING_WATER, PoiType.TOILET)

internal fun isPlausible(pois: List<Poi>) =
    pois.size >= MIN_LATEST_POIS && CORE_TYPES.all { type -> pois.any { it.type == type } }

// Clothes containers are derived from the recycling islands that accept clothing.
internal fun parsePoiData(geoJson: String) = parsePoiData(Json.parseToJsonElement(geoJson).jsonObject)

private fun parsePoiData(root: JsonObject): PoiData {
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

private fun parseFeatures(root: JsonObject): List<Poi> {
    val features = root.getValue("features").jsonArray
    var malformed = 0
    val pois = features.mapNotNull { f ->
        try {
            parseFeature(f)
        } catch (e: Exception) {
            malformed++
            null
        }
    }
    if (malformed > 0) Log.w("Pois", "skipped $malformed malformed of ${features.size} features")
    require(malformed <= features.size * MAX_MALFORMED_SHARE) { "$malformed of ${features.size} features malformed" }
    return pois
}

private const val MAX_MALFORMED_SHARE = 0.05

private fun parseFeature(f: JsonElement): Poi? {
    val feature = f.jsonObject
    val properties = feature.getValue("properties").jsonObject
    val type = PoiType.entries.firstOrNull { it.name == properties.getValue("type").jsonPrimitive.content }
        ?: return null
    val coordinates = feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
    val rawName = properties["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    val legacyTimes = if (type == PoiType.POST_BOX) LEGACY_POST_BOX_NAME.matchEntire(rawName)?.groupValues?.get(1) else null
    fun localText(key: String): LocalText? =
        properties[key]?.jsonPrimitive?.contentOrNull?.let { LocalText(it, properties[key + "En"]?.jsonPrimitive?.contentOrNull) }
    return Poi(
        id = feature.getValue("id").jsonPrimitive.content,
        type = type,
        name = if (legacyTimes != null || rawName in OLD_PLACEHOLDER_NAMES) "" else rawName,
        lat = coordinates[1].jsonPrimitive.content.toDouble(),
        lon = coordinates[0].jsonPrimitive.content.toDouble(),
        materials = properties["materials"]?.jsonArray.orEmpty().mapNotNull { m ->
            Material.entries.firstOrNull { it.name == m.jsonPrimitive.content }
        },
        note = localText("note"),
        schedule = parseSchedule(properties["schedule"]),
        toilet = if (type == PoiType.TOILET) {
            fun text(key: String) = properties[key]?.jsonPrimitive?.contentOrNull
            fun flag(key: String) = properties[key]?.jsonPrimitive?.booleanOrNull == true
            ToiletInfo(
                address = text("address"),
                hours = localText("hours"),
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
        recyclingCentre = if (type == PoiType.RECYCLING_CENTRE) {
            RecyclingCentreInfo(address = properties["address"]?.jsonPrimitive?.contentOrNull)
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
        bookcase = if (type == PoiType.PUBLIC_BOOKCASE) {
            BookcaseInfo(
                phoneBox = properties["phoneBox"]?.jsonPrimitive?.booleanOrNull == true,
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

internal sealed interface Freshness {
    data object VeryOld : Freshness
    data class AsOf(val date: String) : Freshness
}

/** Subtle note for the attribution bar when the data is old; null when it is recent or its age is unknown. */
internal fun freshnessHint(generated: Instant?, now: Instant): Freshness? {
    val age = now - (generated ?: return null)
    return when {
        age > VERY_STALE_AFTER -> Freshness.VeryOld
        age > STALE_AFTER -> Freshness.AsOf(generated.toGermanDate())
        else -> null
    }
}
