// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.roundToInt

/** Interpolates a preset from the photo's current adjustments to its full-strength result. */
internal class PresetBlend(private val start: Adjustments, private val target: Adjustments) {
    val canBlend: Boolean =
        start.filmBase == target.filmBase &&
        start.colorReference == target.colorReference &&
        start.colorKey == target.colorKey &&
        (!start.look.isActive || !target.look.isActive || start.look.lutId == target.look.lutId)

    private val changed = Params.all.filter { it.get(start) != it.get(target) }
    private val sampledCurves by lazy {
        CurveChannel.entries.filter { start.curves[it] != target.curves[it] }.associateWith {
            start.curves[it].sample(CURVE_SAMPLES) to target.curves[it].sample(CURVE_SAMPLES)
        }
    }

    fun at(amount: Float): Adjustments {
        val t = amount.coerceIn(0f, 1f)
        if (t == 0f) return start
        if (t == 1f || !canBlend) return target

        var result = start
        for (slider in changed) {
            result = slider.set(result, mix(slider.get(start), slider.get(target), t))
        }
        var curves = start.curves
        for ((channel, samples) in sampledCurves) {
            val (left, right) = samples
            val points = left.indices.map { i ->
                CurvePoint(i.toFloat() / (CURVE_SAMPLES - 1), mix(left[i], right[i], t))
            }
            curves = curves.with(channel, ToneCurve(points))
        }
        val hsl = if (start.hsl == target.hsl) start.hsl else Hsl(
            hue = mixBands(start.hsl.hue, target.hsl.hue, t),
            saturation = mixBands(start.hsl.saturation, target.hsl.saturation, t),
            luminance = mixBands(start.hsl.luminance, target.hsl.luminance, t),
        )
        return result.copy(
            curves = curves,
            hsl = hsl,
            grading = mixGrading(start.grading, target.grading, t),
            gradientMap = mixGradient(start.gradientMap, target.gradientMap, t),
            look = mixLook(start.look, target.look, t),
            filmBlack = mix(start.filmBlack, target.filmBlack, t),
            filmExposure = mix(start.filmExposure, target.filmExposure, t),
            colorKeyTolerance = mix(start.colorKeyTolerance, target.colorKeyTolerance, t),
        )
    }

    private fun mixBands(a: List<Float>, b: List<Float>, t: Float) =
        List(HslBand.entries.size) { i -> mix(a.getOrElse(i) { 0f }, b.getOrElse(i) { 0f }, t) }

    private fun mixGrading(a: ColorGrading, b: ColorGrading, t: Float): ColorGrading {
        if (a == b) return a
        return ColorGrading(
            shadows = mixTone(a.shadows, b.shadows, t),
            midtones = mixTone(a.midtones, b.midtones, t),
            highlights = mixTone(a.highlights, b.highlights, t),
            global = mixTone(a.global, b.global, t),
            balance = mix(a.balance, b.balance, t),
            blending = mix(a.blending, b.blending, t),
        )
    }

    private fun mixTone(a: GradingTone, b: GradingTone, t: Float): GradingTone {
        if (a == b) return a
        val hue = when {
            a.saturation == 0f -> b.hue
            b.saturation == 0f -> a.hue
            else -> (a.hue + ((b.hue - a.hue + 180f).mod(360f) - 180f) * t).mod(360f)
        }
        return GradingTone(hue, mix(a.saturation, b.saturation, t), mix(a.luminance, b.luminance, t))
    }

    private fun mixGradient(a: GradientMap, b: GradientMap, t: Float): GradientMap {
        if (a == b) return a
        val amount = mix(a.amount, b.amount, t)
        if (a.amount == 0f) return b.copy(amount = amount)
        if (b.amount == 0f) return a.copy(amount = amount)
        return GradientMap(
            amount = amount,
            shadow = mixColor(a.shadow, b.shadow, t),
            middle = mixColor(a.middle, b.middle, t),
            highlight = mixColor(a.highlight, b.highlight, t),
            midpoint = mix(a.midpoint, b.midpoint, t),
        )
    }

    private fun mixColor(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int) = mix(
            ((a ushr shift) and 255).toFloat(),
            ((b ushr shift) and 255).toFloat(),
            t,
        ).roundToInt() shl shift
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    private fun mixLook(a: Look, b: Look, t: Float): Look {
        if (a == b) return a
        val look = if (b.isActive) b else a
        val amount = mix(if (a.isActive) a.amount else 0f, if (b.isActive) b.amount else 0f, t)
        return look.copy(amount = amount)
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private companion object {
        // A 0.025 gap leaves each point editable in the existing curve panel.
        const val CURVE_SAMPLES = 41
    }
}
