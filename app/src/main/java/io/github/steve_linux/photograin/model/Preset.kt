// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable

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
) {
    /** The preset's adjustments, keeping [current]'s noise reduction and sharpening, which depend on the photo. */
    fun applyTo(current: Adjustments): Adjustments = adjustments.copy(
        sharpness = current.sharpness,
        noiseReduction = current.noiseReduction,
        colorNoiseReduction = current.colorNoiseReduction,
    )

    companion object {
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
