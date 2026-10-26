package de.munichspot.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.munichspot.data.Place
import de.munichspot.data.PlaceIndex
import de.munichspot.data.PoiType
import kotlinx.coroutines.delay
import de.munichspot.shared.resources.*
import org.jetbrains.compose.resources.stringResource

internal val CategoryPickerHeight = 48.dp
internal const val MorphMillis = 250

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoryPicker(
    type: PoiType?,
    onTypeChange: (PoiType?) -> Unit,
    places: PlaceIndex?,
    anchor: Place?,
    onAnchorChange: (Place?) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bottomInset: Dp,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    var query by remember { mutableStateOf("") }
    var focusSearch by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) { if (!expanded) query = "" }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val pick = { place: Place ->
        onAnchorChange(place)
        query = ""
        focusManager.clearFocus()
        onExpandedChange(false)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(expanded, enter = fadeIn(tween(MorphMillis)), exit = fadeOut(tween(MorphMillis))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onExpandedChange(false)
                    },
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = maxOf(bottomInset, if (expanded) imeBottom + 8.dp else 0.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = animateColorAsState(
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (expanded) 0.85f else 1f),
                    tween(MorphMillis),
                ).value,
                tonalElevation = if (expanded) 0.dp else 3.dp,
                shadowElevation = if (expanded) 0.dp else 4.dp,
) {
                AnimatedContent(
                    modifier = Modifier
                        .animateContentSize(tween(MorphMillis))
                        .then(if (expanded) Modifier.widthIn(max = 400.dp).fillMaxWidth() else Modifier),
                    targetState = expanded,
                    transitionSpec = { fadeIn(tween(MorphMillis)) togetherWith fadeOut(tween(MorphMillis / 2)) using SizeTransform { _, _ -> snap() } },
                    label = "picker",
                ) { open ->
                    if (open) {
                        val suggestions = remember(query, places) { places?.search(query).orEmpty() }
                        val searching = query.trim().length >= 2
                        LaunchedEffect(Unit) {
                            if (focusSearch) {
                                delay(MorphMillis.toLong())
                                focusRequester.requestFocus()
                                focusSearch = false
                            }
                        }
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SearchField(query, { query = it }, focusRequester, onSearch = { suggestions.firstOrNull()?.let(pick) })
                            if (searching) {
                                if (suggestions.isEmpty()) {
                                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(Res.string.no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            stringResource(Res.string.search_scope),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                for (place in suggestions) SuggestionRow(place) { pick(place) }
                            } else {
                                if (anchor != null) AnchorRow(anchor) { onAnchorChange(null) }
                                Text(stringResource(Res.string.choose_category), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (t in listOf<PoiType?>(null) + PoiType.entries) {
                                        CategoryChoice(t, selected = t == type) {
                                            onTypeChange(t)
                                            onExpandedChange(false)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp), verticalAlignment = Alignment.CenterVertically) {
                            val colors = MaterialTheme.colorScheme
                            val chooseCategory = stringResource(Res.string.choose_category)
                            val searchPlaces = stringResource(Res.string.search_places)
                            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.heightIn(min = CategoryPickerHeight)
                                        .weight(1f, fill = false)
                                        .clip(CircleShape)
                                        .semantics { contentDescription = chooseCategory }
                                        .clickable(role = Role.Button) { onExpandedChange(true) }
                                        .padding(start = 16.dp, end = if (type != null) 4.dp else 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CategoryGlyph(type)
                                    Text(
                                        type?.label() ?: stringResource(Res.string.all_categories),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                if (type != null) ClearButton(stringResource(Res.string.clear_category)) { onTypeChange(null) }
                            }
                            Box(Modifier.size(width = 1.dp, height = 20.dp).background(colors.outlineVariant))
                            Row(
                                Modifier.then(if (anchor != null) Modifier.weight(1f, fill = false) else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    Modifier.heightIn(min = CategoryPickerHeight)
                                        .weight(1f, fill = false)
                                        .clip(CircleShape)
                                        .semantics { contentDescription = searchPlaces }
                                        .clickable(role = Role.Button) {
                                            focusSearch = true
                                            onExpandedChange(true)
                                        }
                                        .padding(start = 12.dp, end = if (anchor != null) 4.dp else 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (anchor != null) PlaceIcon(AnchorRed, Modifier.size(16.dp))
                                    else SearchIcon(colors.onSurfaceVariant, Modifier.size(16.dp))
                                    Text(
                                        anchor?.name ?: searchPlaces,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (anchor != null) colors.onSurface else colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                if (anchor != null) ClearButton(stringResource(Res.string.clear_place)) { onAnchorChange(null) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearButton(description: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 4.dp).size(40.dp).clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { CloseIcon(MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester, onSearch: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).background(colors.surfaceContainerHighest).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchIcon(colors.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(Res.string.search_places), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) { CloseIcon(colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SuggestionRow(place: Place, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaceIcon(colors.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(place.kind.label(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnchorRow(anchor: Place, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(start = 4.dp)) { PlaceIcon(AnchorRed) }
        Text(anchor.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear),
            contentAlignment = Alignment.Center,
        ) { CloseIcon(colors.onSurfaceVariant) }
    }
}

@Composable
private fun CategoryGlyph(type: PoiType?) {
    if (type != null) CategoryIcon(type, 24.dp) else AllCategoriesIcon(MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CategoryChoice(type: PoiType?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 40.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(colors.secondaryContainer)
                else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryGlyph(type)
        Text(
            type?.label() ?: stringResource(Res.string.all),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
        )
    }
}
