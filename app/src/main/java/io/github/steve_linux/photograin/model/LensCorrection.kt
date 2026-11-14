// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Lensfun coefficients evaluated at the photo's focal length and aperture. */
data class LensCorrection(
    val name: String,
    val calibrationScale: Double,
    val vignetteScale: Double,
    val distortion: List<Double>,
    val red: List<Double>,
    val blue: List<Double>,
    val vignette: List<Double>,
) {
    fun mapping(width: Int, height: Int) = LensMapping(this, width, height)
}

/** Maps corrected UVs (before rotation/crop) to the original stored image. */
class LensMapping internal constructor(val profile: LensCorrection, width: Int, height: Int) {
    private val diagonal = hypot(width.toDouble(), height.toDouble())
    val axisX = 2 * width / diagonal * profile.calibrationScale
    val axisY = 2 * height / diagonal * profile.calibrationScale
    private val channels = listOf(listOf(1.0), profile.red, profile.blue)
    val scale: Double = fitScale()

    fun toOriginal(point: Vec2): Vec2 {
        val x = (point.x - 0.5) * scale
        val y = (point.y - 0.5) * scale
        val factor = radial(profile.distortion, hypot(x * axisX, y * axisY))
        return Vec2(0.5 + x * factor, 0.5 + y * factor)
    }

    fun fromOriginal(point: Vec2): Vec2 {
        val x = point.x - 0.5
        val y = point.y - 0.5
        val distorted = hypot(x * axisX, y * axisY)
        if (distorted == 0.0) return point
        var lo = 0.0
        var hi = profile.calibrationScale
        repeat(24) {
            val r = (lo + hi) / 2
            if (r * radial(profile.distortion, r) < distorted) lo = r else hi = r
        }
        val factor = (lo + hi) / (2 * distorted * scale)
        return Vec2(0.5 + x * factor, 0.5 + y * factor)
    }

    /** Conservative source UV bounds for all three channels over an output UV rectangle. */
    fun sourceBounds(left: Double, top: Double, right: Double, bottom: Double): Bounds {
        // Cells keep polynomial interval bounds tight, even for moustache distortion.
        val cells = 4
        var l = Double.POSITIVE_INFINITY
        var t = Double.POSITIVE_INFINITY
        var r = Double.NEGATIVE_INFINITY
        var b = Double.NEGATIVE_INFINITY
        for (y in 0 until cells) for (x in 0 until cells) {
            val cell = bounds(
                left + (right - left) * x / cells, top + (bottom - top) * y / cells,
                left + (right - left) * (x + 1) / cells, top + (bottom - top) * (y + 1) / cells, scale,
            )
            l = min(l, cell.left)
            t = min(t, cell.top)
            r = max(r, cell.right)
            b = max(b, cell.bottom)
        }
        return Bounds(l, t, r, b)
    }

    data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private fun bounds(left: Double, top: Double, right: Double, bottom: Double, scale: Double): Bounds {
        val x0 = (left - 0.5) * scale
        val y0 = (top - 0.5) * scale
        val x1 = (right - 0.5) * scale
        val y1 = (bottom - 0.5) * scale
        val nearX = 0.0.coerceIn(x0, x1)
        val nearY = 0.0.coerceIn(y0, y1)
        val minR = hypot(nearX * axisX, nearY * axisY)
        val maxR = hypot(max(abs(x0), abs(x1)) * axisX, max(abs(y0), abs(y1)) * axisY)
        val d = polynomialBounds(profile.distortion, minR, maxR)
        val minDistorted = minR * d.first.coerceAtLeast(0.0)
        val maxDistorted = maxR * d.second.coerceAtLeast(0.0)
        val ca = channels.map { polynomialBounds(it, minDistorted, maxDistorted) }
        val low = (d.first * ca.minOf { it.first }).coerceAtLeast(0.0)
        val high = (d.second * ca.maxOf { it.second }).coerceAtLeast(low)
        return Bounds(
            0.5 + minOf(x0 * low, x0 * high), 0.5 + minOf(y0 * low, y0 * high),
            0.5 + maxOf(x1 * low, x1 * high), 0.5 + maxOf(y1 * low, y1 * high),
        )
    }

    private fun fitScale(): Double {
        fun fits(scale: Double): Boolean {
            val cells = 12
            // The profile is radial about the center, so one quadrant covers all four.
            for (y in 0 until cells) for (x in 0 until cells) {
                val cell = bounds(
                    0.5 + x * 0.5 / cells, 0.5 + y * 0.5 / cells,
                    0.5 + (x + 1) * 0.5 / cells, 0.5 + (y + 1) * 0.5 / cells, scale,
                )
                if (cell.right > 1.0000001 || cell.bottom > 1.0000001) return false
            }
            return true
        }
        if (fits(1.0)) return 1.0
        var lo = 0.0
        var hi = 1.0
        repeat(18) {
            val mid = (lo + hi) / 2
            if (fits(mid)) lo = mid else hi = mid
        }
        return lo
    }

    private companion object {
        fun radial(coeff: List<Double>, r: Double): Double = coeff.asReversed().fold(0.0) { acc, c -> acc * r + c }

        fun polynomialBounds(coeff: List<Double>, near: Double, far: Double): Pair<Double, Double> {
            var lo = 0.0
            var hi = 0.0
            var nearPower = 1.0
            var farPower = 1.0
            for (c in coeff) {
                lo += minOf(c * nearPower, c * farPower)
                hi += maxOf(c * nearPower, c * farPower)
                nearPower *= near
                farPower *= far
            }
            return lo to hi
        }
    }
}
