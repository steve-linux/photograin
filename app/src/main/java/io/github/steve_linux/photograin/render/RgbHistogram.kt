// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import java.nio.ByteBuffer

/** RGB bins of the processed preview, excluding transparent and mostly transparent pixels. */
class RgbHistogram private constructor(val red: IntArray, val green: IntArray, val blue: IntArray) {
    companion object {
        fun fromRgba(pixels: ByteBuffer): RgbHistogram {
            val red = IntArray(256)
            val green = IntArray(256)
            val blue = IntArray(256)
            for (i in pixels.position() until pixels.limit() - 3 step 4) {
                if ((pixels.get(i + 3).toInt() and 255) < 128) continue
                red[pixels.get(i).toInt() and 255]++
                green[pixels.get(i + 1).toInt() and 255]++
                blue[pixels.get(i + 2).toInt() and 255]++
            }
            return RgbHistogram(red, green, blue)
        }
    }
}
