// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.opengl.GLES30
import io.github.steve_linux.photograin.data.AiUpscaleSource
import io.github.steve_linux.photograin.data.ColorSpaces
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.HalfFloats
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.data.Retoucher
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.math.then
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.FrameStyle
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.SubjectMask
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders an edit at full (or reduced) resolution, tile by tile, so images far larger than
 * the GPU's texture limit can be exported. Each tile decodes only the part of the original
 * it needs. Blocking: call from a background thread.
 */
object Exporter {
    private const val TILE = 1024

    /**
     * Larger tiles for AI enlargement: every tile also runs the model on its margin, which at
     * 1024 px roughly doubles the work.
     */
    private const val AI_TILE = 2048

    /**
     * Largest enlarged output, in pixels: the whole photo is held in memory as one bitmap
     * before encoding (4 bytes a pixel, so about 190 MB).
     */
    const val MAX_UPSCALED_PIXELS = 48_000_000L

    /** Size of the exported file: the photo (see [imageSize]) plus its frame. */
    fun outputSize(source: PxSize, base: BaseOrientation, edit: EditState, maxLongEdge: Int?, upscale: Int = 1): PxSize =
        edit.frame.layout(imageSize(source, base, edit, maxLongEdge, upscale)).size

    /**
     * Size of the photo itself for [edit] when the long edge is limited to [maxLongEdge] (null =
     * full size) or the photo is enlarged [upscale] times, up to [MAX_UPSCALED_PIXELS].
     */
    fun imageSize(source: PxSize, base: BaseOrientation, edit: EditState, maxLongEdge: Int?, upscale: Int = 1): PxSize {
        val full = GeometryMapper(source.width, source.height, base, edit.geometry).outputSize
        val scale = scaleFor(full, maxLongEdge, upscale)
        return PxSize(max(1, (full.width * scale).roundToInt()), max(1, (full.height * scale).roundToInt()))
    }

    /** Whether [upscale] had to be reduced to stay within [MAX_UPSCALED_PIXELS]. */
    fun upscaleLimited(source: PxSize, base: BaseOrientation, edit: EditState, upscale: Int): Boolean {
        val full = GeometryMapper(source.width, source.height, base, edit.geometry).outputSize
        return upscale > 1 && scaleFor(full, null, upscale) < upscale
    }

    /**
     * The source to render and the options to render it with: for an AI enlargement, [source]
     * already enlarged by Real-ESRGAN, rendered at its own size.
     */
    fun prepare(
        context: Context,
        source: RegionSource,
        base: BaseOrientation,
        edit: EditState,
        options: ExportOptions,
    ): Pair<RegionSource, ExportOptions> {
        if (!options.aiUpscale || options.upscale <= 1) return source to options
        val full = GeometryMapper(source.width, source.height, base, edit.geometry).outputSize
        val factor = scaleFor(full, null, options.upscale)
        if (factor <= 1.0) return source to options
        return AiUpscaleSource(context, source, factor) to options.copy(upscale = 1, maxLongEdge = null)
    }

    private fun scaleFor(full: PxSize, maxLongEdge: Int?, upscale: Int): Double {
        if (upscale > 1) {
            val pixels = full.width.toDouble() * full.height
            return max(1.0, min(upscale.toDouble(), sqrt(MAX_UPSCALED_PIXELS / pixels)))
        }
        return if (maxLongEdge == null || full.longEdge <= maxLongEdge) 1.0 else maxLongEdge.toDouble() / full.longEdge
    }

    /**
     * Renders [edit] of [source]. [subject] is the photo's subject for subject masks (see
     * SubjectSegmenter.forEdit). With [opaque], for formats without transparency, what a
     * cutout leaves transparent turns white. [inpaint] fills the object removal spots (see
     * ObjectRemover.with); without it they are left out.
     */
    fun render(
        source: RegionSource,
        base: BaseOrientation,
        edit: EditState,
        lut: CubeLut?,
        maxLongEdge: Int?,
        output: ColorProfile,
        upscale: Int = 1,
        deep: Boolean = false,
        subject: SubjectMask? = null,
        opaque: Boolean = false,
        inpaint: Retoucher.Inpaint? = null,
        onProgress: (Float) -> Unit,
    ): Bitmap {
        val mapper = GeometryMapper(source.width, source.height, base, edit.geometry)
        val full = mapper.outputSize
        val scale = scaleFor(full, maxLongEdge, upscale)
        val out = imageSize(PxSize(source.width, source.height), base, edit, maxLongEdge, upscale)
        val layout = edit.frame.layout(out)
        // Enlarging needs a sharper filter than bilinear; a 1:1 export takes pixels as they are.
        val cubic = scale > 1.01
        // Output pixel -> source pixel, including the export scale.
        val outToSource = Affine.scale(full.width.toDouble() / out.width, full.height.toDouble() / out.height)
            .then(mapper.outputToSource())

        // The bitmaps are tagged with the output space so the encoder embeds its ICC profile. Tiles and
        // result share it, so drawing one onto the other copies the pixels without converting them.
        val colorSpace = ColorSpaces.of(output)
        return OffscreenGl().use {
            val pipeline = Pipeline()
            // A 16-bit export keeps the half-float precision the pipeline works in, where the GPU can render it.
            val halfFloat = deep && pipeline.halfFloatTargets
            val config = if (halfFloat) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
            val bytesPerPixel = if (halfFloat) 8 else 4
            val result = Bitmap.createBitmap(layout.size.width, layout.size.height, config, false, colorSpace)
            val canvas = Canvas(result)
            // Colors given in sRGB are converted to the bitmap's space by the canvas.
            if (edit.frame.style != FrameStyle.NONE) {
                canvas.drawColor(edit.frame.color)
            } else if (opaque) {
                canvas.drawColor(Color.WHITE)
            }
            try {
                val margin = ShaderParams.tileMarginPx(out.longEdge)
                val maxTex = pipeline.maxTextureSize
                // Decoding at a lower sample size is enough when the export itself is downscaled.
                var sample = 1
                while (sample * 2 <= 1.0 / scale) sample *= 2
                var tile = if (source is AiUpscaleSource) AI_TILE else TILE
                val stretch = mapper.perspectiveStretch()
                while (tile > 256 && regionSpan(tile, margin, scale, sample) * stretch > maxTex) tile /= 2

                val cols = ceil(out.width / tile.toDouble()).toInt()
                val rows = ceil(out.height / tile.toDouble()).toInt()
                val tileBitmaps = HashMap<Long, Bitmap>()
                val pixels = ByteBuffer.allocateDirect(tile * tile * bytesPerPixel).order(ByteOrder.nativeOrder())
                val floats = if (halfFloat) ByteBuffer.allocateDirect(tile * tile * 16).order(ByteOrder.nativeOrder()) else null
                var sourceTexture = 0
                val target = RenderTargetHolder(halfFloat)
                // Spots crossing several tiles are worked out once.
                val patches = HashMap<RetouchSpot, Retoucher.Patch>()

                for (row in 0 until rows) for (col in 0 until cols) {
                    val tx = col * tile
                    val ty = row * tile
                    val tw = min(tile, out.width - tx)
                    val th = min(tile, out.height - ty)
                    val gx = tx - margin
                    val gy = ty - margin
                    val gw = tw + 2 * margin
                    val gh = th + 2 * margin

                    val region = sourceBounds(outToSource, gx, gy, gw, gh, source.width, source.height)
                    val bitmap = retouched(source.decode(region, sample), region, sample, source, edit, patches, inpaint)
                    sourceTexture = GlUtil.uploadBitmap(bitmap, sourceTexture)
                    val coveredW = bitmap.width * sample.toDouble()
                    val coveredH = bitmap.height * sample.toDouble()
                    val textureSize = PxSize(bitmap.width, bitmap.height)
                    bitmap.recycle()

                    val toUv = outToSource
                        .then(Affine.translate(-region.left.toDouble(), -region.top.toDouble()))
                        .then(Affine.scale(1.0 / coveredW, 1.0 / coveredH))
                    pipeline.renderIntermediate(
                        sourceTexture, toUv, gx.toFloat(), gy.toFloat(), gw, gh, out.longEdge,
                        cubicSize = textureSize.takeIf { cubic },
                    )

                    target.bind(tw, th)
                    pipeline.renderColor(
                        adjustments = edit.adjustments,
                        lut = lut,
                        clipRect = Pipeline.FULL_CLIP,
                        geoUvRect = floatArrayOf(
                            margin.toFloat() / gw, margin.toFloat() / gh, tw.toFloat() / gw, th.toFloat() / gh,
                        ),
                        imageMap = floatArrayOf(
                            gx.toFloat() / out.width, gy.toFloat() / out.height,
                            gw.toFloat() / out.width, gh.toFloat() / out.height,
                        ),
                        outputWidth = out.width,
                        outputHeight = out.height,
                        compareSplit = -1f,
                        composite = false,
                        working = source.profile,
                        output = output,
                        masks = edit.masks,
                        posToSource = mapper.normalizedOutputToSource(),
                        sourceWidth = source.width,
                        sourceHeight = source.height,
                        subject = subject,
                    )

                    pixels.clear()
                    GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
                    if (floats != null) {
                        readHalfFloat(tw, th, pixels, floats)
                    } else {
                        GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
                    }
                    val tileBitmap = tileBitmaps.getOrPut((tw.toLong() shl 32) or th.toLong()) {
                        Bitmap.createBitmap(tw, th, config, false, colorSpace)
                    }
                    // A raw copy: setPixels() would treat the values as sRGB and convert them.
                    pixels.position(0)
                    tileBitmap.copyPixelsFromBuffer(pixels)
                    canvas.drawBitmap(tileBitmap, (tx + layout.imageLeft).toFloat(), (ty + layout.imageTop).toFloat(), null)
                    onProgress((row * cols + col + 1f) / (rows * cols))
                }
                GlUtil.deleteTexture(sourceTexture)
                target.release()
                patches.values.forEach { it.bitmap.recycle() }
                DecorPainter.drawShapes(canvas, layout.size, edit.shapes)
                DecorPainter.drawTexts(canvas, layout.size, edit.texts)
                tileBitmaps.values.forEach(Bitmap::recycle)
                result
            } catch (e: Throwable) {
                result.recycle()
                throw e
            } finally {
                pipeline.release()
            }
        }
    }

    /**
     * Reads a [w] x [h] half-float tile into [out] as half floats, the layout of an RGBA_F16
     * bitmap. Float reads are always allowed from a float buffer; half floats only when the
     * driver says so, which saves the conversion.
     */
    private fun readHalfFloat(w: Int, h: Int, out: ByteBuffer, floats: ByteBuffer) {
        val q = IntArray(2)
        GLES30.glGetIntegerv(GLES30.GL_IMPLEMENTATION_COLOR_READ_FORMAT, q, 0)
        GLES30.glGetIntegerv(GLES30.GL_IMPLEMENTATION_COLOR_READ_TYPE, q, 1)
        if (q[0] == GLES30.GL_RGBA && q[1] == GLES30.GL_HALF_FLOAT) {
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, out)
            return
        }
        floats.clear()
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_FLOAT, floats)
        val f = floats.asFloatBuffer()
        val s = out.asShortBuffer()
        for (i in 0 until w * h * 4) s.put(i, HalfFloats.fromFloat(f.get(i)))
    }

    /** [bitmap] of the source [region] with the edit's healing, clone and removal spots drawn in. */
    private fun retouched(
        bitmap: Bitmap,
        region: Rect,
        sample: Int,
        source: RegionSource,
        edit: EditState,
        patches: MutableMap<RetouchSpot, Retoucher.Patch>,
        inpaint: Retoucher.Inpaint?,
    ): Bitmap {
        if (edit.retouch.isEmpty()) return bitmap
        val target = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        Retoucher.apply(target, region, sample, source.width, source.height, edit.retouch, patches, inpaint) { rect, s ->
            source.decode(rect, s)
        }
        return target
    }

    /** Pixel span of the decoded region for a tile, in the worst (45 degree) case. */
    private fun regionSpan(tile: Int, margin: Int, scale: Double, sample: Int): Double =
        (tile + 2.0 * margin) / scale / sample * 1.4143 + 4

    /** Source rectangle covering the output block, padded for the filter's reach and clamped. */
    private fun sourceBounds(m: Homography, x: Int, y: Int, w: Int, h: Int, srcW: Int, srcH: Int): Rect {
        val corners = listOf(
            m.apply(Vec2(x.toDouble(), y.toDouble())),
            m.apply(Vec2((x + w).toDouble(), y.toDouble())),
            m.apply(Vec2(x.toDouble(), (y + h).toDouble())),
            m.apply(Vec2((x + w).toDouble(), (y + h).toDouble())),
        )
        val l = floor(corners.minOf { it.x }).toInt() - 3
        val t = floor(corners.minOf { it.y }).toInt() - 3
        val r = ceil(corners.maxOf { it.x }).toInt() + 3
        val b = ceil(corners.maxOf { it.y }).toInt() + 3
        val left = l.coerceIn(0, srcW - 1)
        val top = t.coerceIn(0, srcH - 1)
        return Rect(left, top, r.coerceIn(left + 1, srcW), b.coerceIn(top + 1, srcH))
    }
}

/** Reusable output render target for export tiles. */
private class RenderTargetHolder(private val halfFloat: Boolean) {
    private var target: RenderTarget? = null

    fun bind(w: Int, h: Int) {
        val t = target?.takeIf { it.matches(w, h) } ?: run {
            target?.release()
            RenderTarget(w, h, mipmap = false, halfFloat = halfFloat).also { target = it }
        }
        t.bind()
    }

    fun release() {
        target?.release()
        target = null
    }
}
