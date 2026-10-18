// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.RetouchType

/** Which circle of a spot is grabbed: the area being fixed or where its replacement comes from. */
enum class SpotPart { SPOT, SOURCE }

/** Screen center and radius of a spot's circles. */
private class SpotCircles(val spot: Offset, val source: Offset, val radius: Float)

private fun circles(space: MaskSpace, s: RetouchSpot): SpotCircles {
    val c = space.toScreen(s.x.toDouble(), s.y.toDouble())
    val src = space.toScreen(s.sourceX.toDouble(), s.sourceY.toDouble())
    // The radius on screen: one long-edge unit is as long as the screen distance it maps to.
    val edge = space.unitsToScreen(0.0, 0.0)
    val unit = (space.unitsToScreen(1.0, 0.0) - edge).getDistance()
    return SpotCircles(c, src, s.radius * unit)
}

/**
 * The part of [spots] under [p]: the circles of the [selected] spot first, then the others'
 * spots. At least [reach] pixels around small circles count, so they stay easy to grab.
 */
fun hitSpot(space: MaskSpace, spots: List<RetouchSpot>, selected: Int?, p: Offset, reach: Float): Pair<Int, SpotPart>? {
    if (selected != null && selected in spots.indices) {
        val c = circles(space, spots[selected])
        val r = maxOf(c.radius, reach)
        if ((c.spot - p).getDistance() <= r) return selected to SpotPart.SPOT
        if (spots[selected].type != RetouchType.RED_EYE && (c.source - p).getDistance() <= r) return selected to SpotPart.SOURCE
    }
    for (i in spots.indices.reversed()) {
        if (i == selected) continue
        val c = circles(space, spots[i])
        if ((c.spot - p).getDistance() <= maxOf(c.radius, reach)) return i to SpotPart.SPOT
    }
    return null
}

/** [start] with [part] moved by the drag from screen point [from] to [to]. */
fun dragSpot(space: MaskSpace, start: RetouchSpot, part: SpotPart, from: Offset, to: Offset): RetouchSpot {
    val (fx, fy) = space.toSource(from)
    val (tx, ty) = space.toSource(to)
    val dx = (tx - fx).toFloat()
    val dy = (ty - fy).toFloat()
    return when (part) {
        SpotPart.SPOT -> start.copy(x = start.x + dx, y = start.y + dy, sourceX = start.sourceX + dx, sourceY = start.sourceY + dy)
        SpotPart.SOURCE -> start.copy(sourceX = start.sourceX + dx, sourceY = start.sourceY + dy)
    }
}

/** Every spot as a circle; the selected one also shows its source (red eye has none), joined by a line. */
@Composable
fun RetouchOverlay(spots: List<RetouchSpot>, selected: Int?, space: MaskSpace) {
    Canvas(Modifier.fillMaxSize()) {
        val shadow = Color.Black.copy(alpha = 0.4f)
        spots.forEachIndexed { i, s ->
            val c = circles(space, s)
            if (i == selected && s.type != RetouchType.RED_EYE) {
                val dashed = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                val d = c.source - c.spot
                val len = d.getDistance()
                if (len > 2 * c.radius) {
                    val u = d / len
                    drawLine(Color.White, c.spot + u * c.radius, c.source - u * c.radius, strokeWidth = 1.5.dp.toPx())
                }
                drawCircle(shadow, c.radius, c.source, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White, c.radius, c.source, style = Stroke(1.5.dp.toPx(), pathEffect = dashed))
                drawCircle(shadow, c.radius, c.spot, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, c.radius, c.spot, style = Stroke(2.5.dp.toPx()))
            } else if (i == selected) {
                drawCircle(shadow, c.radius, c.spot, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, c.radius, c.spot, style = Stroke(2.5.dp.toPx()))
            } else {
                drawCircle(shadow, c.radius, c.spot, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.8f), c.radius, c.spot, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}
