package de.publicpoint.map

import de.publicpoint.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.Position

/** The city outline: the second ring of the boundary file's veil polygon (the first is the whole world). */
internal class MunichArea private constructor(private val ring: List<DoubleArray>) {
    fun contains(position: Position): Boolean = contains(position.longitude, position.latitude)

    fun contains(lon: Double, lat: Double): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val (xi, yi) = ring[i]
            val (xj, yj) = ring[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        fun parse(geoJson: String): MunichArea? = runCatching {
            val rings = Json.parseToJsonElement(geoJson).jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
            MunichArea(rings[1].jsonArray.map { p -> p.jsonArray.let { doubleArrayOf(it[0].jsonPrimitive.content.toDouble(), it[1].jsonPrimitive.content.toDouble()) } })
        }.onFailure { Log.w("Map", "boundary parse failed", it) }.getOrNull()
    }
}

internal fun MunichArea?.containsOrBox(position: Position): Boolean =
    this?.contains(position) ?: (position.latitude in 47.9..48.4 && position.longitude in 11.2..11.9)
