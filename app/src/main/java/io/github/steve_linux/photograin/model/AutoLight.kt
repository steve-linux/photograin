// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Homography
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

internal object AutoLight {
    private const val GRID = 96
    private val linear = DoubleArray(256) { toLinear(it / 255.0) }

    data class Settings(val exposure: Float, val contrast: Float, val shadows: Float, val highlights: Float) {
        fun applyTo(adjustments: Adjustments) = adjustments.copy(
            exposure = exposure, contrast = contrast, shadows = shadows, highlights = highlights,
        )
    }

    /** Samples the original photo within the current crop, before tonal adjustments. */
    fun estimate(
        width: Int,
        height: Int,
        pixels: IntArray,
        outputToSource: Homography,
        lens: LensMapping? = null,
    ): Settings? {
        val luminance = IntArray(256)
        val peaks = IntArray(256)
        var count = 0
        for (y in 0 until GRID) for (x in 0 until GRID) {
            val position = outputToSource.apply((x + 0.5) / GRID, (y + 0.5) / GRID)
            val source = lens?.toOriginal(position) ?: position
            if (!(source.x >= 0 && source.x < 1 && source.y >= 0 && source.y < 1)) continue
            val pixel = pixels[(source.y * height).toInt() * width + (source.x * width).toInt()]
            if ((pixel ushr 24) < 128) continue
            val r = pixel shr 16 and 255
            val g = pixel shr 8 and 255
            val b = pixel and 255
            val luma = 0.2126 * linear[r] + 0.7152 * linear[g] + 0.0722 * linear[b]
            luminance[(255 * toSrgb(luma)).roundToInt()]++
            peaks[maxOf(r, g, b)]++
            count++
        }
        if (count < GRID) return null

        val dark = percentile(luminance, count, 0.10)
        val mid = percentile(luminance, count, 0.50)
        val bright = percentile(luminance, count, 0.95)
        val range = bright - dark
        if (range < 0.035) return null

        // Move the midtones partway toward the target. Use a bright RGB channel, not just
        // luminance, to limit exposure increases on scenes with saturated highlights.
        val desired = (0.7 * stops(mid, 0.46)).coerceIn(-0.85, 1.1)
        val headroom = stops(percentile(peaks, count, 0.98), 0.985).coerceAtLeast(0.0)
        val exposure = desired.coerceAtMost(headroom)
        val contrast = ((0.68 - range) * 0.65).coerceIn(-0.18, 0.30)
        val shadows = (1.3 * (0.19 - dark) + 0.22 * (desired - exposure)).coerceIn(0.0, 0.42)
        val highlights = (-1.8 * (bright - 0.86)).coerceIn(-0.30, 0.0)
        return Settings(snap(exposure), snap(contrast), snap(shadows), snap(highlights))
    }

    private fun percentile(bins: IntArray, count: Int, fraction: Double): Double {
        val rank = ((count - 1) * fraction).roundToInt()
        var total = 0
        for (i in bins.indices) {
            total += bins[i]
            if (total > rank) return i / 255.0
        }
        return 1.0
    }

    private fun stops(from: Double, to: Double): Double =
        log2(toLinear(to) / toLinear(from.coerceAtLeast(0.01)))

    private fun toLinear(value: Double): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun toSrgb(value: Double): Double =
        if (value <= 0.0031308) 12.92 * value else 1.055 * value.pow(1.0 / 2.4) - 0.055

    private fun snap(value: Double): Float =
        if (abs(value) < 0.025) 0f else (value * 100).roundToInt() / 100f
}
