// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import kotlin.math.abs

/**
 * 2D projective transform (row-major 3x3), for perspective correction:
 *
 *     w  = g*x + h*y + i
 *     x' = (a*x + b*y + c) / w
 *     y' = (d*x + e*y + f) / w
 */
data class Homography(
    val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0,
    val d: Double = 0.0, val e: Double = 1.0, val f: Double = 0.0,
    val g: Double = 0.0, val h: Double = 0.0, val i: Double = 1.0,
) {
    fun apply(p: Vec2): Vec2 {
        val w = g * p.x + h * p.y + i
        return Vec2((a * p.x + b * p.y + c) / w, (d * p.x + e * p.y + f) / w)
    }

    fun apply(x: Double, y: Double) = apply(Vec2(x, y))

    /** Returns the transform that applies `this` first and then [next]. */
    fun then(next: Homography) = Homography(
        a = next.a * a + next.b * d + next.c * g,
        b = next.a * b + next.b * e + next.c * h,
        c = next.a * c + next.b * f + next.c * i,
        d = next.d * a + next.e * d + next.f * g,
        e = next.d * b + next.e * e + next.f * h,
        f = next.d * c + next.e * f + next.f * i,
        g = next.g * a + next.h * d + next.i * g,
        h = next.g * b + next.h * e + next.i * h,
        i = next.g * c + next.h * f + next.i * i,
    )

    fun then(next: Affine) = then(next.toHomography())

    fun inverse(): Homography {
        val ca = e * i - f * h
        val cb = -(d * i - f * g)
        val cc = d * h - e * g
        val det = a * ca + b * cb + c * cc
        require(det != 0.0) { "Homography is not invertible" }
        return Homography(
            a = ca / det, b = -(b * i - c * h) / det, c = (b * f - c * e) / det,
            d = cb / det, e = (a * i - c * g) / det, f = -(a * f - c * d) / det,
            g = cc / det, h = -(a * h - b * g) / det, i = (a * e - b * d) / det,
        )
    }

    /** Column-major 3x3 matrix, as expected by `glUniformMatrix3fv(..., transpose = false, ...)`. */
    fun toGlMat3(): FloatArray = floatArrayOf(
        a.toFloat(), d.toFloat(), g.toFloat(),
        b.toFloat(), e.toFloat(), h.toFloat(),
        c.toFloat(), f.toFloat(), i.toFloat(),
    )

    companion object {
        val IDENTITY = Homography()

        /** The transform taking each of the four points [from] to the matching one of [to]; null if they are degenerate. */
        fun fromPoints(from: List<Vec2>, to: List<Vec2>): Homography? {
            require(from.size == 4 && to.size == 4)
            // Eight equations in a..h with i = 1, two per point pair.
            val m = Array(8) { DoubleArray(9) }
            for (k in 0 until 4) {
                val (x, y) = from[k]
                val (u, v) = to[k]
                m[2 * k] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                m[2 * k + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val s = solve(m) ?: return null
            return Homography(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], 1.0)
        }

        /** Gaussian elimination with partial pivoting on an n x (n + 1) augmented matrix. */
        private fun solve(m: Array<DoubleArray>): DoubleArray? {
            val n = m.size
            for (col in 0 until n) {
                val pivot = (col until n).maxBy { abs(m[it][col]) }
                if (abs(m[pivot][col]) < 1e-12) return null
                val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = m[r][col] / m[col][col]
                    if (f == 0.0) continue
                    for (c in col..n) m[r][c] -= f * m[col][c]
                }
            }
            return DoubleArray(n) { m[it][n] / m[it][it] }
        }
    }
}

fun Affine.toHomography() = Homography(a = a, b = c, c = tx, d = b, e = d, f = ty)

fun Affine.then(next: Homography): Homography = toHomography().then(next)
