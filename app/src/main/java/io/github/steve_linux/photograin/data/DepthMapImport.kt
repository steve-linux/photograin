// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.steve_linux.photograin.model.DepthMap
import kotlin.math.abs

internal object DepthMapImport {
    fun load(context: Context, uri: Uri, photo: LoadedImage): DepthMap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        val rotated = photo.base.quarterTurns.mod(2) != 0
        val expected = if (rotated) photo.sourceHeight.toDouble() / photo.sourceWidth
            else photo.sourceWidth.toDouble() / photo.sourceHeight
        if (abs(width.toDouble() / height / expected - 1.0) > 0.02) return null

        val size = DepthMap.SIZE
        val sample = (maxOf(width, height) / size).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit(sample) }
        val image = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val resized = try {
            Bitmap.createScaledBitmap(image, size, size, true)
        } catch (error: Exception) {
            image.recycle()
            throw error
        }
        val pixels = IntArray(size * size)
        try {
            resized.getPixels(pixels, 0, size, 0, 0, size, size)
        } finally {
            if (resized !== image) resized.recycle()
            image.recycle()
        }
        val depths = ByteArray(pixels.size) { i ->
            val color = pixels[i]
            val r = (color ushr 16) and 255
            val g = (color ushr 8) and 255
            val b = color and 255
            ((54 * r + 183 * g + 19 * b + 128) shr 8).toByte()
        }
        return DepthMap.fromUpright(depths, photo.base)
    }
}
