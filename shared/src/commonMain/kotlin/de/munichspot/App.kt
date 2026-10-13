package de.munichspot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import de.munichspot.map.MapScreen

@Composable
fun App() {
    MaterialTheme { MapScreen() }
}
