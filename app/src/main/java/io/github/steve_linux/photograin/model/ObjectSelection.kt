// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The geometry of selecting an object with a tap, for subject masks with [Mask.points]: what
 * the selection model (MobileSAM) sees, and its answer as a [SubjectMask]. Free of Android
 * APIs so it can be tested on the JVM.
 *
 * The model sees the photo upright, its long edge [MODEL_SIZE] pixels, which is the size of
 * a [BrushRaster]: the preview scaled to that grid is all it needs.
 */
object ObjectSelection {
    /** The long edge of the model's input. */
    const val MODEL_SIZE = 1024

    /** The model's answer is a square of this many values a side, over its input square. */
    const val LOGITS = 256

    /** The answer's values below minus this are background, above it the object. */
    private const val EDGE = 2f

    /** The upright size of a [w] x [h] image stored with orientation [base]. */
    fun uprightSize(w: Int, h: Int, base: BaseOrientation): PxSize =
        if (base.quarterTurns.mod(2) == 1) PxSize(h, w) else PxSize(w, h)

    /**
     * The [w] x [h] ARGB [pixels] of an image stored with orientation [base], turned upright,
     * with the upright size: the model was trained on upright photos.
     */
    fun upright(pixels: IntArray, w: Int, h: Int, base: BaseOrientation): Pair<IntArray, PxSize> {
        val size = uprightSize(w, h, base)
        val turns = base.quarterTurns.mod(4)
        val out = IntArray(size.width * size.height)
        for (uy in 0 until size.height) for (ux in 0 until size.width) {
            var x = ux
            var y = uy
            var cw = size.width
            var ch = size.height
            // A clockwise turn moves (x, y) to (height - 1 - y, x); undo them one by one.
            repeat(turns) {
                val px = y
                y = cw - 1 - x
                x = px
                cw = ch.also { ch = cw }
            }
            if (base.mirror) x = w - 1 - x
            out[uy * size.width + ux] = pixels[y * w + x]
        }
        return out to size
    }

    /** Source-normalized ([x], [y]) as a fraction of the upright image. */
    fun toUpright(x: Float, y: Float, base: BaseOrientation): Pair<Float, Float> {
        var ux = if (base.mirror) 1f - x else x
        var uy = y
        repeat(base.quarterTurns.mod(4)) {
            val px = 1f - uy
            uy = ux
            ux = px
        }
        return ux to uy
    }

    /**
     * The taps of [points] (x, y, include as in [Mask.points]) for the model: their positions
     * in the pixels of an upright input of [size], and 1 to include or 0 to leave out.
     */
    fun prompt(points: List<Float>, base: BaseOrientation, size: PxSize): Pair<FloatArray, IntArray> {
        val n = points.size / 3
        val coords = FloatArray(n * 2)
        val labels = IntArray(n)
        for (i in 0 until n) {
            val (ux, uy) = toUpright(points[3 * i], points[3 * i + 1], base)
            coords[2 * i] = ux * size.width
            coords[2 * i + 1] = uy * size.height
            labels[i] = if (points[3 * i + 2] > 0.5f) 1 else 0
        }
        return coords to labels
    }

    /**
     * The mask of a source whose [BrushRaster] grid is [gridW] x [gridH], from the model's
     * answer [logits] ([LOGITS] squared values, positive on the object) for an upright input
     * of [size] and a source stored with orientation [base].
     */
    fun fromLogits(logits: FloatArray, size: PxSize, base: BaseOrientation, gridW: Int, gridH: Int): SubjectMask {
        require(logits.size == LOGITS * LOGITS) { "Expected ${LOGITS * LOGITS} values, got ${logits.size}" }
        val k = LOGITS.toFloat() / MODEL_SIZE
        // Only the part of the square the image covered means anything.
        val maxX = size.width * k - 1f
        val maxY = size.height * k - 1f
        val out = ByteArray(gridW * gridH)
        for (gy in 0 until gridH) for (gx in 0 until gridW) {
            val (ux, uy) = toUpright((gx + 0.5f) / gridW, (gy + 0.5f) / gridH, base)
            val fx = (ux * size.width * k - 0.5f).coerceIn(0f, maxX)
            val fy = (uy * size.height * k - 0.5f).coerceIn(0f, maxY)
            val v = bilinear(logits, fx, fy)
            val t = ((v + EDGE) / (2 * EDGE)).coerceIn(0f, 1f)
            out[gy * gridW + gx] = (t * t * (3f - 2f * t) * 255f).roundToInt().toByte()
        }
        return SubjectMask(gridW, gridH, out)
    }

    private fun bilinear(v: FloatArray, fx: Float, fy: Float): Float {
        val n = LOGITS
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val x1 = min(x0 + 1, n - 1)
        val y1 = min(y0 + 1, n - 1)
        val ax = fx - x0
        val ay = fy - y0
        val top = v[y0 * n + x0] * (1 - ax) + v[y0 * n + x1] * ax
        val bottom = v[y1 * n + x0] * (1 - ax) + v[y1 * n + x1] * ax
        return top * (1 - ay) + bottom * ay
    }
}
