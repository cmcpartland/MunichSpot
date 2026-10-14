package de.munichspot.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import de.munichspot.data.Poi
import de.munichspot.data.PoiType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun CategorySearchBar(
    pois: List<Poi>,
    type: PoiType?,
    onTypeChange: (PoiType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSearchBarState()
    val query = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val counts = remember(pois) { pois.groupingBy { it.type }.eachCount() }
    val matches = PoiType.entries.filter { it.matches(query.text.toString()) }
    val collapsed = state.targetValue == SearchBarValue.Collapsed

    BackHandler(enabled = type != null && collapsed) { onTypeChange(null) }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = query,
            searchBarState = state,
            onSearch = {},
            placeholder = {
                if (type != null && collapsed) {
                    Text(type.label(), color = LocalContentColor.current, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("Kategorie suchen")
                }
            },
            leadingIcon = {
                when {
                    !collapsed -> IconButton(onClick = {
                        query.clearText()
                        focus.clearFocus()
                        keyboard?.hide()
                        scope.launch { state.animateToCollapsed() }
                    }) { BackIcon(LocalContentColor.current) }
                    type != null -> CategoryIcon(type, 32.dp)
                    else -> SearchIcon(LocalContentColor.current)
                }
            },
            trailingIcon = if (type != null && collapsed) {
                { IconButton(onClick = { onTypeChange(null) }) { CloseIcon(LocalContentColor.current) } }
            } else null,
        )
    }
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    SearchBar(
        state = state,
        inputField = inputField,
        modifier = modifier,
        colors = SearchBarDefaults.colors(containerColor = container.copy(alpha = 0.88f)),
    )
    ExpandedFullScreenSearchBar(
        state = state,
        inputField = inputField,
        colors = SearchBarDefaults.colors(containerColor = container),
    ) {
        if (matches.isEmpty()) {
            Text(
                "Keine Treffer",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn {
            items(matches, key = { it.name }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onTypeChange(t)
                        query.clearText()
                        focus.clearFocus()
                        keyboard?.hide()
                        scope.launch { state.animateToCollapsed() }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CategoryIcon(t, 40.dp)
                    Column {
                        Text(t.label(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        if (pois.isNotEmpty()) {
                            Text(
                                "${counts[t] ?: 0} Orte",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
