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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.munichspot.data.PoiType

internal val CategoryPickerHeight = 48.dp
private const val MorphMillis = 250

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoryPicker(
    type: PoiType?,
    onTypeChange: (PoiType?) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    BackHandler(enabled = type != null && !expanded) { onTypeChange(null) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(expanded, enter = fadeIn(tween(MorphMillis)), exit = fadeOut(tween(MorphMillis))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onExpandedChange(false)
                    },
            )
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = animateColorAsState(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (expanded) 0.85f else 1f),
                tween(MorphMillis),
            ).value,
            tonalElevation = if (expanded) 0.dp else 3.dp,
            shadowElevation = if (expanded) 0.dp else 4.dp,
            modifier = modifier
                .align(Alignment.BottomCenter)
                .animateContentSize(tween(MorphMillis))
                .then(if (expanded) Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth() else Modifier),
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = { fadeIn(tween(MorphMillis)) togetherWith fadeOut(tween(MorphMillis / 2)) using SizeTransform { _, _ -> snap() } },
                label = "picker",
            ) { open ->
                if (open) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Kategorie wählen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (t in listOf<PoiType?>(null) + PoiType.entries) {
                                CategoryChoice(t, selected = t == type) {
                                    onTypeChange(t)
                                    onExpandedChange(false)
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.heightIn(min = CategoryPickerHeight)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { onExpandedChange(true) }
                                .padding(start = 16.dp, end = if (type == null) 16.dp else 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CategoryGlyph(type)
                            Text(
                                type?.label() ?: "Alle Kategorien",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (type != null) {
                            Box(
                                Modifier.padding(end = 4.dp).size(40.dp).clip(CircleShape)
                                    .clickable(role = Role.Button) { onTypeChange(null) },
                                contentAlignment = Alignment.Center,
                            ) { CloseIcon(MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
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
            type?.label() ?: "Alle",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
        )
    }
}
