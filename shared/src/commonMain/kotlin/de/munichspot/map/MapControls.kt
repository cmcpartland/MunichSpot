package de.munichspot.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.munichspot.data.Freshness
import de.munichspot.data.freshnessHint
import de.munichspot.shared.resources.*
import de.munichspot.shared.resources.Res
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.map.MapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionStyle
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MaplibreLogo

// MapOverlay.Default, but with the scale bar at the bottom left, above the MapLibre logo.
@Composable
internal fun MapControls(mapState: MapState, onLogoHeight: (Dp) -> Unit) {
    val density = LocalDensity.current
    val insets = LocalViewportInsets.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(insets)
            .consumeWindowInsets(insets)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(MapOverlay.Spacing),
    ) {
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DisappearingScaleBar(
                metersPerDp = { mapState.viewport?.metersPerDpAtCenter ?: 0.0 },
                zoom = { mapState.cameraPosition.zoom },
            )
            Box(Modifier.onSizeChanged { onLogoHeight(with(density) { it.height.toDp() }) }) { MaplibreLogo() }
        }
        DisappearingCompassButton(Modifier.align(Alignment.TopEnd))
        // MapLibre's defaults are always white with black text; follow the theme instead.
        val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
        val content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        ExpandingAttributionButton(
            Modifier.align(Alignment.BottomEnd),
            toggleButton = { onClick ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick, role = Role.Button),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        AttributionDefaults.icon(),
                        AttributionDefaults.contentDescription(),
                        Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(content),
                    )
                }
            },
            expandedStyle = AttributionStyle(container, content),
            collapsedStyle = AttributionStyle(container.copy(alpha = 0f), content.copy(alpha = 0f)),
        )
    }
}

internal val WideBreakpoint = 680.dp

@Composable
internal fun DataNotice(status: DataStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = status != DataStatus.Ready,
        modifier = modifier,
        enter = fadeIn(tween(MorphMillis)),
        exit = fadeOut(tween(MorphMillis)),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Text(
                    text = stringResource(if (status == DataStatus.Failed) Res.string.no_data else Res.string.loading_data),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 0.dp).weight(1f, fill = false),
                )
                if (status == DataStatus.Failed) TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
                else Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
internal fun LocatePill(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val description = stringResource(Res.string.my_location)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(MorphMillis)) + scaleIn(tween(MorphMillis)) +
            if (horizontal) expandHorizontally(tween(MorphMillis)) else EnterTransition.None,
        exit = fadeOut(tween(MorphMillis)) + scaleOut(tween(MorphMillis)) +
            if (horizontal) shrinkHorizontally(tween(MorphMillis)) else ExitTransition.None,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(end = if (horizontal) 8.dp else 0.dp)
                .size(CategoryPickerHeight)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) { LocateIcon(MaterialTheme.colorScheme.primary, Modifier.size(24.dp)) }
        }
    }
}

@Composable
internal fun AttributionBar(generated: Instant?, onHeight: (Dp) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
            .navigationBarsPadding(),
    ) {
        val hint = when (val f = remember(generated) { freshnessHint(generated, Clock.System.now()) }) {
            Freshness.VeryOld -> stringResource(Res.string.data_very_old)
            is Freshness.AsOf -> stringResource(Res.string.data_as_of, f.date)
            null -> null
        }
        val attribution = stringResource(Res.string.attribution)
        Text(
            text = if (hint != null) "$attribution · $hint" else attribution,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
