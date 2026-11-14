// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable

enum class TonalRange { SHADOWS, MIDTONES, HIGHLIGHTS }

@Serializable
data class GradeTone(
    val hue: Float = 0f,
    val intensity: Float = 0f,
    val luminance: Float = 0f,
) {
    val isNeutral get() = intensity == 0f && luminance == 0f
}

@Serializable
data class ColorGrade(
    val shadows: GradeTone = GradeTone(),
    val midtones: GradeTone = GradeTone(),
    val highlights: GradeTone = GradeTone(),
    val balance: Float = 0f,
) {
    val isNeutral get() = shadows.isNeutral && midtones.isNeutral && highlights.isNeutral

    operator fun get(range: TonalRange) = when (range) {
        TonalRange.SHADOWS -> shadows
        TonalRange.MIDTONES -> midtones
        TonalRange.HIGHLIGHTS -> highlights
    }

    fun withTone(range: TonalRange, tone: GradeTone) = when (range) {
        TonalRange.SHADOWS -> copy(shadows = tone)
        TonalRange.MIDTONES -> copy(midtones = tone)
        TonalRange.HIGHLIGHTS -> copy(highlights = tone)
    }
}
