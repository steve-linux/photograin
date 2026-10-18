// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.pow

/** The white balance eyedropper: temperature and tint that turn a picked color gray. */
object WhiteBalance {
    /**
     * Temperature and tint (each -1..1) that make the encoded sRGB color [r], [g], [b] (0..1)
     * neutral under the gains of ShaderParams.whiteBalanceGains, or null for a color too dark
     * to judge. Out-of-range answers are clamped, so a strongly colored pick only goes part way.
     */
    fun neutralize(r: Float, g: Float, b: Float): Pair<Float, Float>? {
        val lr = toLinear(r)
        val lg = toLinear(g)
        val lb = toLinear(b)
        if (lr + lg + lb < 0.01) return null
        // Red and blue gains are 1 ± 0.35 t: equal red and blue.
        val t = ((lb - lr) / (0.35 * (lr + lb))).coerceIn(-1.0, 1.0)
        // Green gain is 1 - 0.25 n: green matches red after its gain.
        val n = ((1 - (1 + 0.35 * t) * lr / lg.coerceAtLeast(1e-4)) / 0.25).coerceIn(-1.0, 1.0)
        return t.toFloat() to n.toFloat()
    }

    private fun toLinear(v: Float): Double {
        val c = v.toDouble().coerceIn(0.0, 1.0)
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
