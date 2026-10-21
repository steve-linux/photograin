// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.ObjectSelection
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.SubjectMask
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Selects the object under a tap with MobileSAM through ncnn, on the CPU. Reading the photo
 * takes a few seconds, once per photo; each selection on it then takes a fraction of a second.
 * The model ships in the app's assets; docs/MODELS.md says where it comes from.
 */
object ObjectSelector {
    private const val ENCODER_PARAM = "models/mobilesam_encoder.ncnn.param"
    private const val ENCODER_MODEL = "models/mobilesam_encoder.ncnn.bin"
    private const val DECODER_PARAM = "models/mobilesam_decoder.ncnn.param"
    private const val DECODER_MODEL = "models/mobilesam_decoder.ncnn.bin"
    private const val PROMPT = "models/mobilesam_prompt.bin"

    private val loaded by lazy { runCatching { System.loadLibrary("photograin_ai") }.isSuccess }
    private var handle = 0L

    /** The image last read by the model, and its upright size there. */
    private var encoded = WeakReference<Bitmap>(null)
    private var encodedBase: BaseOrientation? = null
    private var encodedSize = PxSize(1, 1)

    val available: Boolean get() = loaded

    /** Loads the model once; later calls reuse it. */
    @Synchronized
    private fun handle(context: Context): Long {
        if (handle != 0L) return handle
        if (!loaded) throw IOException("Object selection is not available on this device")
        handle = nativeLoad(context.applicationContext.assets, ENCODER_PARAM, ENCODER_MODEL, DECODER_PARAM, DECODER_MODEL, PROMPT)
        return handle
    }

    /**
     * The object of [image], the whole source as stored and shown with orientation [base], at
     * [points] (see Mask.points). The model reads [image] only if it is not the one it read
     * last. Blocking: call from a background thread.
     */
    @Synchronized
    fun select(context: Context, image: Bitmap, base: BaseOrientation, points: List<Float>): SubjectMask {
        val h = handle(context)
        val grid = BrushRaster.size(image.width, image.height)
        if (encoded.get() !== image || encodedBase != base) {
            encoded.clear()
            val scaled = Bitmap.createScaledBitmap(image, grid.width, grid.height, true)
            val pixels = IntArray(grid.width * grid.height)
            scaled.getPixels(pixels, 0, grid.width, 0, 0, grid.width, grid.height)
            if (scaled !== image) scaled.recycle()
            val (upright, size) = ObjectSelection.upright(pixels, grid.width, grid.height, base)
            val input = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                input.setPixels(upright, 0, size.width, 0, 0, size.width, size.height)
                nativeEncode(h, input)
            } finally {
                input.recycle()
            }
            encoded = WeakReference(image)
            encodedBase = base
            encodedSize = size
        }
        val (coords, labels) = ObjectSelection.prompt(points, base, encodedSize)
        val logits = nativeSelect(h, coords, labels) ?: throw IOException("Object selection failed")
        return ObjectSelection.fromLogits(logits, encodedSize, base, grid.width, grid.height)
    }

    @JvmStatic private external fun nativeLoad(
        assets: AssetManager,
        encoderParam: String,
        encoderModel: String,
        decoderParam: String,
        decoderModel: String,
        prompt: String,
    ): Long

    @JvmStatic private external fun nativeEncode(handle: Long, image: Bitmap)
    @JvmStatic private external fun nativeSelect(handle: Long, coords: FloatArray, labels: IntArray): FloatArray?
}
