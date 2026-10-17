// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import kotlin.math.cos
import kotlin.math.sin

/** A 2D point in pixel or normalized coordinates. */
data class Vec2(val x: Double, val y: Double)

/**
 * 2D affine transform in image coordinates (y grows downwards):
 *
 *     x' = a*x + c*y + tx
 *     y' = b*x + d*y + ty
 */
data class Affine(
    val a: Double = 1.0, val b: Double = 0.0,
    val c: Double = 0.0, val d: Double = 1.0,
    val tx: Double = 0.0, val ty: Double = 0.0,
) {
    fun apply(p: Vec2) = Vec2(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

    fun apply(x: Double, y: Double) = apply(Vec2(x, y))

    /** Returns the transform that applies `this` first and then [next]. */
    fun then(next: Affine) = Affine(
        a = next.a * a + next.c * b,
        b = next.b * a + next.d * b,
        c = next.a * c + next.c * d,
        d = next.b * c + next.d * d,
        tx = next.a * tx + next.c * ty + next.tx,
        ty = next.b * tx + next.d * ty + next.ty,
    )

    fun inverse(): Affine {
        val det = a * d - b * c
        require(det != 0.0) { "Affine transform is not invertible" }
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
    }

    /** Column-major 3x3 matrix, as expected by `glUniformMatrix3fv(..., transpose = false, ...)`. */
    fun toGlMat3(): FloatArray = floatArrayOf(
        a.toFloat(), b.toFloat(), 0f,
        c.toFloat(), d.toFloat(), 0f,
        tx.toFloat(), ty.toFloat(), 1f,
    )

    companion object {
        val IDENTITY = Affine()

        fun translate(x: Double, y: Double) = Affine(tx = x, ty = y)

        fun scale(sx: Double, sy: Double) = Affine(a = sx, d = sy)

        /** Rotation by [radians], clockwise on screen because y points down. */
        fun rotate(radians: Double): Affine {
            val cs = cos(radians)
            val sn = sin(radians)
            return Affine(a = cs, b = sn, c = -sn, d = cs)
        }

        fun rotateAround(radians: Double, cx: Double, cy: Double) =
            translate(-cx, -cy).then(rotate(radians)).then(translate(cx, cy))
    }
}
