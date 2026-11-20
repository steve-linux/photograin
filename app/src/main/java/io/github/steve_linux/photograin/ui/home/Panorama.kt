// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import io.github.steve_linux.photograin.data.ImageLoader
import kotlin.math.roundToInt

internal class PanoramaAlignmentException : IllegalArgumentException("Cannot align consecutive photos")

/** Horizontal translation and feathered seams for ordered shots with the same field of view. */
internal fun stitchPanorama(context: Context, uris: List<Uri>, height: Int, vertical: Boolean = false, columns: Int = 0): Bitmap {
    if (columns > 0) require(uris.size >= columns * 2 && uris.size % columns == 0)
    val photos = mutableListOf<Bitmap>()
    try {
        for (uri in uris) {
            val image = ImageLoader.load(context, uri, height * 2)
            val source = image.preview
            try {
                val orientation = Matrix().apply {
                    if (image.base.mirror) setScale(-1f, 1f)
                    postRotate(image.base.quarterTurns * 90f - if (vertical) 90f else 0f)
                }
                val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
                orientation.mapRect(bounds)
                val scale = height / bounds.height()
                orientation.postScale(scale, scale)
                orientation.postTranslate(-bounds.left * scale, -bounds.top * scale)
                val upright = Bitmap.createBitmap((bounds.width() * scale).roundToInt().coerceAtLeast(1), height, Bitmap.Config.ARGB_8888)
                photos += upright
                Canvas(upright).drawBitmap(source, orientation, Paint(Paint.FILTER_BITMAP_FLAG))
            } finally {
                source.recycle()
            }
        }

        if (columns > 0) return stitchMosaic(photos, columns, height)
        val stitched = stitchRow(photos, height)
        if (!vertical) return stitched
        return try { rotate(stitched, 90f) } finally { stitched.recycle() }
    } finally {
        photos.forEach(Bitmap::recycle)
    }
}

private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap =
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { setRotate(degrees) }, true)

private fun stitchMosaic(photos: List<Bitmap>, columns: Int, height: Int): Bitmap {
    val strips = mutableListOf<Bitmap>()
    val turned = mutableListOf<Bitmap>()
    try {
        for (row in photos.chunked(columns)) strips += stitchRow(row, height)
        val width = strips.minOf { it.width }
        for (row in strips) {
            val crop = Bitmap.createBitmap(row, (row.width - width) / 2, 0, width, row.height)
            try { turned += rotate(crop, -90f) } finally { if (crop !== row) crop.recycle() }
        }
        val stitched = stitchRow(turned, width)
        return try { rotate(stitched, 90f) } finally { stitched.recycle() }
    } finally {
        turned.forEach(Bitmap::recycle)
        strips.forEach(Bitmap::recycle)
    }
}

private fun stitchRow(photos: List<Bitmap>, height: Int): Bitmap {
    val offsets = mutableListOf(FrameShift(0, 0, 0.0))
    val widths = photos.map { (it.width * ANALYSIS_HEIGHT / height.toFloat()).roundToInt().coerceAtLeast(1) }
    val analysis = photos.mapIndexed { index, photo -> alignmentPixels(photo, widths[index], ANALYSIS_HEIGHT) }
    for (index in 1 until photos.size) {
        val left = widths[index - 1]
        val right = widths[index]
        val overlap = minOf(left, right)
        val shift = matchFrames(
            analysis[index - 1], left, analysis[index], right, ANALYSIS_HEIGHT,
            (left - overlap * 0.70).roundToInt().coerceAtLeast(1)..(left - overlap * 0.18).roundToInt(),
            -6..6, listOf(0.0, -1.0, 1.0, -2.0, 2.0, -3.0, 3.0),
        ) ?: throw PanoramaAlignmentException()
        val previous = offsets.last()
        val scale = height / ANALYSIS_HEIGHT.toFloat()
        offsets += FrameShift(previous.x + (shift.x * scale).roundToInt(),
            previous.y + (shift.y * scale).roundToInt(), previous.angle - shift.angle)
    }
    val width = photos.zip(offsets).maxOf { (photo, offset) -> offset.x + photo.width }
    val top = photos.indices.maxOf { index ->
        val angle = Math.toRadians(offsets[index].angle)
        offsets[index].y + kotlin.math.ceil(kotlin.math.abs(kotlin.math.sin(angle)) * photos[index].width / 2 +
            (1 - kotlin.math.cos(angle)) * height / 2).toInt()
    }
    val bottom = photos.indices.minOf { index ->
        val angle = Math.toRadians(offsets[index].angle)
        offsets[index].y + height - kotlin.math.ceil(kotlin.math.abs(kotlin.math.sin(angle)) * photos[index].width / 2 +
            (1 - kotlin.math.cos(angle)) * height / 2).toInt()
    }
    val croppedHeight = bottom - top
    if (croppedHeight < height / 2 || width.toLong() * croppedHeight > 24_000_000) throw PanoramaAlignmentException()
    val result = Bitmap.createBitmap(width, croppedHeight, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(result)
        canvas.drawBitmap(photos.first(), 0f, -top.toFloat(), null)
        val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        for (index in 1 until photos.size) {
            val x = offsets[index].x.toFloat()
            val y = (offsets[index].y - top).toFloat()
            val photo = photos[index]
            val overlap = (offsets[index - 1].x + photos[index - 1].width - offsets[index].x)
                .coerceIn(1, photo.width)
            val layer = canvas.saveLayer(0f, 0f, width.toFloat(), croppedHeight.toFloat(), null)
            canvas.save()
            canvas.rotate(offsets[index].angle.toFloat(), x + photo.width / 2f, y + height / 2f)
            canvas.drawBitmap(photo, x, y, null)
            canvas.restore()
            fade.shader = LinearGradient(x, 0f, x + overlap, 0f, 0x00ffffff, -1, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), croppedHeight.toFloat(), fade)
            canvas.restoreToCount(layer)
        }
        return result
    } catch (e: Throwable) {
        result.recycle()
        throw e
    }
}

private const val ANALYSIS_HEIGHT = 80
