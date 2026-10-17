// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.HslBand
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

/**
 * Converts user-facing [Adjustments] into the values the color shader consumes.
 * Kept free of Android APIs so it can be unit tested on the JVM.
 */
object ShaderParams {
    /** Radius of the local-contrast blur, as a fraction of the output long edge. */
    const val LOCAL_SIGMA_FRACTION = 0.015

    /** Taps on each side of the separable blur kernel (see the blur shader). */
    const val BLUR_TAPS = 12

    /** Target sigma, in texels of the downsampled texture, the blur kernel is tuned for. */
    const val BLUR_SIGMA_TEXELS = 4.0

    /** White balance as per-channel gains, normalized to keep luminance constant. */
    fun whiteBalanceGains(a: Adjustments): FloatArray {
        val r = 1.0 + 0.35 * a.temperature
        val b = 1.0 - 0.35 * a.temperature
        val g = 1.0 - 0.25 * a.tint
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return floatArrayOf((r / lum).toFloat(), (g / lum).toFloat(), (b / lum).toFloat())
    }

    /** Blur sigma in pixels for an output whose long edge is [longEdgePx] at the current resolution. */
    fun localSigmaPx(longEdgePx: Int) = max(1.0, LOCAL_SIGMA_FRACTION * longEdgePx)

    /** Power-of-two downsampling factor that brings [sigmaPx] near [BLUR_SIGMA_TEXELS]. */
    fun blurDownsample(sigmaPx: Double): Int {
        val ratio = sigmaPx / BLUR_SIGMA_TEXELS
        if (ratio <= 1.0) return 1
        return 2.0.pow(ceil(log2(ratio))).toInt()
    }

    /** Extra pixels needed around an export tile so blurs near its border are correct. */
    fun tileMarginPx(longEdgePx: Int): Int = ceil(3.5 * localSigmaPx(longEdgePx)).toInt() + 4

    /**
     * Grain cell size as a fraction of the output long edge. Expressing grain relative
     * to the image keeps the preview and the full-size export looking the same.
     */
    fun grainCellFraction(a: Adjustments): Float = (0.0004 + 0.0018 * a.grainSize).toFloat()

    /** HSL as 8 vec3 (hue shift, saturation, luminance) packed for glUniform3fv. */
    fun hslUniform(a: Adjustments): FloatArray {
        val out = FloatArray(HslBand.entries.size * 3)
        for (i in HslBand.entries.indices) {
            out[i * 3] = a.hsl.hue.getOrElse(i) { 0f }
            out[i * 3 + 1] = a.hsl.saturation.getOrElse(i) { 0f }
            out[i * 3 + 2] = a.hsl.luminance.getOrElse(i) { 0f }
        }
        return out
    }
}
