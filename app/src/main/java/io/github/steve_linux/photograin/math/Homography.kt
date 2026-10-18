// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

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
    }
}

fun Affine.toHomography() = Homography(a = a, b = c, c = tx, d = b, e = d, f = ty)

fun Affine.then(next: Homography): Homography = toHomography().then(next)
