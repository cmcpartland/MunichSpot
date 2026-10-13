package de.munichspot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import de.munichspot.data.Poi
import de.munichspot.data.fetchLatestPois
import de.munichspot.data.loadStartupPois
import de.munichspot.map.MapScreen

@Composable
fun App() {
    val pois by produceState(emptyList<Poi>()) {
        value = loadStartupPois()
        fetchLatestPois()?.let { value = it }
    }
    MaterialTheme { MapScreen(pois) }
}
