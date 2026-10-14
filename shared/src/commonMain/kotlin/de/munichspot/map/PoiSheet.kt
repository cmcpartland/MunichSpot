package de.munichspot.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import de.munichspot.data.PostBoxInfo
import de.munichspot.data.ToiletInfo
import de.munichspot.data.ToiletStatus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoiSheet(poi: Poi, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val (title, source) = when (poi.type) {
        PoiType.RECYCLING -> "Wertstoffinsel" to "Quelle: Landeshauptstadt München (dl-by-de/2.0)"
        PoiType.POST_BOX -> "Briefkasten" to "Quelle: © OpenStreetMap contributors"
        PoiType.DRINKING_WATER -> "Trinkbrunnen" to "Quelle: Landeshauptstadt München (dl-by-de/2.0)"
        PoiType.TOILET -> "Öffentliche Toilette" to "Quelle: Landeshauptstadt München (dl-by-de/2.0)"
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(14.dp).clip(CircleShape).background(poi.type.pinColor()),
                )
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (poi.name != title) Text(poi.name, style = MaterialTheme.typography.bodyLarge)
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
            if (poi.type == PoiType.DRINKING_WATER) {
                poi.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (poi.note?.contains("24/7") != true) {
                    Text(
                        "Viele Trinkbrunnen sind im Winter abgeschaltet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            poi.toilet?.let { ToiletDetails(it) }
            poi.postBox?.let { PostBoxDetails(it) }
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

@Composable
private fun ToiletDetails(toilet: ToiletInfo) {
    val body = MaterialTheme.typography.bodyMedium
    toilet.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        toilet.hours?.let { Text("Geöffnet: $it", style = body) }
        toilet.price?.let { Text(if (it == 0.0) "Kostenlos" else formatEuro(it), style = body) }
        toilet.floor?.let { Text("Etage: $it", style = body) }
        listOfNotNull(
            "Barrierefrei".takeIf { toilet.accessible },
            "Wickeltisch".takeIf { toilet.changing },
            "Euroschlüssel".takeIf { toilet.euroKey },
        ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = body) }
        listOf("F" to "Damen", "M" to "Herren", "U" to "Unisex")
            .filter { it.first in toilet.rooms }
            .takeIf { it.isNotEmpty() }
            ?.let { Text(it.joinToString(" · ") { r -> r.second }, style = body) }
    }
    val warning = when (toilet.status) {
        ToiletStatus.DEFECT -> "Laut Stadt defekt oder nicht zugänglich"
        ToiletStatus.IMPAIRED -> "Laut Stadt teilweise beeinträchtigt"
        null -> null
    }
    warning?.let { Text(it, style = body, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun PostBoxDetails(info: PostBoxInfo) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    info.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    if (info.collectionTimes != null) {
        Text("Leerungszeiten", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            formatCollectionTimes(info.collectionTimes).forEach { Text(it, style = body) }
        }
        Text("Uhrzeiten der Leerung, keine Öffnungszeiten.", style = body, color = muted)
    } else {
        Text("Keine Leerungszeiten bekannt", style = body, color = muted)
    }
    if (info.indoor) Text("Im Gebäude – nur während dessen Öffnungszeiten zugänglich", style = body, color = muted)
}

private val DAYS = mapOf(
    "Mo" to "Mo", "Tu" to "Di", "We" to "Mi", "Th" to "Do", "Fr" to "Fr", "Sa" to "Sa", "Su" to "So", "PH" to "Feiertag", "off" to "keine Leerung",
)
private val RULE_SPLIT = Regex(",\\s+(?=(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)\\b)")
private val DAY_TOKEN = Regex("\\b(Mo|Tu|We|Th|Fr|Sa|Su|PH|off)\\b")
private val DAY_RANGE = Regex("([A-Za-z])-([A-Za-z])")
private val TIME_LIST = Regex("(\\d),(\\d)")

internal fun formatCollectionTimes(raw: String): List<String> =
    raw.split(';').flatMap { it.split(RULE_SPLIT) }.map { it.trim() }.filter { it.isNotEmpty() }.map { rule ->
        rule.replace(DAY_TOKEN) { DAYS.getValue(it.value) }.replace(DAY_RANGE, "$1–$2").replace(TIME_LIST, "$1, $2")
    }

private fun formatEuro(price: Double): String {
    val cents = (price * 100).roundToInt()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
}
