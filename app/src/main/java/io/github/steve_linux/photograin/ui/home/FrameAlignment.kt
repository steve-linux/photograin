// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.graphics.Bitmap
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal data class FrameShift(val x: Int, val y: Int, val angle: Double)

internal fun alignmentPixels(bitmap: Bitmap, width: Int, height: Int): IntArray {
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    return try {
        IntArray(width * height).apply {
            scaled.getPixels(this, 0, width, 0, 0, width, height)
            for (index in indices) this[index] = imageLuma(this[index])
        }
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
}

internal fun imageLuma(p: Int): Int =
    (54 * ((p ushr 16) and 255) + 183 * ((p ushr 8) and 255) + 19 * (p and 255)) shr 8

/** Finds the correction mapping the reference frame to source coordinates. */
internal fun matchFrames(
    left: IntArray, leftWidth: Int, right: IntArray, rightWidth: Int, height: Int,
    xRange: IntRange, yRange: IntRange, angles: List<Double>,
): FrameShift? {
    var best: FrameShift? = null
    var score = 0.72
    for (degrees in angles) {
        val radians = Math.toRadians(degrees)
        val cosine = kotlin.math.cos(radians)
        val sine = kotlin.math.sin(radians)
        val cx = rightWidth / 2.0
        val cy = height / 2.0
        for (dy in yRange) for (dx in xRange) {
            var a = 0.0
            var b = 0.0
            var aa = 0.0
            var bb = 0.0
            var ab = 0.0
            var count = 0
            val start = maxOf(2, dx + 2)
            val end = minOf(leftWidth - 2, dx + rightWidth - 2)
            for (y in 3 until height - 3 step 3) for (x in start until end step 3) {
                val px = x - dx - cx
                val py = y - dy - cy
                val sx = (cx + cosine * px - sine * py).roundToInt()
                val sy = (cy + sine * px + cosine * py).roundToInt()
                if (sx !in 0 until rightWidth || sy !in 0 until height) continue
                val u = left[y * leftWidth + x].toDouble()
                val v = right[sy * rightWidth + sx].toDouble()
                a += u
                b += v
                aa += u * u
                bb += v * v
                ab += u * v
                count++
            }
            if (count < 30) continue
            val va = aa - a * a / count
            val vb = bb - b * b / count
            if (minOf(va, vb) <= 12 * count) continue
            val correlation = (ab - a * b / count) / sqrt(va * vb)
            if (correlation > score) {
                score = correlation
                best = FrameShift(dx, dy, degrees)
            }
        }
    }
    return best
}
