// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds how much a photo is tilted from the straight lines in it: horizons, roofs, door
 * frames, walls. Horizontal and vertical lines count alike, since a tilt turns both.
 */
object Horizon {
    /** Largest tilt looked for, in degrees; beyond it lines are taken to be meant as diagonals. */
    const val MAX_TILT = 20f

    private const val BLOCK = 16
    private const val BIN = 0.1f

    /**
     * The straighten angle in degrees (as [io.github.steve_linux.photograin.model.Geometry.straighten])
     * that levels the [width] x [height] luminance image [lum], or null when it has no lines
     * clear enough to go by.
     */
    fun straightenAngle(lum: FloatArray, width: Int, height: Int): Float? {
        require(lum.size >= width * height)
        val bins = (2 * MAX_TILT / BIN).roundToInt() + 1
        val histogram = DoubleArray(bins)
        var total = 0.0
        // Per block, the structure tensor of the gradients gives one orientation and how
        // strongly the block agrees on it: a straight edge does, texture and noise do not.
        for (by in 1 until height - BLOCK step BLOCK) for (bx in 1 until width - BLOCK step BLOCK) {
            var xx = 0.0
            var yy = 0.0
            var xy = 0.0
            for (y in by until minOf(by + BLOCK, height - 1)) for (x in bx until minOf(bx + BLOCK, width - 1)) {
                val i = y * width + x
                // Scharr: closer to the true direction than Sobel at angles between the axes.
                val gx = 3 * (lum[i - width + 1] + lum[i + width + 1] - lum[i - width - 1] - lum[i + width - 1]) +
                    10 * (lum[i + 1] - lum[i - 1])
                val gy = 3 * (lum[i + width - 1] + lum[i + width + 1] - lum[i - width - 1] - lum[i - width + 1]) +
                    10 * (lum[i + width] - lum[i - width])
                xx += gx * gx
                yy += gy * gy
                xy += gx * gy
            }
            val energy = xx + yy
            if (energy < 1e-4) continue
            val coherence = sqrt((xx - yy) * (xx - yy) + 4 * xy * xy) / energy
            if (coherence < 0.75) continue
            // Gradient direction, then the edge across it, folded so vertical edges join horizontal ones.
            val gradient = 0.5 * atan2(2 * xy, xx - yy) * 180 / PI
            val tilt = fold(gradient + 90)
            if (abs(tilt) > MAX_TILT) continue
            val weight = energy * coherence * coherence
            val pos = (tilt + MAX_TILT) / BIN
            val lo = pos.toInt().coerceIn(0, bins - 1)
            val hi = (lo + 1).coerceAtMost(bins - 1)
            val f = pos - lo
            histogram[lo] += weight * (1 - f)
            histogram[hi] += weight * f
            total += weight
        }
        if (total <= 0) return null
        val smooth = smooth(histogram, radius = 5)
        val peak = smooth.indices.maxBy { smooth[it] }
        // The peak must stand out: a third of all line weight within a degree of it.
        val reach = (1f / BIN).roundToInt()
        var near = 0.0
        var moment = 0.0
        for (k in maxOf(0, peak - reach)..minOf(bins - 1, peak + reach)) {
            near += histogram[k]
            moment += histogram[k] * k
        }
        if (near < total / 3) return null
        val tilt = (moment / near).toFloat() * BIN - MAX_TILT
        // A line tilted clockwise on screen is levelled by turning the photo the other way.
        return -tilt
    }

    /** [degrees] folded into -45..45, a quarter turn being the same line orientation. */
    private fun fold(degrees: Double): Double {
        var d = degrees % 90
        if (d >= 45) d -= 90
        if (d < -45) d += 90
        return d
    }

    private fun smooth(values: DoubleArray, radius: Int): DoubleArray = DoubleArray(values.size) { i ->
        var sum = 0.0
        for (k in -radius..radius) {
            val j = i + k
            if (j in values.indices) sum += values[j] * (radius + 1 - abs(k))
        }
        sum
    }
}
