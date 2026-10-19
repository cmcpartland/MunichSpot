package de.munichspot.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.munichspot.data.Material
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import de.munichspot.data.PostBoxInfo
import de.munichspot.data.ToiletInfo
import de.munichspot.data.ToiletStatus
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import de.munichspot.shared.resources.*

private val DragHandleHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoiSheet(poi: Poi?, onBounds: (Rect) -> Unit, onDismiss: () -> Unit) {
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var kept by remember { mutableStateOf(poi) }
    if (poi != null) kept = poi
    LaunchedEffect(poi == null) {
        if (poi != null) {
            state.show()
        } else {
            state.hide()
            kept = null
        }
    }
    var handleTop by remember { mutableStateOf(0f) }
    BottomSheet(
        modifier = Modifier.fillMaxWidth().wrapContentWidth(),
        state = state,
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                Modifier.onGloballyPositioned { handleTop = it.positionOnScreen().y },
            )
        },
    ) {
        kept?.let { PoiSheetContent(it, handleTop) { b -> if (poi != null) onBounds(b) } }
    }
}

@Composable
private fun PoiSheetContent(poi: Poi, handleTop: Float, onBounds: (Rect) -> Unit) {
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val (titleRes, sourceRes) = when (poi.type) {
        PoiType.RECYCLING -> Res.string.title_recycling to Res.string.source_city
        PoiType.CLOTHES -> Res.string.title_clothes to Res.string.source_city
        PoiType.POST_BOX -> Res.string.title_post_box to Res.string.source_osm
        PoiType.DRINKING_WATER -> Res.string.title_drinking_water to Res.string.source_city
        PoiType.TOILET -> Res.string.title_toilet to Res.string.source_city
    }
    val title = stringResource(titleRes)
    val source = stringResource(sourceRes)
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned {
            val p = it.positionOnScreen()
            with(density) { onBounds(Rect(p.x, if (handleTop > 0f) handleTop else p.y - DragHandleHeight.toPx(), p.x + it.size.width, p.y + it.size.height)) }
        }.navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(poi.type, size = 32.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (poi.name.isNotEmpty() && poi.name != title) {
                    Text(poi.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (poi.type == PoiType.RECYCLING) {
            val lines = poi.materials.accepted()
            if (lines.isEmpty()) {
                Text(
                    stringResource(Res.string.no_container_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (poi.type == PoiType.CLOTHES) {
            Text(
                stringResource(Res.string.clothes_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (poi.type == PoiType.DRINKING_WATER) {
            poi.note?.let { Text(stringResource(Res.string.note, it), style = MaterialTheme.typography.bodyMedium) }
            Text(
                stringResource(Res.string.fountain_winter_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        poi.toilet?.let { ToiletDetails(it) }
        poi.postBox?.let { PostBoxDetails(it) }
        Button(onClick = { openDirections(poi, uriHandler) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.directions)) }
        Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun List<Material>.accepted(): List<String> = buildList {
    val glass = listOf(
        Material.GLASS_BROWN to stringResource(Res.string.glass_brown),
        Material.GLASS_GREEN to stringResource(Res.string.glass_green),
        Material.GLASS_WHITE to stringResource(Res.string.glass_white),
    ).filter { it.first in this@accepted }
    if (glass.isNotEmpty()) add(stringResource(Res.string.glass_colors, glass.joinToString(" · ") { it.second }))
    if (Material.PACKAGING in this@accepted) add(stringResource(Res.string.packaging))
}

private val FLOOR_LEVEL = Regex("^(\\d+)\\.\\s*(UG|OG)$")

@Composable
private fun ToiletDetails(toilet: ToiletInfo) {
    val body = MaterialTheme.typography.bodyMedium
    toilet.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        toilet.price?.let { Text(if (it == 0.0) stringResource(Res.string.free) else formatEuro(it), style = body) }
        listOfNotNull(
            stringResource(Res.string.accessible).takeIf { toilet.accessible },
            stringResource(Res.string.changing_table).takeIf { toilet.changing },
            stringResource(Res.string.euro_key).takeIf { toilet.euroKey },
        ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = body) }
        listOf("F" to stringResource(Res.string.room_women), "M" to stringResource(Res.string.room_men), "U" to stringResource(Res.string.room_unisex))
            .filter { it.first in toilet.rooms }
            .takeIf { it.isNotEmpty() }
            ?.let { Text(it.joinToString(" · ") { r -> r.second }, style = body) }
    }
    val hours = toilet.hours?.split(" / ", " | ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    if (hours.isNotEmpty()) {
        Text(stringResource(Res.string.opening_hours), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { hours.forEach { Text(it, style = body) } }
    }
    val floors = toilet.floor?.split(" / ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    if (floors.isNotEmpty()) {
        Text(stringResource(Res.string.floor), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            floors.forEach { f ->
                val level = FLOOR_LEVEL.matchEntire(f)
                val n = level?.groupValues?.get(1)?.toInt() ?: 0
                val text = when {
                    f == "EG" -> stringResource(Res.string.floor_ground)
                    level?.groupValues?.get(2) == "UG" -> stringResource(Res.string.floor_basement, n)
                    level != null -> stringResource(Res.string.floor_upper, n)
                    else -> f
                }
                Text(text, style = body)
            }
        }
    }
    val warning = when (toilet.status) {
        ToiletStatus.DEFECT -> stringResource(Res.string.toilet_defect)
        ToiletStatus.IMPAIRED -> stringResource(Res.string.toilet_impaired)
        null -> null
    }
    warning?.let { Text(it, style = body, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun PostBoxDetails(info: PostBoxInfo) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val days = mapOf(
        "Mo" to stringResource(Res.string.day_mo), "Tu" to stringResource(Res.string.day_tu), "We" to stringResource(Res.string.day_we),
        "Th" to stringResource(Res.string.day_th), "Fr" to stringResource(Res.string.day_fr), "Sa" to stringResource(Res.string.day_sa),
        "Su" to stringResource(Res.string.day_su), "PH" to stringResource(Res.string.day_ph), "off" to stringResource(Res.string.day_off),
    )
    info.address?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    if (info.collectionTimes != null) {
        Text(stringResource(Res.string.collection_times), style = MaterialTheme.typography.titleSmall)
        val rows = formatCollectionTimes(info.collectionTimes, days)
        val times = body.copy(fontFeatureSettings = "tnum")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { Text(it.first, style = body, color = muted, maxLines = 1, softWrap = false) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { Text(it.second, style = times, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false) }
            }
        }
    } else {
        Text(stringResource(Res.string.no_collection_times), style = body, color = muted)
    }
    if (info.indoor) Text(stringResource(Res.string.indoor_note), style = body, color = muted)
}

private val RULE_SPLIT = Regex("(\\d),\\s+(?=(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)\\b)")
private val DAY_PART = Regex("^((?:Mo|Tu|We|Th|Fr|Sa|Su|PH)(?:\\s*[-,]\\s*(?:Mo|Tu|We|Th|Fr|Sa|Su|PH))*)\\s*(.*)$")
private val DAY_TOKEN = Regex("\\b(Mo|Tu|We|Th|Fr|Sa|Su|PH)\\b")
private val DAY_RANGE = Regex("([A-Za-z])-([A-Za-z])")
private val TIME_LIST = Regex("(\\d),(\\d)")
private val DAY_LIST = Regex("([A-Za-z]),\\s*([A-Za-z])")
private val SPACES = Regex("\\s+")

internal fun formatCollectionTimes(raw: String, days: Map<String, String>): List<Pair<String, String>> =
    raw.replace(RULE_SPLIT, "$1;").split(';').map { it.trim().replace(SPACES, " ") }.filter { it.isNotEmpty() }.map { rule ->
        val m = DAY_PART.find(rule)
        val dayPart = m?.groupValues?.get(1).orEmpty()
        val rest = m?.groupValues?.get(2) ?: rule
        val dayText = dayPart.replace(DAY_TOKEN) { days.getValue(it.value) }.replace(DAY_RANGE, "$1–$2").replace(DAY_LIST, "$1, $2")
        val timeText = if (rest == "off") days.getValue("off") else rest.replace(TIME_LIST, "$1, $2")
        dayText to timeText
    }

private fun formatEuro(price: Double): String {
    val cents = (price * 100).roundToInt()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
}
