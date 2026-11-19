// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.Background
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParams
import io.github.steve_linux.photograin.model.MaskRange
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.Mat3
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

    private const val INVERT_SELECTION = 4
    private const val DISABLED_EFFECT = 8
    private const val MULTIPLE_COMPONENTS = 16

    /** Local adjustments packed for the color shader, including each mask's options and effects. */
    class MaskUniforms(
        val count: Int,
        val geo: FloatArray,
        val opt: FloatArray,
        val range: FloatArray,
        val locA: FloatArray,
        val locB: FloatArray,
        val locEffects: FloatArray,
        val locToneDetail: FloatArray,
        val locDetail: FloatArray,
    )

    fun maskUniforms(masks: List<LocalAdjustment>): MaskUniforms {
        val n = minOf(masks.size, LocalAdjustment.MAX)
        val geo = FloatArray(LocalAdjustment.MAX * 4)
        val opt = FloatArray(LocalAdjustment.MAX * 4)
        val range = FloatArray(LocalAdjustment.MAX * 4)
        val locA = FloatArray(LocalAdjustment.MAX * 4)
        val locB = FloatArray(LocalAdjustment.MAX * 4)
        val locEffects = FloatArray(LocalAdjustment.MAX * 4)
        val locToneDetail = FloatArray(LocalAdjustment.MAX * 4)
        val locDetail = FloatArray(LocalAdjustment.MAX * 4)
        for (i in 0 until n) {
            val l = masks[i]
            val m = l.mask
            val k = i * 4
            if (m.type == MaskType.DEPTH) {
                m.depth?.let { depth ->
                    geo[k] = depth.center.coerceIn(0f, 1f)
                    geo[k + 1] = depth.width.coerceIn(0f, 1f) * 0.5f
                    geo[k + 2] = depth.transition.coerceIn(0.004f, 1f)
                    geo[k + 3] = if (depth.map.raster != null) 1f else 0f
                }
            } else if (m.type == MaskType.LINEAR) {
                geo[k] = m.x0; geo[k + 1] = m.y0; geo[k + 2] = m.x1; geo[k + 3] = m.y1
            } else {
                geo[k] = m.x0; geo[k + 1] = m.y0; geo[k + 2] = m.radiusX; geo[k + 3] = m.radiusY
            }
            opt[k] = when (m.type) {
                MaskType.LINEAR -> 0f
                MaskType.RADIAL -> 1f
                // A subject mask is a brush mask seeded with the model's result.
                MaskType.BRUSH, MaskType.SUBJECT -> 2f
                MaskType.FULL -> 3f
                MaskType.DEPTH -> 4f
            }
            // A brush mask is drawn into the layer of the brush texture matching its index.
            opt[k + 1] = if (LocalAdjustment.usesRaster(m.type) || m.type == MaskType.DEPTH) i.toFloat() else m.angle
            opt[k + 2] = if (l.autoSky && m.type == MaskType.FULL) -1f else m.feather
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
            val amount = l.amount.coerceIn(LocalParams.amount.min, LocalParams.amount.max)
            locA[k] = l.exposure * amount
            locA[k + 1] = l.contrast * amount
            locA[k + 2] = l.highlights * amount
            locA[k + 3] = l.shadows * amount
            locB[k] = l.temperature * amount
            locB[k + 1] = l.tint * amount
            locB[k + 2] = l.saturation * amount
            locB[k + 3] = l.clarity * amount
            locEffects[k] = l.blur * amount
            locEffects[k + 1] = l.pixelate * amount
            val components = l.components.filter { it.index in 0 until n && it.index != i }.distinctBy { it.index }
            // Preserve the legacy layout for old recipes. New lists store up to seven operations
            // in two exactly representable float-backed words, using five bits per operation.
            val flags = l.combination.ordinal or
                (if (l.invertSelection) INVERT_SELECTION else 0) or
                (if (l.enabled) 0 else DISABLED_EFFECT)
            if (components.isEmpty()) {
                locEffects[k + 2] = (l.combineWith?.takeIf { it in 0 until n && it != i } ?: -1).toFloat()
                locEffects[k + 3] = flags.toFloat()
            } else {
                var first = 0
                var rest = flags or MULTIPLE_COMPONENTS
                components.forEachIndexed { position, component ->
                    val code = 1 + component.index + component.operation.ordinal * 8
                    if (position < 4) first = first or (code shl (5 * position))
                    else rest = rest or (code shl (5 * (position - 3)))
                }
                locEffects[k + 2] = first.toFloat()
                locEffects[k + 3] = rest.toFloat()
            }
            locToneDetail[k] = l.whites * amount
            locToneDetail[k + 1] = l.blacks * amount
            locToneDetail[k + 2] = l.textureAmount * amount
            locToneDetail[k + 3] = l.dehaze * amount
            locDetail[k] = l.hueShift / 360f * amount
            locDetail[k + 1] = l.sharpness * amount
            locDetail[k + 2] = l.noiseReduction * amount
            locDetail[k + 3] = l.defringe * amount
        }
        return MaskUniforms(n, geo, opt, range, locA, locB, locEffects, locToneDetail, locDetail)
    }

    /**
     * What replaces the photo outside the cutout mask [local], for the shader's uCutoutFill:
     * the background color in the working space [working], encoded with the sRGB curve like
     * the pixels it mixes with, and 1 in the fourth value; all 0 for a transparent background.
     */
    fun cutoutFill(local: LocalAdjustment, working: ColorProfile): FloatArray {
        if (local.background != Background.COLOR) return FloatArray(4)
        val c = local.backgroundColor
        val srgb = doubleArrayOf((c shr 16 and 0xFF) / 255.0, (c shr 8 and 0xFF) / 255.0, (c and 0xFF) / 255.0)
        val linear = Mat3.apply(ColorProfile.SRGB.conversionTo(working), DoubleArray(3) { ColorProfile.SRGB.decode(srgb[it]) })
        return floatArrayOf(
            ColorProfile.SRGB.encode(linear[0]).toFloat(),
            ColorProfile.SRGB.encode(linear[1]).toFloat(),
            ColorProfile.SRGB.encode(linear[2]).toFloat(),
            1f,
        )
    }
}
