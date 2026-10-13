package de.munichspot.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.munichspot.data.Material
import de.munichspot.data.Poi
import de.munichspot.data.PoiType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoiSheet(poi: Poi, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val (title, source) = when (poi.type) {
        PoiType.RECYCLING -> "Wertstoffinsel" to "Quelle: Landeshauptstadt München (dl-by-de/2.0)"
        PoiType.POST_BOX -> "Briefkasten" to "Quelle: © OpenStreetMap contributors"
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(14.dp).clip(CircleShape).background(poi.type.pinColor()),
                )
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Text(poi.name, style = MaterialTheme.typography.bodyLarge)
            if (poi.type == PoiType.RECYCLING) {
                val lines = poi.materials.accepted()
                if (lines.isEmpty()) {
                    Text(
                        "Keine Containerdaten verfügbar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            Button(onClick = { uriHandler.openUri(navigationUri(poi)) }) { Text("Navigate") }
            Text(source, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun List<Material>.accepted(): List<String> = buildList {
    val glass = listOf(
        Material.GLASS_BROWN to "braun",
        Material.GLASS_GREEN to "grün",
        Material.GLASS_WHITE to "weiß",
    ).filter { it.first in this@accepted }
    if (glass.isNotEmpty()) add("Glas (${glass.joinToString(" · ") { it.second }})")
    if (Material.PACKAGING in this@accepted) add("Leichtverpackungen")
    if (Material.CLOTHING in this@accepted) add("Altkleider")
}
