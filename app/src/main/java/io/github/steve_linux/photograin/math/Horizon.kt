// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds how much a photo is tilted from the straight lines in it: horizons, roofs, door
 * frames, walls, lines of text. Horizontal and vertical lines count alike, since a tilt
 * turns both.
 *
 * Each candidate angle is scored by how sharply the photo's edges pile up into rows and
 * columns once turned by it: at the true tilt, every straight line falls into a single row
 * or column, and the sum of squared row and column totals peaks.
 */
object Horizon {
    /** Largest tilt looked for, in degrees; beyond it lines are taken to be meant as diagonals. */
    const val MAX_TILT = 20f

    private const val MAX_POINTS = 40_000

    /** How far above the typical angle's score the best one must be to count. */
    private const val PROMINENCE = 1.25

    /**
     * The straighten angle in degrees (as [io.github.steve_linux.photograin.model.Geometry.straighten])
     * that levels the [width] x [height] luminance image [lum], or null when it has no lines
     * clear enough to go by.
     */
    fun straightenAngle(lum: FloatArray, width: Int, height: Int): Float? {
        require(lum.size >= width * height)
        val edges = edgePoints(lum, width, height) ?: return null
        val diag = hypot(width.toDouble(), height.toDouble())
        val bins = DoubleArray(2 * diag.toInt() + 4)
        fun score(degrees: Double) = score(edges, degrees * PI / 180, bins, diag, 0) + score(edges, degrees * PI / 180, bins, diag, 1)

        // Coarse, then fine around the best coarse angle.
        val steps = (MAX_TILT * 2).roundToInt()
        val rows = DoubleArray(2 * steps + 1) { score(edges, (it - steps) * 0.5 * PI / 180, bins, diag, 0) }
        val columns = DoubleArray(2 * steps + 1) { score(edges, (it - steps) * 0.5 * PI / 180, bins, diag, 1) }
        // A real tilt turns horizontal and vertical lines alike. When both have a clear favourite
        // angle and they disagree, the lines are slanted on purpose or by perspective (a box shot
        // from above, a tiled floor), and turning the photo to either would not level it.
        val rowBest = rows.indices.maxBy { rows[it] }
        val columnBest = columns.indices.maxBy { columns[it] }
        if (stands(rows, columns) && stands(columns, rows) && abs(rowBest - columnBest) > 2) return null
        val coarse = DoubleArray(rows.size) { rows[it] + columns[it] }
        val best = (coarse.indices.maxBy { coarse[it] } - steps) * 0.5
        val fine = DoubleArray(21) { score(best - 0.5 + it * 0.05) }
        val k = fine.indices.maxBy { fine[it] }
        // A tilt only counts when it stands out from the typical angle: in noise or texture
        // every angle scores about the same.
        val typical = coarse.sorted()[coarse.size / 2]
        if (fine[k] < typical * PROMINENCE) return null
        // Between fine steps, the vertex of the parabola through the best one and its neighbours.
        var tilt = best - 0.5 + k * 0.05
        if (k in 1..19) {
            val d = fine[k - 1] - 2 * fine[k] + fine[k + 1]
            if (d < 0) tilt += 0.05 * 0.5 * (fine[k - 1] - fine[k + 1]) / d
        }
        // A line tilted clockwise on screen is levelled by turning the photo the other way.
        return (-tilt).toFloat()
    }

    /** Edge points as (x, y, gx, gy), x and y from the centre: the strongest tenth or so, or null if too few. */
    private fun edgePoints(lum: FloatArray, w: Int, h: Int): FloatArray? {
        if (w < 16 || h < 16) return null
        val mags = FloatArray(w * h)
        val gxs = FloatArray(w * h)
        val gys = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            // Scharr: closer to the true direction than Sobel at angles between the axes.
            val gx = 3 * (lum[i - w + 1] + lum[i + w + 1] - lum[i - w - 1] - lum[i + w - 1]) + 10 * (lum[i + 1] - lum[i - 1])
            val gy = 3 * (lum[i + w - 1] + lum[i + w + 1] - lum[i - w - 1] - lum[i - w + 1]) + 10 * (lum[i + w] - lum[i - w])
            gxs[i] = gx
            gys[i] = gy
            mags[i] = sqrt(gx * gx + gy * gy)
        }
        // The threshold for the strongest tenth, from a spread-out sample.
        val sample = FloatArray(4096) { mags[(it.toLong() * 7919 % mags.size).toInt()] }.sorted()
        val threshold = maxOf(sample[(sample.size * 0.9).toInt()], 0.5f)
        var count = 0
        for (m in mags) if (m >= threshold) count++
        if (count < 200) return null
        val stride = maxOf(1, count / MAX_POINTS)
        val out = FloatArray((count / stride + 1) * 4)
        var n = 0
        var seen = 0
        for (i in mags.indices) {
            if (mags[i] < threshold || seen++ % stride != 0 || n + 4 > out.size) continue
            out[n++] = i % w - w / 2f
            out[n++] = i / w - h / 2f
            out[n++] = gxs[i]
            out[n++] = gys[i]
        }
        return out.copyOf(n)
    }

    /**
     * Whether [scores] have a favourite angle of their own: a peak inside the range (lines off
     * the axis score higher the further they are turned, so a lone horizon makes the columns
     * climb towards an end), standing out from the typical angle, and with a fair share of the
     * edges compared with the [other] axis.
     */
    private fun stands(scores: DoubleArray, other: DoubleArray): Boolean {
        val best = scores.indices.maxBy { scores[it] }
        val max = scores[best]
        return best in 2 until scores.size - 2 &&
            max >= scores.sorted()[scores.size / 2] * PROMINENCE &&
            max >= other.max() * 0.2
    }

    /**
     * How sharply the edges fall into rows ([axis] 0) or columns (1) once turned back by
     * [radians]: each point adds the part of its gradient across the row to that row's total.
     */
    private fun score(edges: FloatArray, radians: Double, bins: DoubleArray, diag: Double, axis: Int): Double {
        val c = cos(radians)
        val s = sin(radians)
        var total = 0.0
        run {
            bins.fill(0.0)
            var p = 0
            while (p < edges.size) {
                val x = edges[p]
                val y = edges[p + 1]
                val gx = edges[p + 2]
                val gy = edges[p + 3]
                // Rows run along the tilted direction (c, s); columns across it.
                val pos = if (axis == 0) -x * s + y * c else x * c + y * s
                val across = if (axis == 0) -gx * s + gy * c else gx * c + gy * s
                bins[(pos + diag).toInt().coerceIn(0, bins.size - 1)] += abs(across)
                p += 4
            }
            for (b in bins) total += b * b
        }
        return total
    }
}
