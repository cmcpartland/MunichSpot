package de.munichspot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import de.munichspot.data.Poi
import de.munichspot.data.loadPois
import de.munichspot.map.MapScreen

@Composable
fun App() {
    val pois by produceState(emptyList<Poi>()) { value = loadPois() }
    MaterialTheme { MapScreen(pois) }
}
