// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A straight piece of an edge, from ([x1], [y1]) to ([x2], [y2]), in pixels. */
data class LineSegment(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    val length get() = hypot(x2 - x1, y2 - y1)
}

/**
 * Finds the straight edges of a photo: window frames, roof lines, the sides of a building.
 *
 * A simplified line segment detector in the spirit of LSD (von Gioi et al.): pixels with a
 * strong gradient are grown into regions whose edge runs in about the same direction, and a
 * region that is long and thin enough becomes a segment through its middle. Written from the
 * description of the method, no code taken from elsewhere.
 */
object LineSegments {
    /** How far, in radians, a pixel's edge direction may differ from its region's. */
    private const val TOLERANCE = PI / 8

    /**
     * The segments of the [width] x [height] luminance image [lum] (0..1) at least [minLength]
     * pixels long, longest first.
     */
    fun detect(lum: FloatArray, width: Int, height: Int, minLength: Double = max(width, height) * 0.04): List<LineSegment> {
        require(lum.size >= width * height)
        if (width < 16 || height < 16) return emptyList()
        val n = width * height
        val mag = FloatArray(n)
        // Edge direction (along the edge, not across), folded into 0..pi.
        val dir = FloatArray(n)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val gx = 3 * (lum[i - width + 1] + lum[i + width + 1] - lum[i - width - 1] - lum[i + width - 1]) +
                10 * (lum[i + 1] - lum[i - 1])
            val gy = 3 * (lum[i + width - 1] + lum[i + width + 1] - lum[i - width - 1] - lum[i - width + 1]) +
                10 * (lum[i + width] - lum[i - width])
            mag[i] = sqrt(gx * gx + gy * gy)
            var a = atan2(gy, gx) + PI.toFloat() / 2
            while (a < 0) a += PI.toFloat()
            while (a >= PI.toFloat()) a -= PI.toFloat()
            dir[i] = a
        }
        // Only clear edges: above a fixed contrast and among the strongest fifth or so.
        val sample = FloatArray(4096) { mag[(it.toLong() * 7919 % n).toInt()] }.sorted()
        val threshold = max(sample[(sample.size * 0.8).toInt()], 0.6f)

        // Seeds from the strongest pixels down, so regions start on the clearest part of an edge.
        val seeds = (0 until n).filter { mag[it] >= threshold }.sortedByDescending { mag[it] }
        val used = BooleanArray(n)
        val region = IntArray(n)
        val segments = ArrayList<LineSegment>()
        for (seed in seeds) {
            if (used[seed]) continue
            used[seed] = true
            region[0] = seed
            var size = 1
            var sumC = cos(2.0 * dir[seed])
            var sumS = sin(2.0 * dir[seed])
            var angle = dir[seed].toDouble()
            var k = 0
            while (k < size) {
                val p = region[k++]
                val px = p % width
                val py = p / width
                for (dy in -1..1) for (dx in -1..1) {
                    val qx = px + dx
                    val qy = py + dy
                    if (qx < 1 || qy < 1 || qx >= width - 1 || qy >= height - 1) continue
                    val q = qy * width + qx
                    if (used[q] || mag[q] < threshold) continue
                    if (angleDiff(dir[q].toDouble(), angle) > TOLERANCE) continue
                    used[q] = true
                    region[size++] = q
                    sumC += cos(2.0 * dir[q])
                    sumS += sin(2.0 * dir[q])
                    angle = atan2(sumS, sumC) / 2
                }
            }
            if (size >= 8) fit(region, size, width, mag, minLength)?.let { segments += it }
        }
        return segments.sortedByDescending { it.length }
    }

    /** The segment through a region's pixels, or null when the region is too short or too wide to be a line. */
    private fun fit(region: IntArray, size: Int, width: Int, mag: FloatArray, minLength: Double): LineSegment? {
        var w = 0.0
        var mx = 0.0
        var my = 0.0
        for (k in 0 until size) {
            val p = region[k]
            val m = mag[p].toDouble()
            w += m
            mx += m * (p % width)
            my += m * (p / width)
        }
        mx /= w
        my /= w
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (k in 0 until size) {
            val p = region[k]
            val m = mag[p].toDouble()
            val dx = p % width - mx
            val dy = p / width - my
            sxx += m * dx * dx
            syy += m * dy * dy
            sxy += m * dx * dy
        }
        sxx /= w
        syy /= w
        sxy /= w
        // Principal axis of the region: the direction of the line.
        val theta = 0.5 * atan2(2 * sxy, sxx - syy)
        val ux = cos(theta)
        val uy = sin(theta)
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        var spread = 0.0
        for (k in 0 until size) {
            val p = region[k]
            val dx = p % width - mx
            val dy = p / width - my
            val along = dx * ux + dy * uy
            val across = -dx * uy + dy * ux
            if (along < lo) lo = along
            if (along > hi) hi = along
            spread += mag[p] * across * across
        }
        val length = hi - lo
        if (length < minLength) return null
        // A real line is a couple of pixels thick however long it is; a curve or texture is not.
        val thickness = sqrt(spread / w)
        if (thickness > 1.2 + 0.01 * length) return null
        return LineSegment(mx + lo * ux, my + lo * uy, mx + hi * ux, my + hi * uy)
    }

    /** Difference between two undirected angles (mod pi), 0..pi/2. */
    private fun angleDiff(a: Double, b: Double): Double {
        var d = abs(a - b) % PI
        if (d > PI / 2) d = PI - d
        return d
    }
}
