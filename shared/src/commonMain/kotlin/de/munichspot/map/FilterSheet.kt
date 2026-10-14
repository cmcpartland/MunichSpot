package de.munichspot.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.munichspot.data.PoiType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(type: PoiType, filters: PoiFilters, onChange: (PoiFilters) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(filters.reset(type)) }, enabled = !filters.isDefault(type)) {
                    Text("Zurücksetzen")
                }
            }
            when (type) {
                PoiType.RECYCLING -> {
                    val selected = filters.recyclingMaterials
                    for ((entry, label) in listOf(
                        RecyclingFilter.GLASS to "Glas",
                        RecyclingFilter.PACKAGING to "Leichtverpackungen",
                    )) {
                        CheckRow(label, entry in selected) {
                            onChange(filters.copy(recyclingMaterials = if (it) selected + entry else selected - entry))
                        }
                    }
                }
                PoiType.TOILET -> {
                    val selected = filters.toiletRequirements
                    for ((entry, label) in listOf(
                        ToiletFilter.ACCESSIBLE to "Nur barrierefrei",
                        ToiletFilter.FREE to "Nur kostenlos",
                        ToiletFilter.CHANGING to "Mit Wickeltisch",
                        ToiletFilter.HIDE_DEFECT to "Defekte ausblenden",
                    )) {
                        CheckRow(label, entry in selected) {
                            onChange(filters.copy(toiletRequirements = if (it) selected + entry else selected - entry))
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
