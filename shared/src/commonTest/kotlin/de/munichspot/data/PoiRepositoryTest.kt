package de.munichspot.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PoiRepositoryTest {
    private fun recycling(id: String, vararg materials: String) =
        feature(id, "RECYCLING", extra = ""","materials":[${materials.joinToString(",") { "\"$it\"" }}]""")

    @Test
    fun clothingIslandYieldsClothesPoiAndStripsClothing() {
        val pois = parsePoiData(geojson(listOf(recycling("a", "GLASS_GREEN", "CLOTHING")))).pois
        assertEquals(listOf("a", "a-clothes"), pois.map { it.id })
        assertEquals(PoiType.RECYCLING, pois[0].type)
        assertEquals(listOf(Material.GLASS_GREEN), pois[0].materials)
        assertEquals(PoiType.CLOTHES, pois[1].type)
        assertTrue(pois[1].materials.isEmpty())
    }

    @Test
    fun clothingOnlyIslandBecomesOnlyClothes() {
        val pois = parsePoiData(geojson(listOf(recycling("a", "CLOTHING")))).pois
        assertEquals(listOf("a-clothes"), pois.map { it.id })
    }

    @Test
    fun islandWithoutClothingIsUnchanged() {
        val pois = parsePoiData(geojson(listOf(recycling("a", "PACKAGING")))).pois
        assertEquals(listOf("a"), pois.map { it.id })
    }

    @Test
    fun placeholderNamesBecomeEmpty() {
        val pois = parsePoiData(geojson(listOf(feature("a", "TOILET", "Öffentliche Toilette"), feature("b", "TOILET", "Hauptbahnhof")))).pois
        assertEquals(listOf("", "Hauptbahnhof"), pois.map { it.name })
    }

    @Test
    fun legacyPostBoxNameYieldsCollectionTimes() {
        val poi = parsePoiData(geojson(listOf(feature("a", "POST_BOX", "Briefkasten (Mo-Fr 17:00)")))).pois.single()
        assertEquals("", poi.name)
        assertEquals("Mo-Fr 17:00", poi.postBox?.collectionTimes)
    }

    @Test
    fun explicitCollectionTimesWinOverLegacyName() {
        val poi = parsePoiData(geojson(listOf(feature("a", "POST_BOX", "Briefkasten (alt)", extra = ""","collectionTimes":"neu""""))))
            .pois.single()
        assertEquals("neu", poi.postBox?.collectionTimes)
    }

    @Test
    fun unknownTypeIsDroppedWithoutCountingAsMalformed() {
        val features = List(10) { feature("p$it", "POST_BOX") } + feature("x", "SPACESHIP")
        assertEquals(10, parsePoiData(geojson(features)).pois.size)
    }

    @Test
    fun fewMalformedFeaturesAreSkipped() {
        val good = List(40) { feature("p$it", "POST_BOX") }
        val data = parsePoiData(geojson(good + """{"type":"Feature","properties":{}}""" + """{"nonsense":1}"""))
        assertEquals(40, data.pois.size)
    }

    @Test
    fun tooManyMalformedFeaturesRejectTheFile() {
        val good = List(10) { feature("p$it", "POST_BOX") }
        assertFailsWith<IllegalArgumentException> { parsePoiData(geojson(good + """{"nonsense":1}""")) }
    }

    @Test
    fun malformedShareAtLimitIsAccepted() {
        val good = List(95) { feature("p$it", "POST_BOX") }
        assertEquals(95, parsePoiData(geojson(good + List(5) { """{"nonsense":1}""" })).pois.size)
    }

    @Test
    fun garbageThrows() {
        assertFailsWith<Exception> { parsePoiData("not json") }
    }

    @Test
    fun generatedIsParsed() {
        assertEquals(Instant.parse("2026-03-04T05:06:07Z"), parsePoiData(geojson(emptyList(), "2026-03-04T05:06:07Z")).generated)
    }

    @Test
    fun missingOrInvalidGeneratedIsNull() {
        assertNull(parsePoiData(geojson(emptyList(), null)).generated)
        assertNull(parsePoiData(geojson(emptyList(), "yesterday")).generated)
    }

    @Test
    fun toiletAndScheduleAreParsed() {
        val extra = ""","address":"Weg 1","accessible":true,"status":"DEFECT","schedule":[{"from":480,"to":1200}]"""
        val poi = parsePoiData(geojson(listOf(feature("t", "TOILET", extra = extra)))).pois.single()
        val toilet = assertNotNull(poi.toilet)
        assertEquals("Weg 1", toilet.address)
        assertTrue(toilet.accessible)
        assertEquals(ToiletStatus.DEFECT, toilet.status)
        assertEquals(listOf(ScheduleRule(480, 1200, null, null)), poi.schedule)
    }

    @Test
    fun plausibleNeedsEnoughPoisAndAllCoreTypes() {
        assertTrue(isPlausible(parsePoiData(geojson(plausibleFeatures())).pois))
        assertFalse(isPlausible(parsePoiData(geojson(plausibleFeatures(999))).pois))
        val noToilets = List(1200) { feature("p$it", if (it % 3 == 0) "RECYCLING" else if (it % 3 == 1) "POST_BOX" else "DRINKING_WATER") }
        assertFalse(isPlausible(parsePoiData(geojson(noToilets)).pois))
    }

    @Test
    fun fileWithoutNewerTypesIsStillPlausible() {
        val pois = parsePoiData(geojson(plausibleFeatures())).pois
        assertTrue(pois.none { it.type == PoiType.PUBLIC_BOOKCASE || it.type == PoiType.RECYCLING_CENTRE })
        assertNotNull(parseLatest(geojson(plausibleFeatures())))
        assertNull(parseLatest(geojson(plausibleFeatures(10))))
    }

    @Test
    fun schemaVersionMissingOrKnownIsAccepted() {
        assertNotNull(parseLatest(geojson(plausibleFeatures())))
        assertNotNull(parseLatest(geojson(plausibleFeatures(), schemaVersion = "1")))
    }

    @Test
    fun newerOrNonIntegerSchemaVersionIsRejected() {
        assertNull(parseLatest(geojson(plausibleFeatures(), schemaVersion = "2")))
        assertNull(parseLatest(geojson(plausibleFeatures(), schemaVersion = "\"1\"")))
        assertNull(parseLatest(geojson(plausibleFeatures(), schemaVersion = "1.5")))
    }
}
