// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.pow

/** Extra highlight gain encoded in an Ultra HDR JPEG over its SDR base. */
internal object HighlightGain {
    const val DEFAULT_STOPS = 1f
    const val DEFAULT_THRESHOLD = 0.52f
    const val MIN_STOPS = 0.25f
    const val MAX_STOPS = 3f
    const val MAX_THRESHOLD = 0.95f

    fun ratio(stops: Float): Float = 2f.pow(
        if (stops.isFinite()) stops.coerceIn(MIN_STOPS, MAX_STOPS) else DEFAULT_STOPS,
    )

    fun encode(pixel: Int, threshold: Float): Int {
        val start = if (threshold.isFinite()) threshold.coerceIn(0f, MAX_THRESHOLD) else DEFAULT_THRESHOLD
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        val light = (54 * r + 183 * g + 19 * b) / 65280f
        val t = ((light - start) / (1f - start)).coerceIn(0f, 1f)
        return (t * t * 255).toInt()
    }
}
