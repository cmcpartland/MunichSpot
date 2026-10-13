package de.munichspot.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

internal fun PoiType.pinColor(): Color = if (this == PoiType.RECYCLING) RecyclingGreen else PostBoxYellow

private val RecyclingGreen = Color(0xFF2E7D32)
private val PostBoxYellow = Color(0xFFF9A825)

private const val ATTRIBUTION =
    "© OpenStreetMap contributors · OpenFreeMap · Daten: Landeshauptstadt München (dl-by-de/2.0)"

@Composable
fun MapScreen(pois: List<Poi>) {
    var selected by remember { mutableStateOf<Poi?>(null) }
    val poisById = remember(pois) { pois.associateBy { it.id } }
    var filters by remember { mutableStateOf(PoiFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    val recycling = remember(pois, filters) {
        pois.filter { it.type == PoiType.RECYCLING && filters.matches(it) }.toGeoJson()
    }
    val mailboxes = remember(pois, filters) {
        pois.filter { it.type == PoiType.POST_BOX && filters.matches(it) }.toGeoJson()
    }
    LaunchedEffect(filters) {
        if (selected?.let(filters::matches) == false) selected = null
    }
    val scope = rememberCoroutineScope()
    lateinit var mapState: MapState
    mapState = rememberMapState(
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = 48.137, longitude = 11.575),
            zoom = 11.0,
        ),
    ) {
        for ((type, json) in listOf(PoiType.RECYCLING to recycling, PoiType.POST_BOX to mailboxes)) {
            PoiLayers(
                type = type,
                json = json,
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
    }

    val density = LocalDensity.current
    var attributionBarHeight by remember { mutableStateOf(0.dp) }

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
        if (showFilters) FilterSheet(filters, onChange = { filters = it }, onDismiss = { showFilters = false })
        SmallFloatingActionButton(
            onClick = {
                selected = null
                showFilters = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = attributionBarHeight + 56.dp),
        ) {
            BadgedBox(badge = { if (!filters.isDefault) Badge() }) {
                FilterIcon(LocalContentColor.current)
            }
        }
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
        textColor = const(Color.White),
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

private fun String.jsonEscape(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
