// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

internal object PhotoAnalysis {
    data class Signature(val horizontal: Long, val vertical: Long, val samples: IntArray, val aspect: Float)

    /** 9×9 luminance samples of the whole oriented picture. */
    fun signature(samples: IntArray, aspect: Float): Signature {
        require(samples.size == 81 && aspect > 0f)
        var horizontal = 0L
        var vertical = 0L
        for (y in 0..7) {
            for (x in 0..7) {
                val bit = 1L shl (8 * y + x)
                val at = 9 * y + x
                if (samples[at] > samples[at + 1]) horizontal = horizontal or bit
                if (samples[at] > samples[at + 9]) vertical = vertical or bit
            }
        }
        return Signature(horizontal, vertical, samples, aspect)
    }

    fun similar(a: Signature, b: Signature): Boolean {
        val shapeRatio = a.aspect / b.aspect
        if (shapeRatio !in 0.94f..1.06f) return false
        val distance = (a.horizontal xor b.horizontal).countOneBits() + (a.vertical xor b.vertical).countOneBits()
        if (distance > 14) return false
        if (spread(a.samples) < 6 || spread(b.samples) < 6) return false
        return a.samples.indices.sumOf { abs(a.samples[it] - b.samples[it]) } <= 11 * a.samples.size
    }

    private fun spread(samples: IntArray): Double {
        val average = samples.average()
        return sqrt(samples.sumOf { (it - average) * (it - average) } / samples.size)
    }

    fun exposureDelta(reference: Double, sample: Double): Float? {
        if (!reference.isFinite() || !sample.isFinite() || reference < 0.002 || sample < 0.002) return null
        return log2(reference / sample).toFloat().coerceIn(-2f, 2f)
    }
}
