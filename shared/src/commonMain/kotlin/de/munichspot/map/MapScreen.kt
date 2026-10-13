package de.munichspot.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LocationIndicatorDefaults
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationTrackingStatus
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

internal fun PoiType.pinColor(): Color = when (this) {
    PoiType.RECYCLING -> RecyclingGreen
    PoiType.POST_BOX -> PostBoxYellow
    PoiType.DRINKING_WATER -> DrinkingWaterBlue
    PoiType.TOILET -> ToiletPurple
}

internal fun PoiType.label(): String = when (this) {
    PoiType.RECYCLING -> "Wertstoffinseln"
    PoiType.POST_BOX -> "Briefkästen"
    PoiType.DRINKING_WATER -> "Trinkbrunnen"
    PoiType.TOILET -> "Toiletten"
}

internal val DrinkingWaterText = Color(0xFF01579B)

private val RecyclingGreen = Color(0xFF2E7D32)
private val PostBoxYellow = Color(0xFFF9A825)
private val DrinkingWaterBlue = Color(0xFF81D4FA)
private val ToiletPurple = Color(0xFF8E24AA)
private val LocationBlue = Color(0xFF1A73E8)

internal const val ATTRIBUTION =
    "© OpenStreetMap contributors · OpenFreeMap · Daten: Landeshauptstadt München (dl-by-de/2.0)"

private val Marienplatz = Position(latitude = 48.1374, longitude = 11.5755)
private var locationAsked = false

/** Box of +-[radiusKm] around [center], so the map frames a circle of that radius. */
private fun boundsAround(center: Position, radiusKm: Double = 5.0): BoundingBox {
    val dLat = radiusKm / 111.32
    val dLon = radiusKm / (111.32 * cos(center.latitude * PI / 180))
    return BoundingBox(
        southwest = Position(latitude = center.latitude - dLat, longitude = center.longitude - dLon),
        northeast = Position(latitude = center.latitude + dLat, longitude = center.longitude + dLon),
    )
}

@Composable
internal fun MapScreen(
    pois: List<Poi>,
    type: PoiType?,
    onTypeChange: (PoiType?) -> Unit,
    filters: PoiFilters,
    onFiltersChange: (PoiFilters) -> Unit,
) {
    var selected by remember { mutableStateOf<Poi?>(null) }
    val poisById = remember(pois) { pois.associateBy { it.id } }
    var showFilters by remember { mutableStateOf(false) }
    val geoJson = PoiType.entries.associateWith { t ->
        val f = if (type == t) filters else PoiFilters()
        val key = when (t) {
            PoiType.RECYCLING -> f.recyclingMaterials
            PoiType.TOILET -> f.toiletRequirements
            else -> null
        }
        remember(pois, key) { pois.filter { it.type == t && f.matches(it) }.toGeoJson() }
    }
    LaunchedEffect(type, filters) {
        if (type != null && selected?.let { it.type != type || !filters.matches(it) } == true) selected = null
    }
    val scope = rememberCoroutineScope()
    val locationState = rememberLocationState()
    val snackbar = remember { SnackbarHostState() }
    var centerRequested by remember { mutableStateOf(false) }
    lateinit var mapState: MapState
    mapState = rememberMapState(
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        // Roughly the 5 km framing on a phone; refined below once the viewport is known.
        initialCameraPosition = CameraPosition(center = Marienplatz, zoom = 11.1),
    ) {
        for (t in PoiType.entries) {
            if (type != null && type != t) continue
            PoiLayers(
                type = t,
                json = geoJson.getValue(t),
                mapState = mapState,
                scope = scope,
                onPoi = { id ->
                    poisById[id]?.let {
                        selected = it
                        showFilters = false
                    } != null
                },
            )
        }
        LocationIndicatorLayer(
            id = "user-location",
            locationState = locationState,
            visible = locationState.permission is LocationPermission.Granted,
            accuracyRadiusColor = LocationBlue.copy(alpha = 0.15f),
            accuracyRadiusBorderColor = LocationBlue.copy(alpha = 0.4f),
            topImage = LocationIndicatorDefaults.topImage(color = LocationBlue),
            bearingImage = null,
        )
    }

    val density = LocalDensity.current
    var attributionBarHeight by remember { mutableStateOf(0.dp) }

    LaunchedEffect(Unit) {
        mapState.animateCameraToBounds(boundsAround(Marienplatz), animation = CameraAnimation.Ease { duration = Duration.ZERO })
        val permission = locationState.permission
        if (!locationAsked && permission is LocationPermission.NotGranted && permission.canRequest != false) {
            locationAsked = true
            locationState.requestPermission()
        }
        val position = withTimeoutOrNull(5.seconds) {
            snapshotFlow { locationState.lastLocation?.position }.first { it != null }
        }
        if (position != null && position.latitude in 47.9..48.4 && position.longitude in 11.2..11.9) {
            mapState.animateCameraToBounds(boundsAround(position))
        }
    }

    LaunchedEffect(centerRequested) {
        if (!centerRequested) return@LaunchedEffect
        // Waits for a fix; gives up on a known denial, a tracking failure or after a timeout.
        val position = withTimeoutOrNull(15.seconds) {
            snapshotFlow {
                val permission = locationState.permission
                val failed = (permission is LocationPermission.NotGranted && permission.canRequest != true) ||
                    locationState.status is LocationTrackingStatus.Unavailable
                locationState.lastLocation?.position to failed
            }.first { (position, failed) -> position != null || failed }.first
        }
        if (position != null) {
            mapState.animateCamera(CameraUpdate(center = position, zoom = 15.0))
        } else {
            snackbar.showSnackbar("Standort nicht verfügbar")
        }
        centerRequested = false
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            // Keeps MapLibre's own attribution button clear of our attribution bar.
            viewportInsets = PaddingValues(bottom = attributionBarHeight),
            interactions = MapInteractions {
                callbacks { click { onUnhandled { _ ->
                    selected = null
                    ClickResult.Consume
                } } }
            },
        )
        selected?.let { PoiSheet(it) { selected = null } }
        if (showFilters && type != null) FilterSheet(type, filters, onChange = onFiltersChange, onDismiss = { showFilters = false })
        CategorySearchBar(
            pois, type, onTypeChange,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SmallFloatingActionButton(
            onClick = {
                if (locationState.permission !is LocationPermission.Granted) locationState.requestPermission()
                centerRequested = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = attributionBarHeight + 108.dp),
        ) {
            LocateIcon(LocalContentColor.current)
        }
        if (type != null && type.hasFilters()) SmallFloatingActionButton(
            onClick = {
                selected = null
                showFilters = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = attributionBarHeight + 56.dp),
        ) {
            BadgedBox(badge = { if (!filters.isDefault(type)) Badge() }) {
                FilterIcon(LocalContentColor.current)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = attributionBarHeight + 8.dp),
        )
        Surface(
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { attributionBarHeight = with(density) { it.height.toDp() } }
                .navigationBarsPadding(),
        ) {
            Text(
                text = ATTRIBUTION,
                fontSize = 10.sp,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
@MaplibreComposable
private fun PoiLayers(
    type: PoiType,
    json: String,
    mapState: MapState,
    scope: CoroutineScope,
    onPoi: (String?) -> Boolean,
) {
    val name = type.name.lowercase()
    val color = type.pinColor()
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json)) {
        cluster = true
        clusterRadius = 50
        clusterMaxZoom = 14
    }
    CircleLayer(
        id = "$name-clusters",
        source = source,
        filter = feature.has("point_count"),
        radius = step(
            feature["point_count"].asNumber(),
            const(14.dp),
            10 to const(18.dp),
            50 to const(24.dp),
        ),
        color = const(color),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        onClick = { features ->
            val cluster = features.firstOrNull()
            val center = (cluster?.geometry as? Point)?.coordinates
            if (cluster == null || center == null) {
                ClickResult.Pass
            } else {
                scope.launch {
                    val zoom = mapState.style.sources[source]?.getClusterExpansionZoom(cluster)
                        ?: (mapState.cameraPosition.zoom + 2)
                    mapState.animateCamera(CameraUpdate(center = center, zoom = zoom + 0.5))
                }
                ClickResult.Consume
            }
        },
    )
    SymbolLayer(
        id = "$name-cluster-count",
        source = source,
        filter = feature.has("point_count"),
        textField = format(span(feature["point_count_abbreviated"].asString())),
        textFont = const(listOf("Noto Sans Bold")),
        textSize = const(14.sp),
        // White is unreadable on the pale fountain colour.
        textColor = const(if (type == PoiType.DRINKING_WATER) DrinkingWaterText else Color.White),
        textAllowOverlap = const(true),
    )
    CircleLayer(
        id = "$name-pois",
        source = source,
        filter = !feature.has("point_count"),
        radius = const(7.dp),
        color = const(color),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        hitPadding = 12.dp,
        onClick = { features ->
            val id = features.firstOrNull()?.id?.jsonPrimitive?.content
            if (onPoi(id)) ClickResult.Consume else ClickResult.Pass
        },
    )
}

private fun List<Poi>.toGeoJson(): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    this@toGeoJson.forEachIndexed { i, p ->
        if (i > 0) append(',')
        append("""{"type":"Feature","id":"${p.id.jsonEscape()}",""")
        append(""""geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]},""")
        append(""""properties":{"type":"${p.type.name}","name":"${p.name.jsonEscape()}"}}""")
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
