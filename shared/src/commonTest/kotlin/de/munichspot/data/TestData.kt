package de.munichspot.data

internal fun feature(
    id: String,
    type: String,
    name: String = "",
    lon: Double = 11.5,
    lat: Double = 48.1,
    extra: String = "",
) = """{"type":"Feature","id":"$id","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"type":"$type","name":"$name"$extra}}"""

internal fun geojson(features: List<String>, generated: String? = "2026-01-01T00:00:00Z", schemaVersion: String? = null) =
    """{"type":"FeatureCollection",${schemaVersion?.let { """"schemaVersion":$it,""" } ?: ""}${generated?.let { """"generated":"$it",""" } ?: ""}"features":[${features.joinToString(",")}]}"""

internal fun plausibleFeatures(count: Int = 1000): List<String> {
    val types = listOf("RECYCLING", "POST_BOX", "DRINKING_WATER", "TOILET")
    return List(count) { feature("p$it", types[it % types.size]) }
}
