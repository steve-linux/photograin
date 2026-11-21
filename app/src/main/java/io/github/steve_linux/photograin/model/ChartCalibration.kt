// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/** Fits a linear RGB correction to photographed chart patches and their known sRGB values. */
internal object ChartCalibration {
    data class Patch(val measured: DoubleArray, val reference: Int)

    fun fit(patches: List<Patch>): DoubleArray? {
        if (patches.size < 3) return null
        val gram = DoubleArray(9)
        val targets = DoubleArray(9)
        for ((measured, reference) in patches) {
            val expected = DoubleArray(3) { channel ->
                ColorProfile.SRGB.decode(((reference shr (16 - 8 * channel)) and 255) / 255.0)
            }
            for (row in 0..2) for (col in 0..2) {
                gram[row * 3 + col] += measured[row] * measured[col]
                targets[row * 3 + col] += expected[row] * measured[col]
            }
        }
        val inverse = runCatching { Mat3.inverse(gram) }.getOrNull() ?: return null
        // Reject near-collinear patches: a set of only neutrals cannot calibrate colors.
        if (gram.maxOf { kotlin.math.abs(it) } * inverse.maxOf { kotlin.math.abs(it) } > 2_000) return null
        return Mat3.multiply(targets, inverse).takeIf { matrix -> matrix.all(Double::isFinite) }
    }

    fun cube(name: String, matrix: DoubleArray): ByteArray {
        val size = 17
        val profile = ColorProfile.SRGB
        val samples = DoubleArray(size) { profile.decode(it / (size - 1.0)) }
        val title = name.replace('"', '\'').replace(Regex("[\\r\\n]"), " ")
        return buildString {
            appendLine("TITLE \"$title\"")
            appendLine("LUT_3D_SIZE $size")
            for (b in samples) for (g in samples) for (r in samples) {
                val rgb = Mat3.apply(matrix, doubleArrayOf(r, g, b))
                appendLine(rgb.joinToString(" ") { profile.encode(it).toString() })
            }
        }.encodeToByteArray()
    }
}
