// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable

/** Brightness of the grays made from each source hue, from -1 to +1. */
@Serializable
data class MonochromeMix(
    val enabled: Boolean = false,
    val bands: List<Float> = Hsl.ZERO,
) {
    operator fun get(band: HslBand): Float = bands.getOrElse(band.ordinal) { 0f }.coerceIn(-1f, 1f)

    fun withBand(band: HslBand, value: Float) = copy(
        bands = HslBand.entries.map { if (it == band) value.coerceIn(-1f, 1f) else this[it] },
    )

    fun uniform() = FloatArray(HslBand.entries.size) { this[HslBand.entries[it]] }
}
