package de.munichspot.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.munichspot.data.LocalText
import de.munichspot.data.Material
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import de.munichspot.data.PostBoxInfo
import de.munichspot.data.ToiletInfo
import de.munichspot.data.ToiletStatus
import de.munichspot.data.isOpen
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import de.munichspot.shared.resources.*

private val DragHandleHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoiSheet(poi: Poi?, now: LocalDateTime, onBounds: (Rect) -> Unit, onDismiss: () -> Unit) {
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
        kept?.let { PoiSheetContent(it, now, handleTop) { b -> if (poi != null) onBounds(b) } }
    }
}

@Composable
private fun PoiSheetContent(poi: Poi, now: LocalDateTime, handleTop: Float, onBounds: (Rect) -> Unit) {
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
    val nameIsAddress = poi.type == PoiType.RECYCLING || poi.type == PoiType.CLOTHES
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
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (!nameIsAddress && poi.name.isNotEmpty() && poi.name != title) {
                    Text(poi.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalButton(
                onClick = { openDirections(poi, uriHandler) },
                modifier = Modifier.heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                DirectionsIcon(LocalContentColor.current)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.directions), style = MaterialTheme.typography.labelLarge)
            }
        }
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val hint = MaterialTheme.typography.bodyMedium
        if (nameIsAddress && poi.name.isNotEmpty()) {
            DetailRow({ PlaceIcon(muted, it) }) { Text(poi.name, style = MaterialTheme.typography.bodyLarge) }
        }
        if (poi.type == PoiType.RECYCLING) {
            val chips = listOf(
                Material.GLASS_BROWN to stringResource(Res.string.glass_brown),
                Material.GLASS_GREEN to stringResource(Res.string.glass_green),
                Material.GLASS_WHITE to stringResource(Res.string.glass_white),
                Material.PACKAGING to stringResource(Res.string.packaging),
            ).filter { it.first in poi.materials }
            DetailRow(null) {
                if (chips.isEmpty()) {
                    Text(stringResource(Res.string.no_container_data), style = hint, color = muted)
                } else {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { FactChip(it.second) }
                    }
                }
            }
        }
        if (poi.type == PoiType.CLOTHES) {
            DetailRow({ InfoIcon(muted, it) }) { Text(stringResource(Res.string.clothes_hint), style = hint, color = muted) }
        }
        if (poi.type == PoiType.DRINKING_WATER) {
            poi.note?.let {
                DetailRow({ InfoIcon(muted, it) }) {
                    Text(stringResource(Res.string.note, it.shown()), style = MaterialTheme.typography.bodyLarge)
                    if (it.translated()) AutoTranslatedLabel()
                }
            }
            DetailRow({ SnowflakeIcon(muted, it) }) { Text(stringResource(Res.string.fountain_winter_hint), style = hint, color = muted) }
        }
        poi.toilet?.let { ToiletDetails(it, now) }
        poi.postBox?.let { PostBoxDetails(it) }
        Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun isEnglish() = stringResource(Res.string.data_language) == "en"

@Composable
private fun LocalText.shown() = if (isEnglish()) en ?: de else de

@Composable
private fun LocalText.translated() = isEnglish() && en != null

@Composable
private fun AutoTranslatedLabel() =
    Text(stringResource(Res.string.auto_translated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private val FLOOR_LEVEL = Regex("^(\\d+)\\.\\s*(UG|OG)$")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToiletDetails(toilet: ToiletInfo, now: LocalDateTime) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    toilet.address?.let { DetailRow({ PlaceIcon(muted, it) }) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
    val hours = toilet.hours?.shown()?.split(" / ", " | ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    if (hours.isNotEmpty()) {
        var expanded by remember(toilet) { mutableStateOf(false) }
        val open = toilet.schedule?.isOpen(now)
        val rotation by animateFloatAsState(if (expanded) 180f else 0f)
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }) {
            DetailRow({ ClockIcon(muted, it) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            when (open) {
                                true -> Res.string.open_now
                                false -> Res.string.closed_now
                                null -> Res.string.opening_hours
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = when (open) {
                            true -> openColor()
                            false -> error
                            null -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ChevronIcon(muted, Modifier.rotate(rotation))
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        hours.forEach { Text(it, style = body) }
                        if (toilet.hours?.translated() == true) AutoTranslatedLabel()
                    }
                }
            }
        }
    }
    toilet.price?.let { price ->
        DetailRow({ EuroIcon(muted, it) }) {
            Text(if (price == 0.0) stringResource(Res.string.free) else formatEuro(price, isEnglish()), style = MaterialTheme.typography.bodyLarge)
        }
    }
    val floors = toilet.floor?.split(" / ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    if (floors.isNotEmpty()) {
        val text = floors.map { f ->
            val level = FLOOR_LEVEL.matchEntire(f)
            val n = level?.groupValues?.get(1)?.toInt() ?: 0
            when {
                f == "EG" -> stringResource(Res.string.floor_ground)
                level?.groupValues?.get(2) == "UG" && n == 1 -> stringResource(Res.string.floor_basement_first)
                level?.groupValues?.get(2) == "UG" -> stringResource(Res.string.floor_basement, n)
                level != null -> stringResource(Res.string.floor_upper, n)
                else -> f
            }
        }.joinToString(" · ")
        DetailRow({ StairsIcon(muted, it) }) { Text(text, style = MaterialTheme.typography.bodyLarge) }
    }
    listOf("F" to stringResource(Res.string.room_women), "M" to stringResource(Res.string.room_men), "U" to stringResource(Res.string.room_unisex))
        .filter { it.first in toilet.rooms }
        .takeIf { it.isNotEmpty() }
        ?.let { rooms ->
            DetailRow({ PeopleIcon(muted, it) }) { Text(rooms.joinToString(" · ") { it.second }, style = MaterialTheme.typography.bodyLarge) }
        }
    val chips = listOfNotNull(
        stringResource(Res.string.accessible).takeIf { toilet.accessible },
        stringResource(Res.string.changing_table).takeIf { toilet.changing },
        stringResource(Res.string.euro_key).takeIf { toilet.euroKey },
    )
    if (chips.isNotEmpty()) {
        DetailRow({ CheckCircleIcon(muted, it) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { FactChip(it) }
            }
        }
    }
    val warning = when (toilet.status) {
        ToiletStatus.DEFECT -> stringResource(Res.string.toilet_defect)
        ToiletStatus.IMPAIRED -> stringResource(Res.string.toilet_impaired)
        null -> null
    }
    warning?.let { DetailRow({ WarningIcon(error, it) }) { Text(it, style = body, color = error) } }
}

@Composable
private fun DetailRow(icon: (@Composable (Modifier) -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) icon(Modifier.padding(top = 2.dp)) else Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), content = content)
    }
}

@Composable
private fun FactChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun openColor() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
private fun PostBoxDetails(info: PostBoxInfo) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val days = mapOf(
        "Mo" to stringResource(Res.string.day_mo), "Tu" to stringResource(Res.string.day_tu), "We" to stringResource(Res.string.day_we),
        "Th" to stringResource(Res.string.day_th), "Fr" to stringResource(Res.string.day_fr), "Sa" to stringResource(Res.string.day_sa),
        "Su" to stringResource(Res.string.day_su), "PH" to stringResource(Res.string.day_ph), "off" to stringResource(Res.string.day_off),
    )
    info.address?.let { DetailRow({ PlaceIcon(muted, it) }) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
    DetailRow({ ClockIcon(muted, it) }) {
        if (info.collectionTimes != null) {
            val rows = formatCollectionTimes(info.collectionTimes, days)
            val times = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
            Text(stringResource(Res.string.collection_times), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rows.forEach { Text(it.first, style = MaterialTheme.typography.bodyLarge, color = muted, maxLines = 1, softWrap = false) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rows.forEach { Text(it.second, style = times, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false) }
                }
            }
        } else {
            Text(stringResource(Res.string.no_collection_times), style = body, color = muted)
        }
    }
    if (info.indoor) DetailRow({ InfoIcon(muted, it) }) { Text(stringResource(Res.string.indoor_note), style = body, color = muted) }
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

private fun formatEuro(price: Double, english: Boolean): String {
    val cents = (price * 100).roundToInt()
    val amount = "${cents / 100}${if (english) "." else ","}${(cents % 100).toString().padStart(2, '0')}"
    return if (english) "€$amount" else "$amount €"
}
