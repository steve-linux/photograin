// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable

@Serializable
enum class PresetPart { LIGHT, COLOR, DETAIL, EFFECTS, CURVES, MIX, LUT }

/** Individual settings carried by a preset imported from a partial recipe. */
@Serializable
data class PresetFields(
    val sliders: Set<String> = emptySet(),
    val colorMix: Set<String> = emptySet(),
    val curveChannels: Set<CurveChannel> = emptySet(),
) {
    val isEmpty: Boolean get() = sliders.isEmpty() && colorMix.isEmpty() && curveChannels.isEmpty()

    internal fun apply(current: Adjustments, source: Adjustments): Adjustments {
        var result = current
        for (spec in Params.all) {
            if (spec.id in sliders) result = spec.set(result, spec.get(source))
        }
        var hsl = result.hsl
        for (control in HslControl.entries) for (band in HslBand.entries) {
            if (colorMixKey(control, band) in colorMix) {
                hsl = control.update(hsl, band, control.value(source.hsl, band))
            }
        }
        var curves = result.curves
        for (channel in curveChannels) curves = curves.with(channel, source.curves[channel])
        return result.copy(hsl = hsl, curves = curves)
    }

    companion object {
        internal fun colorMixKey(control: HslControl, band: HslBand) = "${control.name}.${band.name}"
    }
}

/**
 * A saved set of adjustments (geometry excluded), applied in one tap.
 * Built-in presets have a [builtIn] key for their translated name; user presets have a [name].
 */
@Serializable
data class Preset(
    val id: String,
    val name: String = "",
    val adjustments: Adjustments,
    val builtIn: String? = null,
    val parts: Set<PresetPart>? = null,
    val fields: PresetFields? = null,
) {
    /** A legacy preset applies all adjustments. Selective presets retain the destination values for excluded parts. */
    fun applyTo(current: Adjustments): Adjustments {
        fields?.let { return it.apply(current, adjustments) }
        val selected = parts ?: return adjustments.copy(
            sharpness = current.sharpness,
            noiseReduction = current.noiseReduction,
            colorNoiseReduction = current.colorNoiseReduction,
        )
        return applyParts(current, selected)
    }

    private fun applyParts(current: Adjustments, parts: Set<PresetPart>): Adjustments {
        var result = current
        for (spec in Params.all) {
            val part = when (spec.group) {
                ToolGroup.LIGHT -> PresetPart.LIGHT
                ToolGroup.COLOR -> PresetPart.COLOR
                ToolGroup.DETAIL -> PresetPart.DETAIL
                ToolGroup.EFFECTS -> PresetPart.EFFECTS
                else -> continue
            }
            if (part in parts) result = spec.set(result, spec.get(adjustments))
        }
        if (PresetPart.COLOR in parts) {
            result = result.copy(
                grading = adjustments.grading,
                colorReference = adjustments.colorReference,
                colorKey = adjustments.colorKey,
                colorKeyTolerance = adjustments.colorKeyTolerance,
            )
        }
        if (PresetPart.EFFECTS in parts) {
            result = result.copy(
                gradientMap = adjustments.gradientMap,
                filmBase = adjustments.filmBase,
                filmExposure = adjustments.filmExposure,
                filmBlack = adjustments.filmBlack,
            )
        }
        return result.copy(
            curves = if (PresetPart.CURVES in parts) adjustments.curves else result.curves,
            hsl = if (PresetPart.MIX in parts) adjustments.hsl else result.hsl,
            look = if (PresetPart.LUT in parts) adjustments.look else result.look,
        )
    }

    companion object {
        fun capture(name: String, source: Adjustments, parts: Set<PresetPart>): Preset {
            val preset = Preset(id = "", name = name, adjustments = source, parts = parts.toSet())
            return preset.copy(adjustments = preset.applyTo(Adjustments()))
        }

        val BUILT_IN: List<Preset> = listOf(
            builtIn("natural", Adjustments()),
            builtIn("vivid", Adjustments(contrast = 0.15f, vibrance = 0.4f, clarity = 0.1f)),
            builtIn("warm", Adjustments(temperature = 0.3f, tint = 0.05f, vibrance = 0.15f)),
            builtIn("cool", Adjustments(temperature = -0.3f, tint = -0.05f, vibrance = 0.1f)),
            builtIn("soft", Adjustments(contrast = -0.2f, highlights = -0.2f, clarity = -0.3f)),
            builtIn("film", Adjustments(contrast = -0.1f, blacks = 0.3f, saturation = -0.15f, vignette = -0.2f, grain = 0.3f)),
            builtIn("bw", Adjustments(saturation = -1f, contrast = 0.2f)),
            builtIn("bw_contrast", Adjustments(saturation = -1f, contrast = 0.5f, clarity = 0.3f, grain = 0.2f)),
        )

        private fun builtIn(key: String, adjustments: Adjustments) =
            Preset(id = "builtin:$key", adjustments = adjustments, builtIn = key)
    }
}
