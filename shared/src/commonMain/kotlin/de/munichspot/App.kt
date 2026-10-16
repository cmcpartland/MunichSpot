package de.munichspot

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.munichspot.data.Place
import de.munichspot.data.PlaceIndex
import de.munichspot.data.PlaceSaver
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import de.munichspot.data.fetchLatestPois
import de.munichspot.data.loadPlaces
import de.munichspot.data.loadStartupPois
import de.munichspot.map.MapScreen
import de.munichspot.map.PoiFilters

@Composable
fun App() {
    val pois by produceState(emptyList<Poi>()) {
        value = loadStartupPois()
        fetchLatestPois()?.let { value = it }
    }
    val places by produceState<PlaceIndex?>(null) { value = PlaceIndex(loadPlaces()) }
    var anchor by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf<Place?>(null) }
    var type by rememberSaveable { mutableStateOf<PoiType?>(null) }
    var filters by remember { mutableStateOf(PoiFilters()) }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        MapScreen(pois, type, onTypeChange = { type = it }, filters, onFiltersChange = { filters = it }, places, anchor, onAnchorChange = { anchor = it })
    }
}
