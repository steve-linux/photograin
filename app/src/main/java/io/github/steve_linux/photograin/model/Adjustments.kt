// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable

/**
 * Global tonal and color adjustments. Exposure is in EV; every other value is
 * normalized to -1..1 (bipolar) or 0..1 (unipolar), with 0 meaning "no change".
 */
@Serializable
data class Adjustments(
    // Light
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    // Color
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    // Detail
    val sharpness: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val noiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    // Effects
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val grainSize: Float = 0.35f,
    // Advanced tone and color
    val curves: Curves = Curves(),
    val hsl: Hsl = Hsl(),
    // Creative look
    val look: Look = Look(),
)

/** An imported .cube LUT applied after the other color adjustments, blended by [amount]. */
@Serializable
data class Look(
    /** Id of the LUT in the app's LUT library, or null for none. */
    val lutId: String? = null,
    /** Name shown in the panel, kept so a recipe stays readable if the LUT is deleted. */
    val name: String? = null,
    val amount: Float = 1f,
) {
    val isActive get() = lutId != null && amount > 0f
}

enum class ToolGroup { LOOK, LIGHT, COLOR, DETAIL, EFFECTS, CROP }

/** Describes one slider: its range, default and how it reads/writes [Adjustments]. */
class ParamSpec(
    val id: String,
    val group: ToolGroup,
    val min: Float,
    val max: Float,
    val get: (Adjustments) -> Float,
    val set: (Adjustments, Float) -> Adjustments,
    /** Multiplier for the number shown next to the slider. */
    val displayScale: Float = 100f,
    val decimals: Int = 0,
) {
    val default: Float get() = get(Adjustments())
}

object Params {
    val all: List<ParamSpec> = listOf(
        ParamSpec("exposure", ToolGroup.LIGHT, -4f, 4f, { it.exposure }, { a, v -> a.copy(exposure = v) }, displayScale = 1f, decimals = 2),
        ParamSpec("contrast", ToolGroup.LIGHT, -1f, 1f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
        ParamSpec("highlights", ToolGroup.LIGHT, -1f, 1f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
        ParamSpec("shadows", ToolGroup.LIGHT, -1f, 1f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
        ParamSpec("whites", ToolGroup.LIGHT, -1f, 1f, { it.whites }, { a, v -> a.copy(whites = v) }),
        ParamSpec("blacks", ToolGroup.LIGHT, -1f, 1f, { it.blacks }, { a, v -> a.copy(blacks = v) }),
        ParamSpec("temperature", ToolGroup.COLOR, -1f, 1f, { it.temperature }, { a, v -> a.copy(temperature = v) }),
        ParamSpec("tint", ToolGroup.COLOR, -1f, 1f, { it.tint }, { a, v -> a.copy(tint = v) }),
        ParamSpec("vibrance", ToolGroup.COLOR, -1f, 1f, { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
        ParamSpec("saturation", ToolGroup.COLOR, -1f, 1f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
        ParamSpec("sharpness", ToolGroup.DETAIL, 0f, 1f, { it.sharpness }, { a, v -> a.copy(sharpness = v) }),
        ParamSpec("clarity", ToolGroup.DETAIL, -1f, 1f, { it.clarity }, { a, v -> a.copy(clarity = v) }),
        ParamSpec("dehaze", ToolGroup.DETAIL, -1f, 1f, { it.dehaze }, { a, v -> a.copy(dehaze = v) }),
        ParamSpec("noiseReduction", ToolGroup.DETAIL, 0f, 1f, { it.noiseReduction }, { a, v -> a.copy(noiseReduction = v) }),
        ParamSpec("colorNoiseReduction", ToolGroup.DETAIL, 0f, 1f, { it.colorNoiseReduction }, { a, v -> a.copy(colorNoiseReduction = v) }),
        ParamSpec("vignette", ToolGroup.EFFECTS, -1f, 1f, { it.vignette }, { a, v -> a.copy(vignette = v) }),
        ParamSpec("grain", ToolGroup.EFFECTS, 0f, 1f, { it.grain }, { a, v -> a.copy(grain = v) }),
        ParamSpec("grainSize", ToolGroup.EFFECTS, 0f, 1f, { it.grainSize }, { a, v -> a.copy(grainSize = v) }),
    )

    fun forGroup(group: ToolGroup) = all.filter { it.group == group }
}
