// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import io.github.steve_linux.photograin.model.Quad
import kotlin.math.abs
import kotlin.math.roundToInt

/** Votes for near-vertical lines in each half of the oriented preview. */
internal fun detectVerticalGuides(luma: FloatArray, width: Int, height: Int): Quad? {
    if (width < 24 || height < 24) return null
    val slopes = (-16..16).map { it * 0.025 }
    val votes = Array(slopes.size) { FloatArray(width) }
    val center = height * 0.5
    for (y in 2 until height - 2) for (x in 2 until width - 2) {
        val i = y * width + x
        val gx = abs(luma[i + 1] - luma[i - 1])
        val gy = abs(luma[i + width] - luma[i - width])
        if (gx < 0.06f || gx < gy * 1.4f) continue
        for ((j, slope) in slopes.withIndex()) {
            val atCenter = (x - slope * (y - center)).roundToInt()
            if (atCenter in 0 until width) votes[j][atCenter] += gx
        }
    }
    fun line(range: IntRange): Pair<Int, Double>? {
        var strength = height * 0.035f
        var winner: Pair<Int, Double>? = null
        for ((j, slope) in slopes.withIndex()) for (x in range) {
            if (votes[j][x] > strength) {
                strength = votes[j][x]
                winner = x to slope
            }
        }
        return winner
    }
    val left = line(width / 10..width * 4 / 10) ?: return null
    val right = line(width * 6 / 10..width * 9 / 10) ?: return null
    val top = 0.12f
    val bottom = 0.88f
    fun x(line: Pair<Int, Double>, y: Float) =
        ((line.first + line.second * (height * y - center)) / width).toFloat()
    val quad = Quad(x(left, top), top, x(right, top), top, x(right, bottom), bottom, x(left, bottom), bottom)
    return quad.takeIf { it.isUsable(1.0, 1.0) && it.points.all { p -> p.first in 0f..1f } }
}
