// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import io.github.steve_linux.photograin.model.Retouch
import io.github.steve_linux.photograin.model.RetouchSpot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws healing and clone spots into bitmaps of the original, for the preview and the export. */
object Retoucher {
    /** A finished spot: [bitmap] covers the source rectangle [rect] at the sample size it was made for. */
    class Patch(val bitmap: Bitmap, val rect: Rect)

    /**
     * Applies [spots] to [target], a mutable bitmap showing the source rectangle [region] (in
     * pixels of a [sourceWidth] x [sourceHeight] original) at 1/[sample] scale. The pixels of
     * each spot and of its source come from [decode], which takes a source rectangle and the
     * sample size and must return a new bitmap each time (it is recycled after use).
     * [cache] keeps finished spots between export tiles, or between previews; its bitmaps are
     * not recycled here.
     */
    fun apply(
        target: Bitmap,
        region: Rect,
        sample: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        spots: List<RetouchSpot>,
        cache: MutableMap<RetouchSpot, Patch>? = null,
        decode: (Rect, Int) -> Bitmap,
    ) {
        if (spots.isEmpty()) return
        val canvas = Canvas(target)
        spots.take(Retouch.MAX_SPOTS).forEach { spot ->
            val dest = destRect(spot, sourceWidth, sourceHeight, sample) ?: return@forEach
            if (!Rect.intersects(dest, region)) return@forEach
            val patch = cache?.get(spot)
                ?: patch(spot, dest, sample, sourceWidth, sourceHeight, decode)?.also { cache?.put(spot, it) }
                ?: return@forEach
            canvas.drawBitmap(
                patch.bitmap,
                (patch.rect.left - region.left) / sample.toFloat(),
                (patch.rect.top - region.top) / sample.toFloat(),
                null,
            )
            if (cache == null) patch.bitmap.recycle()
        }
    }

    /** The square around a spot, with room outside the circle for healing to read, clamped to the image. */
    private fun destRect(spot: RetouchSpot, w: Int, h: Int, sample: Int): Rect? {
        val r = spot.radius * max(w, h)
        val reach = r + max(2.0 * sample, r * 0.2)
        val cx = spot.x * w
        val cy = spot.y * h
        val rect = Rect(
            max(0, floor(cx - reach).toInt()),
            max(0, floor(cy - reach).toInt()),
            min(w, ceil(cx + reach).toInt()),
            min(h, ceil(cy + reach).toInt()),
        )
        return rect.takeIf { it.width() > sample && it.height() > sample }
    }

    private fun patch(
        spot: RetouchSpot,
        dest: Rect,
        sample: Int,
        w: Int,
        h: Int,
        decode: (Rect, Int) -> Bitmap,
    ): Patch? {
        // The source square, moved back inside the image if it pokes out.
        var dx = ((spot.sourceX - spot.x) * w).roundToInt()
        var dy = ((spot.sourceY - spot.y) * h).roundToInt()
        dx = dx.coerceIn(-dest.left, w - dest.right)
        dy = dy.coerceIn(-dest.top, h - dest.bottom)
        val src = Rect(dest.left + dx, dest.top + dy, dest.right + dx, dest.bottom + dy)
        if (src.width() <= 0 || src.height() <= 0) return null

        val dstBitmap = decode(dest, sample)
        val srcBitmap = decode(src, sample)
        val pw = min(dstBitmap.width, srcBitmap.width)
        val ph = min(dstBitmap.height, srcBitmap.height)
        val dstPixels = rgb(dstBitmap, pw, ph)
        val srcPixels = rgb(srcBitmap, pw, ph)
        val colorSpace = dstBitmap.colorSpace
        dstBitmap.recycle()
        srcBitmap.recycle()

        val cx = (spot.x * w - dest.left) / sample
        val cy = (spot.y * h - dest.top) / sample
        val r = spot.radius * max(w, h) / sample.toDouble()
        val out = Retouch.apply(dstPixels, srcPixels, pw, ph, cx.toDouble(), cy.toDouble(), r, spot)

        val buffer = ByteBuffer.allocateDirect(pw * ph * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until pw * ph) {
            buffer.put(out[i * 3].roundToInt().coerceIn(0, 255).toByte())
            buffer.put(out[i * 3 + 1].roundToInt().coerceIn(0, 255).toByte())
            buffer.put(out[i * 3 + 2].roundToInt().coerceIn(0, 255).toByte())
            buffer.put(255.toByte())
        }
        buffer.position(0)
        // Same color space as the photo, so drawing it back copies the values without converting them.
        val bitmap = if (colorSpace != null) {
            Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888, false, colorSpace)
        } else {
            Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        }
        bitmap.copyPixelsFromBuffer(buffer)
        return Patch(bitmap, Rect(dest.left, dest.top, dest.left + pw * sample, dest.top + ph * sample))
    }

    /** The top-left [w] x [h] pixels of an ARGB_8888 bitmap as RGB floats, as stored (no color conversion). */
    private fun rgb(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val b = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val buffer = ByteBuffer.allocateDirect(b.byteCount).order(ByteOrder.nativeOrder())
        b.copyPixelsToBuffer(buffer)
        val stride = b.rowBytes
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val o = y * stride + x * 4
            val i = (y * w + x) * 3
            out[i] = (buffer.get(o).toInt() and 0xFF).toFloat()
            out[i + 1] = (buffer.get(o + 1).toInt() and 0xFF).toFloat()
            out[i + 2] = (buffer.get(o + 2).toInt() and 0xFF).toFloat()
        }
        if (b !== bitmap) b.recycle()
        return out
    }
}
