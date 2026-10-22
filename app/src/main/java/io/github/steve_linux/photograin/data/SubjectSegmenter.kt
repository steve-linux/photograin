// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.app.ActivityManager
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.ModelPack
import io.github.steve_linux.photograin.model.SubjectMask
import io.github.steve_linux.photograin.model.Subjects
import java.io.IOException
import kotlin.math.max

/**
 * Finds the main subject of a photo through ncnn, on the CPU: with U^2-Net-p, small enough to
 * take about a second and shipped in the app's assets, or with IS-Net once its model pack is
 * imported (see [ModelPacks]), finer but much slower. docs/MODELS.md says where they come from.
 */
object SubjectSegmenter {
    private const val TAG = "SubjectSegmenter"
    private const val PARAM = "models/u2netp.ncnn.param"
    private const val MODEL = "models/u2netp.ncnn.bin"

    /** IS-Net sees a 1024 square, with each channel as v / 255 - 0.5. */
    private const val ISNET_SIZE = 1024
    private val ISNET_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val ISNET_DEVIATION = floatArrayOf(1f, 1f, 1f)

    /**
     * Free memory IS-Net needs to run: about 1 GB at its peak, with room to spare.
     * With less the system would kill the app, so the bundled model finds the subject instead.
     */
    private const val ISNET_MEMORY = 3L shl 29

    private val loaded by lazy { runCatching { System.loadLibrary("photograin_ai") }.isSuccess }
    private var handle = 0L

    /** The pack [handle] was loaded from, or null for the bundled model. */
    private var handlePack: ModelPack? = null
    private var size = SubjectMask.MODEL_SIZE

    val available: Boolean get() = loaded

    data class Result(val mask: SubjectMask, val usedFallback: Boolean)

    /** Loads the best model installed; later calls reuse it until the packs change. */
    @Synchronized
    private fun handle(context: Context): Long {
        val pack = ModelPack.ISNET.takeIf { ModelPacks.isInstalled(context, it) && roomFor(context, ISNET_MEMORY) }
        if (handle != 0L && pack == handlePack) return handle
        if (!loaded) throw IOException("Subject detection is not available on this device")
        if (handle != 0L) nativeFree(handle)
        handle = 0L
        if (pack != null) {
            val param = ModelPacks.file(context, pack, "isnet.ncnn.param") ?: throw IOException("The model pack is gone")
            val model = ModelPacks.file(context, pack, "isnet.ncnn.bin") ?: throw IOException("The model pack is gone")
            handle = nativeLoadFile(param.path, model.path, ISNET_SIZE, ISNET_MEAN, ISNET_DEVIATION)
            size = ISNET_SIZE
        } else {
            handle = nativeLoad(context.applicationContext.assets, PARAM, MODEL)
            size = SubjectMask.MODEL_SIZE
        }
        handlePack = pack
        return handle
    }

    /**
     * The subject of [image], the whole source as stored, shown with orientation [base].
     * Blocking: call from a background thread.
     */
    fun segment(context: Context, image: Bitmap, base: BaseOrientation): SubjectMask =
        segmentResult(context, image, base).mask

    /** Returns the model choice together with its mask, even if another caller runs next. */
    @Synchronized
    fun segmentResult(context: Context, image: Bitmap, base: BaseOrientation): Result {
        val h = handle(context)
        val fallback = handlePack == null && ModelPacks.isInstalled(context, ModelPack.ISNET)
        val grid = BrushRaster.size(image.width, image.height)
        val n = size
        // Resize directly to the network input. Going through the rectangular brush grid
        // discards short-edge detail, then enlarges it again (with nearest-neighbour sampling).
        // Keep the bundled model's area-averaged preprocessing unchanged.
        val detailed = handlePack == ModelPack.ISNET
        val w = if (detailed) n else grid.width
        val height = if (detailed) n else grid.height
        val scaled = Bitmap.createScaledBitmap(image, w, height, true)
        val pixels = IntArray(w * height)
        scaled.getPixels(pixels, 0, w, 0, 0, w, height)
        if (scaled !== image) scaled.recycle()
        val input = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        try {
            input.setPixels(SubjectMask.modelInput(pixels, w, height, base, n), 0, n, 0, 0, n, n)
            val raw = nativeRun(h, input) ?: throw IOException("Subject detection failed")
            return Result(SubjectMask.fromModel(raw, base, grid.width, grid.height, n, boostSubject = !detailed), fallback)
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

    /** Whether the system has [bytes] of memory free, without running low. */
    private fun roomFor(context: Context, bytes: Long): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return true
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val room = !info.lowMemory && info.availMem - info.threshold >= bytes
        if (!room) Log.w(TAG, "Not enough memory for IS-Net (${info.availMem shr 20} MB free): using U^2-Net-p")
        return room
    }

    @JvmStatic private external fun nativeLoad(assets: AssetManager, param: String, model: String): Long
    @JvmStatic private external fun nativeLoadFile(param: String, model: String, size: Int, mean: FloatArray, deviation: FloatArray): Long
    @JvmStatic private external fun nativeFree(handle: Long)
    @JvmStatic private external fun nativeRun(handle: Long, input: Bitmap): ByteArray?
}
