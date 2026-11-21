// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.github.steve_linux.photograin.model.PhotoAnalysis
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.pow
import kotlin.math.roundToInt

internal suspend fun photoDigest(context: Context, uri: Uri): String? = try {
    val source = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read photo")
    source.use {
        val hash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = it.read(bytes)
            if (count < 0) break
            hash.update(bytes, 0, count)
        }
        hash.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

internal fun photoSignature(bitmap: Bitmap): PhotoAnalysis.Signature {
    val small = Bitmap.createScaledBitmap(bitmap, 9, 9, true)
    val pixels = IntArray(81)
    try {
        small.getPixels(pixels, 0, 9, 0, 0, 9, 9)
    } finally {
        if (small !== bitmap) small.recycle()
    }
    return PhotoAnalysis.signature(IntArray(81) { i ->
        val pixel = pixels[i]
        (54 * (pixel shr 16 and 255) + 183 * (pixel shr 8 and 255) + 19 * (pixel and 255)) / 256
    }, bitmap.width.toFloat() / bitmap.height)
}

private val linearSrgb = DoubleArray(256) {
    val encoded = it / 255.0
    if (encoded <= 0.04045) encoded / 12.92 else ((encoded + 0.055) / 1.055).pow(2.4)
}

internal fun regionLuminance(bitmap: Bitmap, x: Float, y: Float): Double? {
    val pixels = regionPixels(bitmap, x, y, 0.16f)
    var sum = 0.0
    var used = 0
    for (pixel in pixels) {
        if (pixel ushr 24 < 128) continue
        val luma = 0.2126 * linearSrgb[pixel shr 16 and 255] + 0.7152 * linearSrgb[pixel shr 8 and 255] +
            0.0722 * linearSrgb[pixel and 255]
        if (luma < 0.002 || luma > 0.98) continue
        sum += luma
        used++
    }
    return if (used > pixels.size / 4) sum / used else null
}

private fun regionPixels(bitmap: Bitmap, x: Float, y: Float, fraction: Float): IntArray {
    val width = (bitmap.width * fraction).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * fraction).roundToInt().coerceAtLeast(1)
    val left = (x * bitmap.width - width / 2f).roundToInt().coerceIn(0, bitmap.width - width)
    val top = (y * bitmap.height - height / 2f).roundToInt().coerceIn(0, bitmap.height - height)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, left, top, width, height)
    return pixels
}

internal fun sampleColor(bitmap: Bitmap, x: Float, y: Float, fraction: Float, grayCard: Boolean): DoubleArray? {
    val pixels = regionPixels(bitmap, x, y, fraction)
    val sums = DoubleArray(3)
    var used = 0
    for (pixel in pixels) {
        if (pixel ushr 24 < 250) continue
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        if (grayCard && (minOf(r, g, b) < 12 || maxOf(r, g, b) > 245)) continue
        sums[0] += linearSrgb[r]
        sums[1] += linearSrgb[g]
        sums[2] += linearSrgb[b]
        used++
    }
    if (used <= pixels.size / 4) return null
    return DoubleArray(3) { sums[it] / used }
}

internal fun grayCardBalance(bitmap: Bitmap, x: Float, y: Float, fraction: Float): Pair<Float, Float>? =
    sampleColor(bitmap, x, y, fraction, grayCard = true)?.let {
        io.github.steve_linux.photograin.model.WhiteBalance.neutralizeLinear(it[0], it[1], it[2])
    }

internal fun balancePreview(bitmap: Bitmap, temperature: Float, tint: Float): Bitmap =
    gainsPreview(bitmap, 1 + 0.35 * temperature, 1 - 0.25 * tint, 1 - 0.35 * temperature)

/** Exposure-only preview on the same linear scale used to estimate the correction. */
internal fun exposurePreview(bitmap: Bitmap, stops: Float): Bitmap {
    val gain = 2.0.pow(stops.toDouble())
    return gainsPreview(bitmap, gain, gain, gain)
}

private fun gainsPreview(bitmap: Bitmap, red: Double, green: Double, blue: Double): Bitmap {
    fun channel(gain: Double) = IntArray(256) {
        val linear = (linearSrgb[it] * gain).coerceIn(0.0, 1.0)
        ((if (linear <= 0.0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - 0.055) * 255)
            .roundToInt().coerceIn(0, 255)
    }
    val r = channel(red)
    val g = channel(green)
    val b = channel(blue)
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        val value = pixels[i]
        pixels[i] = (value and 0xff000000.toInt()) or (r[value shr 16 and 255] shl 16) or
            (g[value shr 8 and 255] shl 8) or b[value and 255]
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
