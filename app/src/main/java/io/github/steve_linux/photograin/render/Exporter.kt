// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.opengl.GLES30
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.BaseOrientation
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

/**
 * Renders an edit at full (or reduced) resolution, tile by tile, so images far larger than
 * the GPU's texture limit can be exported. Each tile decodes only the part of the original
 * it needs. Blocking: call from a background thread.
 */
object Exporter {
    private const val TILE = 1024

    /** Output size for [edit] when the long edge is limited to [maxLongEdge] (null = full size). */
    fun outputSize(source: PxSize, base: BaseOrientation, edit: EditState, maxLongEdge: Int?): PxSize {
        val full = GeometryMapper(source.width, source.height, base, edit.geometry).outputSize
        val scale = scaleFor(full, maxLongEdge)
        return PxSize(max(1, (full.width * scale).roundToInt()), max(1, (full.height * scale).roundToInt()))
    }

    private fun scaleFor(full: PxSize, maxLongEdge: Int?): Double =
        if (maxLongEdge == null || full.longEdge <= maxLongEdge) 1.0 else maxLongEdge.toDouble() / full.longEdge

    fun render(
        source: RegionSource,
        base: BaseOrientation,
        edit: EditState,
        maxLongEdge: Int?,
        onProgress: (Float) -> Unit,
    ): Bitmap {
        val mapper = GeometryMapper(source.width, source.height, base, edit.geometry)
        val full = mapper.outputSize
        val scale = scaleFor(full, maxLongEdge)
        val out = outputSize(PxSize(source.width, source.height), base, edit, maxLongEdge)
        // Output pixel -> source pixel, including the export scale.
        val outToSource = Affine.scale(full.width.toDouble() / out.width, full.height.toDouble() / out.height)
            .then(mapper.outputToSource())

        val result = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
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
                val tileBitmap = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
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
                    bitmap.recycle()

                    val toUv = outToSource
                        .then(Affine.translate(-region.left.toDouble(), -region.top.toDouble()))
                        .then(Affine.scale(1.0 / coveredW, 1.0 / coveredH))
                    pipeline.renderIntermediate(sourceTexture, toUv, gx.toFloat(), gy.toFloat(), gw, gh, out.longEdge)

                    target.bind(tw, th)
                    pipeline.renderColor(
                        adjustments = edit.adjustments,
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
                    )

                    pixels.clear()
                    GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
                    GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
                    copyTile(pixels, tw, th, tileBitmap)
                    canvas.drawBitmap(tileBitmap, Rect(0, 0, tw, th), Rect(tx, ty, tx + tw, ty + th), null)
                    onProgress((row * cols + col + 1f) / (rows * cols))
                }
                GlUtil.deleteTexture(sourceTexture)
                target.release()
                tileBitmap.recycle()
            } finally {
                pipeline.release()
            }
        }
        return result
    }

    /** Copies the top-left [w] x [h] of tightly packed RGBA [pixels] into [dst]. */
    private fun copyTile(pixels: ByteBuffer, w: Int, h: Int, dst: Bitmap) {
        val ints = IntArray(w * h)
        pixels.position(0)
        for (i in 0 until w * h) {
            val r = pixels.get().toInt() and 0xff
            val g = pixels.get().toInt() and 0xff
            val b = pixels.get().toInt() and 0xff
            pixels.get()
            ints[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        dst.setPixels(ints, 0, w, 0, 0, w, h)
    }

    /** Pixel span of the decoded region for a tile, in the worst (45 degree) case. */
    private fun regionSpan(tile: Int, margin: Int, scale: Double, sample: Int): Double =
        (tile + 2.0 * margin) / scale / sample * 1.4143 + 4

    /** Source rectangle covering the output block, padded for bilinear filtering and clamped. */
    private fun sourceBounds(m: Affine, x: Int, y: Int, w: Int, h: Int, srcW: Int, srcH: Int): Rect {
        val corners = listOf(
            m.apply(Vec2(x.toDouble(), y.toDouble())),
            m.apply(Vec2((x + w).toDouble(), y.toDouble())),
            m.apply(Vec2(x.toDouble(), (y + h).toDouble())),
            m.apply(Vec2((x + w).toDouble(), (y + h).toDouble())),
        )
        val l = floor(corners.minOf { it.x }).toInt() - 2
        val t = floor(corners.minOf { it.y }).toInt() - 2
        val r = ceil(corners.maxOf { it.x }).toInt() + 2
        val b = ceil(corners.maxOf { it.y }).toInt() + 2
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
