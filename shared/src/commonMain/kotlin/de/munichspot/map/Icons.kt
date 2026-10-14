package de.munichspot.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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
internal fun SearchIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 2.dp.toPx()
        val r = size.width * 0.33f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, radius = r, center = c, style = Stroke(w))
        val d = r * 0.72f
        drawLine(color, c + Offset(d, d), Offset(size.width * 0.92f, size.height * 0.92f), w, StrokeCap.Round)
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
internal fun BackIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 2.dp.toPx()
        val m = size.width * 0.15f
        val c = Offset(m, size.height / 2)
        drawLine(color, c, Offset(size.width - m, size.height / 2), w, StrokeCap.Round)
        drawLine(color, c, Offset(size.width / 2, m), w, StrokeCap.Round)
        drawLine(color, c, Offset(size.width / 2, size.height - m), w, StrokeCap.Round)
    }
}
