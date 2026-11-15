// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Homography

/** Sample the unedited preview on an even grid across the current crop. */
internal class CropSamples(
    private val width: Int,
    private val height: Int,
    private val pixels: IntArray,
    private val outputToSource: Homography,
    private val lens: LensMapping? = null,
    val profile: ColorProfile = ColorProfile.SRGB,
) {
    inline fun forEachPixel(sample: (r: Int, g: Int, b: Int) -> Unit) {
        val grid = 96
        for (y in 0 until grid) for (x in 0 until grid) {
            val position = outputToSource.apply((x + 0.5) / grid, (y + 0.5) / grid)
            val source = lens?.toOriginal(position) ?: position
            if (!(source.x >= 0 && source.x < 1 && source.y >= 0 && source.y < 1)) continue
            val pixel = pixels[(source.y * height).toInt() * width + (source.x * width).toInt()]
            if ((pixel ushr 24) < 128) continue
            sample(pixel shr 16 and 255, pixel shr 8 and 255, pixel and 255)
        }
    }
}
