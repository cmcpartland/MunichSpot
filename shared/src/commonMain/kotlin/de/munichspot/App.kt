package de.munichspot

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.munichspot.data.FetchResult
import de.munichspot.data.Place
import de.munichspot.data.PlaceData
import de.munichspot.data.PlaceSaver
import de.munichspot.data.PoiData
import de.munichspot.data.PoiType
import de.munichspot.data.fetchLatestPois
import de.munichspot.data.fetchLatestPlaces
import de.munichspot.data.loadStartupPlaces
import de.munichspot.data.loadStartupPois
import de.munichspot.data.rememberBerlinNow
import de.munichspot.map.DataStatus
import de.munichspot.map.MapScreen
import de.munichspot.map.PoiFilters
import kotlinx.coroutines.launch

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
                when (val result = fetchLatestPois(conditional = data != null)) {
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
    LifecycleResumeEffect(loaded) {
        if (loaded) refresh()
        onPauseOrDispose {}
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
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        MapScreen(
            data?.pois.orEmpty(), data?.generated, status,
            onRetry = {
                failed = false
                refresh()
            },
            type, onTypeChange = { type = it }, filters, onFiltersChange = { filters = it }, places?.index, anchor, onAnchorChange = { anchor = it }, now,
        )
    }
}
