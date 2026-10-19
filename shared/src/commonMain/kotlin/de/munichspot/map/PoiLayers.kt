package de.munichspot.map

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.munichspot.data.PoiType
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.asBoolean
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.TranslateAnchor
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point

private val PoiIconSize = 28.dp
private val ClusterIconSize = 34.dp
internal val SelectedPoiIconSize = 42.dp
internal val SelectedGlowRadius = 36.dp
private val WarningMarkSize = 14.dp
internal val SelectedWarningMarkSize = 20.dp
private val BadgeOffset = DpOffset(13.dp, (-13).dp)
private val WarningMarkOffset = DpOffset(9.dp, (-9).dp)
internal val SelectedWarningMarkOffset = DpOffset(14.dp, (-14).dp)
private val ClothesOffset = DpOffset(16.dp, (-10).dp)

internal fun PoiType.pinOffset(): DpOffset = if (this == PoiType.CLOTHES) ClothesOffset else DpOffset.Zero
private operator fun DpOffset.plus(o: DpOffset) = DpOffset(x + o.x, y + o.y)
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

/** Light veil over everything outside the city boundary, plus a faint boundary line. */
@Composable
@MaplibreComposable
internal fun CityVeilLayers(boundary: String, dark: Boolean) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(boundary))
    FillLayer(
        id = "city-veil",
        source = source,
        color = const(if (dark) Color.Black else Color.White),
        opacity = const(if (dark) 0.35f else 0.4f),
    )
    LineLayer(
        id = "city-boundary",
        source = source,
        color = const(if (dark) Color(0xFFB0B8CC) else Color(0xFF7A8290)),
        opacity = const(0.35f),
        width = const(1.dp),
    )
}

@Composable
@MaplibreComposable
internal fun PoiIconLayers(
    type: PoiType,
    source: GeoJsonSource,
    mapState: MapState,
    scope: CoroutineScope,
    islandVisible: Boolean,
    onPoi: (String?) -> Boolean,
) {
    val name = type.name.lowercase()
    val painter = remember(type) { CategoryIconPainter(type) }
    SymbolLayer(
        id = "$name-clusters",
        source = source,
        filter = IsCluster,
        iconImage = image(painter, size = DpSize(ClusterIconSize, ClusterIconSize)),
        iconTranslate = const(type.pinOffset()),
        iconTranslateAnchor = const(TranslateAnchor.Viewport),
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
        iconTranslate = const(type.pinOffset()),
        iconTranslateAnchor = const(TranslateAnchor.Viewport),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        hitPadding = 8.dp,
        onClick = { features ->
            val feature = features.firstOrNull()
            val id = feature?.id?.jsonPrimitive?.content
            if (type == PoiType.CLOTHES && islandVisible && closerToIsland(feature, mapState)) {
                ClickResult.Pass
            } else if (onPoi(id)) ClickResult.Consume else ClickResult.Pass
        },
    )
}

/** True when the tap is nearer the island's own centre than the shifted clothes pin. */
private fun ClickEvent.closerToIsland(feature: Feature<Geometry, JsonObject?>?, mapState: MapState): Boolean {
    val point = (feature?.geometry as? Point)?.coordinates ?: return false
    val island = mapState.screenLocationFromPosition(point) ?: return false
    fun distance(o: DpOffset) = hypot(o.x.value - screenOffset.x.value, o.y.value - screenOffset.y.value)
    return distance(island) < distance(island + ClothesOffset)
}

/** The selected pin's highlight as layers, so it pans with the map; the overlay [SelectedPin] only bridges until it renders. */
@Composable
@MaplibreComposable
internal fun SelectedPinLayers(source: GeoJsonSource) {
    for (t in PoiType.entries) {
        val painter = remember(t) { SelectedPinPainter(t) }
        SymbolLayer(
            id = "${t.name.lowercase()}-selected",
            source = source,
            filter = feature["type"].asString() eq const(t.name),
            iconImage = image(painter, size = DpSize(SelectedGlowRadius * 2, SelectedGlowRadius * 2)),
            iconTranslate = const(t.pinOffset()),
            iconTranslateAnchor = const(TranslateAnchor.Viewport),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
        )
    }
    val warning = remember { WarningMarkPainter() }
    SymbolLayer(
        id = "selected-warning",
        source = source,
        filter = feature["warn"].asBoolean() eq const(true),
        iconImage = image(warning, size = DpSize(SelectedWarningMarkSize, SelectedWarningMarkSize)),
        iconTranslate = const(SelectedWarningMarkOffset),
        iconTranslateAnchor = const(TranslateAnchor.Viewport),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** Count badge at the cluster icon's top-right corner: a white disc with a type-coloured border. */
@Composable
@MaplibreComposable
internal fun PoiBadgeLayers(type: PoiType, source: GeoJsonSource, mapState: MapState, scope: CoroutineScope) {
    CircleLayer(
        id = "${type.name.lowercase()}-cluster-badge",
        source = source,
        filter = IsCluster,
        radius = const(10.dp),
        color = const(Color.White),
        strokeColor = const(type.pinColor()),
        strokeWidth = const(2.dp),
        translate = const(BadgeOffset + type.pinOffset()),
        translateAnchor = const(TranslateAnchor.Viewport),
        onClick = zoomToCluster(source, mapState, scope),
    )
}

/** Small warning mark at the pin's top-right corner for pois the dataset flags as possibly defective. */
@Composable
@MaplibreComposable
internal fun PoiWarningLayer(type: PoiType, source: GeoJsonSource) {
    val painter = remember { WarningMarkPainter() }
    SymbolLayer(
        id = "${type.name.lowercase()}-warning",
        source = source,
        filter = !IsCluster and (feature["warn"].asBoolean() eq const(true)),
        iconImage = image(painter, size = DpSize(WarningMarkSize, WarningMarkSize)),
        iconTranslate = const(WarningMarkOffset + type.pinOffset()),
        iconTranslateAnchor = const(TranslateAnchor.Viewport),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

@Composable
@MaplibreComposable
internal fun PoiCountLayers(type: PoiType, source: GeoJsonSource, mapState: MapState, scope: CoroutineScope) {
    SymbolLayer(
        id = "${type.name.lowercase()}-cluster-count",
        source = source,
        filter = IsCluster,
        textField = format(span(feature["point_count_abbreviated"].asString())),
        textFont = const(listOf("Noto Sans Bold")),
        textSize = const(11.sp),
        textColor = const(Color(0xFF212121)),
        textTranslate = const(BadgeOffset + type.pinOffset()),
        textTranslateAnchor = const(TranslateAnchor.Viewport),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        onClick = zoomToCluster(source, mapState, scope),
    )
}
