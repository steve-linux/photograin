// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.abs
import kotlin.math.roundToInt

internal object AutoWhiteBalance {
    // Bitmap.getPixels returns sRGB values even when the preview uses a wider working space.
    private val linear = DoubleArray(256) { ColorProfile.SRGB.decode(it / 255.0) }

    fun estimate(samples: CropSamples): Pair<Float, Float>? {
        val toWorking = ColorProfile.SRGB.conversionTo(samples.profile)
        val candidates = ArrayList<Pair<Float, Float>>()
        var visible = 0
        samples.forEachPixel { r, g, b ->
            visible++
            val min = minOf(r, g, b)
            val max = maxOf(r, g, b)
            // Near-neutral midtones are informative. Deep shadows are noisy and bright clipped
            // channels cannot tell us how much stronger they really were.
            if (min >= 40 && max <= 242 && max - min <= 0.22 * max) {
                val rgb = Mat3.apply(toWorking, doubleArrayOf(linear[r], linear[g], linear[b]))
                WhiteBalance.neutralizeLinear(rgb[0], rgb[1], rgb[2])?.let { candidates.add(it) }
            }
        }
        if (candidates.size < maxOf(96, visible / 8)) return null
        val temperature = candidates.map { it.first }.sorted()[candidates.size / 2]
        val tint = candidates.map { it.second }.sorted()[candidates.size / 2]
        val agreeing = candidates.count { abs(it.first - temperature) <= 0.20f && abs(it.second - tint) <= 0.25f }
        if (agreeing < candidates.size * 0.60f) return null

        // Keep some of the original light rather than fully neutralizing an uncertain reference.
        fun correction(value: Float) = (value * 0.8f).coerceIn(-0.60f, 0.60f).let { (it * 100).roundToInt() / 100f }
        return correction(temperature) to correction(tint)
    }
}
