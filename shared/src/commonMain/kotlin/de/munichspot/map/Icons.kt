package de.munichspot.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun FilterIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = 2.dp.toPx()
        for ((i, frac) in listOf(1f, 0.66f, 0.33f).withIndex()) {
            val y = size.height * (0.15f + 0.35f * i)
            val half = size.width * frac / 2
            drawLine(color, Offset(size.width / 2 - half, y), Offset(size.width / 2 + half, y), w, StrokeCap.Round)
        }
    }
}

@Composable
internal fun LocateIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = 2.dp.toPx()
        val c = center
        val r = size.width * 0.3f
        drawCircle(color, radius = 2.5.dp.toPx(), center = c)
        drawCircle(color, radius = r, center = c, style = Stroke(w))
        for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
            drawLine(color, c + Offset(dx, dy) * (r + 1.dp.toPx()), c + Offset(dx, dy) * (size.width / 2), w, StrokeCap.Round)
        }
    }
}

@Composable
internal fun CloseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = 2.dp.toPx()
        val m = size.width * 0.15f
        drawLine(color, Offset(m, m), Offset(size.width - m, size.height - m), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - m, m), Offset(m, size.height - m), w, StrokeCap.Round)
    }
}

@Composable
internal fun AllCategoriesIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val gap = 3.dp.toPx()
        val m = size.width * 0.17f
        val side = (size.width - 2 * m - gap) / 2
        val r = CornerRadius(2.dp.toPx())
        for (i in 0..1) for (j in 0..1) {
            drawRoundRect(color, Offset(m + i * (side + gap), m + j * (side + gap)), Size(side, side), r)
        }
    }
}

@Composable
internal fun SearchIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 2.dp.toPx()
        val r = size.width * 0.3f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, radius = r, center = c, style = Stroke(w))
        val d = r * 0.7071f
        drawLine(color, c + Offset(d, d), Offset(size.width * 0.88f, size.height * 0.88f), w, StrokeCap.Round)
    }
}

@Composable
internal fun ChevronIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.4f)
            lineTo(size.width * 0.5f, size.height * 0.65f)
            lineTo(size.width * 0.75f, size.height * 0.4f)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun ClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        drawCircle(color, radius = size.width * 0.42f, style = Stroke(w))
        val path = Path().apply {
            moveTo(size.width / 2, size.height * 0.3f)
            lineTo(size.width / 2, size.height / 2)
            lineTo(size.width * 0.65f, size.height * 0.6f)
        }
        drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun EuroIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        val r = size.width * 0.34f
        val c = Offset(size.width * 0.57f, size.height / 2)
        drawArc(color, 40f, 280f, false, c - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
        for (dy in listOf(-0.09f, 0.09f)) {
            val y = size.height * (0.5f + dy)
            drawLine(color, Offset(size.width * 0.14f, y), Offset(size.width * 0.58f, y), w, StrokeCap.Round)
        }
    }
}

@Composable
internal fun StairsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.85f)
            lineTo(size.width * 0.35f, size.height * 0.85f)
            lineTo(size.width * 0.35f, size.height * 0.6f)
            lineTo(size.width * 0.58f, size.height * 0.6f)
            lineTo(size.width * 0.58f, size.height * 0.35f)
            lineTo(size.width * 0.82f, size.height * 0.35f)
            lineTo(size.width * 0.82f, size.height * 0.15f)
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun PeopleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        for (cx in listOf(0.32f, 0.68f)) {
            drawCircle(color, radius = size.width * 0.12f, center = Offset(size.width * cx, size.height * 0.3f), style = Stroke(w))
            drawArc(
                color, 180f, 180f, false,
                Offset(size.width * (cx - 0.2f), size.height * 0.52f), Size(size.width * 0.4f, size.height * 0.4f),
                style = Stroke(w, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun CheckCircleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        drawCircle(color, radius = size.width * 0.42f, style = Stroke(w))
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.66f)
            lineTo(size.width * 0.7f, size.height * 0.38f)
        }
        drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun WarningIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        val tri = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.12f)
            lineTo(size.width * 0.92f, size.height * 0.85f)
            lineTo(size.width * 0.08f, size.height * 0.85f)
            close()
        }
        drawPath(tri, color, style = Stroke(w, join = StrokeJoin.Round))
        drawLine(color, Offset(size.width / 2, size.height * 0.4f), Offset(size.width / 2, size.height * 0.62f), w, StrokeCap.Round)
        drawCircle(color, radius = w * 0.6f, center = Offset(size.width / 2, size.height * 0.74f))
    }
}

/** Teardrop with its tip at the bottom centre of a [w] x [h] box and a round head of diameter [w]. */
internal fun teardropPath(w: Float, h: Float): Path {
    val r = w / 2
    val alpha = acos(r / (h - r)) * 180f / PI.toFloat()
    val a = (90f - alpha) * PI.toFloat() / 180f
    return Path().apply {
        moveTo(r, h)
        lineTo(r + r * cos(a), r + r * sin(a))
        arcTo(Rect(0f, 0f, w, w), 90f - alpha, -(360f - 2 * alpha), false)
        close()
    }
}

@Composable
internal fun PlaceIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width * 0.6f
        translate((size.width - w) / 2, size.height * 0.05f) { drawPin(color, w, size.height * 0.9f, dot = null) }
    }
}

/** A teardrop pin; [dot] is the colour of the inner dot, or null to punch it out. */
internal fun DrawScope.drawPin(color: Color, w: Float, h: Float, dot: Color?) {
    val dotCenter = Offset(w / 2, w / 2)
    val path = teardropPath(w, h)
    if (dot == null) {
        path.fillType = PathFillType.EvenOdd
        path.addOval(Rect(dotCenter, w * 0.18f))
        drawPath(path, color)
    } else {
        drawPath(path, color)
        drawCircle(dot, w * 0.2f, dotCenter)
    }
}

internal val AnchorRed = Color(0xFFE6194B)

@Composable
internal fun InfoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        val cx = size.width / 2
        drawCircle(color, radius = size.width * 0.42f, style = Stroke(w))
        drawLine(color, Offset(cx, size.height * 0.46f), Offset(cx, size.height * 0.72f), w, StrokeCap.Round)
        drawCircle(color, radius = w * 0.65f, center = Offset(cx, size.height * 0.31f))
    }
}

@Composable
internal fun SnowflakeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.width * 0.42f
        for (i in 0..2) {
            val a = (i * 60f + 90f) * PI.toFloat() / 180f
            val d = Offset(cos(a), sin(a))
            drawLine(color, c - d * r, c + d * r, w, StrokeCap.Round)
            for (s in listOf(-1f, 1f)) {
                val e = d * (s * r * 0.62f)
                val base = c + e
                for (k in listOf(-1f, 1f)) {
                    val b = a + k * PI.toFloat() / 4 + if (s < 0) PI.toFloat() else 0f
                    drawLine(color, base, base + Offset(cos(b), sin(b)) * (r * 0.3f), w, StrokeCap.Round)
                }
            }
        }
    }
}
