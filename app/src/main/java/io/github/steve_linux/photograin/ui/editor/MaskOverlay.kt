// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** The parts of a mask that can be dragged on the photo. */
enum class MaskHandle { START, END, MOVE, RADIUS_X, RADIUS_Y }

/**
 * Converts between screen pixels and mask coordinates for the photo drawn in [rect].
 * [posToSource] maps the output (0..1) to the source (0..1); the source is [sourceWidth] x
 * [sourceHeight]. Mask math happens in long-edge units, where circles are round.
 */
class MaskSpace(
    private val rect: RectF,
    private val posToSource: Affine,
    sourceWidth: Int,
    sourceHeight: Int,
) {
    private val sourceToPos = posToSource.inverse()
    private val ax = sourceWidth / max(sourceWidth, sourceHeight).toDouble()
    private val ay = sourceHeight / max(sourceWidth, sourceHeight).toDouble()

    /** Source-normalized point -> screen. */
    fun toScreen(x: Double, y: Double): Offset {
        val p = sourceToPos.apply(x, y)
        return Offset((rect.left + p.x * rect.width()).toFloat(), (rect.top + p.y * rect.height()).toFloat())
    }

    /** Point in long-edge units -> screen. */
    fun unitsToScreen(ux: Double, uy: Double) = toScreen(ux / ax, uy / ay)

    /** Screen -> source-normalized point. */
    fun toSource(p: Offset): Pair<Double, Double> {
        val s = posToSource.apply((p.x - rect.left) / rect.width().toDouble(), (p.y - rect.top) / rect.height().toDouble())
        return s.x to s.y
    }

    fun handles(m: Mask): Map<MaskHandle, Offset> = when (m.type) {
        MaskType.LINEAR -> mapOf(
            MaskHandle.START to toScreen(m.x0.toDouble(), m.y0.toDouble()),
            MaskHandle.END to toScreen(m.x1.toDouble(), m.y1.toDouble()),
            MaskHandle.MOVE to toScreen((m.x0 + m.x1) / 2.0, (m.y0 + m.y1) / 2.0),
        )
        MaskType.RADIAL -> {
            val cx = m.x0 * ax
            val cy = m.y0 * ay
            val cs = cos(m.angle.toDouble())
            val sn = sin(m.angle.toDouble())
            mapOf(
                MaskHandle.MOVE to toScreen(m.x0.toDouble(), m.y0.toDouble()),
                MaskHandle.RADIUS_X to unitsToScreen(cx + m.radiusX * cs, cy + m.radiusX * sn),
                MaskHandle.RADIUS_Y to unitsToScreen(cx - m.radiusY * sn, cy + m.radiusY * cs),
            )
        }
    }

    /** Where to grab an unselected mask. */
    fun pin(m: Mask): Offset = handles(m).getValue(MaskHandle.MOVE)

    /**
     * [start] with [handle] dragged from screen point [from] to [to]. Moves keep the offset
     * between finger and handle; the other handles follow the finger.
     */
    fun drag(start: Mask, handle: MaskHandle, from: Offset, to: Offset): Mask {
        val (fx, fy) = toSource(from)
        val (tx, ty) = toSource(to)
        val dx = (tx - fx).toFloat()
        val dy = (ty - fy).toFloat()
        return when (handle) {
            MaskHandle.MOVE -> start.copy(x0 = start.x0 + dx, y0 = start.y0 + dy, x1 = start.x1 + dx, y1 = start.y1 + dy)
            MaskHandle.START -> start.copy(x0 = start.x0 + dx, y0 = start.y0 + dy)
            MaskHandle.END -> start.copy(x1 = start.x1 + dx, y1 = start.y1 + dy)
            MaskHandle.RADIUS_X, MaskHandle.RADIUS_Y -> {
                val h = toSource(handles(start).getValue(handle))
                // Vector from the center to where the handle is now, in long-edge units.
                val vx = (h.first + dx - start.x0) * ax
                val vy = (h.second + dy - start.y0) * ay
                if (handle == MaskHandle.RADIUS_X) {
                    start.copy(radiusX = max(hypot(vx, vy), MIN_RADIUS).toFloat(), angle = atan2(vy, vx).toFloat())
                } else {
                    val cs = cos(start.angle.toDouble())
                    val sn = sin(start.angle.toDouble())
                    start.copy(radiusY = max(abs(-sn * vx + cs * vy), MIN_RADIUS).toFloat())
                }
            }
        }
    }

    /** Outline of the ellipse scaled by [scale], as screen points. */
    fun ellipse(m: Mask, scale: Double): List<Offset> {
        val cx = m.x0 * ax
        val cy = m.y0 * ay
        val cs = cos(m.angle.toDouble())
        val sn = sin(m.angle.toDouble())
        return (0..SEGMENTS).map {
            val t = 2 * PI * it / SEGMENTS
            val u = m.radiusX * scale * cos(t)
            val v = m.radiusY * scale * sin(t)
            unitsToScreen(cx + u * cs - v * sn, cy + u * sn + v * cs)
        }
    }

    private companion object {
        const val SEGMENTS = 72
        const val MIN_RADIUS = 0.01
    }
}

/**
 * The handle of [masks] under [p], within [reach] pixels: first those of the [selected] mask,
 * then the pins of the others. Returns the mask's index and the handle.
 */
fun hitMask(space: MaskSpace, masks: List<LocalAdjustment>, selected: Int?, p: Offset, reach: Float): Pair<Int, MaskHandle>? {
    if (selected != null && selected in masks.indices) {
        space.handles(masks[selected].mask)
            .minByOrNull { (it.value - p).getDistance() }
            ?.takeIf { (it.value - p).getDistance() <= reach }
            ?.let { return selected to it.key }
    }
    return masks.indices
        .filter { it != selected }
        .minByOrNull { (space.pin(masks[it].mask) - p).getDistance() }
        ?.takeIf { (space.pin(masks[it].mask) - p).getDistance() <= reach }
        ?.let { it to MaskHandle.MOVE }
}

/** Draws every mask: the selected one with its handles, the others as pins to tap. */
@Composable
fun MaskOverlay(masks: List<LocalAdjustment>, selected: Int?, space: MaskSpace, dragging: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        masks.forEachIndexed { i, local ->
            if (i == selected) drawSelected(local.mask, space, dragging) else drawPin(space.pin(local.mask))
        }
    }
}

private fun DrawScope.drawSelected(m: Mask, space: MaskSpace, dragging: Boolean) {
    val line = Color.White.copy(alpha = if (dragging) 0.95f else 0.8f)
    val width = 1.5.dp.toPx()
    val dashed = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
    val handles = space.handles(m)
    when (m.type) {
        MaskType.LINEAR -> {
            val a = handles.getValue(MaskHandle.START)
            val b = handles.getValue(MaskHandle.END)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = max(hypot(dx, dy), 1f)
            // Lines across the whole view, perpendicular to the direction of the fade.
            val reach = hypot(size.width, size.height)
            val perp = Offset(-dy / len * reach, dx / len * reach)
            drawLine(line, a - perp, a + perp, strokeWidth = width)
            drawLine(line, b - perp, b + perp, strokeWidth = width)
            val mid = handles.getValue(MaskHandle.MOVE)
            drawLine(line, mid - perp, mid + perp, strokeWidth = width, pathEffect = dashed)
        }
        MaskType.RADIAL -> {
            drawPath(path(space.ellipse(m, 1.0)), line, style = Stroke(width))
            if (m.feather > 0.02f) {
                drawPath(path(space.ellipse(m, 1.0 - m.feather)), line, style = Stroke(width, pathEffect = dashed))
            }
        }
    }
    for ((handle, p) in handles) {
        val r = if (handle == MaskHandle.MOVE) 11.dp.toPx() else 8.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = r + 1.5.dp.toPx(), center = p)
        drawCircle(Color.White, radius = r, center = p, style = Stroke(2.5.dp.toPx()))
        if (handle == MaskHandle.MOVE) drawCircle(Color.White, radius = 4.dp.toPx(), center = p)
    }
}

private fun DrawScope.drawPin(p: Offset) {
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = p)
    drawCircle(Color.White, radius = 7.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
}

private fun path(points: List<Offset>) = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}
