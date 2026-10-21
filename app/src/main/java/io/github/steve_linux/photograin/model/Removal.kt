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
 * The geometry of removing objects with [RetouchType.REMOVE]: which part of the photo the
 * inpainting model sees, and the painted area as a grid over it. Free of Android APIs so it
 * can be tested on the JVM.
 *
 * Everything is a fraction of the source, so the preview and the export give the model the
 * same picture: it always works on [MODEL_SIZE] pixels whatever the photo's resolution.
 */
object Removal {
    /** The model's square input. */
    const val MODEL_SIZE = 512

    /** Model pixels added around the strokes for the area to fill, so their soft edge is covered too. */
    const val GROW = 3.0

    /** Hardness of the removal brush: a soft edge hides the seam. */
    const val HARDNESS = 0.6f

    /** The part of the long edge always kept around the strokes, so small objects get enough context. */
    private const val MIN_MARGIN = 0.04

    /**
     * The area of a [width] x [height] source that the model sees for [strokes], in source
     * pixels as left, top, right, bottom: a square around them with room for context on each
     * side, moved and clipped to stay inside the photo. Null without strokes.
     */
    fun crop(strokes: List<BrushStroke>, width: Int, height: Int): IntArray? {
        val b = bounds(strokes, width, height) ?: return null
        val long = max(width, height).toDouble()
        val size = max(b[2] - b[0], b[3] - b[1])
        val side = min(size + 2 * max(size * 0.5, MIN_MARGIN * long), long)
        val cx = (b[0] + b[2]) / 2
        val cy = (b[1] + b[3]) / 2
        val l = (cx - side / 2).coerceIn(0.0, max(0.0, width - side))
        val t = (cy - side / 2).coerceIn(0.0, max(0.0, height - side))
        val out = intArrayOf(
            floor(l).toInt().coerceIn(0, width - 1),
            floor(t).toInt().coerceIn(0, height - 1),
            ceil(l + side).toInt().coerceIn(1, width),
            ceil(t + side).toInt().coerceIn(1, height),
        )
        return out.takeIf { it[2] > it[0] && it[3] > it[1] }
    }

    /** The box the strokes paint in a [width] x [height] source, in pixels (left, top, right, bottom). */
    fun bounds(strokes: List<BrushStroke>, width: Int, height: Int): DoubleArray? {
        val long = max(width, height).toDouble()
        var l = Double.MAX_VALUE
        var t = Double.MAX_VALUE
        var r = -Double.MAX_VALUE
        var b = -Double.MAX_VALUE
        for (s in strokes) {
            val rad = s.radius * long
            for (k in 0 until s.pointCount) {
                val x = s.points[2 * k] * width.toDouble()
                val y = s.points[2 * k + 1] * height.toDouble()
                l = min(l, x - rad); t = min(t, y - rad); r = max(r, x + rad); b = max(b, y + rad)
            }
        }
        return if (r < l) null else doubleArrayOf(l, t, r, b)
    }

    /** The model's grid for a [cropW] x [cropH] crop: its long edge is [MODEL_SIZE]. */
    fun modelSize(cropW: Int, cropH: Int): PxSize {
        val k = MODEL_SIZE.toDouble() / max(cropW, cropH)
        return PxSize(
            (cropW * k).roundToInt().coerceIn(1, MODEL_SIZE),
            (cropH * k).roundToInt().coerceIn(1, MODEL_SIZE),
        )
    }

    /**
     * [strokes] drawn into a [gridW] x [gridH] grid over [crop] (source pixels, as from [crop])
     * of a [width] x [height] source, 0..255. With [grow] > 0 every stroke is that many grid
     * pixels wider and fully on, for the area the model fills; otherwise the brush is soft at
     * the edge, for blending the result in.
     */
    fun raster(
        strokes: List<BrushStroke>,
        crop: IntArray,
        width: Int,
        height: Int,
        gridW: Int,
        gridH: Int,
        grow: Double = 0.0,
    ): ByteArray {
        val out = ByteArray(gridW * gridH)
        val sx = gridW / (crop[2] - crop[0]).toDouble()
        val sy = gridH / (crop[3] - crop[1]).toDouble()
        val long = max(width, height).toDouble()
        for (s in strokes) {
            val r = s.radius * long * (sx + sy) / 2 + grow
            val hard = if (grow > 0) 0.99 else s.hardness.coerceIn(0f, 0.99f).toDouble()
            fun px(k: Int) = (s.points[2 * k] * width.toDouble() - crop[0]) * sx
            fun py(k: Int) = (s.points[2 * k + 1] * height.toDouble() - crop[1]) * sy
            if (s.pointCount == 1) stamp(out, gridW, gridH, px(0), py(0), r, hard)
            val spacing = max(0.75, r * 0.15)
            for (k in 0 until s.pointCount - 1) {
                val ax = px(k); val ay = py(k); val bx = px(k + 1); val by = py(k + 1)
                val steps = max(1, ceil(hypot(bx - ax, by - ay) / spacing).toInt())
                for (i in 0..steps) {
                    val t = i.toDouble() / steps
                    stamp(out, gridW, gridH, ax + (bx - ax) * t, ay + (by - ay) * t, r, hard)
                }
            }
        }
        return out
    }

    /**
     * [rgba] ([w] x [h] RGBA bytes) in the top-left corner of a [MODEL_SIZE] square, the rest
     * filled by mirroring it, so the model sees no hard border there.
     */
    fun padded(rgba: ByteArray, w: Int, h: Int): ByteArray {
        val n = MODEL_SIZE
        val out = ByteArray(n * n * 4)
        for (y in 0 until n) {
            val sy = mirror(y, h)
            for (x in 0 until n) {
                val si = (sy * w + mirror(x, w)) * 4
                rgba.copyInto(out, (y * n + x) * 4, si, si + 4)
            }
        }
        return out
    }

    /**
     * A circle around the strokes, for picking and listing the spot: center and radius (in
     * long-edge units) of the box they paint in a [width] x [height] source.
     */
    fun circle(strokes: List<BrushStroke>, width: Int, height: Int): Triple<Float, Float, Float>? {
        val b = bounds(strokes, width, height) ?: return null
        val long = max(width, height).toDouble()
        return Triple(
            ((b[0] + b[2]) / 2 / width).toFloat(),
            ((b[1] + b[3]) / 2 / height).toFloat(),
            (max(b[2] - b[0], b[3] - b[1]) / 2 / long).toFloat(),
        )
    }

    /** Reflects [i] back into 0 until [n] (without repeating the edge pixel). */
    private fun mirror(i: Int, n: Int): Int {
        if (n == 1) return 0
        val period = 2 * (n - 1)
        val m = i % period
        return if (m < n) m else period - m
    }

    private fun stamp(out: ByteArray, w: Int, h: Int, cx: Double, cy: Double, r: Double, hard: Double) {
        val radius = max(r, 0.5)
        val x0 = max(0, floor(cx - radius).toInt())
        val x1 = min(w - 1, ceil(cx + radius).toInt())
        val y0 = max(0, floor(cy - radius).toInt())
        val y1 = min(h - 1, ceil(cy + radius).toInt())
        val r2 = radius * radius
        for (y in y0..y1) {
            val dy = y + 0.5 - cy
            for (x in x0..x1) {
                val dx = x + 0.5 - cx
                val d2 = dx * dx + dy * dy
                if (d2 >= r2) continue
                val t = ((sqrt(d2) / radius - hard) / (1 - hard)).coerceIn(0.0, 1.0)
                val v = ((1 - t * t * (3 - 2 * t)) * 255).roundToInt()
                val i = y * w + x
                if (v > (out[i].toInt() and 0xFF)) out[i] = v.toByte()
            }
        }
    }
}
