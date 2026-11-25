// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.annotation.Keep
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.HdrAlignment
import io.github.steve_linux.photograin.model.HdrFrame
import io.github.steve_linux.photograin.model.HdrPlan
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/** Called from native code between frames; false cancels the merge. */
@Keep
fun interface HdrProgress {
    fun onProgress(fraction: Float): Boolean
}

/** A probed set of brackets, ready to merge. */
class HdrInput(
    val uris: List<Uri>,
    val frames: List<HdrFrame>,
    /** Every frame is decoded into this space; RAW frames come in Display P3. */
    val profile: ColorProfile,
)

class HdrOutput(
    val bitmap: Bitmap,
    val profile: ColorProfile,
    /** The frame of middle brightness: its metadata goes with the merge. */
    val reference: Uri,
    val alignment: HdrAlignment,
    /** Below 1 when the phone had too little free memory for full size. */
    val scale: Float,
)

/**
 * Merges bracketed exposures of one scene (the same shot taken darker and brighter) into one
 * photo with detail in both shadows and highlights, through the native photograin_hdr library.
 * The frames are aligned if handheld and, where something moved, taken from one frame only.
 */
object HdrMerger {
    private val loaded by lazy { runCatching { System.loadLibrary("photograin_hdr") }.isSuccess }

    val available: Boolean get() = loaded

    enum class Stage { DECODE, ALIGN, FUSE }

    /** Reads sizes, orientation and kinds without decoding pixels. */
    fun probe(context: Context, uris: List<Uri>): HdrInput {
        var wide = false
        val frames = uris.map { uri ->
            val mime = context.contentResolver.getType(uri)
            val name = ImageLoader.queryName(context, uri)
            when {
                RawImage.isRaw(mime, name) -> {
                    val info = RawImage.probe(context, uri)
                    wide = true
                    oriented(info.width, info.height, BaseOrientation.fromExif(info.exifOrientation), raw = true)
                }
                ImageCodecs.isJxl(mime, name) -> {
                    // libjxl has no cheap probe here, and JPEG XL brackets are rare: decode once to learn the size.
                    val (bitmap, profile) = ImageCodecs.decodeJxl(context, uri)
                    if (profile != ColorProfile.SRGB) wide = true
                    HdrFrame(bitmap.width, bitmap.height, raw = false).also { bitmap.recycle() }
                }
                else -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    // With inJustDecodeBounds decodeStream always returns null, so check the stream, not the result.
                    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                    stream.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image: $uri")
                    if (ColorSpaces.workingSpaceFor(bounds.outColorSpace) != ColorProfile.SRGB) wide = true
                    oriented(bounds.outWidth, bounds.outHeight, ImageLoader.baseOrientation(context, uri), raw = false)
                }
            }
        }
        return HdrInput(uris, frames, if (wide) ColorProfile.DISPLAY_P3 else ColorProfile.SRGB)
    }

    private fun oriented(w: Int, h: Int, base: BaseOrientation, raw: Boolean) =
        if (base.quarterTurns % 2 == 1) HdrFrame(h, w, raw) else HdrFrame(w, h, raw)

    /** The scale at which [input] fits in the memory free right now. */
    fun scaleFor(context: Context, input: HdrInput): Float {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        // Leave room for the app itself and for the system not to start killing apps.
        val budget = ((info.availMem - info.threshold) * 0.7).toLong() - (64L shl 20)
        return HdrPlan.scaleFor(input.frames, max(budget, 0L))
    }

    /**
     * Decodes, aligns and fuses [input]. [onProgress] gets the stage and its progress (0..1) and
     * returns false to cancel, which throws [CancellationException].
     */
    fun merge(
        context: Context,
        input: HdrInput,
        align: Boolean,
        deghost: Boolean,
        onProgress: (Stage, Float) -> Boolean,
    ): HdrOutput {
        if (!loaded) throw IOException("HDR merge is not available on this device")
        val scale = scaleFor(context, input)
        val width = max(1, (input.frames[0].width * scale).roundToInt())
        val height = max(1, (input.frames[0].height * scale).roundToInt())
        val bitmaps = ArrayList<Bitmap>(input.uris.size)
        try {
            for ((i, uri) in input.uris.withIndex()) {
                if (!onProgress(Stage.DECODE, i.toFloat() / input.uris.size)) throw CancellationException()
                bitmaps += decode(context, uri, input.frames[i], input.profile, width, height)
            }
            onProgress(Stage.DECODE, 1f)
            val frames = bitmaps.toTypedArray()
            val aligned = nativeAlign(frames, align) { onProgress(Stage.ALIGN, it) }
                ?: throw CancellationException()
            val alignment = HdrAlignment.fromNative(aligned)
            val output = Bitmap.createBitmap(
                alignment.width, alignment.height, Bitmap.Config.ARGB_8888, false, ColorSpaces.of(input.profile),
            )
            val done = try {
                nativeFuse(frames, aligned, deghost, output) { onProgress(Stage.FUSE, it) }
            } catch (e: Throwable) {
                output.recycle()
                throw e
            }
            if (!done) {
                output.recycle()
                throw CancellationException()
            }
            return HdrOutput(output, input.profile, input.uris[alignment.reference], alignment, scale)
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    /** One frame, upright, exactly [width] x [height]: RAW as half floats, everything else at 8 bits. */
    private fun decode(context: Context, uri: Uri, frame: HdrFrame, profile: ColorProfile, width: Int, height: Int): Bitmap {
        val mime = context.contentResolver.getType(uri)
        val name = ImageLoader.queryName(context, uri)
        val longEdge = max(width, height)
        val decoded: Bitmap
        val base: BaseOrientation
        when {
            frame.raw -> {
                val info = RawImage.probe(context, uri)
                // Half size skips demosaicing: plenty when the merge is that small anyway.
                val half = max(frame.width, frame.height) >= 2 * longEdge
                // The camera's exposure is kept: auto brightness would undo the bracketing.
                decoded = RawImage.develop(context, uri, halfSize = half, autoBright = false)
                base = BaseOrientation.fromExif(info.exifOrientation)
            }
            ImageCodecs.isJxl(mime, name) -> {
                val (bitmap, own) = ImageCodecs.decodeJxl(context, uri)
                decoded = if (own == profile) bitmap else convert(bitmap, profile)
                base = BaseOrientation()
            }
            else -> {
                val sample = ImageLoader.sampleSizeFor(max(frame.width, frame.height), longEdge)
                decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inPreferredColorSpace = ColorSpaces.of(profile)
                    })
                } ?: throw IOException("Cannot decode $uri")
                base = ImageLoader.baseOrientation(context, uri)
            }
        }
        val upright = ImageLoader.oriented(decoded, base)
        if (upright !== decoded) decoded.recycle()
        if (upright.width == width && upright.height == height) return upright
        val sized = Bitmap.createScaledBitmap(upright, width, height, true)
        if (sized !== upright) upright.recycle()
        return sized
    }

    /** Redraws [bitmap] in [profile]; Android converts the colors. */
    private fun convert(bitmap: Bitmap, profile: ColorProfile): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888, false, ColorSpaces.of(profile))
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
        return out
    }

    @JvmStatic private external fun nativeAlign(frames: Array<Bitmap>, align: Boolean, progress: HdrProgress): IntArray?
    @JvmStatic private external fun nativeFuse(
        frames: Array<Bitmap>, alignment: IntArray, deghost: Boolean, output: Bitmap, progress: HdrProgress,
    ): Boolean
}
