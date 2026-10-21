// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import io.github.steve_linux.photograin.model.Removal
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Removes objects with MI-GAN through ncnn, on the CPU: the model fills a painted area of a
 * 512 pixel square from its surroundings in about a second. It ships in the app's assets;
 * docs/MODELS.md says where it comes from.
 */
object ObjectRemover {
    private const val PARAM = "models/migan.ncnn.param"
    private const val MODEL = "models/migan.ncnn.bin"

    private val loaded by lazy { runCatching { System.loadLibrary("photograin_ai") }.isSuccess }
    private var handle = 0L

    val available: Boolean get() = loaded

    /** The model for Retoucher, loaded from [context]'s assets the first time it is needed. */
    fun with(context: Context): Retoucher.Inpaint {
        val app = context.applicationContext
        return Retoucher.Inpaint { image, holes -> fill(app, image, holes) }
    }

    /** Loads the model once; later calls reuse it. */
    @Synchronized
    private fun handle(context: Context): Long {
        if (handle != 0L) return handle
        if (!loaded) throw IOException("Object removal is not available on this device")
        handle = nativeLoad(context.assets, PARAM, MODEL)
        return handle
    }

    /**
     * [image] with the holes filled: it is scaled to the model's square, filled, and scaled
     * back. Blocking: call from a background thread.
     */
    @Synchronized
    private fun fill(context: Context, image: Bitmap, holes: (Int, Int) -> ByteArray): Bitmap {
        val h = handle(context)
        val n = Removal.MODEL_SIZE
        val size = Removal.modelSize(image.width, image.height)
        val scaled = Bitmap.createScaledBitmap(image, size.width, size.height, true)
        val pixels = raw(scaled)
        if (scaled !== image) scaled.recycle()

        val square = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        try {
            square.copyPixelsFromBuffer(ByteBuffer.wrap(Removal.padded(pixels, size.width, size.height)))
            val mask = ByteArray(n * n)
            val grid = holes(size.width, size.height)
            for (y in 0 until size.height) grid.copyInto(mask, y * n, y * size.width, (y + 1) * size.width)
            nativeRun(h, square, mask)
            val part = Bitmap.createBitmap(square, 0, 0, size.width, size.height)
            val back = Bitmap.createScaledBitmap(part, image.width, image.height, true)
            if (part !== back && part !== square) part.recycle()
            // Same color space as the photo, with the values copied as they are.
            val out = if (image.colorSpace != null) {
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888, true, image.colorSpace!!)
            } else {
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            }
            val buffer = ByteBuffer.allocateDirect(back.byteCount).order(ByteOrder.nativeOrder())
            back.copyPixelsToBuffer(buffer)
            buffer.position(0)
            out.copyPixelsFromBuffer(buffer)
            if (back !== square) back.recycle()
            return out
        } finally {
            square.recycle()
        }
    }

    /** The pixels of an ARGB_8888 bitmap as tightly packed RGBA bytes, as stored. */
    private fun raw(bitmap: Bitmap): ByteArray {
        val b = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val buffer = ByteBuffer.allocate(b.byteCount)
        b.copyPixelsToBuffer(buffer)
        val stride = b.rowBytes
        val row = b.width * 4
        val out = ByteArray(row * b.height)
        for (y in 0 until b.height) System.arraycopy(buffer.array(), y * stride, out, y * row, row)
        if (b !== bitmap) b.recycle()
        return out
    }

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, model: String): Long
    @JvmStatic private external fun nativeRun(handle: Long, image: Bitmap, mask: ByteArray)
}
