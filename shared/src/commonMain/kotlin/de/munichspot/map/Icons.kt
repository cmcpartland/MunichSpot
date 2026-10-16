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
