// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import io.github.steve_linux.photograin.data.ImageLoader
import kotlin.math.exp
import kotlin.math.roundToInt

/** Exposure fusion for aligned shots. The output is an SDR bitmap with blended detail. */
internal fun fuseExposures(context: Context, uris: List<Uri>, longEdge: Int, focusStack: Boolean = false, medianStack: Boolean = false, lightenStack: Boolean = false): Bitmap {
    val inputs = mutableListOf<Bitmap>()
    var width = 0
    var height = 0
    try {
        for (uri in uris) {
            val image = ImageLoader.load(context, uri, longEdge)
            val source = image.preview
            try {
                val orientation = Matrix().apply {
                    if (image.base.mirror) setScale(-1f, 1f)
                    postRotate(image.base.quarterTurns * 90f)
                }
                val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
                orientation.mapRect(bounds)
                if (inputs.isEmpty()) {
                    val scale = longEdge / maxOf(bounds.width(), bounds.height())
                    width = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
                    height = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
                }
                require(kotlin.math.abs(bounds.width() / bounds.height() - width.toFloat() / height) <= 0.015f)
                orientation.postTranslate(-bounds.left, -bounds.top)
                orientation.postScale(width / bounds.width(), height / bounds.height())
                val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                inputs += input
                Canvas(input).drawBitmap(source, orientation, Paint(Paint.FILTER_BITMAP_FLAG))
            } finally {
                source.recycle()
            }
        }
        val sampleW = (width * 80f / maxOf(width, height)).roundToInt().coerceAtLeast(2)
        val sampleH = (height * 80f / maxOf(width, height)).roundToInt().coerceAtLeast(2)
        val reference = alignmentPixels(inputs.first(), sampleW, sampleH)
        val shifts = inputs.mapIndexed { index, image ->
            if (index == 0 || focusStack || medianStack || lightenStack) FrameShift(0, 0, 0.0)
            else matchFrames(reference, sampleW, alignmentPixels(image, sampleW, sampleH), sampleW, sampleH,
                -6..6, -6..6, listOf(0.0)) ?: FrameShift(0, 0, 0.0)
        }.map { shift ->
            FrameShift((shift.x * width.toFloat() / sampleW).roundToInt(),
                (shift.y * height.toFloat() / sampleH).roundToInt(), 0.0)
        }
        val exposureOffsets = inputs.mapIndexed { index, image ->
            val sample = alignmentPixels(image, sampleW, sampleH)
            val shift = shifts[index]
            val sx = (shift.x * sampleW.toFloat() / width).roundToInt()
            val sy = (shift.y * sampleH.toFloat() / height).roundToInt()
            val differences = mutableListOf<Int>()
            for (y in 0 until sampleH) for (x in 0 until sampleW) {
                if (x - sx !in 0 until sampleW || y - sy !in 0 until sampleH) continue
                val a = reference[y * sampleW + x]
                val b = sample[(y - sy) * sampleW + x - sx]
                if (a in 20..235 && b in 20..235) differences += b - a
            }
            differences.sorted().let { it.getOrElse(it.size / 2) { 0 } }
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val rows = Array(inputs.size) { IntArray(width) }
        val median = Array(3) { IntArray(inputs.size) }
        val pixels = IntArray(width)
        val upper = Array(inputs.size) { IntArray(width) }
        val lower = Array(inputs.size) { IntArray(width) }
        try {
            for (y in 0 until height) {
                for (i in inputs.indices) {
                    val sy = y - shifts[i].y
                    if (sy !in 0 until height) continue
                    inputs[i].getPixels(rows[i], 0, width, 0, sy, width, 1)
                    if (focusStack) {
                        inputs[i].getPixels(upper[i], 0, width, 0, (sy - 2).coerceAtLeast(0), width, 1)
                        inputs[i].getPixels(lower[i], 0, width, 0, (sy + 2).coerceAtMost(height - 1), width, 1)
                    }
                }
                for (x in 0 until width) {
                    if (lightenStack) {
                        var red = 0
                        var green = 0
                        var blue = 0
                        var alpha = 0
                        for (row in rows) {
                            val pixel = row[x]
                            if (pixel ushr 24 < 128) continue
                            red = maxOf(red, (pixel ushr 16) and 255)
                            green = maxOf(green, (pixel ushr 8) and 255)
                            blue = maxOf(blue, pixel and 255)
                            alpha = 255
                        }
                        pixels[x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                        continue
                    }
                    if (medianStack) {
                        var count = 0
                        for (row in rows) {
                            val pixel = row[x]
                            if (pixel ushr 24 < 128) continue
                            median[0][count] = (pixel ushr 16) and 255
                            median[1][count] = (pixel ushr 8) and 255
                            median[2][count] = pixel and 255
                            count++
                        }
                        if (count == 0) {
                            pixels[x] = 0
                        } else {
                            for (channel in median) java.util.Arrays.sort(channel, 0, count)
                            fun middle(channel: Int) = (median[channel][(count - 1) / 2] + median[channel][count / 2]) / 2
                            pixels[x] = (255 shl 24) or (middle(0) shl 16) or (middle(1) shl 8) or middle(2)
                        }
                        continue
                    }
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    var sum = 0f
                    val referencePixel = rows[0][x]
                    val referenceLight = imageLuma(referencePixel)
                    for (i in rows.indices) {
                        val sx = x - shifts[i].x
                        if (sx !in 0 until width || y - shifts[i].y !in 0 until height) continue
                        val row = rows[i]
                        val p = row[sx]
                        val red = (p ushr 16) and 255
                        val green = (p ushr 8) and 255
                        val blue = p and 255
                        val luminance = (54 * red + 183 * green + 19 * blue) shr 8
                        val contribution = if (focusStack) {
                            val horizontal = imageLuma(row[(sx - 2).coerceAtLeast(0)]) +
                                imageLuma(row[(sx + 2).coerceAtMost(width - 1)])
                            val vertical = imageLuma(upper[i][sx]) + imageLuma(lower[i][sx])
                            val detail = kotlin.math.abs(4 * luminance - horizontal - vertical).toFloat()
                            (1f + detail) * (1f + detail)
                        } else {
                            val mismatch = kotlin.math.abs(luminance - referenceLight - exposureOffsets[i])
                            val ghost = if (i > 0 && referenceLight in 30..225 && luminance in 20..235 && mismatch > 40) 0.03f else 1f
                            EXPOSURE_WEIGHTS[luminance] * ghost
                        }
                        val weight = contribution * ((p ushr 24) / 255f)
                        r += red * weight
                        g += green * weight
                        b += blue * weight
                        sum += weight
                    }
                    pixels[x] = if (sum <= 0f) 0 else (
                        (255 shl 24) or ((r / sum).roundToInt().coerceIn(0, 255) shl 16) or
                            ((g / sum).roundToInt().coerceIn(0, 255) shl 8) or (b / sum).roundToInt().coerceIn(0, 255)
                    )
                }
                output.setPixels(pixels, 0, width, 0, y, width, 1)
            }
            return output
        } catch (e: Throwable) {
            output.recycle()
            throw e
        }
    } finally {
        inputs.forEach(Bitmap::recycle)
    }
}

private val EXPOSURE_WEIGHTS = FloatArray(256) {
    val distance = (it / 255.0 - 0.5) / 0.20
    exp(-0.5 * distance * distance).toFloat()
}
