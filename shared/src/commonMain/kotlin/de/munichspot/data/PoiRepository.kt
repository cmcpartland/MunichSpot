package de.munichspot.data

import de.munichspot.shared.resources.Res
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Recycling islands: Landeshauptstadt München, AWM, opendata.muenchen.de (dl-by-de/2.0)
// Mailboxes: © OpenStreetMap contributors (ODbL)
suspend fun loadPois(): List<Poi> =
    parsePois(Res.readBytes("files/pois.geojson").decodeToString())

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
        )
    }
