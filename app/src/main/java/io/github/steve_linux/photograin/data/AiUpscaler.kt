// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import io.github.steve_linux.photograin.model.ColorProfile
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real-ESRGAN (realesr-general-x4v3) through ncnn, on the GPU with Vulkan when there is one.
 * The model ships in the app's assets; docs/MODELS.md says where it comes from.
 */
object AiUpscaler {
    const val SCALE = 4
    private const val TAG = "AiUpscaler"
    private const val PARAM = "models/realesr_general_x4v3.ncnn.param"
    private const val MODEL = "models/realesr_general_x4v3.ncnn.bin"

    private val loaded by lazy { runCatching { System.loadLibrary("photograin_ai") }.isSuccess }
    private var handle = 0L

    val available: Boolean get() = loaded

    /** Loads the model once; later calls reuse it. */
    @Synchronized
    private fun handle(context: Context): Long {
        if (handle != 0L) return handle
        if (!loaded) throw IOException("AI upscaling is not available on this device")
        handle = nativeLoad(context.applicationContext.assets, PARAM, MODEL, true)
        Log.i(TAG, if (nativeUsesGpu(handle)) "Running on the GPU" else "Running on the CPU")
        return handle
    }

    /** [input] enlarged [SCALE] times. Both are ARGB_8888 in the same color space. */
    @Synchronized
    fun upscale(context: Context, input: Bitmap): Bitmap {
        val h = handle(context)
        val source = if (input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, false)
        val space = source.colorSpace ?: ColorSpaces.of(ColorProfile.SRGB)
        val out = Bitmap.createBitmap(source.width * SCALE, source.height * SCALE, Bitmap.Config.ARGB_8888, false, space)
        try {
            nativeRun(h, source, out)
        } catch (e: Exception) {
            out.recycle()
            throw e
        } finally {
            if (source !== input) source.recycle()
        }
        return out
    }

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, model: String, gpu: Boolean): Long
    @JvmStatic private external fun nativeUsesGpu(handle: Long): Boolean
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap, output: Bitmap)
}

/**
 * [base] enlarged [factor] times by [AiUpscaler]: each region is decoded from the original
 * with some context, run through the model at 4x and resized to [factor].
 */
class AiUpscaleSource(
    private val context: Context,
    private val base: RegionSource,
    private val factor: Double,
) : RegionSource {
    override val width = max(1, (base.width * factor).roundToInt())
    override val height = max(1, (base.height * factor).roundToInt())
    override val profile get() = base.profile

    override fun decode(rect: Rect, sampleSize: Int): Bitmap {
        // The original pixels behind rect, plus context so the model sees across the edges.
        val sx = base.width.toDouble() / width
        val sy = base.height.toDouble() / height
        val left = max(0, floor(rect.left * sx).toInt() - CONTEXT)
        val top = max(0, floor(rect.top * sy).toInt() - CONTEXT)
        val right = min(base.width, ceil(rect.right * sx).toInt() + CONTEXT)
        val bottom = min(base.height, ceil(rect.bottom * sy).toInt() + CONTEXT)
        val original = base.decode(Rect(left, top, right, bottom), 1)
        val enlarged = try {
            AiUpscaler.upscale(context, original)
        } finally {
            original.recycle()
        }
        // Resize the 4x result to this source's scale, then cut out rect.
        val rx = width.toDouble() / base.width
        val ry = height.toDouble() / base.height
        val fitted = Bitmap.createScaledBitmap(
            enlarged,
            max(1, ((right - left) * rx).roundToInt()),
            max(1, ((bottom - top) * ry).roundToInt()),
            true,
        ).also { if (it !== enlarged) enlarged.recycle() }
        val cx = (rect.left - left * rx).roundToInt().coerceIn(0, fitted.width - 1)
        val cy = (rect.top - top * ry).roundToInt().coerceIn(0, fitted.height - 1)
        val cw = min(rect.width(), fitted.width - cx)
        val ch = min(rect.height(), fitted.height - cy)
        var region = Bitmap.createBitmap(fitted, cx, cy, cw, ch).also { if (it !== fitted) fitted.recycle() }
        if (sampleSize > 1) {
            region = Bitmap.createScaledBitmap(region, max(1, cw / sampleSize), max(1, ch / sampleSize), true)
                .also { if (it !== region) region.recycle() }
        }
        return region
    }

    override fun close() = base.close()

    private companion object {
        const val CONTEXT = 8
    }
}
