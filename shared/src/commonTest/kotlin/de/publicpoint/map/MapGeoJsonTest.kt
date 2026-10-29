package de.publicpoint.map

import de.publicpoint.data.Poi
import de.publicpoint.data.PoiType
import de.publicpoint.data.ToiletInfo
import de.publicpoint.data.ToiletStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapGeoJsonTest {
    private fun poi(id: String, name: String, type: PoiType = PoiType.POST_BOX, toilet: ToiletInfo? = null) =
        Poi(id, type, name, lat = 48.1, lon = 11.5, toilet = toilet)

    private fun features(pois: List<Poi>, closed: Set<String> = emptySet()) =
        Json.parseToJsonElement(pois.toGeoJson(closed)).jsonObject.getValue("features").jsonArray.map { it.jsonObject }

    @Test
    fun hostileCharactersRoundTrip() {
        val name = "a\"b\\c\nd\te\u0001f\u001fg\r h/ä€😀"
        val id = "id\"\n1"
        val feature = features(listOf(poi(id, name))).single()
        assertEquals(name, feature.getValue("properties").jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(id, feature.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun controlCharactersAreEscapedInOutput() {
        val out = listOf(poi("a", "x\ny\u0001")).toGeoJson()
        assertEquals(false, out.any { it < ' ' })
        assertEquals(true, "\\u000a" in out && "\\u0001" in out)
    }

    @Test
    fun coordinatesAreLonLat() {
        val coords = features(listOf(poi("a", "x"))).single().getValue("geometry").jsonObject.getValue("coordinates").jsonArray
        assertEquals(11.5, coords[0].jsonPrimitive.content.toDouble())
        assertEquals(48.1, coords[1].jsonPrimitive.content.toDouble())
    }

    @Test
    fun closedAndWarnFlags() {
        val defect = ToiletInfo(null, null, null, null, false, false, false, emptyList(), ToiletStatus.DEFECT)
        val list = listOf(poi("a", "x"), poi("b", "y", PoiType.TOILET, defect))
        val props = features(list, closed = setOf("b")).map { it.getValue("properties").jsonObject }
        assertNull(props[0]["closed"])
        assertNull(props[0]["warn"])
        assertEquals(true, props[1].getValue("closed").jsonPrimitive.boolean)
        assertEquals(true, props[1].getValue("warn").jsonPrimitive.boolean)
    }

    @Test
    fun emptyListIsValid() {
        assertEquals(0, features(emptyList()).size)
    }
}
