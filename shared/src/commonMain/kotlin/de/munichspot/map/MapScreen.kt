package de.munichspot.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.munichspot.Log
import de.munichspot.data.Place
import de.munichspot.data.PlaceIndex
import de.munichspot.data.PlaceKind
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import de.munichspot.shared.resources.*
import de.munichspot.shared.resources.Res
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.expressions.value.TranslateAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LocationIndicatorDefaults
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationTrackingStatus
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val SelectedPollNanos = 1_500_000_000L

private val LocationBlue = Color(0xFF1A73E8)

internal enum class DataStatus { Loading, Ready, Failed }

private var locationAsked = false

private val StyleRetryDelays = listOf(5.seconds, 15.seconds, 60.seconds)

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
    now: LocalDateTime,
) {
    var selected by remember { mutableStateOf<Poi?>(null) }
    val poisById = remember(pois) { pois.associateBy { it.id } }
    var showFilters by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selected, showFilters) { if (selected != null || showFilters) pickerOpen = false }
    LaunchedEffect(showFilters) { if (showFilters) selected = null }
    val currentNow by rememberUpdatedState(now)
    // Only changes when a toilet, recycling centre or bookcase opens or closes, so their sources aren't rebuilt every minute.
    val closedIds by remember(pois) {
        derivedStateOf { pois.filter { it.knownClosed(currentNow) }.mapTo(mutableSetOf()) { it.id } as Set<String> }
    }
    val geoJson = PoiType.entries.associateWith { t ->
        val f = if (type == t) filters else PoiFilters()
        val key = when (t) {
            PoiType.RECYCLING -> f.recyclingMaterials
            PoiType.TOILET -> f.toiletRequirements to closedIds
            PoiType.RECYCLING_CENTRE -> f.centreRequirements to closedIds
            PoiType.PUBLIC_BOOKCASE -> closedIds
            else -> null
        }
        remember(pois, key) { pois.filter { it.type == t && f.matches(it, currentNow) }.toGeoJson(closedIds) }
    }
    val selectedClosed = selected?.id in closedIds
    val selectedGeoJson = remember(selected, selectedClosed) { listOfNotNull(selected).toGeoJson(closedIds) }
    var selectedOnMap by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(type, filters, closedIds) {
        if (type != null && selected?.let { it.type != type || !filters.matches(it, currentNow) } == true) selected = null
    }
    val scope = rememberCoroutineScope()
    val locationState = rememberLocationState()
    val snackbar = remember { SnackbarHostState() }
    var centerRequested by remember { mutableStateOf(false) }
    var panned by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val boundary by produceState<String?>(null) {
        value = runCatching { Res.readBytes("files/munich_boundary.geojson").decodeToString() }
            .onFailure { Log.w("Map", "boundary resource failed to load", it) }.getOrNull()
    }
    val munich = remember(boundary) { boundary?.let { MunichArea.parse(it) } }
    var styleAttempt by remember { mutableIntStateOf(0) }
    lateinit var mapState: MapState
    mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(
            "https://tiles.openfreemap.org/styles/" + (if (dark) "fiord" else "positron") + if (styleAttempt > 0) "?r=$styleAttempt" else "",
        ),
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
        val clearSelection = { selected = null }
        // Each type's badges and counts sit right above its own icons, so they stack with the pin they belong to.
        for (t in visible) {
            PoiIconLayers(
                type = t,
                source = sources.getValue(t),
                mapState = mapState,
                scope = scope,
                islandVisible = type == null,
                onCluster = clearSelection,
                onPoi = { id ->
                    poisById[id]?.let {
                        selected = it
                        showFilters = false
                    } != null
                },
            )
            if (t == PoiType.TOILET) PoiWarningLayer(t, sources.getValue(t))
            if (t.hasSchedule()) PoiClosedLayer(t, sources.getValue(t))
            PoiBadgeLayers(t, sources.getValue(t), mapState, scope, clearSelection)
            PoiCountLayers(t, sources.getValue(t), mapState, scope, clearSelection)
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
        var start = -1L
        while (selectedOnMap != poi.id) {
            val t = withFrameNanos { it }
            if (start < 0) start = t
            if (t - start > SelectedPollNanos) {
                Log.d("MapScreen", "selected pin not found on screen, using overlay")
                break
            }
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
            .filter { it }
            .collect {
                panned = true
                if (selected != null) {
                    panBack = null
                    selected = null
                }
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
                pois = pois.filter { it.type == type && filters.matches(it, now) },
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
                    Box(Modifier.placedAt(Position(longitude = it.lon, latitude = it.lat)).offset(o.x, o.y)) { SelectedPin(it, selectedClosed) }
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
        val loadState = mapState.style.loadState
        var styleSlow by remember { mutableStateOf(false) }
        LaunchedEffect(loadState, styleAttempt) {
            styleSlow = false
            when (loadState) {
                StyleLoadState.Ready -> {}
                is StyleLoadState.Failed -> {}
                else -> {
                    delay(10.seconds)
                    Log.w("Map", "style still loading after 10 s")
                    styleSlow = true
                }
            }
        }
        val styleFailed = loadState is StyleLoadState.Failed || styleSlow
        LaunchedEffect(loadState) { if (loadState is StyleLoadState.Failed) Log.w("Map", "style failed: ${loadState.reason}") }
        val currentFailed by rememberUpdatedState(styleFailed)
        LifecycleResumeEffect(Unit) {
            if (currentFailed) styleAttempt++
            onPauseOrDispose {}
        }
        var autoRetries by remember { mutableIntStateOf(0) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(loadState) { if (loadState == StyleLoadState.Ready) autoRetries = 0 }
        // Only on Failed: while Loading MapLibre retries by itself, and a restart would cut off a slow load.
        val styleError = loadState is StyleLoadState.Failed
        LaunchedEffect(styleError) {
            if (!styleError) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(StyleRetryDelays.getOrElse(autoRetries) { StyleRetryDelays.last() })
                    autoRetries++
                    styleAttempt++
                }
            }
        }
        // A successful poi download means the network is back.
        LaunchedEffect(pois.isNotEmpty()) { if (pois.isNotEmpty() && currentFailed) styleAttempt++ }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            NoticePill(styleFailed, stringResource(Res.string.map_failed), { styleAttempt++ })
            DataNotice(dataStatus, onRetry)
        }
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
        AttributionBar(generated, { attributionBarHeight = it }, Modifier.align(Alignment.BottomCenter))
        PoiSheet(
            selected,
            now,
            onBounds = { b -> sheetBounds = b },
            onOpenFailed = { scope.launch { snackbar.showSnackbar(getString(Res.string.open_failed)) } },
        ) { selected = null }
        val sheetTop = sheetBounds?.takeIf { selected != null }?.let { with(density) { (mapBounds.bottom - it.top).toDp() } }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (sheetTop ?: pickerTop) + 8.dp),
        )
    }
}
