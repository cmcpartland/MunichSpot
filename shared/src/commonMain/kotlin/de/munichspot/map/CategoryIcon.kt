package de.munichspot.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.munichspot.data.PoiType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CategoryIcon(type: PoiType, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawCategoryIcon(type, ring = false) }
}

/** Paints [CategoryIcon] as a map image, with a white ring so it stands out on the map. */
internal class CategoryIconPainter(private val type: PoiType) : Painter() {
    override val intrinsicSize = Size.Unspecified
    override fun DrawScope.onDraw() = drawCategoryIcon(type, ring = true)
}

/** The type-coloured circle with its glyph; [ring] adds a white outline inside the bounds. */
internal fun DrawScope.drawCategoryIcon(type: PoiType, ring: Boolean) {
    val d = size.minDimension
    val inset = if (ring) 2.dp.toPx() else 0f
    if (ring) drawCircle(Color.White, d / 2)
    drawCircle(type.pinColor(), d / 2 - inset)
    val glyph = (d - 2 * inset) * 0.6f
    inset((d - glyph) / 2) {
        drawGlyph(type, if (type == PoiType.DRINKING_WATER) DrinkingWaterText else Color.White)
    }
}

/** Paints [drawWarningMark] as a map image. */
internal class WarningMarkPainter : Painter() {
    override val intrinsicSize = Size.Unspecified
    override fun DrawScope.onDraw() = drawWarningMark()
}

/** White disc with a black ring and a red exclamation mark. */
internal fun DrawScope.drawWarningMark() {
    val d = size.minDimension
    drawCircle(Color.Black, d / 2)
    drawCircle(Color.White, d / 2 - d * 0.12f)
    val w = d * 0.14f
    drawLine(WarningRed, Offset(d / 2, d * 0.26f), Offset(d / 2, d * 0.56f), w, StrokeCap.Round)
    drawCircle(WarningRed, w * 0.6f, Offset(d / 2, d * 0.74f))
}

private fun DrawScope.drawGlyph(type: PoiType, c: Color) {
    val s = size.width
    val w = s * 0.07f
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (type) {
        PoiType.RECYCLING -> {
            // Recycling symbol: three chasing arrows (arc + solid head) around a circle, rotated 120 degrees apart.
            val r = 0.34f * s
            val a0 = -85f
            val sweep = 60f
            val end = (a0 + sweep) * PI / 180.0
            val bx = 0.5f * s + r * cos(end).toFloat()
            val by = 0.5f * s + r * sin(end).toFloat()
            val tx = -sin(end).toFloat()
            val ty = cos(end).toFloat()
            val hw = 0.16f * s
            val len = 0.22f * s
            repeat(3) { i ->
                rotate(i * 120f, pivot = center) {
                    drawArc(
                        c, a0, sweep, false, p(0.5f, 0.5f) - Offset(r, r), Size(2 * r, 2 * r),
                        style = Stroke(w * 1.8f, cap = StrokeCap.Butt),
                    )
                    drawPath(
                        Path().apply {
                            moveTo(bx + hw * cos(end).toFloat(), by + hw * sin(end).toFloat())
                            lineTo(bx + len * tx, by + len * ty)
                            lineTo(bx - hw * cos(end).toFloat(), by - hw * sin(end).toFloat())
                            close()
                        },
                        c,
                    )
                }
            }
        }
        PoiType.CLOTHES -> drawPath(
            Path().apply {
                moveTo(0.34f * s, 0.12f * s)
                quadraticTo(0.5f * s, 0.26f * s, 0.66f * s, 0.12f * s)
                lineTo(0.94f * s, 0.26f * s)
                lineTo(0.82f * s, 0.46f * s)
                lineTo(0.72f * s, 0.4f * s)
                lineTo(0.72f * s, 0.88f * s)
                lineTo(0.28f * s, 0.88f * s)
                lineTo(0.28f * s, 0.4f * s)
                lineTo(0.18f * s, 0.46f * s)
                lineTo(0.06f * s, 0.26f * s)
                close()
            },
            c,
        )
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
