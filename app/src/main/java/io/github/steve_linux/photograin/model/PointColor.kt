// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One color picked on the photo and how to change it ("Point Color"): every pixel close to
 * the sample in hue, saturation and brightness is moved by [hueShift], [saturationShift] and
 * [luminanceShift], fading out as it gets further away. [range] widens or narrows what counts
 * as close. The sample is in HSV of the source pixels (0..1), like the shader's rgbToHsv.
 */
@Serializable
data class PointColor(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val value: Float = 0f,
    val hueShift: Float = 0f,
    val saturationShift: Float = 0f,
    val luminanceShift: Float = 0f,
    val range: Float = 0.5f,
) {
    val isNeutral get() = hueShift == 0f && saturationShift == 0f && luminanceShift == 0f

    /** Same as the color shader's pointColorWeight: how much a source pixel ([r], [g], [b], 0..1) is changed. */
    fun weight(r: Float, g: Float, b: Float): Float {
        val (h, s, v) = hsv(r, g, b)
        val k = range.coerceIn(0f, 1f)
        var dh = abs(h - hue)
        dh = min(dh, 1f - dh)
        val hueTolerance = 0.025f + 0.11f * k
        val satTolerance = 0.12f + 0.45f * k
        val valueTolerance = 0.18f + 0.55f * k
        val wh = 1f - smoothstep(hueTolerance, hueTolerance * 1.8f + 0.01f, dh)
        val ws = 1f - smoothstep(satTolerance, satTolerance + 0.15f, abs(s - saturation))
        val wv = 1f - smoothstep(valueTolerance, valueTolerance + 0.15f, abs(v - value))
        // Grays have no hue to match.
        val colored = smoothstep(0.04f, 0.12f, s)
        return wh * ws * wv * colored
    }

    companion object {
        /** As many as the shader takes. */
        const val MAX = 8

        /** Hue shift at full slider, in degrees either way. */
        const val HUE_DEGREES = 40f

        /** A new point sampled from the color ([r], [g], [b], 0..1), with no change yet. */
        fun sample(r: Float, g: Float, b: Float): PointColor? {
            val (h, s, v) = hsv(r, g, b)
            // A gray has no hue to pick.
            if (s < 0.06f || v < 0.04f) return null
            return PointColor(hue = h, saturation = s, value = v)
        }

        /** HSV (0..1) of an RGB color, matching the shader's rgbToHsv. */
        fun hsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val c = mx - mn
            var h = 0f
            if (c > 0f) {
                h = when (mx) {
                    r -> ((g - b) / c).mod(6f)
                    g -> (b - r) / c + 2f
                    else -> (r - g) / c + 4f
                } / 6f
            }
            val s = if (mx > 0f) c / mx else 0f
            return Triple(h, s, mx)
        }

        /** The sample's color as sRGB-encoded 0xRRGGBB, for its swatch. */
        fun swatch(point: PointColor): Int {
            val h = point.hue * 6f
            val c = point.value * point.saturation
            val x = c * (1f - abs(h.mod(2f) - 1f))
            val m = point.value - c
            val (r, g, b) = when (h.toInt().coerceIn(0, 5)) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            fun byte(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            return (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
        }

        private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
