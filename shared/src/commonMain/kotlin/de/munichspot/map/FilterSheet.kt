package de.munichspot.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import de.munichspot.data.PoiType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(filters: PoiFilters, onChange: (PoiFilters) -> Unit, onDismiss: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val all = RecyclingFilter.entries.toSet()
    val selected = filters.recyclingMaterials
    val parentState = when {
        selected.isEmpty() -> ToggleableState.Off
        selected.size == all.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(PoiFilters()) }, enabled = !filters.isDefault) {
                    Text("Zurücksetzen")
                }
            }
            FilterRow(
                modifier = Modifier.triStateToggleable(parentState, role = Role.Checkbox) {
                    onChange(filters.copy(recyclingMaterials = if (parentState == ToggleableState.Off) all else emptySet()))
                },
                label = "Wertstoffinseln",
                dot = PoiType.RECYCLING.pinColor(),
                checkbox = { TriStateCheckbox(parentState, onClick = null) },
                trailing = {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Chevron(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.rotate(if (expanded) 180f else 0f))
                    }
                },
            )
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 32.dp)) {
                    for ((entry, label) in listOf(
                        RecyclingFilter.GLASS to "Glas",
                        RecyclingFilter.PACKAGING to "Leichtverpackungen",
                        RecyclingFilter.CLOTHING to "Altkleider",
                    )) {
                        val checked = entry in selected
                        FilterRow(
                            modifier = Modifier.toggleable(checked, role = Role.Checkbox) {
                                onChange(filters.copy(recyclingMaterials = if (checked) selected - entry else selected + entry))
                            },
                            label = label,
                            checkbox = { Checkbox(checked, onCheckedChange = null) },
                        )
                    }
                }
            }
            FilterRow(
                modifier = Modifier.toggleable(filters.showMailboxes, role = Role.Checkbox) {
                    onChange(filters.copy(showMailboxes = it))
                },
                label = "Briefkästen",
                dot = PoiType.POST_BOX.pinColor(),
                checkbox = { Checkbox(filters.showMailboxes, onCheckedChange = null) },
            )
            FilterRow(
                modifier = Modifier.toggleable(filters.showDrinkingWater, role = Role.Checkbox) {
                    onChange(filters.copy(showDrinkingWater = it))
                },
                label = "Trinkbrunnen",
                dot = PoiType.DRINKING_WATER.pinColor(),
                checkbox = { Checkbox(filters.showDrinkingWater, onCheckedChange = null) },
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    checkbox: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        checkbox()
        dot?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(it)) }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Chevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(12.dp, 8.dp)) {
        val w = 2.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
    }
}

