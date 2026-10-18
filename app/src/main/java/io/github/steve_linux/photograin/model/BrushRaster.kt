// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The strokes of a brush mask drawn into an 8-bit grid over the whole source image, at a fixed
 * density ([LONG_EDGE] texels on the long edge) so the preview and the export use the same mask.
 *
 * Painting keeps the strongest value and erasing the weakest, so drawing a stamp twice changes
 * nothing: a stroke that grows can be drawn segment by segment and gives the same result as
 * drawing it whole.
 */
class BrushRaster(sourceWidth: Int, sourceHeight: Int) {
    val width: Int
    val height: Int
    val data: ByteArray

    /** Texels per source long-edge unit. */
    private val scale: Double
    private var drawn: List<BrushStroke> = emptyList()

    init {
        val size = size(sourceWidth, sourceHeight)
        width = size.width
        height = size.height
        scale = max(width, height).toDouble()
        data = ByteArray(width * height)
    }

    /**
     * Brings the grid up to date with [strokes]. Only new segments are drawn when the change
     * is a stroke being extended or added; anything else redraws everything.
     * Returns whether the grid changed.
     */
    fun sync(strokes: List<BrushStroke>): Boolean {
        if (strokes === drawn) return false
        val n = drawn.size
        when {
            strokes.size == n && n > 0 && samePrefix(strokes, n - 1) && extends(strokes[n - 1], drawn[n - 1]) ->
                draw(strokes[n - 1], from = drawn[n - 1].pointCount - 1)
            strokes.size == n + 1 && samePrefix(strokes, n) -> draw(strokes[n], from = 0)
            strokes.size == n && samePrefix(strokes, n) -> Unit
            else -> {
                data.fill(0)
                strokes.forEach { draw(it, from = 0) }
            }
        }
        drawn = strokes
        return true
    }

    /** Strength 0..1 at the texel nearest to source-normalized ([x], [y]). */
    fun valueAt(x: Float, y: Float): Float {
        val i = floor(x * width).toInt().coerceIn(0, width - 1)
        val j = floor(y * height).toInt().coerceIn(0, height - 1)
        return (data[j * width + i].toInt() and 0xFF) / 255f
    }

    private fun samePrefix(strokes: List<BrushStroke>, count: Int): Boolean {
        for (i in 0 until count) if (strokes[i] !== drawn[i]) return false
        return true
    }

    private fun extends(new: BrushStroke, old: BrushStroke): Boolean =
        new.radius == old.radius && new.hardness == old.hardness && new.erase == old.erase &&
            new.points.size >= old.points.size && new.points.subList(0, old.points.size) == old.points

    /** Draws the segments of [stroke] that start at point [from] or later. */
    private fun draw(stroke: BrushStroke, from: Int) {
        val count = stroke.pointCount
        if (count == 0) return
        val r = stroke.radius * scale
        if (count == 1) {
            stamp(px(stroke, 0), py(stroke, 0), r, stroke)
            return
        }
        // Each segment places its own evenly spaced stamps, so it draws the same whenever it is drawn.
        val spacing = max(0.75, r * 0.15)
        for (k in max(from, 0) until count - 1) {
            val ax = px(stroke, k)
            val ay = py(stroke, k)
            val bx = px(stroke, k + 1)
            val by = py(stroke, k + 1)
            val steps = max(1, ceil(hypot(bx - ax, by - ay) / spacing).toInt())
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                stamp(ax + (bx - ax) * t, ay + (by - ay) * t, r, stroke)
            }
        }
    }

    private fun px(s: BrushStroke, k: Int) = s.points[2 * k] * width.toDouble()
    private fun py(s: BrushStroke, k: Int) = s.points[2 * k + 1] * height.toDouble()

    /** A soft disc centered on ([cx], [cy]) texels, of radius [r] texels. */
    private fun stamp(cx: Double, cy: Double, r: Double, stroke: BrushStroke) {
        val radius = max(r, 0.5)
        val hard = stroke.hardness.coerceIn(0f, 0.99f).toDouble()
        val x0 = max(0, floor(cx - radius).toInt())
        val x1 = min(width - 1, ceil(cx + radius).toInt())
        val y0 = max(0, floor(cy - radius).toInt())
        val y1 = min(height - 1, ceil(cy + radius).toInt())
        val r2 = radius * radius
        for (y in y0..y1) {
            val dy = y + 0.5 - cy
            val row = y * width
            for (x in x0..x1) {
                val dx = x + 0.5 - cx
                val d2 = dx * dx + dy * dy
                if (d2 >= r2) continue
                val t = ((sqrt(d2) / radius - hard) / (1 - hard)).coerceIn(0.0, 1.0)
                val v = ((1 - t * t * (3 - 2 * t)) * 255).roundToInt()
                if (v == 0) continue
                val old = data[row + x].toInt() and 0xFF
                val new = if (stroke.erase) min(old, 255 - v) else max(old, v)
                if (new != old) data[row + x] = new.toByte()
            }
        }
    }

    companion object {
        const val LONG_EDGE = 1024

        /** Grid size for a source of [sourceWidth] x [sourceHeight]. */
        fun size(sourceWidth: Int, sourceHeight: Int): PxSize {
            val long = max(sourceWidth, sourceHeight).toDouble()
            return PxSize(
                max(1, (LONG_EDGE * sourceWidth / long).roundToInt()),
                max(1, (LONG_EDGE * sourceHeight / long).roundToInt()),
            )
        }
    }
}
