package de.munichspot.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.inset
import de.munichspot.data.freshnessHint
import kotlin.time.Clock
import kotlin.time.Instant
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionStyle
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MaplibreLogo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.MaterialTheme
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Geometry
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
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.munichspot.data.Place
import de.munichspot.data.PlaceIndex
import de.munichspot.data.PlaceKind
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import de.munichspot.data.ToiletStatus
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.asBoolean
import org.maplibre.compose.expressions.dsl.eq
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.TranslateAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import androidx.compose.runtime.produceState
import de.munichspot.shared.resources.Res
import org.maplibre.compose.layers.LocationIndicatorDefaults
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.expressions.value.SymbolAnchor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import de.munichspot.data.Freshness
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import de.munichspot.shared.resources.*

internal fun PoiType.pinColor(): Color = when (this) {
    PoiType.RECYCLING -> RecyclingGreen
    PoiType.CLOTHES -> ClothesRed
    PoiType.POST_BOX -> PostBoxYellow
    PoiType.DRINKING_WATER -> DrinkingWaterBlue
    PoiType.TOILET -> ToiletPurple
}

@Composable
internal fun PoiType.label(): String = stringResource(
    when (this) {
        PoiType.RECYCLING -> Res.string.type_recycling
        PoiType.CLOTHES -> Res.string.type_clothes
        PoiType.POST_BOX -> Res.string.type_post_box
        PoiType.DRINKING_WATER -> Res.string.type_drinking_water
        PoiType.TOILET -> Res.string.type_toilet
    },
)

@Composable
internal fun PlaceKind.label(): String = stringResource(
    when (this) {
        PlaceKind.STREET -> Res.string.kind_street
        PlaceKind.PARK -> Res.string.kind_park
        PlaceKind.SQUARE -> Res.string.kind_square
        PlaceKind.STATION -> Res.string.kind_station
        PlaceKind.LANDMARK -> Res.string.kind_landmark
        PlaceKind.DISTRICT -> Res.string.kind_district
    },
)

internal val DrinkingWaterText = Color(0xFF01579B)

private val RecyclingGreen = Color(0xFF2E7D32)
private val ClothesRed = Color(0xFFC62828)
private val PostBoxYellow = Color(0xFFF9A825)
private val DrinkingWaterBlue = Color(0xFF81D4FA)
private val ToiletPurple = Color(0xFF8E24AA)
private val PoiIconSize = 28.dp
private val ClusterIconSize = 34.dp
private val SelectedPoiIconSize = 42.dp
private val SelectedGlowRadius = 36.dp
internal val WarningRed = Color(0xFFD32F2F)
private val WarningMarkSize = 14.dp
private val SelectedWarningMarkSize = 20.dp
private val LocationBlue = Color(0xFF1A73E8)

internal enum class DataStatus { Loading, Ready, Failed }

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

@OptIn(FlowPreview::class)
@Composable
internal fun MapScreen(
    pois: List<Poi>,
    generated: Instant?,
    dataStatus: DataStatus,
    onRetry: () -> Unit,
    type: PoiType?,
    onTypeChange: (PoiType?) -> Unit,
    filters: PoiFilters,
    onFiltersChange: (PoiFilters) -> Unit,
    places: PlaceIndex?,
    anchor: Place?,
    onAnchorChange: (Place?) -> Unit,
) {
    var selected by remember { mutableStateOf<Poi?>(null) }
    val poisById = remember(pois) { pois.associateBy { it.id } }
    var showFilters by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selected, showFilters) { if (selected != null || showFilters) pickerOpen = false }
    LaunchedEffect(showFilters) { if (showFilters) selected = null }
    val geoJson = PoiType.entries.associateWith { t ->
        val f = if (type == t) filters else PoiFilters()
        val key = when (t) {
            PoiType.RECYCLING -> f.recyclingMaterials
            PoiType.TOILET -> f.toiletRequirements
            else -> null
        }
        remember(pois, key) { pois.filter { it.type == t && f.matches(it) }.toGeoJson() }
    }
    val selectedGeoJson = remember(selected) { listOfNotNull(selected).toGeoJson() }
    var selectedOnMap by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(type, filters) {
        if (type != null && selected?.let { it.type != type || !filters.matches(it) } == true) selected = null
    }
    val scope = rememberCoroutineScope()
    val locationState = rememberLocationState()
    val snackbar = remember { SnackbarHostState() }
    var centerRequested by remember { mutableStateOf(false) }
    var panned by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val boundary by produceState<String?>(null) {
        value = runCatching { Res.readBytes("files/munich_boundary.geojson").decodeToString() }.getOrNull()
    }
    val munich = remember(boundary) { boundary?.let { MunichArea.parse(it) } }
    lateinit var mapState: MapState
    mapState = rememberMapState(
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/" + if (dark) "fiord" else "positron"),
        // Roughly the 700 m framing on a phone; refined below once the viewport is known.
        initialCameraPosition = CameraPosition(center = Marienplatz, zoom = 13.9),
    ) {
        boundary?.let { CityVeilLayers(it, dark) }
        val sources = PoiType.entries.associateWith { t ->
            rememberGeoJsonSource(GeoJsonData.JsonString(geoJson.getValue(t))) {
                cluster = true
                clusterRadius = 50
                clusterMaxZoom = 14
            }
        }
        val visible = PoiType.entries.filter { type == null || type == it }
        // Each type's badges and counts sit right above its own icons, so they stack with the pin they belong to.
        for (t in visible) {
            PoiIconLayers(
                type = t,
                source = sources.getValue(t),
                mapState = mapState,
                scope = scope,
                islandVisible = type == null,
                onPoi = { id ->
                    poisById[id]?.let {
                        selected = it
                        showFilters = false
                    } != null
                },
            )
            if (t == PoiType.TOILET) PoiWarningLayer(t, sources.getValue(t))
            PoiBadgeLayers(t, sources.getValue(t), mapState, scope)
            PoiCountLayers(t, sources.getValue(t), mapState, scope)
        }
        SelectedPinLayers(rememberGeoJsonSource(GeoJsonData.JsonString(selectedGeoJson)))
        LocationIndicatorLayer(
            id = "user-location",
            locationState = locationState,
            visible = locationState.permission is LocationPermission.Granted,
            accuracyRadiusColor = LocationBlue.copy(alpha = 0.15f),
            accuracyRadiusBorderColor = LocationBlue.copy(alpha = 0.4f),
            topImage = LocationIndicatorDefaults.topImage(color = LocationBlue),
            bearingImage = null,
        )
        val anchorSource = rememberGeoJsonSource(GeoJsonData.JsonString(anchor.toGeoJson()))
        val anchorPainter = remember { AnchorPinPainter() }
        SymbolLayer(
            id = "anchor-pin",
            source = anchorSource,
            iconImage = image(anchorPainter, size = AnchorPinSize),
            iconAnchor = const(SymbolAnchor.Bottom),
            iconTranslate = const(DpOffset(0.dp, 2.dp)),
            iconTranslateAnchor = const(TranslateAnchor.Viewport),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
        )
    }

    val density = LocalDensity.current
    var mapBounds by remember { mutableStateOf(Rect.Zero) }
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }
    val topInset = WindowInsets.statusBars.getTop(density) + with(density) { 16.dp.toPx() }

    LaunchedEffect(selected?.id) {
        selectedOnMap = null
        val poi = selected ?: return@LaunchedEffect
        val position = Position(longitude = poi.lon, latitude = poi.lat)
        val layerIds = PoiType.entries.mapTo(mutableSetOf()) { "${it.name.lowercase()}-selected" }
        while (selectedOnMap != poi.id) {
            withFrameNanos { }
            val pin = mapState.screenLocationFromPosition(position) ?: continue
            val o = poi.type.pinOffset()
            val x = pin.x + o.x
            val y = pin.y + o.y
            val rendered = mapState.queryRenderedFeatures(DpRect(x - 6.dp, y - 6.dp, x + 6.dp, y + 6.dp), layerIds)
            if (rendered.any { it.id?.jsonPrimitive?.content == poi.id }) selectedOnMap = poi.id
        }
    }

    var panBack by remember { mutableStateOf<Position?>(null) }
    LaunchedEffect(selected?.id) {
        val poi = selected
        if (poi == null) {
            val back = panBack
            panBack = null
            sheetBounds = null
            if (back != null) mapState.animateCamera(CameraUpdate(center = back), CameraAnimation.Ease { duration = 300.milliseconds })
            return@LaunchedEffect
        }
        val position = Position(longitude = poi.lon, latitude = poi.lat)
        suspend fun reveal(sheet: Rect) {
            val pin = mapState.screenLocationFromPosition(position) ?: return
            val center = mapState.screenLocationFromPosition(mapState.cameraPosition.center) ?: return
            val pinY = mapBounds.top + with(density) { (pin.y + poi.type.pinOffset().y).toPx() }
            val pinX = mapBounds.left + with(density) { (pin.x + poi.type.pinOffset().x).toPx() }
            val reach = with(density) { (SelectedPoiIconSize.toPx() / 2 + 16.dp.toPx()) }
            val covered = pinX + reach > sheet.left && pinX - reach < sheet.right && pinY + reach > sheet.top
            if (!covered && pinY >= topInset) return
            val shift = with(density) { ((topInset + sheet.top) / 2 - pinY).toDp() }
            val target = mapState.positionFromScreenLocation(DpOffset(center.x, center.y - shift)) ?: return
            if (panBack == null) panBack = mapState.cameraPosition.center
            mapState.animateCamera(CameraUpdate(center = target), CameraAnimation.Ease { duration = 300.milliseconds })
        }
        val first = snapshotFlow { sheetBounds }.filterNotNull().first()
        // The sheet keeps its size while sliding up, so its final top is the map's bottom minus its height,
        // or half the screen when a tall sheet opens partially expanded.
        val top = maxOf(mapBounds.bottom - first.height, mapBounds.center.y)
        reveal(Rect(first.left, top, first.right, mapBounds.bottom))
        reveal(snapshotFlow { sheetBounds }.filterNotNull().debounce(150).first())
    }
    var attributionBarHeight by remember { mutableStateOf(0.dp) }
    var logoHeight by remember { mutableStateOf(0.dp) }
    val pickerBottom = attributionBarHeight + maxOf(logoHeight, 40.dp) + MapOverlay.Spacing + 8.dp
    val pickerTop = pickerBottom + CategoryPickerHeight

    val currentAnchor by rememberUpdatedState(anchor)
    LaunchedEffect(Unit) {
        mapState.animateCameraToBounds(boundsAround(Marienplatz), animation = CameraAnimation.Ease { duration = Duration.ZERO })
        val permission = locationState.permission
        if (!locationAsked && permission is LocationPermission.NotGranted && permission.canRequest != false) {
            locationAsked = true
            locationState.requestPermission()
        }
        snapshotFlow { locationState.permission is LocationPermission.Granted }.first { it }
        val position = withTimeoutOrNull(10.seconds) {
            snapshotFlow { locationState.lastLocation?.position }.first { it != null }
        } ?: return@LaunchedEffect
        val area = withTimeoutOrNull(3.seconds) { snapshotFlow { boundary }.first { it != null } }?.let { MunichArea.parse(it) }
        if (!area.containsOrBox(position)) {
            snackbar.showSnackbar(getString(Res.string.outside_munich))
        } else if (!panned && currentAnchor == null) {
            mapState.animateCameraToBounds(boundsAround(position))
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.Gesture }
            .collect { if (it) panned = true }
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
        if (position != null && !munich.containsOrBox(position)) {
            snackbar.showSnackbar(getString(Res.string.outside_munich))
        } else if (position != null) {
            mapState.animateCamera(CameraUpdate(center = position, zoom = 15.0))
            panned = false
        } else {
            snackbar.showSnackbar(getString(Res.string.location_unavailable))
        }
        centerRequested = false
    }

    // With none of the category's pins on screen, slowly zooms out (centre fixed) to the nearest two.
    suspend fun revealNearest() {
        if (type == null || showFilters || mapBounds == Rect.Zero) return
        val camera = mapState.cameraPosition
        val center = mapState.screenLocationFromPosition(camera.center) ?: return
        val zoom = with(density) {
            revealZoom(
                pois = pois.filter { it.type == type && filters.matches(it) },
                camera = camera,
                mapSize = mapBounds.size.toDpSize(),
                center = center,
                top = (topInset - mapBounds.top).toDp(),
                bottom = pickerTop,
            )
        } ?: return
        mapState.animateCamera(CameraUpdate(zoom = zoom), CameraAnimation.Ease { duration = 1200.milliseconds })
    }

    // Picking a category, or closing the filter sheet, reveals the nearest pins.
    LaunchedEffect(type, filters, showFilters) { revealNearest() }

    var hadAnchor by remember { mutableStateOf(anchor != null) }
    LaunchedEffect(anchor) {
        val cleared = hadAnchor && anchor == null
        hadAnchor = anchor != null
        if (cleared) {
            val me = locationState.lastLocation?.position
            if (me != null && munich.containsOrBox(me)) {
                mapState.animateCameraToBounds(boundsAround(me))
                panned = false
                revealNearest()
            }
            return@LaunchedEffect
        }
        val a = anchor ?: return@LaunchedEffect
        selected = null
        panned = true
        val radius = if (a.kind == PlaceKind.DISTRICT) 1.5 else 0.7
        mapState.animateCameraToBounds(boundsAround(Position(longitude = a.lon, latitude = a.lat), radius))
        revealNearest()
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(
            state = mapState,
            modifier = Modifier.fillMaxSize().onGloballyPositioned { mapBounds = Rect(it.positionOnScreen(), it.size.toSize()) },
            // Keeps MapLibre's own attribution button clear of our attribution bar.
            viewportInsets = PaddingValues(bottom = attributionBarHeight),
            overlay = {
                MapControls(mapState) { logoHeight = it }
                selected?.takeIf { it.id != selectedOnMap }?.let {
                    val o = it.type.pinOffset()
                    Box(Modifier.placedAt(Position(longitude = it.lon, latitude = it.lat)).offset(o.x, o.y)) { SelectedPin(it) }
                }
            },
            interactions = MapInteractions {
                callbacks { click { onUnhandled { _ ->
                    selected = null
                    ClickResult.Consume
                } } }
            },
        )
        if (showFilters && type != null) FilterSheet(type, filters, onChange = onFiltersChange, onDismiss = { showFilters = false })
        val wide = with(density) { mapBounds.width.toDp() } >= WideBreakpoint
        val locate = {
            if (locationState.permission !is LocationPermission.Granted) locationState.requestPermission()
            if (anchor != null) {
                // Locate does its own fly-to, so skip the anchor-cleared fly-back.
                hadAnchor = false
                onAnchorChange(null)
            }
            centerRequested = true
        }
        val filterButton: @Composable () -> Unit = {
            if (type != null && type.hasFilters()) SmallFloatingActionButton(
                onClick = {
                    selected = null
                    showFilters = true
                },
            ) {
                BadgedBox(badge = { if (!filters.isDefault(type)) Badge() }) {
                    FilterIcon(LocalContentColor.current)
                }
            }
        }
        if (wide) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = pickerBottom)
                    .height(CategoryPickerHeight),
            ) {
                LocatePill(panned && !pickerOpen, locate, horizontal = true)
                filterButton()
            }
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = pickerTop + 8.dp),
        ) {
            LocatePill(panned && !pickerOpen, locate)
            filterButton()
        }
        DataNotice(
            dataStatus, onRetry,
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp, start = 16.dp, end = 16.dp),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = pickerTop + 8.dp),
        )
        CategoryPicker(
            type, onTypeChange, places, anchor, onAnchorChange, pickerOpen,
            onExpandedChange = {
                pickerOpen = it
                if (it) {
                    selected = null
                    showFilters = false
                }
            },
            bottomInset = pickerBottom,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { attributionBarHeight = with(density) { it.height.toDp() } }
                .navigationBarsPadding(),
        ) {
            val hint = when (val f = remember(generated) { freshnessHint(generated, Clock.System.now()) }) {
                Freshness.VeryOld -> stringResource(Res.string.data_very_old)
                is Freshness.AsOf -> stringResource(Res.string.data_as_of, f.date)
                null -> null
            }
            val attribution = stringResource(Res.string.attribution)
            Text(
                text = if (hint != null) "$attribution · $hint" else attribution,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        PoiSheet(selected, onBounds = { b -> sheetBounds = b }) { selected = null }
    }
}

// MapOverlay.Default, but with the scale bar at the bottom left, above the MapLibre logo.
@Composable
private fun MapControls(mapState: MapState, onLogoHeight: (Dp) -> Unit) {
    val density = LocalDensity.current
    val insets = LocalViewportInsets.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(insets)
            .consumeWindowInsets(insets)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(MapOverlay.Spacing),
    ) {
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DisappearingScaleBar(
                metersPerDp = { mapState.viewport?.metersPerDpAtCenter ?: 0.0 },
                zoom = { mapState.cameraPosition.zoom },
            )
            Box(Modifier.onSizeChanged { onLogoHeight(with(density) { it.height.toDp() }) }) { MaplibreLogo() }
        }
        DisappearingCompassButton(Modifier.align(Alignment.TopEnd))
        // MapLibre's defaults are always white with black text; follow the theme instead.
        val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
        val content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        ExpandingAttributionButton(
            Modifier.align(Alignment.BottomEnd),
            toggleButton = { onClick ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick, role = Role.Button),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        AttributionDefaults.icon(),
                        AttributionDefaults.contentDescription(),
                        Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(content),
                    )
                }
            },
            expandedStyle = AttributionStyle(container, content),
            collapsedStyle = AttributionStyle(container.copy(alpha = 0f), content.copy(alpha = 0f)),
        )
    }
}

private val BadgeOffset = DpOffset(13.dp, (-13).dp)
private val WarningMarkOffset = DpOffset(9.dp, (-9).dp)
private val SelectedWarningMarkOffset = DpOffset(14.dp, (-14).dp)
private val ClothesOffset = DpOffset(16.dp, (-10).dp)

private fun PoiType.pinOffset(): DpOffset = if (this == PoiType.CLOTHES) ClothesOffset else DpOffset.Zero
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
private fun CityVeilLayers(boundary: String, dark: Boolean) {
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
private fun PoiIconLayers(
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

private val AnchorPinSize = DpSize(24.dp, 32.dp)

private fun Place?.toGeoJson() = """{"type":"FeatureCollection","features":[""" +
    (this?.let { """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]}}""" } ?: "") + "]}"

private class AnchorPinPainter : Painter() {
    override val intrinsicSize = Size.Unspecified
    override fun DrawScope.onDraw() {
        val pad = 2.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        translate(pad + 0.5.dp.toPx(), pad + 1.dp.toPx()) { drawPath(teardropPath(w, h), Color.Black.copy(alpha = 0.3f)) }
        translate(pad, pad) {
            drawPath(teardropPath(w, h), Color.White, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
            drawPin(AnchorRed, w, h, dot = Color.White)
        }
    }
}

@Composable
private fun SelectedPin(poi: Poi) {
    Canvas(Modifier.size(SelectedGlowRadius * 2)) {
        drawSelectedPin(poi.type)
        if (poi.possiblyDefect) {
            val s = SelectedWarningMarkSize.toPx()
            val l = center.x + SelectedWarningMarkOffset.x.toPx() - s / 2
            val t = center.y + SelectedWarningMarkOffset.y.toPx() - s / 2
            inset(l, t, size.width - l - s, size.height - t - s) { drawWarningMark() }
        }
    }
}

private fun DrawScope.drawSelectedPin(type: PoiType) {
    val glow = type.pinColor().copy(alpha = 0.6f)
    drawCircle(Brush.radialGradient(0f to glow, 0.4f to glow, 1f to Color.Transparent, radius = size.width / 2))
    inset((size.width - SelectedPoiIconSize.toPx()) / 2) { drawCategoryIcon(type, ring = true) }
}

private class SelectedPinPainter(private val type: PoiType) : Painter() {
    override val intrinsicSize = Size.Unspecified
    override fun DrawScope.onDraw() = drawSelectedPin(type)
}

/** The selected pin's highlight as layers, so it pans with the map; the overlay [SelectedPin] only bridges until it renders. */
@Composable
@MaplibreComposable
private fun SelectedPinLayers(source: GeoJsonSource) {
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
private fun PoiBadgeLayers(type: PoiType, source: GeoJsonSource, mapState: MapState, scope: CoroutineScope) {
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
private fun PoiWarningLayer(type: PoiType, source: GeoJsonSource) {
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
private fun PoiCountLayers(type: PoiType, source: GeoJsonSource, mapState: MapState, scope: CoroutineScope) {
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

/** Hint only: derived from dataset free text, so it adds a warning mark and never filters. */
internal val Poi.possiblyDefect: Boolean get() = toilet?.status == ToiletStatus.DEFECT

private fun List<Poi>.toGeoJson(): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    this@toGeoJson.forEachIndexed { i, p ->
        if (i > 0) append(',')
        append("""{"type":"Feature","id":"${p.id.jsonEscape()}",""")
        append(""""geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]},""")
        append(""""properties":{"type":"${p.type.name}","name":"${p.name.jsonEscape()}"${if (p.possiblyDefect) ""","warn":true""" else ""}}}""")
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

private val WideBreakpoint = 680.dp

@Composable
private fun DataNotice(status: DataStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = status != DataStatus.Ready,
        modifier = modifier,
        enter = fadeIn(tween(MorphMillis)),
        exit = fadeOut(tween(MorphMillis)),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Text(
                    text = stringResource(if (status == DataStatus.Failed) Res.string.no_data else Res.string.loading_data),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 0.dp).weight(1f, fill = false),
                )
                if (status == DataStatus.Failed) TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
                else Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun LocatePill(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val description = stringResource(Res.string.my_location)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(MorphMillis)) + scaleIn(tween(MorphMillis)) +
            if (horizontal) expandHorizontally(tween(MorphMillis)) else EnterTransition.None,
        exit = fadeOut(tween(MorphMillis)) + scaleOut(tween(MorphMillis)) +
            if (horizontal) shrinkHorizontally(tween(MorphMillis)) else ExitTransition.None,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(end = if (horizontal) 8.dp else 0.dp)
                .size(CategoryPickerHeight)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) { LocateIcon(MaterialTheme.colorScheme.primary, Modifier.size(24.dp)) }
        }
    }
}
