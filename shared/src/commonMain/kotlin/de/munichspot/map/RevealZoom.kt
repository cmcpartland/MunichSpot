package de.munichspot.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.munichspot.data.Poi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import org.maplibre.compose.camera.CameraPosition

private const val WorldSizeDp = 512.0
private const val MinRevealZoom = 10.0
private val RevealMargin = 24.dp

private fun mercatorX(lon: Double) = (lon + 180) / 360
private fun mercatorY(lat: Double) = (1 - ln(tan(PI / 4 + lat * PI / 360)) / PI) / 2

/**
 * The zoom at which the [count] nearest [pois] fit between [top] and [bottom] of the map, keeping the camera
 * centre at [center] (screen dp), or null if one of them is already on screen.
 */
internal fun revealZoom(
    pois: List<Poi>,
    camera: CameraPosition,
    mapSize: DpSize,
    center: DpOffset,
    top: Dp,
    bottom: Dp,
    count: Int = 2,
): Double? {
    if (pois.isEmpty()) return null
    val cx = mercatorX(camera.center.longitude)
    val cy = mercatorY(camera.center.latitude)
    val b = camera.bearing * PI / 180
    val up = (center.y - top - RevealMargin).value
    val down = (mapSize.height - bottom - center.y - RevealMargin).value
    val left = (center.x - RevealMargin).value
    val right = (mapSize.width - center.x - RevealMargin).value
    if (min(min(up, down), min(left, right)) <= 0f) return null
    val fits = pois.map { poi ->
        val dx = mercatorX(poi.lon) - cx
        val dy = mercatorY(poi.lat) - cy
        val sx = dx * cos(b) + dy * sin(b)
        val sy = -dx * sin(b) + dy * cos(b)
        fun fit(d: Double, neg: Float, pos: Float) =
            if (d == 0.0) Double.POSITIVE_INFINITY else log2((if (d < 0) neg else pos) / (abs(d) * WorldSizeDp))
        min(fit(sx, left, right), fit(sy, up, down))
    }.sortedDescending()
    if (fits.first() >= camera.zoom) return null
    return fits[min(count, fits.size) - 1].coerceAtLeast(MinRevealZoom).takeIf { it < camera.zoom }
}
