package de.munichspot.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.munichspot.data.PoiType

@Composable
internal fun CategoryIcon(type: PoiType, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(type.pinColor()), contentAlignment = Alignment.Center) {
        val glyph = if (type == PoiType.DRINKING_WATER) DrinkingWaterText else Color.White
        Canvas(Modifier.size(size * 0.6f)) { drawGlyph(type, glyph) }
    }
}

private fun DrawScope.drawGlyph(type: PoiType, c: Color) {
    val s = size.width
    val w = s * 0.07f
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (type) {
        PoiType.RECYCLING -> {
            // Bin: lid, handle, tapered body with ribs.
            drawLine(c, p(0.15f, 0.25f), p(0.85f, 0.25f), w, StrokeCap.Round)
            drawLine(c, p(0.38f, 0.25f), p(0.38f, 0.14f), w, StrokeCap.Round)
            drawLine(c, p(0.62f, 0.25f), p(0.62f, 0.14f), w, StrokeCap.Round)
            drawLine(c, p(0.38f, 0.14f), p(0.62f, 0.14f), w, StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(0.22f * s, 0.25f * s); lineTo(0.28f * s, 0.88f * s)
                    lineTo(0.72f * s, 0.88f * s); lineTo(0.78f * s, 0.25f * s)
                },
                c, style = stroke,
            )
            for (x in listOf(0.38f, 0.5f, 0.62f)) drawLine(c, p(x, 0.4f), p(x, 0.74f), w, StrokeCap.Round)
        }
        PoiType.POST_BOX -> {
            drawRoundRect(c, p(0.1f, 0.2f), Size(0.8f * s, 0.6f * s), CornerRadius(0.08f * s), style = stroke)
            drawPath(
                Path().apply {
                    moveTo(0.12f * s, 0.26f * s); lineTo(0.5f * s, 0.56f * s); lineTo(0.88f * s, 0.26f * s)
                },
                c, style = stroke,
            )
        }
        PoiType.DRINKING_WATER -> drawPath(
            Path().apply {
                moveTo(0.5f * s, 0.1f * s)
                cubicTo(0.62f * s, 0.3f * s, 0.78f * s, 0.46f * s, 0.78f * s, 0.64f * s)
                cubicTo(0.78f * s, 0.8f * s, 0.65f * s, 0.9f * s, 0.5f * s, 0.9f * s)
                cubicTo(0.35f * s, 0.9f * s, 0.22f * s, 0.8f * s, 0.22f * s, 0.64f * s)
                cubicTo(0.22f * s, 0.46f * s, 0.38f * s, 0.3f * s, 0.5f * s, 0.1f * s)
                close()
            },
            c,
        )
        PoiType.TOILET -> {
            drawCircle(c, 0.08f * s, p(0.27f, 0.14f))
            drawCircle(c, 0.08f * s, p(0.73f, 0.14f))
            drawRoundRect(c, p(0.17f, 0.28f), Size(0.2f * s, 0.34f * s), CornerRadius(0.06f * s))
            drawLine(c, p(0.22f, 0.6f), p(0.22f, 0.9f), w * 1.5f, StrokeCap.Round)
            drawLine(c, p(0.32f, 0.6f), p(0.32f, 0.9f), w * 1.5f, StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(0.73f * s, 0.28f * s); lineTo(0.58f * s, 0.65f * s); lineTo(0.88f * s, 0.65f * s); close()
                },
                c,
            )
            drawLine(c, p(0.67f, 0.68f), p(0.67f, 0.9f), w * 1.5f, StrokeCap.Round)
            drawLine(c, p(0.79f, 0.68f), p(0.79f, 0.9f), w * 1.5f, StrokeCap.Round)
            drawLine(c, p(0.5f, 0.1f), p(0.5f, 0.92f), w * 0.4f, StrokeCap.Round)
        }
    }
}
