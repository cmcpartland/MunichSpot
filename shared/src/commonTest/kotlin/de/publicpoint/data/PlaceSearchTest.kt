package de.publicpoint.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceSearchTest {
    private fun place(name: String, kind: PlaceKind = PlaceKind.STREET, lat: Double = 48.1, lon: Double = 11.5) = Place(name, kind, lat, lon)
    private fun names(places: List<Place>, query: String, limit: Int = 6) = PlaceIndex(places).search(query, limit = limit).map { it.name }

    @Test
    fun umlautsAndEszettFold() {
        val places = listOf(place("Müllerstraße"), place("Östliche Allee"), place("Äußere Straße"))
        assertEquals(listOf("Müllerstraße"), names(places, "mull"))
        assertEquals(listOf("Müllerstraße"), names(places, "müller"))
        assertEquals(listOf("Östliche Allee"), names(places, "ostl"))
        assertEquals(listOf("Äußere Straße"), names(places, "ausse"))
    }

    @Test
    fun strVariantsMatch() {
        val places = listOf(place("Leopoldstraße"), place("Hauptstr. Nord"))
        assertEquals(listOf("Leopoldstraße"), names(places, "leopoldstr."))
        assertEquals(listOf("Leopoldstraße"), names(places, "leopoldstrasse"))
        assertEquals(listOf("Leopoldstraße"), names(places, "leopoldstraße"))
        assertEquals(listOf("Hauptstr. Nord"), names(places, "hauptstraße"))
    }

    @Test
    fun partialStreetSuffixMatchesWhileTyping() {
        val places = listOf(place("Leopoldstraße"))
        for (q in listOf("leopoldstr", "leopoldstra", "leopoldstras", "leopoldstrass")) assertEquals(listOf("Leopoldstraße"), names(places, q), q)
    }

    @Test
    fun prefixRanksBeforeWordBeforeSubstring() {
        val places = listOf(place("Am Marienplatz"), place("Neumarien"), place("Marienplatz"))
        assertEquals(listOf("Marienplatz", "Am Marienplatz", "Neumarien"), names(places, "marien"))
    }

    @Test
    fun kindBreaksTiesWithinRank() {
        val places = listOf(place("Isarweg", PlaceKind.STREET), place("Isartor", PlaceKind.STATION), place("Isarvorstadt", PlaceKind.DISTRICT))
        assertEquals(listOf("Isarvorstadt", "Isartor", "Isarweg"), names(places, "isar"))
    }

    @Test
    fun distanceBreaksTiesWithinKind() {
        val far = place("Hauptstraße", lat = 48.3)
        val near = place("Hauptstraße 2", lat = 48.1)
        val result = PlaceIndex(listOf(far, near)).search("haupt", nearLat = 48.1, nearLon = 11.5)
        assertEquals(listOf(near, far), result)
    }

    @Test
    fun shortQueryAndNoMatchGiveNothing() {
        val places = listOf(place("Marienplatz"))
        assertEquals(emptyList(), names(places, "m"))
        assertEquals(emptyList(), names(places, " "))
        assertEquals(emptyList(), names(places, "xyz"))
    }

    @Test
    fun duplicateNamesCollapseAndLimitApplies() {
        val places = listOf(place("Marienplatz"), place("MARIENPLATZ"), place("Marienstraße"), place("Marienhof"))
        assertEquals(3, names(places, "marien").size)
        assertEquals(2, names(places, "marien", limit = 2).size)
    }

    @Test
    fun parsePlaceDataSkipsBadRowsAndReadsGenerated() {
        val json = """{"generated":"2026-01-01T00:00:00Z","places":[["A","street",48.1,11.5],["B","nonsense",48.1,11.5],["C","park","x",11.5]]}"""
        val data = parsePlaceData(json)
        assertEquals(listOf("A"), data.places.map { it.name })
        assertEquals(kotlin.time.Instant.parse("2026-01-01T00:00:00Z"), data.generated)
    }
}
