// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.log2
import kotlin.math.roundToInt

internal class ToneHistogram(withWaveform: Boolean = false, withVectorscope: Boolean = false) {
    val waveform = if (withWaveform) Array(3) { IntArray(SCOPE_SIZE * SCOPE_SIZE) } else null
    val vectorscope = if (withVectorscope) IntArray(SCOPE_SIZE * SCOPE_SIZE) else null

    fun addWaveform(column: Int, red: Int, green: Int, blue: Int) {
        waveform?.let {
            it[0][(red / 2) * SCOPE_SIZE + column]++
            it[1][(green / 2) * SCOPE_SIZE + column]++
            it[2][(blue / 2) * SCOPE_SIZE + column]++
        }
    }
    val channels = Array(4) { IntArray(256) }
    private var count = 0

    fun add(red: Int, green: Int, blue: Int) {
        channels[0][red]++
        channels[1][green]++
        channels[2][blue]++
        channels[3][(54 * red + 183 * green + 19 * blue + 128) shr 8]++
        vectorscope?.let { it[chromaIndex(red, green, blue)]++ }
        count++
    }

    private fun percentile(fraction: Double): Double {
        val rank = (fraction * (count - 1)).toInt()
        var seen = 0
        return channels[3].indexOfFirst {
            seen += it
            seen > rank
        }.coerceAtLeast(0) / 255.0
    }

    fun autoLight(current: Adjustments): Adjustments {
        if (count == 0) return current
        val low = percentile(0.05)
        val middle = percentile(0.5)
        val high = percentile(0.95)
        if (high - low < 0.04) return current
        val exposure = (2.0 * log2(0.46 / middle.coerceAtLeast(0.10))).coerceIn(-0.9, 0.9)
        return current.copy(
            exposure = exposure.toFloat(),
            contrast = ((0.72 - (high - low)) * 0.55).coerceIn(-0.2, 0.3).toFloat(),
            shadows = ((0.15 - low) * 1.4).coerceIn(0.0, 0.22).toFloat(),
            highlights = ((0.88 - high) * 1.35).coerceIn(-0.22, 0.2).toFloat(),
        )
    }

    companion object {
        const val SCOPE_SIZE = 128

        /** Cb/Cr from the display-encoded sRGB sample, on a centered full-range grid. */
        fun chromaIndex(red: Int, green: Int, blue: Int): Int {
            val luma = (2126 * red + 7152 * green + 722 * blue) / 10000.0
            fun bin(chroma: Double): Int =
                ((chroma + 0.5) * (SCOPE_SIZE - 1)).roundToInt().coerceIn(0, SCOPE_SIZE - 1)
            val cb = bin((blue - luma) / (255 * 1.8556))
            val cr = bin((red - luma) / (255 * 1.5748))
            return cr * SCOPE_SIZE + cb
        }
    }
}
