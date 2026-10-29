package de.publicpoint

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.publicpoint.data.FetchResult
import de.publicpoint.data.Place
import de.publicpoint.data.PlaceData
import de.publicpoint.data.PlaceSaver
import de.publicpoint.data.PoiData
import de.publicpoint.data.PoiType
import de.publicpoint.data.fetchLatestPois
import de.publicpoint.data.fetchLatestPlaces
import de.publicpoint.data.loadStartupPlaces
import de.publicpoint.data.loadStartupPois
import de.publicpoint.data.rememberBerlinNow
import de.publicpoint.map.CameraCarry
import de.publicpoint.map.DataStatus
import de.publicpoint.map.MapScreen
import de.publicpoint.map.PoiFilters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RETRY_DELAYS_MS = listOf(5_000L, 15_000L, 60_000L)

@Composable
fun App() {
    var data by remember { mutableStateOf<PoiData?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                val result = fetchLatestPois(conditional = data != null)
                Log.d("PoiData", "refresh: ${result::class.simpleName}")
                when (result) {
                    is FetchResult.Updated -> {
                        data = result.data
                        failed = false
                    }
                    FetchResult.Unchanged -> failed = false
                    FetchResult.Failed -> failed = data == null
                }
            } finally {
                refreshing = false
            }
        }
    }
    var places by remember { mutableStateOf<PlaceData?>(null) }
    LaunchedEffect(Unit) {
        data = loadStartupPois()
        loaded = true
    }
    // Places change weekly at most, so they are refreshed once per app start, not on every resume.
    LaunchedEffect(Unit) {
        places = loadStartupPlaces()
        (fetchLatestPlaces(places?.generated) as? FetchResult.Updated)?.let { places = it.data }
    }
    LifecycleStartEffect(loaded) {
        if (loaded) refresh()
        onStopOrDispose {}
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(failed, data == null) {
        if (!failed || data != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var attempt = 0
            while (failed && data == null) {
                delay(RETRY_DELAYS_MS.getOrElse(attempt++) { RETRY_DELAYS_MS.last() })
                if (failed && data == null) refresh()
            }
        }
    }
    var anchor by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf<Place?>(null) }
    var type by rememberSaveable { mutableStateOf<PoiType?>(null) }
    var filters by remember { mutableStateOf(PoiFilters()) }
    val now = rememberBerlinNow()
    val status = when {
        data != null || !loaded -> DataStatus.Ready
        failed -> DataStatus.Failed
        else -> DataStatus.Loading
    }
    val dark = isSystemInDarkTheme()
    val camera = remember { CameraCarry() }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        // A new map per style: on iOS a live base style swap leaves the pin images under the wrong ids.
        key(dark) {
            MapScreen(
                data?.pois.orEmpty(), data?.generated, status,
                onRetry = {
                    failed = false
                    refresh()
                },
                type, onTypeChange = { type = it }, filters, onFiltersChange = { filters = it }, places?.index, anchor, onAnchorChange = { anchor = it }, now, camera,
            )
        }
    }
}
