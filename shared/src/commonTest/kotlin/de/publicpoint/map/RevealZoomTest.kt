package de.publicpoint.map

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.publicpoint.data.Poi
import de.publicpoint.data.PoiType
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevealZoomTest {
    private val camera = CameraPosition(center = Marienplatz, zoom = 15.0)
    private val size = DpSize(400.dp, 800.dp)
    private val center = DpOffset(200.dp, 400.dp)

    private fun poi(dLat: Double, dLon: Double = 0.0) =
        Poi("p$dLat$dLon", PoiType.DRINKING_WATER, "", Marienplatz.latitude + dLat, Marienplatz.longitude + dLon)

    private fun reveal(pois: List<Poi>, cam: CameraPosition = camera, count: Int = 2) =
        revealZoom(pois, cam, size, center, top = 0.dp, bottom = 100.dp, count = count)

    @Test
    fun noPoisGivesNull() = assertNull(reveal(emptyList()))

    @Test
    fun nearbyPoiOnScreenGivesNull() = assertNull(reveal(listOf(poi(0.0005), poi(0.0006))))

    @Test
    fun farawayPoisZoomOut() {
        val zoom = assertNotNull(reveal(listOf(poi(0.05), poi(0.06), poi(0.2))))
        assertTrue(zoom < camera.zoom)
        assertTrue(zoom >= 10.0)
    }

    @Test
    fun usesTheNthNearestPoi() {
        val near = poi(0.03)
        val mid = poi(0.06)
        val far = poi(0.5)
        val two = reveal(listOf(far, mid, near), count = 2)!!
        val one = reveal(listOf(far, mid, near), count = 1)!!
        assertTrue(two < one)
    }

    @Test
    fun zoomIsClampedToMinimum() {
        assertEquals(10.0, reveal(listOf(poi(5.0), poi(6.0))))
    }

    @Test
    fun bearingRotatesTheScreenAxes() {
        val east = listOf(poi(0.0, 0.02), poi(0.0, 0.02))
        val wide = reveal(east)!!
        val rotated = reveal(east, camera.copy(bearing = 90.0))!!
        assertTrue(rotated > wide, "east is along the taller screen axis at bearing 90, so less zooming out is needed")
    }

    @Test
    fun boundsAroundIsCenteredAndScaled() {
        val b = boundsAround(Marienplatz)
        assertEquals(Marienplatz.latitude, (b.southwest.latitude + b.northeast.latitude) / 2, 1e-9)
        assertEquals(Marienplatz.longitude, (b.southwest.longitude + b.northeast.longitude) / 2, 1e-9)
        assertEquals(0.7 / 111.32, b.northeast.latitude - Marienplatz.latitude, 1e-9)
        val wide = boundsAround(Marienplatz, 1.4)
        assertEquals(2 * (b.northeast.latitude - Marienplatz.latitude), wide.northeast.latitude - Marienplatz.latitude, 1e-9)
        assertTrue(b.northeast.longitude - Marienplatz.longitude > b.northeast.latitude - Marienplatz.latitude)
    }
}
