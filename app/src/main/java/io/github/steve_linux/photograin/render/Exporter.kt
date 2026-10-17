// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.opengl.GLES30
import io.github.steve_linux.photograin.data.ColorSpaces
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.PxSize
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
     * Largest enlarged output, in pixels: the whole photo is held in memory as one bitmap
     * before encoding (4 bytes a pixel, so about 190 MB).
     */
    const val MAX_UPSCALED_PIXELS = 48_000_000L

    /**
     * Output size for [edit] when the long edge is limited to [maxLongEdge] (null = full size)
     * or the photo is enlarged [upscale] times, up to [MAX_UPSCALED_PIXELS].
     */
    fun outputSize(source: PxSize, base: BaseOrientation, edit: EditState, maxLongEdge: Int?, upscale: Int = 1): PxSize {
        val full = GeometryMapper(source.width, source.height, base, edit.geometry).outputSize
        val scale = scaleFor(full, maxLongEdge, upscale)
        return PxSize(max(1, (full.width * scale).roundToInt()), max(1, (full.height * scale).roundToInt()))
    }

    /** Whether [upscale] had to be reduced to stay within [MAX_UPSCALED_PIXELS]. */
    fun upscaleLimited(source: PxSize, base: BaseOrientation, edit: EditState, upscale: Int): Boolean {
        val full = GeometryMapper(source.width, source.height, base, edit.geometry).outputSize
        return upscale > 1 && scaleFor(full, null, upscale) < upscale
    }

    private fun scaleFor(full: PxSize, maxLongEdge: Int?, upscale: Int): Double {
        if (upscale > 1) {
            val pixels = full.width.toDouble() * full.height
            return max(1.0, min(upscale.toDouble(), sqrt(MAX_UPSCALED_PIXELS / pixels)))
        }
        return if (maxLongEdge == null || full.longEdge <= maxLongEdge) 1.0 else maxLongEdge.toDouble() / full.longEdge
    }

    fun render(
        source: RegionSource,
        base: BaseOrientation,
        edit: EditState,
        lut: CubeLut?,
        maxLongEdge: Int?,
        output: ColorProfile,
        upscale: Int = 1,
        onProgress: (Float) -> Unit,
    ): Bitmap {
        val mapper = GeometryMapper(source.width, source.height, base, edit.geometry)
        val full = mapper.outputSize
        val scale = scaleFor(full, maxLongEdge, upscale)
        val out = outputSize(PxSize(source.width, source.height), base, edit, maxLongEdge, upscale)
        // Enlarging needs a sharper filter than bilinear; a 1:1 export takes pixels as they are.
        val cubic = scale > 1.01
        // Output pixel -> source pixel, including the export scale.
        val outToSource = Affine.scale(full.width.toDouble() / out.width, full.height.toDouble() / out.height)
            .then(mapper.outputToSource())

        // The bitmaps are tagged with the output space so the encoder embeds its ICC profile. Tiles and
        // result share it, so drawing one onto the other copies the pixels without converting them.
        val colorSpace = ColorSpaces.of(output)
        val result = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888, false, colorSpace)
        val canvas = Canvas(result)

        OffscreenGl().use {
            val pipeline = Pipeline()
            try {
                val margin = ShaderParams.tileMarginPx(out.longEdge)
                val maxTex = pipeline.maxTextureSize
                // Decoding at a lower sample size is enough when the export itself is downscaled.
                var sample = 1
                while (sample * 2 <= 1.0 / scale) sample *= 2
                var tile = TILE
                while (tile > 256 && regionSpan(tile, margin, scale, sample) > maxTex) tile /= 2

                val cols = ceil(out.width / tile.toDouble()).toInt()
                val rows = ceil(out.height / tile.toDouble()).toInt()
                val tileBitmaps = HashMap<Long, Bitmap>()
                val pixels = ByteBuffer.allocateDirect(tile * tile * 4).order(ByteOrder.nativeOrder())
                var sourceTexture = 0
                val target = RenderTargetHolder()

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
                    val bitmap = source.decode(region, sample)
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
                    )

                    pixels.clear()
                    GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
                    GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
                    val tileBitmap = tileBitmaps.getOrPut((tw.toLong() shl 32) or th.toLong()) {
                        Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888, false, colorSpace)
                    }
                    // A raw copy: setPixels() would treat the values as sRGB and convert them.
                    pixels.position(0)
                    tileBitmap.copyPixelsFromBuffer(pixels)
                    canvas.drawBitmap(tileBitmap, tx.toFloat(), ty.toFloat(), null)
                    onProgress((row * cols + col + 1f) / (rows * cols))
                }
                GlUtil.deleteTexture(sourceTexture)
                target.release()
                tileBitmaps.values.forEach(Bitmap::recycle)
            } finally {
                pipeline.release()
            }
        }
        return result
    }

    /** Pixel span of the decoded region for a tile, in the worst (45 degree) case. */
    private fun regionSpan(tile: Int, margin: Int, scale: Double, sample: Int): Double =
        (tile + 2.0 * margin) / scale / sample * 1.4143 + 4

    /** Source rectangle covering the output block, padded for the filter's reach and clamped. */
    private fun sourceBounds(m: Affine, x: Int, y: Int, w: Int, h: Int, srcW: Int, srcH: Int): Rect {
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
private class RenderTargetHolder {
    private var target: RenderTarget? = null

    fun bind(w: Int, h: Int) {
        val t = target?.takeIf { it.matches(w, h) } ?: run {
            target?.release()
            RenderTarget(w, h, mipmap = false, halfFloat = false).also { target = it }
        }
        t.bind()
    }

    fun release() {
        target?.release()
        target = null
    }
}
