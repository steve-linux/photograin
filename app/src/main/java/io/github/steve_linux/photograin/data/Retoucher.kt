// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.github.steve_linux.photograin.model.Removal
import io.github.steve_linux.photograin.model.Retouch
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.RetouchType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws healing and clone spots into bitmaps of the original, for the preview and the export. */
object Retoucher {
    /**
     * A finished spot: [bitmap] covers the source rectangle [rect], at the sample size it was
     * made for or, for an object removal, at the size the inpainting worked at.
     */
    class Patch(val bitmap: Bitmap, val rect: Rect)

    /** The inpainting model, which removal spots need: see ObjectRemover. */
    fun interface Inpaint {
        /**
         * [image] with the holes filled, as a new mutable ARGB_8888 bitmap of the same size and
         * color space, its values taken as they are (no color conversion). [holes] gives the
         * area to fill on a grid of a given size over the image, nonzero in the holes.
         */
        fun fill(image: Bitmap, holes: (Int, Int) -> ByteArray): Bitmap
    }

    /** Longest edge the area around a removal is worked on at: the model sees 512 pixels of it anyway. */
    private const val REMOVE_WORK = 1024

    /**
     * Applies [spots] to [target], a mutable bitmap showing the source rectangle [region] (in
     * pixels of a [sourceWidth] x [sourceHeight] original) at 1/[sample] scale. The pixels of
     * each spot and of its source come from [decode], which takes a source rectangle and the
     * sample size and must return a new bitmap each time (it is recycled after use).
     * [cache] keeps finished spots between export tiles, or between previews; its bitmaps are
     * not recycled here. Removal spots are left out without [inpaint].
     */
    fun apply(
        target: Bitmap,
        region: Rect,
        sample: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        spots: List<RetouchSpot>,
        cache: MutableMap<RetouchSpot, Patch>? = null,
        inpaint: Inpaint? = null,
        decode: (Rect, Int) -> Bitmap,
    ) {
        if (spots.isEmpty()) return
        val canvas = Canvas(target)
        val scaled = Paint(Paint.FILTER_BITMAP_FLAG)
        spots.take(Retouch.MAX_SPOTS).forEach { spot ->
            val remove = spot.type == RetouchType.REMOVE
            if (remove && inpaint == null) return@forEach
            val dest = (if (remove) removeRect(spot, sourceWidth, sourceHeight) else destRect(spot, sourceWidth, sourceHeight, sample))
                ?: return@forEach
            if (!Rect.intersects(dest, region)) return@forEach
            val patch = cache?.get(spot)
                ?: (if (remove) {
                    removal(spot, dest, sample, sourceWidth, sourceHeight, inpaint!!, decode)
                } else {
                    patch(spot, dest, sample, sourceWidth, sourceHeight, decode)
                })?.also { cache?.put(spot, it) }
                ?: return@forEach
            val left = (patch.rect.left - region.left) / sample.toFloat()
            val top = (patch.rect.top - region.top) / sample.toFloat()
            if (remove) {
                // Worked on at its own size: stretched over its area, its soft edge blending in.
                val right = (patch.rect.right - region.left) / sample.toFloat()
                val bottom = (patch.rect.bottom - region.top) / sample.toFloat()
                canvas.drawBitmap(patch.bitmap, null, RectF(left, top, right, bottom), scaled)
            } else {
                canvas.drawBitmap(patch.bitmap, left, top, null)
            }
            if (cache == null) patch.bitmap.recycle()
        }
    }

    /** What the inpainting model sees of a removal spot, in source pixels. */
    private fun removeRect(spot: RetouchSpot, w: Int, h: Int): Rect? =
        Removal.crop(spot.strokes, w, h)?.let { Rect(it[0], it[1], it[2], it[3]) }

    /**
     * A removal spot: the area around it filled by [inpaint] where the strokes paint, and
     * transparent elsewhere, so only the painted part of the photo changes.
     */
    private fun removal(
        spot: RetouchSpot,
        dest: Rect,
        sample: Int,
        w: Int,
        h: Int,
        inpaint: Inpaint,
        decode: (Rect, Int) -> Bitmap,
    ): Patch? {
        var work = sample
        while (max(dest.width(), dest.height()) / work > REMOVE_WORK) work *= 2
        val cw = max(1, ceil(dest.width() / work.toDouble()).toInt())
        val ch = max(1, ceil(dest.height() / work.toDouble()).toInt())
        val decoded = decode(dest, work)
        // Some sources ignore the sample size: bring what they give to the size wanted.
        val image = if (decoded.width == cw && decoded.height == ch) decoded else {
            Bitmap.createScaledBitmap(decoded, cw, ch, true).also { if (it !== decoded) decoded.recycle() }
        }
        val crop = intArrayOf(dest.left, dest.top, dest.right, dest.bottom)
        val filled = try {
            inpaint.fill(image) { gw, gh -> Removal.raster(spot.strokes, crop, w, h, gw, gh, Removal.GROW) }
        } finally {
            image.recycle()
        }
        val alpha = Removal.raster(spot.strokes, crop, w, h, cw, ch)
        val opacity = spot.opacity.coerceIn(0f, 1f)
        val buffer = ByteBuffer.allocateDirect(filled.byteCount).order(ByteOrder.nativeOrder())
        filled.copyPixelsToBuffer(buffer)
        val stride = filled.rowBytes
        // Premultiplied by the painted strength, as bitmaps with alpha are stored.
        for (y in 0 until ch) for (x in 0 until cw) {
            val a = (alpha[y * cw + x].toInt() and 0xFF) * opacity / 255f
            val o = y * stride + x * 4
            for (c in 0..2) buffer.put(o + c, ((buffer.get(o + c).toInt() and 0xFF) * a).roundToInt().toByte())
            buffer.put(o + 3, (a * 255).roundToInt().toByte())
        }
        buffer.position(0)
        filled.setHasAlpha(true)
        filled.copyPixelsFromBuffer(buffer)
        return Patch(filled, dest)
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
        // Red eye works on the spot alone.
        val srcBitmap = if (spot.type == RetouchType.RED_EYE) null else decode(src, sample)
        val pw = min(dstBitmap.width, srcBitmap?.width ?: dstBitmap.width)
        val ph = min(dstBitmap.height, srcBitmap?.height ?: dstBitmap.height)
        val dstPixels = rgb(dstBitmap, pw, ph)
        val srcPixels = srcBitmap?.let { rgb(it, pw, ph) } ?: dstPixels
        val colorSpace = dstBitmap.colorSpace
        dstBitmap.recycle()
        srcBitmap?.recycle()

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
