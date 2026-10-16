package de.munichspot.data

import kotlin.math.cos
import kotlin.math.hypot

private val StreetWord = Regex("strasse|str\\.")
private val PartialStreetWord = Regex("str(a|as|ass)$")
private val Separators = Regex("[\\s\\-]+")

internal fun normalizePlaceText(text: String, query: Boolean = false): String {
    val s = buildString {
        for (c in text.lowercase()) when (c) {
            'ä' -> append('a')
            'ö' -> append('o')
            'ü' -> append('u')
            'ß' -> append("ss")
            else -> append(c)
        }
    }.replace(StreetWord, "str").replace(Separators, " ").trim()
    return if (query) s.replace(PartialStreetWord, "str") else s
}

/** Places with their normalised names, computed once so typing stays fast. */
class PlaceIndex(val places: List<Place>) {
    private val names = places.map { normalizePlaceText(it.name) }

    /** Best matches: name starts with the query, then a word starts with it, then a substring; then kind, then distance to [near]. */
    fun search(query: String, nearLat: Double? = null, nearLon: Double? = null, limit: Int = 6): List<Place> {
        val q = normalizePlaceText(query, query = true)
        if (q.length < 2) return emptyList()
        val hits = ArrayList<Triple<Int, Int, Int>>()
        for (i in names.indices) {
            val n = names[i]
            val rank = when {
                n.startsWith(q) -> 0
                n.contains(" $q") -> 1
                n.contains(q) -> 2
                else -> continue
            }
            hits += Triple(rank, kindPriority(places[i].kind), i)
        }
        fun distance(p: Place): Double {
            if (nearLat == null || nearLon == null) return 0.0
            return hypot((p.lat - nearLat) * 111.32, (p.lon - nearLon) * 111.32 * cos(nearLat * 0.0174533))
        }
        return hits.sortedWith(
            compareBy<Triple<Int, Int, Int>>({ it.first }, { it.second })
                .thenBy { distance(places[it.third]) }
                .thenBy { names[it.third].length },
        ).take(limit).map { places[it.third] }
    }
}

private fun kindPriority(kind: PlaceKind) = when (kind) {
    PlaceKind.DISTRICT -> 0
    PlaceKind.PARK -> 1
    PlaceKind.STATION -> 2
    PlaceKind.SQUARE -> 3
    PlaceKind.LANDMARK -> 4
    PlaceKind.STREET -> 5
}

fun searchPlaces(places: List<Place>, query: String, nearLat: Double? = null, nearLon: Double? = null, limit: Int = 6): List<Place> =
    PlaceIndex(places).search(query, nearLat, nearLon, limit)
