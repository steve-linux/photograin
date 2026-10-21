// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Rect
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.SubjectMask
import io.github.steve_linux.photograin.model.Subjects
import java.io.IOException
import kotlin.math.max

/**
 * Finds the main subject of a photo with U^2-Net-p through ncnn, on the CPU: the model is
 * small enough to take about a second. It ships in the app's assets; docs/MODELS.md says
 * where it comes from.
 */
object SubjectSegmenter {
    private const val PARAM = "models/u2netp.ncnn.param"
    private const val MODEL = "models/u2netp.ncnn.bin"

    private val loaded by lazy { runCatching { System.loadLibrary("photograin_ai") }.isSuccess }
    private var handle = 0L

    val available: Boolean get() = loaded

    /** Loads the model once; later calls reuse it. */
    @Synchronized
    private fun handle(context: Context): Long {
        if (handle != 0L) return handle
        if (!loaded) throw IOException("Subject detection is not available on this device")
        handle = nativeLoad(context.applicationContext.assets, PARAM, MODEL)
        return handle
    }

    /**
     * The subject of [image], the whole source as stored, shown with orientation [base].
     * Blocking: call from a background thread.
     */
    @Synchronized
    fun segment(context: Context, image: Bitmap, base: BaseOrientation): SubjectMask {
        val h = handle(context)
        val grid = BrushRaster.size(image.width, image.height)
        val scaled = Bitmap.createScaledBitmap(image, grid.width, grid.height, true)
        val pixels = IntArray(grid.width * grid.height)
        scaled.getPixels(pixels, 0, grid.width, 0, 0, grid.width, grid.height)
        if (scaled !== image) scaled.recycle()
        val n = SubjectMask.MODEL_SIZE
        val input = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        try {
            input.setPixels(SubjectMask.modelInput(pixels, grid.width, grid.height, base), 0, n, 0, 0, n, n)
            val raw = nativeRun(h, input) ?: throw IOException("Subject detection failed")
            return SubjectMask.fromModel(raw, base, grid.width, grid.height)
        } finally {
            input.recycle()
        }
    }

    /**
     * What the subject masks of [edit] cover in [source], or null without any: the main
     * subject, and the objects their points select (see ObjectSelector). The photo is decoded
     * at about the size of a mask's grid, so it works on photos of any size.
     */
    fun forEdit(context: Context, source: RegionSource, base: BaseOrientation, edit: EditState): Subjects? {
        val points = edit.masks.filter { it.mask.type == MaskType.SUBJECT }.map { it.mask.points }
        if (points.isEmpty()) return null
        var sample = 1
        while (max(source.width, source.height) / (sample * 2) >= BrushRaster.LONG_EDGE * 2) sample *= 2
        val bitmap = source.decode(Rect(0, 0, source.width, source.height), sample)
        return try {
            Subjects(
                main = if (points.any { it.isEmpty() }) segment(context, bitmap, base) else null,
                objects = points.filter { it.isNotEmpty() }.distinct().associateWith { ObjectSelector.select(context, bitmap, base, it) },
            )
        } finally {
            bitmap.recycle()
        }
    }

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, model: String): Long
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap): ByteArray?
}
