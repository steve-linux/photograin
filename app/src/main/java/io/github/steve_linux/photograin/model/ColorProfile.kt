// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.pow

/**
 * RGB color spaces Photograin works in and exports to. All share the D65 white point, so
 * converting between them is a 3x3 matrix on linear light, with no chromatic adaptation.
 *
 * @param primaries CIE xy of red, green and blue.
 * @param gamma exponent of a pure power transfer curve, or 0 for the piecewise sRGB curve.
 */
enum class ColorProfile(private val primaries: DoubleArray, val gamma: Double) {
    SRGB(doubleArrayOf(0.640, 0.330, 0.300, 0.600, 0.150, 0.060), 0.0),
    DISPLAY_P3(doubleArrayOf(0.680, 0.320, 0.265, 0.690, 0.150, 0.060), 0.0),
    ADOBE_RGB(doubleArrayOf(0.640, 0.330, 0.210, 0.710, 0.150, 0.060), 563.0 / 256.0);

    /** Linear RGB to CIE XYZ (row-major), so that RGB white maps to D65 with Y = 1. */
    val toXyz: DoubleArray by lazy {
        val m = DoubleArray(9)
        for (c in 0 until 3) {
            val x = primaries[2 * c]
            val y = primaries[2 * c + 1]
            m[c] = x / y
            m[3 + c] = 1.0
            m[6 + c] = (1 - x - y) / y
        }
        // Scale each primary so that R = G = B = 1 lands on the white point.
        val s = Mat3.apply(Mat3.inverse(m), D65)
        DoubleArray(9) { m[it] * s[it % 3] }
    }

    /** Matrix from linear values in this space to linear values in [target] (row-major). */
    fun conversionTo(target: ColorProfile): DoubleArray =
        if (target == this) Mat3.IDENTITY.copyOf() else Mat3.multiply(Mat3.inverse(target.toXyz), toXyz)

    /** Luminance weights of the red, green and blue channels. */
    val luma: DoubleArray get() = doubleArrayOf(toXyz[3], toXyz[4], toXyz[5])

    /** Encoded value (0..1) to linear light. */
    fun decode(v: Double): Double = when {
        gamma > 0 -> v.coerceAtLeast(0.0).pow(gamma)
        v <= 0.04045 -> v / 12.92
        else -> ((v + 0.055) / 1.055).pow(2.4)
    }

    /** Linear light to an encoded value (0..1). */
    fun encode(v: Double): Double {
        val c = v.coerceIn(0.0, 1.0)
        return when {
            gamma > 0 -> c.pow(1 / gamma)
            c <= 0.0031308 -> c * 12.92
            else -> 1.055 * c.pow(1 / 2.4) - 0.055
        }
    }

    companion object {
        /** D65 white, XYZ with Y = 1 (from xy 0.3127, 0.3290). */
        private val D65 = doubleArrayOf(0.3127 / 0.3290, 1.0, (1 - 0.3127 - 0.3290) / 0.3290)
    }
}

/** Minimal row-major 3x3 matrix helpers for color conversions. */
object Mat3 {
    val IDENTITY = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
        val r = i / 3
        val c = i % 3
        a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
    }

    fun apply(m: DoubleArray, v: DoubleArray) = DoubleArray(3) { r ->
        m[r * 3] * v[0] + m[r * 3 + 1] * v[1] + m[r * 3 + 2] * v[2]
    }

    fun inverse(m: DoubleArray): DoubleArray {
        val (a, b, c) = Triple(m[0], m[1], m[2])
        val (d, e, f) = Triple(m[3], m[4], m[5])
        val (g, h, i) = Triple(m[6], m[7], m[8])
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(det != 0.0) { "Singular matrix" }
        return doubleArrayOf(
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
        )
    }

    /** Column-major float copy, as glUniformMatrix3fv expects without transposing. */
    fun toGl(m: DoubleArray) = FloatArray(9) { i -> m[(i % 3) * 3 + i / 3].toFloat() }
}
