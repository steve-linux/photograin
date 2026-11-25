// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.LineSegment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What automatic perspective correction straightens. */
enum class UprightMode {
    /** Lines that should be vertical (the sides of buildings), with the photo levelled around them. */
    VERTICAL,

    /** Vertical lines and the horizontal lines of the main facade, as if shot square on. */
    FULL,
}

/**
 * Automatic perspective correction ("upright"): finds where the photo's vertical lines meet
 * (their vanishing point) and, for [UprightMode.FULL], where the horizontal lines of the main
 * facade meet, then builds the four-point [Quad] whose sides run along those lines. Squaring
 * that quad up, as four-point perspective already does, makes the lines straight.
 *
 * Working through a quad keeps everything else unchanged: preview, export tiles, masks and
 * auto-fit crop handle it already, it is saved in the recipe, and the user can drag its points.
 */
object Upright {
    /** Lines within this angle of vertical (or horizontal) can belong to that direction. */
    private const val CANDIDATE_ANGLE = 35.0 * PI / 180

    /** A line counts towards a vanishing point when it points at it within this angle. */
    private const val INLIER_ANGLE = 1.5 * PI / 180

    /**
     * Nearest a vanishing point may be to the middle of the photo, in half long edges: closer
     * than this the lines converge so much that straightening them would stretch the photo
     * beyond use, and they are more likely meant as diagonals.
     */
    const val MIN_DISTANCE = 1.6

    /** Least total length of agreeing lines, in half long edges, for a direction to count. */
    private const val MIN_SUPPORT = 0.5

    /** Line-ups that are already straight within this angle at the frame's edge need no correction. */
    private const val STRAIGHT_ENOUGH = 0.2 * PI / 180

    /** Outcome of a correction attempt. */
    sealed interface Result {
        /** The points to square up, normalized to the frame as [Geometry.quad]. */
        data class Corrected(val quad: Quad) : Result

        /** The lines are already straight: nothing to change. */
        data object AlreadyStraight : Result

        /** Not enough clear lines in the needed directions. */
        data object NotFound : Result

        /** The lines converge too strongly to straighten them sensibly. */
        data object TooStrong : Result
    }

    /** A vanishing point in centered coordinates (homogeneous), and the length of lines meeting there. */
    private class Vanishing(val p: DoubleArray, val support: Double)

    /**
     * The correction for [segments], given in pixels of the [frameWidth] x [frameHeight] frame
     * (after rotation and mirroring, before perspective and straightening).
     */
    fun correct(segments: List<LineSegment>, frameWidth: Double, frameHeight: Double, mode: UprightMode): Result {
        val cx = frameWidth / 2
        val cy = frameHeight / 2
        val scale = max(frameWidth, frameHeight) / 2
        // Centered and scaled so the long edge spans -1..1: well conditioned for the algebra.
        val lines = segments.map {
            Line((it.x1 - cx) / scale, (it.y1 - cy) / scale, (it.x2 - cx) / scale, (it.y2 - cy) / scale)
        }
        val vertical = lines.filter { abs(it.angleFromVertical()) < CANDIDATE_ANGLE }
        val v = vanishingPoint(vertical) ?: return Result.NotFound
        if (!pointsAlong(v.p, vertical = true)) return Result.NotFound
        if (distance(v.p) < MIN_DISTANCE) return Result.TooStrong
        val u = if (mode == UprightMode.FULL) {
            val horizontal = lines.filter { abs(it.angleFromHorizontal()) < CANDIDATE_ANGLE }
            val found = vanishingPoint(horizontal) ?: return Result.NotFound
            if (!pointsAlong(found.p, vertical = false)) return Result.NotFound
            if (distance(found.p) < MIN_DISTANCE) return Result.TooStrong
            found
        } else {
            null
        }
        val halfW = frameWidth / 2 / scale
        val halfH = frameHeight / 2 / scale
        if (isStraight(v.p, vertical = true, halfW, halfH) && (u == null || isStraight(u.p, vertical = false, halfW, halfH))) {
            return Result.AlreadyStraight
        }

        // The quad's sides: through the vertical vanishing point, either side of the middle;
        // top and bottom across that direction (VERTICAL) or through the horizontal one (FULL).
        val n = directionTo(v.p)
        val t = doubleArrayOf(-n[1], n[0])
        var a = halfW * 0.5
        var b = halfH * 0.5
        repeat(12) {
            val corners = quadCorners(v.p, u?.p, n, t, a, b)
            if (corners != null) {
                val quad = toQuad(corners, cx, cy, scale, frameWidth, frameHeight)
                if (quad != null && quad.isUsable(frameWidth, frameHeight)) return Result.Corrected(quad)
            }
            a *= 0.8
            b *= 0.8
        }
        return Result.TooStrong
    }

    private class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
        val length = hypot(x2 - x1, y2 - y1)
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        val dx = (x2 - x1) / length
        val dy = (y2 - y1) / length

        /** Homogeneous line through both ends, with (a, b) of unit length. */
        val h: DoubleArray = cross(doubleArrayOf(x1, y1, 1.0), doubleArrayOf(x2, y2, 1.0)).let {
            val k = hypot(it[0], it[1])
            doubleArrayOf(it[0] / k, it[1] / k, it[2] / k)
        }

        fun angleFromVertical() = atan2(dx, dy).let { fold(it) }
        fun angleFromHorizontal() = atan2(dy, dx).let { fold(it) }

        /** Angle between this line and the direction from its middle to [p] (homogeneous). */
        fun angleTo(p: DoubleArray): Double {
            val tx = p[0] - mx * p[2]
            val ty = p[1] - my * p[2]
            val len = hypot(tx, ty)
            if (len < 1e-12) return PI / 2
            return acos(min(1.0, abs(tx * dx + ty * dy) / len))
        }
    }

    /** An undirected angle folded into -pi/2..pi/2. */
    private fun fold(a: Double): Double {
        var r = a
        while (r > PI / 2) r -= PI
        while (r < -PI / 2) r += PI
        return r
    }

    /**
     * Where most of [lines] meet: every pair of the longest lines proposes a point, the one with
     * the most length pointing at it wins, and least squares over those lines refines it.
     */
    private fun vanishingPoint(lines: List<Line>): Vanishing? {
        if (lines.size < 2) return null
        val seeds = lines.sortedByDescending { it.length }.take(60)
        var best: DoubleArray? = null
        var bestScore = 0.0
        for (i in seeds.indices) for (j in i + 1 until seeds.size) {
            val l1 = seeds[i]
            val l2 = seeds[j]
            // Two pieces of the same line say nothing about where lines meet.
            if (abs(dot(l1.h, doubleArrayOf(l2.mx, l2.my, 1.0))) < 0.01) continue
            val p = normalize(cross(l1.h, l2.h)) ?: continue
            val s = score(lines, p)
            if (s > bestScore) {
                bestScore = s
                best = p
            }
        }
        var p = best ?: return null
        repeat(3) {
            val inliers = lines.filter { it.angleTo(p) < INLIER_ANGLE }
            if (inliers.size < 2) return@repeat
            p = refine(inliers) ?: return@repeat
        }
        val inliers = lines.filter { it.angleTo(p) < INLIER_ANGLE }
        val support = inliers.sumOf { it.length }
        if (inliers.size < 3 || support < MIN_SUPPORT) return null
        return Vanishing(p, support)
    }

    /** Length of the lines pointing at [p], longer and better-aimed lines counting more. */
    private fun score(lines: List<Line>, p: DoubleArray): Double {
        var s = 0.0
        for (l in lines) {
            val a = l.angleTo(p)
            if (a < INLIER_ANGLE) s += l.length * (1 - a / INLIER_ANGLE)
        }
        return s
    }

    /** The point closest to all [lines] (length-weighted), as the least eigenvector of the sum of l l^T. */
    private fun refine(lines: List<Line>): DoubleArray? {
        val m = Array(3) { DoubleArray(3) }
        for (l in lines) for (r in 0 until 3) for (c in 0 until 3) m[r][c] += l.length * l.h[r] * l.h[c]
        return normalize(smallestEigenvector(m))
    }

    /** Whether the vanishing point [p] lies in the [vertical] (or horizontal) direction from the middle. */
    private fun pointsAlong(p: DoubleArray, vertical: Boolean): Boolean {
        val d = directionTo(p)
        val angle = if (vertical) atan2(d[0], d[1]) else atan2(d[1], d[0])
        return abs(fold(angle)) < CANDIDATE_ANGLE
    }

    /** Distance of [p] from the middle, infinite for a point at infinity. */
    private fun distance(p: DoubleArray): Double =
        if (abs(p[2]) < 1e-12) Double.POSITIVE_INFINITY else hypot(p[0] / p[2], p[1] / p[2])

    /** Unit direction from the middle towards [p]. */
    private fun directionTo(p: DoubleArray): DoubleArray {
        val (x, y) = if (abs(p[2]) < 1e-12) p[0] to p[1] else p[0] / p[2] to p[1] / p[2]
        val len = hypot(x, y)
        return doubleArrayOf(x / len, y / len)
    }

    /**
     * Whether the lines meeting at [p] are already straight: at the frame's edges, the line
     * through [p] differs from the true vertical (or horizontal) by less than [STRAIGHT_ENOUGH].
     */
    private fun isStraight(p: DoubleArray, vertical: Boolean, halfW: Double, halfH: Double): Boolean {
        val points = listOf(0.0 to 0.0, -halfW to 0.0, halfW to 0.0, 0.0 to -halfH, 0.0 to halfH)
        for ((x, y) in points) {
            val tx = p[0] - x * p[2]
            val ty = p[1] - y * p[2]
            val angle = if (vertical) atan2(tx, ty) else atan2(ty, tx)
            if (abs(fold(angle)) > STRAIGHT_ENOUGH) return false
        }
        return true
    }

    /** Corners top-left, top-right, bottom-right, bottom-left in centered coordinates, or null. */
    private fun quadCorners(v: DoubleArray, u: DoubleArray?, n: DoubleArray, t: DoubleArray, a: Double, b: Double): List<DoubleArray>? {
        val left = cross(doubleArrayOf(-a * t[0], -a * t[1], 1.0), v)
        val right = cross(doubleArrayOf(a * t[0], a * t[1], 1.0), v)
        val p1 = doubleArrayOf(b * n[0], b * n[1], 1.0)
        val p2 = doubleArrayOf(-b * n[0], -b * n[1], 1.0)
        val across = u ?: doubleArrayOf(t[0], t[1], 0.0)
        val side1 = cross(p1, across)
        val side2 = cross(p2, across)
        val corners = listOf(cross(left, side1), cross(right, side1), cross(right, side2), cross(left, side2)).map {
            if (abs(it[2]) < 1e-9) return null
            doubleArrayOf(it[0] / it[2], it[1] / it[2])
        }
        // Clockwise on screen (y down), starting from the top-left.
        val mx = corners.sumOf { it[0] } / 4
        val my = corners.sumOf { it[1] } / 4
        val sorted = corners.sortedBy { atan2(it[1] - my, it[0] - mx) }
        val first = sorted.indices.minBy { sorted[it][0] + sorted[it][1] }
        return List(4) { sorted[(first + it) % 4] }
    }

    private fun toQuad(c: List<DoubleArray>, cx: Double, cy: Double, scale: Double, w: Double, h: Double): Quad? {
        val p = c.map { ((it[0] * scale + cx) / w).toFloat() to ((it[1] * scale + cy) / h).toFloat() }
        if (p.any { (x, y) -> x !in 0f..1f || y !in 0f..1f }) return null
        return Quad(p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second, p[3].first, p[3].second)
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun normalize(p: DoubleArray): DoubleArray? {
        val len = sqrt(dot(p, p))
        if (len < 1e-15 || !len.isFinite()) return null
        return doubleArrayOf(p[0] / len, p[1] / len, p[2] / len)
    }

    /** Eigenvector of the symmetric 3x3 [m] with the smallest eigenvalue (Jacobi rotations). */
    private fun smallestEigenvector(m: Array<DoubleArray>): DoubleArray {
        val a = Array(3) { m[it].copyOf() }
        val v = Array(3) { r -> DoubleArray(3) { c -> if (r == c) 1.0 else 0.0 } }
        repeat(50) {
            var p = 0
            var q = 1
            for ((r, c) in listOf(0 to 2, 1 to 2)) if (abs(a[r][c]) > abs(a[p][q])) { p = r; q = c }
            if (abs(a[p][q]) < 1e-18) return@repeat
            val theta = 0.5 * atan2(2 * a[p][q], a[q][q] - a[p][p])
            val c = kotlin.math.cos(theta)
            val s = kotlin.math.sin(theta)
            for (k in 0 until 3) {
                val akp = a[k][p]
                val akq = a[k][q]
                a[k][p] = c * akp - s * akq
                a[k][q] = s * akp + c * akq
            }
            for (k in 0 until 3) {
                val apk = a[p][k]
                val aqk = a[q][k]
                a[p][k] = c * apk - s * aqk
                a[q][k] = s * apk + c * aqk
            }
            for (k in 0 until 3) {
                val vkp = v[k][p]
                val vkq = v[k][q]
                v[k][p] = c * vkp - s * vkq
                v[k][q] = s * vkp + c * vkq
            }
        }
        val i = (0 until 3).minBy { a[it][it] }
        return doubleArrayOf(v[0][i], v[1][i], v[2][i])
    }
}
