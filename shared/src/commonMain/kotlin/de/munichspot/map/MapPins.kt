package de.munichspot.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.munichspot.data.Poi
import de.munichspot.data.PoiType

internal val AnchorPinSize = DpSize(24.dp, 32.dp)

internal class AnchorPinPainter : Painter() {
    override val intrinsicSize = Size.Unspecified
    override fun DrawScope.onDraw() {
        val pad = 2.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        translate(pad + 0.5.dp.toPx(), pad + 1.dp.toPx()) { drawPath(teardropPath(w, h), Color.Black.copy(alpha = 0.3f)) }
        translate(pad, pad) {
            drawPath(teardropPath(w, h), Color.White, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
            drawPin(AnchorRed, w, h, dot = Color.White)
        }
    }
}

@Composable
internal fun SelectedPin(poi: Poi, closed: Boolean) {
    Canvas(Modifier.size(SelectedGlowRadius * 2)) {
        drawSelectedPin(poi.type)
        if (poi.possiblyDefect) {
            val s = SelectedWarningMarkSize.toPx()
            val l = center.x + SelectedWarningMarkOffset.x.toPx() - s / 2
            val t = center.y + SelectedWarningMarkOffset.y.toPx() - s / 2
            inset(l, t, size.width - l - s, size.height - t - s) { drawWarningMark() }
        }
        if (closed) {
            val s = SelectedWarningMarkSize.toPx()
            val l = center.x + SelectedClockMarkOffset.x.toPx() - s / 2
            val t = center.y + SelectedClockMarkOffset.y.toPx() - s / 2
            inset(l, t, size.width - l - s, size.height - t - s) { drawClockMark() }
        }
    }
}

private fun DrawScope.drawSelectedPin(type: PoiType) {
    val glow = type.pinColor().copy(alpha = 0.6f)
    drawCircle(Brush.radialGradient(0f to glow, 0.4f to glow, 1f to Color.Transparent, radius = size.width / 2))
    inset((size.width - SelectedPoiIconSize.toPx()) / 2) { drawCategoryIcon(type, ring = true) }
}

internal class SelectedPinPainter(private val type: PoiType) : Painter() {
    override val intrinsicSize = Size.Unspecified
    override fun DrawScope.onDraw() = drawSelectedPin(type)
}
