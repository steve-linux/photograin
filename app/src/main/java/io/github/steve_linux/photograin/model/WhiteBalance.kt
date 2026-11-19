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
        return neutralizeLinear(lr, lg, lb)
    }

    /** Estimates an illuminant from a sparse grid of opaque, unclipped midtones. */
    fun estimate(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Pair<Float, Float>? {
        val columns = width.coerceAtMost(48)
        val rows = height.coerceAtMost(48)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var weightSum = 0.0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val x = ((column + 0.5) * width / columns).toInt()
                val y = ((row + 0.5) * height / rows).toInt()
                val pixel = pixelAt(x, y)
                if (pixel ushr 24 < 250) continue
                val r = ((pixel ushr 16) and 255) / 255f
                val g = ((pixel ushr 8) and 255) / 255f
                val b = (pixel and 255) / 255f
                val brightest = maxOf(r, g, b)
                val saturation = (brightest - minOf(r, g, b)) / brightest.coerceAtLeast(0.01f)
                if (brightest !in 0.15f..0.97f || saturation > 0.45f) continue
                val weight = (1.0 - saturation) * (1.0 - saturation)
                red += weight * toLinear(r)
                green += weight * toLinear(g)
                blue += weight * toLinear(b)
                weightSum += weight
            }
        }
        if (weightSum < minOf(4, columns * rows) * 0.3 || weightSum <= 0.0) return null
        return neutralizeLinear(red / weightSum, green / weightSum, blue / weightSum)
    }

    private fun neutralizeLinear(lr: Double, lg: Double, lb: Double): Pair<Float, Float>? {
        if (lr + lg + lb < 0.01 || lr + lb < 1e-4) return null
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
