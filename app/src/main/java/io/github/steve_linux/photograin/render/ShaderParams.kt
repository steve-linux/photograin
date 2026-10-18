// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.MaskRange
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.RangeType
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

    /** Local adjustments packed as four vec4 arrays; see the mask uniforms in the color shader. */
    class MaskUniforms(
        val count: Int,
        val geo: FloatArray,
        val opt: FloatArray,
        val range: FloatArray,
        val locA: FloatArray,
        val locB: FloatArray,
    )

    fun maskUniforms(masks: List<LocalAdjustment>): MaskUniforms {
        val n = minOf(masks.size, LocalAdjustment.MAX)
        val geo = FloatArray(LocalAdjustment.MAX * 4)
        val opt = FloatArray(LocalAdjustment.MAX * 4)
        val range = FloatArray(LocalAdjustment.MAX * 4)
        val locA = FloatArray(LocalAdjustment.MAX * 4)
        val locB = FloatArray(LocalAdjustment.MAX * 4)
        for (i in 0 until n) {
            val l = masks[i]
            val m = l.mask
            val k = i * 4
            if (m.type == MaskType.LINEAR) {
                geo[k] = m.x0; geo[k + 1] = m.y0; geo[k + 2] = m.x1; geo[k + 3] = m.y1
            } else {
                geo[k] = m.x0; geo[k + 1] = m.y0; geo[k + 2] = m.radiusX; geo[k + 3] = m.radiusY
            }
            opt[k] = when (m.type) {
                MaskType.LINEAR -> 0f
                MaskType.RADIAL -> 1f
                MaskType.BRUSH -> 2f
                MaskType.FULL -> 3f
            }
            // A brush mask is drawn into the layer of the brush texture matching its index.
            opt[k + 1] = if (m.type == MaskType.BRUSH) i.toFloat() else m.angle
            opt[k + 2] = m.feather
            opt[k + 3] = if (m.invert) 1f else 0f
            val r = m.range
            when (r.type) {
                RangeType.NONE -> Unit
                RangeType.LUMINANCE -> {
                    range[k] = 1f; range[k + 1] = r.low; range[k + 2] = r.high; range[k + 3] = r.smooth
                }
                RangeType.COLOR -> {
                    range[k] = 2f; range[k + 1] = r.hue; range[k + 2] = MaskRange.hueTolerance(r.tolerance)
                    range[k + 3] = r.saturation
                }
            }
            locA[k] = l.exposure; locA[k + 1] = l.contrast; locA[k + 2] = l.highlights; locA[k + 3] = l.shadows
            locB[k] = l.temperature; locB[k + 1] = l.tint; locB[k + 2] = l.saturation; locB[k + 3] = l.clarity
        }
        return MaskUniforms(n, geo, opt, range, locA, locB)
    }
}
