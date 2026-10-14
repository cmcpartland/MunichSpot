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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
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
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
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
import org.maplibre.compose.sources.GeoJsonSource
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
private val PoiIconSize = 28.dp
private val ClusterIconSize = 34.dp
private val LocationBlue = Color(0xFF1A73E8)

internal const val ATTRIBUTION =
    "© OpenStreetMap contributors · OpenFreeMap · Daten: Landeshauptstadt München (dl-by-de/2.0)"

private val Marienplatz = Position(latitude = 48.1374, longitude = 11.5755)
private var locationAsked = false

/** Box of +-[radiusKm] around [center], so the map frames a circle of that radius. */
private fun boundsAround(center: Position, radiusKm: Double = 0.7): BoundingBox {
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
        // Roughly the 700 m framing on a phone; refined below once the viewport is known.
        initialCameraPosition = CameraPosition(center = Marienplatz, zoom = 13.9),
    ) {
        val sources = PoiType.entries.associateWith { t ->
            rememberGeoJsonSource(GeoJsonData.JsonString(geoJson.getValue(t))) {
                cluster = true
                clusterRadius = 50
                clusterMaxZoom = 14
            }
        }
        val visible = PoiType.entries.filter { type == null || type == it }
        // Icons of every type first, then all badges, then all counts, so no icon covers another type's badge.
        for (t in visible) {
            PoiIconLayers(
                type = t,
                source = sources.getValue(t),
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
        for (t in visible) PoiBadgeLayers(t, sources.getValue(t), mapState, scope)
        for (t in visible) PoiCountLayers(t, sources.getValue(t), mapState, scope)
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

private val BadgeOffset = DpOffset(13.dp, (-13).dp)
private val IsCluster = feature.has("point_count")

private fun zoomToCluster(source: GeoJsonSource, mapState: MapState, scope: CoroutineScope): FeaturesClickHandler =
    { features ->
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
    }

@Composable
@MaplibreComposable
private fun PoiIconLayers(
    type: PoiType,
    source: GeoJsonSource,
    mapState: MapState,
    scope: CoroutineScope,
    onPoi: (String?) -> Boolean,
) {
    val name = type.name.lowercase()
    val painter = remember(type) { CategoryIconPainter(type) }
    SymbolLayer(
        id = "$name-clusters",
        source = source,
        filter = IsCluster,
        iconImage = image(painter, size = DpSize(ClusterIconSize, ClusterIconSize)),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        hitPadding = 6.dp,
        onClick = zoomToCluster(source, mapState, scope),
    )
    SymbolLayer(
        id = "$name-pois",
        source = source,
        filter = !IsCluster,
        iconImage = image(painter, size = DpSize(PoiIconSize, PoiIconSize)),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        hitPadding = 8.dp,
        onClick = { features ->
            val id = features.firstOrNull()?.id?.jsonPrimitive?.content
            if (onPoi(id)) ClickResult.Consume else ClickResult.Pass
        },
    )
}

/** Count badge at the cluster icon's top-right corner: a white disc with a type-coloured border. */
@Composable
@MaplibreComposable
private fun PoiBadgeLayers(type: PoiType, source: GeoJsonSource, mapState: MapState, scope: CoroutineScope) {
    CircleLayer(
        id = "${type.name.lowercase()}-cluster-badge",
        source = source,
        filter = IsCluster,
        radius = const(10.dp),
        color = const(Color.White),
        strokeColor = const(type.pinColor()),
        strokeWidth = const(2.dp),
        translate = const(BadgeOffset),
        onClick = zoomToCluster(source, mapState, scope),
    )
}

@Composable
@MaplibreComposable
private fun PoiCountLayers(type: PoiType, source: GeoJsonSource, mapState: MapState, scope: CoroutineScope) {
    SymbolLayer(
        id = "${type.name.lowercase()}-cluster-count",
        source = source,
        filter = IsCluster,
        textField = format(span(feature["point_count_abbreviated"].asString())),
        textFont = const(listOf("Noto Sans Bold")),
        textSize = const(11.sp),
        textColor = const(Color(0xFF212121)),
        textTranslate = const(BadgeOffset),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        onClick = zoomToCluster(source, mapState, scope),
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
