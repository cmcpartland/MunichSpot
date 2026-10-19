package de.munichspot.map

import de.munichspot.data.Place
import de.munichspot.data.Poi
import de.munichspot.data.ToiletStatus
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point

/** Hint only: derived from dataset free text, so it adds a warning mark and never filters. */
internal val Poi.possiblyDefect: Boolean get() = toilet?.status == ToiletStatus.DEFECT

internal fun Place?.toGeoJson() = """{"type":"FeatureCollection","features":[""" +
    (this?.let { """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]}}""" } ?: "") + "]}"

internal fun List<Poi>.toGeoJson(): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    this@toGeoJson.forEachIndexed { i, p ->
        if (i > 0) append(',')
        append("""{"type":"Feature","id":"${p.id.jsonEscape()}",""")
        append(""""geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]},""")
        append(""""properties":{"type":"${p.type.name}","name":"${p.name.jsonEscape()}"${if (p.possiblyDefect) ""","warn":true""" else ""}}}""")
    }
    append("]}")
}

private fun String.jsonEscape(): String = buildString {
    for (c in this@jsonEscape) when {
        c == '\\' -> append("\\\\")
        c == '"' -> append("\\\"")
        c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> append(c)
    }
}
