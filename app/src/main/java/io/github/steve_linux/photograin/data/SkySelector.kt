// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.SkySelection
import io.github.steve_linux.photograin.model.SubjectMask
import java.io.IOException

/** Dedicated sky segmentation with bundled MIT-licensed U²-Net-p weights; fully offline. */
object SkySelector {
    private val loaded by lazy { runCatching { System.loadLibrary("photograin_ai") }.isSuccess }
    private var handle = 0L

    @Synchronized
    fun select(context: Context, image: Bitmap, base: BaseOrientation): SubjectMask {
        if (!loaded) throw IOException("Sky detection is not available on this device")
        if (handle == 0L) {
            handle = nativeLoad(context.applicationContext.assets, "models/skyseg.ncnn.param", "models/skyseg.ncnn.bin")
        }
        val grid = BrushRaster.size(image.width, image.height)
        val scaled = Bitmap.createScaledBitmap(image, grid.width, grid.height, true)
        val pixels = IntArray(grid.width * grid.height)
        try {
            scaled.getPixels(pixels, 0, grid.width, 0, 0, grid.width, grid.height)
        } finally {
            if (scaled !== image) scaled.recycle()
        }
        val n = SkySelection.MODEL_SIZE
        val input = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        return try {
            input.setPixels(SubjectMask.modelInput(pixels, grid.width, grid.height, base, n), 0, n, 0, 0, n, n)
            val raw = nativeRun(handle, input) ?: throw IOException("Sky detection failed")
            SkySelection.fromModel(raw, base, grid.width, grid.height)
        } finally {
            input.recycle()
        }
    }

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, model: String): Long
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap): ByteArray?
}
