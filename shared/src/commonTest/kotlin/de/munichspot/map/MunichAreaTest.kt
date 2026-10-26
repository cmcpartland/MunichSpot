package de.munichspot.map

import org.maplibre.spatialk.geojson.Position
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MunichAreaTest {
    // A veil polygon: the world, then a square roughly around the city (lon 11.4-11.7, lat 48.05-48.25).
    private val boundary = """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[
        [[-180,-90],[180,-90],[180,90],[-180,90],[-180,-90]],
        [[11.4,48.05],[11.7,48.05],[11.7,48.25],[11.4,48.25],[11.4,48.05]]]}}"""

    private val area = assertNotNull(MunichArea.parse(boundary))

    @Test
    fun marienplatzIsInside() = assertTrue(area.contains(Marienplatz))

    @Test
    fun otherCitiesAreOutside() {
        assertFalse(area.contains(Position(longitude = 10.8978, latitude = 48.3705)))
        assertFalse(area.contains(Position(longitude = 13.405, latitude = 52.52)))
    }

    @Test
    fun invalidBoundaryIsNull() {
        assertNull(MunichArea.parse("nope"))
        assertNull(MunichArea.parse("""{"geometry":{"coordinates":[[]]}}"""))
    }

    @Test
    fun fallbackBoxIsUsedWithoutBoundary() {
        val none: MunichArea? = null
        assertTrue(none.containsOrBox(Marienplatz))
        assertFalse(none.containsOrBox(Position(longitude = 13.405, latitude = 52.52)))
    }
}
