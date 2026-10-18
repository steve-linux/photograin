// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.model.Quad

/**
 * The four perspective points over the uncorrected photo drawn in [imageRect]; each corner is
 * dragged by itself. Lines join them so it shows which edges will become straight.
 */
@Composable
fun QuadOverlay(quad: Quad, imageRect: RectF, onChange: (Quad) -> Unit) {
    val currentQuad by rememberUpdatedState(quad)
    val currentRect by rememberUpdatedState(imageRect)
    var corner by remember { mutableStateOf<Int?>(null) }
    var start by remember { mutableStateOf(Offset.Zero) }
    var total by remember { mutableStateOf(Offset.Zero) }

    fun screen(r: RectF, x: Float, y: Float) = Offset(r.left + x * r.width(), r.top + y * r.height())

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val reach = 36.dp.toPx()
                detectDragGestures(
                    onDragStart = { p ->
                        val r = currentRect
                        val nearest = currentQuad.points.withIndex().minBy { (_, xy) -> (screen(r, xy.first, xy.second) - p).getDistance() }
                        corner = nearest.index.takeIf { (screen(r, nearest.value.first, nearest.value.second) - p).getDistance() <= reach }
                        start = Offset(nearest.value.first, nearest.value.second)
                        total = Offset.Zero
                    },
                    onDragEnd = { corner = null },
                    onDragCancel = { corner = null },
                    onDrag = { change, amount ->
                        val c = corner ?: return@detectDragGestures
                        change.consume()
                        total += amount
                        val r = currentRect
                        onChange(currentQuad.withPoint(c, start.x + total.x / r.width(), start.y + total.y / r.height()))
                    },
                )
            },
    ) {
        val p = quad.points.map { (x, y) -> screen(imageRect, x, y) }
        val outline = Path().apply {
            moveTo(p[0].x, p[0].y)
            for (k in 1..3) lineTo(p[k].x, p[k].y)
            close()
        }
        drawPath(outline, Color.Black.copy(alpha = 0.5f), style = Stroke(3.dp.toPx()))
        drawPath(outline, Color.White, style = Stroke(1.5.dp.toPx()))
        // Lines through the middle of opposite sides, which will end up level and upright.
        val guide = Color.White.copy(alpha = 0.45f)
        drawLine(guide, (p[0] + p[3]) / 2f, (p[1] + p[2]) / 2f, strokeWidth = 1.dp.toPx())
        drawLine(guide, (p[0] + p[1]) / 2f, (p[3] + p[2]) / 2f, strokeWidth = 1.dp.toPx())
        for ((k, c) in p.withIndex()) {
            val r = if (k == corner) 16.dp.toPx() else 12.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = r + 2.dp.toPx(), center = c)
            drawCircle(Color.White, radius = r, center = c, style = Stroke(2.5.dp.toPx()))
            drawCircle(Color.White, radius = 1.5.dp.toPx(), center = c)
        }
    }
}
