// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.opengl.GLES30
import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.Mat3
import io.github.steve_linux.photograin.model.PxSize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max

/**
 * The GPU pipeline shared by the live preview and the export:
 *
 *  1. geometry: source texture -> rotated/straightened/cropped image (mipmapped)
 *  2. blur: two separable passes on a downsampled copy (local luminance for
 *     shadows/highlights, clarity and dehaze)
 *  3. color: every global adjustment, to the screen or to an export tile
 *
 * Steps 1 and 2 only depend on geometry, so moving a color slider re-runs step 3 alone.
 * Must be created, used and released on the thread that owns the GL context.
 */
internal class Pipeline {
    private val quad: Int
    private val geometryProgram = GlProgram(Shaders.VERTEX, Shaders.GEOMETRY)
    private val blurProgram = GlProgram(Shaders.VERTEX, Shaders.BLUR)
    private val colorProgram = GlProgram(Shaders.VERTEX, Shaders.COLOR)
    private val halfFloat = GlUtil.hasExtension("GL_EXT_color_buffer_half_float") ||
        GlUtil.hasExtension("GL_EXT_color_buffer_float")

    val maxTextureSize = GlUtil.maxTextureSize()

    var geo: RenderTarget? = null
        private set
    private var blurA: RenderTarget? = null
    private var blurB: RenderTarget? = null
    private var curveTexture = 0
    private var uploadedCurves: Curves? = null
    private var lutTexture = 0
    private var uploadedLut: CubeLut? = null
    private var brushTexture = 0
    private var brushTextureSize: PxSize? = null
    private val brushRasters = arrayOfNulls<BrushRaster>(LocalAdjustment.MAX)
    private var brushUpload: ByteBuffer? = null

    init {
        val vertices = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(vertices).also { it.position(0) }
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        quad = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Renders steps 1 and 2 for a [width] x [height] block of output pixels starting at
     * ([originX], [originY]). [outputToUv] maps output pixels to UVs of [sourceTexture].
     * [longEdgePx] is the long edge of the whole output at this resolution. With
     * [cubicSize] (the texture's size) the source is sampled with Catmull-Rom, for enlarging.
     */
    fun renderIntermediate(
        sourceTexture: Int,
        outputToUv: Homography,
        originX: Float,
        originY: Float,
        width: Int,
        height: Int,
        longEdgePx: Int,
        cubicSize: PxSize? = null,
    ) {
        val geoTarget = geo?.takeIf { it.matches(width, height) } ?: run {
            geo?.release()
            RenderTarget(width, height, mipmap = true, halfFloat = halfFloat).also { geo = it }
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        geoTarget.bind()
        geometryProgram.use()
        geometryProgram.texture("uTex", 0, sourceTexture)
        geometryProgram.mat3("uM", outputToUv.toGlMat3())
        geometryProgram.int("uCubic", if (cubicSize != null) 1 else 0)
        geometryProgram.vec2("uTexSize", (cubicSize?.width ?: 1).toFloat(), (cubicSize?.height ?: 1).toFloat())
        drawQuad(geometryProgram, FULL_CLIP, floatArrayOf(originX, originY, width.toFloat(), height.toFloat()))
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, geoTarget.texture)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        val sigma = ShaderParams.localSigmaPx(longEdgePx)
        val down = ShaderParams.blurDownsample(sigma)
        val bw = max(1, ceil(width / down.toDouble()).toInt())
        val bh = max(1, ceil(height / down.toDouble()).toInt())
        val a = blurA?.takeIf { it.matches(bw, bh) } ?: run {
            blurA?.release()
            RenderTarget(bw, bh, mipmap = false, halfFloat = halfFloat).also { blurA = it }
        }
        val b = blurB?.takeIf { it.matches(bw, bh) } ?: run {
            blurB?.release()
            RenderTarget(bw, bh, mipmap = false, halfFloat = halfFloat).also { blurB = it }
        }

        blurProgram.use()
        blurProgram.float("uSigma", (sigma / down).toFloat())

        a.bind()
        blurProgram.texture("uTex", 0, geoTarget.texture)
        blurProgram.float("uLod", log2(down.toFloat()))
        blurProgram.vec2("uStep", down.toFloat() / width, 0f)
        drawQuad(blurProgram, FULL_CLIP, UNIT_RECT)

        b.bind()
        blurProgram.texture("uTex", 0, a.texture)
        blurProgram.float("uLod", 0f)
        blurProgram.vec2("uStep", 0f, 1f / bh)
        drawQuad(blurProgram, FULL_CLIP, UNIT_RECT)
    }

    /**
     * Runs step 3 into the currently bound framebuffer and viewport.
     *
     * @param clipRect where to draw, in clip space (x0, y0, x1, y1); y0 receives the image top.
     * @param geoUvRect which part of the geometry texture to draw (origin.xy, size.zw).
     * @param imageMap maps geometry UVs to positions in the whole output (origin.xy, size.zw).
     * @param lut the table for [Adjustments.look], or null when it is off or not available.
     * @param working the color space of the source pixels, which the adjustments work in.
     * @param output the color space to write: sRGB for the screen, the chosen profile for export.
     * @param masks local adjustments; [posToSource] maps output positions (0..1) to source
     *   positions (0..1), and [sourceWidth] x [sourceHeight] gives the source's shape.
     * @param showMask index of the mask to tint red, or -1.
     */
    fun renderColor(
        adjustments: Adjustments,
        lut: CubeLut?,
        clipRect: FloatArray,
        geoUvRect: FloatArray,
        imageMap: FloatArray,
        outputWidth: Int,
        outputHeight: Int,
        compareSplit: Float,
        composite: Boolean,
        working: ColorProfile = ColorProfile.SRGB,
        output: ColorProfile = ColorProfile.SRGB,
        background: FloatArray = floatArrayOf(0f, 0f, 0f),
        masks: List<LocalAdjustment> = emptyList(),
        posToSource: Homography = Homography.IDENTITY,
        sourceWidth: Int = 1,
        sourceHeight: Int = 1,
        showMask: Int = -1,
    ) {
        val geoTarget = checkNotNull(geo) { "renderIntermediate() must run first" }
        val blurred = checkNotNull(blurB)
        val p = colorProgram
        p.use()
        p.texture("uGeo", 0, geoTarget.texture)
        p.texture("uBlur", 1, blurred.texture)
        p.vec2("uGeoTexel", 1f / geoTarget.width, 1f / geoTarget.height)
        p.vec4("uImgMap", imageMap)
        val long = max(outputWidth, outputHeight).toFloat()
        p.vec2("uAspect", outputWidth / long, outputHeight / long)
        p.float("uLongEdgePx", long)

        val wb = ShaderParams.whiteBalanceGains(adjustments)
        p.vec3("uWb", wb[0], wb[1], wb[2])
        p.float("uExposure", adjustments.exposure)
        p.float("uContrast", adjustments.contrast)
        p.float("uHighlights", adjustments.highlights)
        p.float("uShadows", adjustments.shadows)
        p.float("uWhites", adjustments.whites)
        p.float("uBlacks", adjustments.blacks)
        p.float("uVibrance", adjustments.vibrance)
        p.float("uSaturation", adjustments.saturation)
        p.float("uSharpness", adjustments.sharpness)
        p.float("uClarity", adjustments.clarity)
        p.float("uDehaze", adjustments.dehaze)
        p.float("uVignette", adjustments.vignette)
        p.float("uGrain", adjustments.grain)
        p.float("uGrainCell", ShaderParams.grainCellFraction(adjustments))
        p.float("uLumaNr", adjustments.noiseReduction)
        p.float("uChromaNr", adjustments.colorNoiseReduction)
        val curvesOn = !adjustments.curves.isIdentity
        p.float("uCurvesOn", if (curvesOn) 1f else 0f)
        p.texture("uCurve", 2, curveLut(adjustments.curves))
        val hslOn = !adjustments.hsl.isIdentity
        p.float("uHslOn", if (hslOn) 1f else 0f)
        if (hslOn) p.vec3Array("uHsl", ShaderParams.hslUniform(adjustments))
        val lutOn = lut != null && adjustments.look.isActive
        p.float("uLutOn", if (lutOn) 1f else 0f)
        // The sampler must always point at a 3D texture, even when unused.
        val lutTex = lutTexture(if (lutOn) lut else uploadedLut)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        p.int("uLut", 3)
        if (lutOn) {
            val n = lut!!.size.toFloat()
            p.float("uLutAmount", adjustments.look.amount.coerceIn(0f, 1f))
            p.float("uLutScale", (n - 1f) / n)
            p.float("uLutOffset", 0.5f / n)
        }
        val luma = working.luma
        p.vec3("uLuma", luma[0].toFloat(), luma[1].toFloat(), luma[2].toFloat())
        val lutConvert = lutOn && working != ColorProfile.SRGB
        p.float("uLutConvert", if (lutConvert) 1f else 0f)
        if (lutConvert) {
            p.mat3("uToSrgb", Mat3.toGl(working.conversionTo(ColorProfile.SRGB)))
            p.mat3("uFromSrgb", Mat3.toGl(ColorProfile.SRGB.conversionTo(working)))
        }
        val outConvert = working != output
        p.float("uOutConvert", if (outConvert) 1f else 0f)
        if (outConvert) {
            p.mat3("uOutM", Mat3.toGl(working.conversionTo(output)))
            p.float("uOutGamma", output.gamma.toFloat())
        }
        val mu = ShaderParams.maskUniforms(masks)
        p.int("uMaskCount", mu.count)
        val shown = if (showMask in 0 until mu.count) showMask else -1
        p.int("uShowMask", shown)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, brushTexture(masks, sourceWidth, sourceHeight))
        p.int("uBrush", 4)
        if (mu.count > 0 || shown >= 0) {
            p.mat3("uPosToSrc", posToSource.toGlMat3())
            val srcLong = max(sourceWidth, sourceHeight).toFloat()
            p.vec2("uSrcAspect", sourceWidth / srcLong, sourceHeight / srcLong)
            p.vec4Array("uMaskGeo", mu.geo)
            p.vec4Array("uMaskOpt", mu.opt)
            p.vec4Array("uMaskRange", mu.range)
            p.vec4Array("uLocA", mu.locA)
            p.vec4Array("uLocB", mu.locB)
        }
        p.float("uCompare", compareSplit)
        p.float("uComposite", if (composite) 1f else 0f)
        p.vec3("uBg", background[0], background[1], background[2])
        drawQuad(p, clipRect, geoUvRect)
    }

    /** 256x1 lookup texture for [curves], re-uploaded only when the curves change. */
    private fun curveLut(curves: Curves): Int {
        if (curveTexture != 0 && curves == uploadedCurves) return curveTexture
        if (curveTexture == 0) curveTexture = GlUtil.genTexture()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curveTexture)
        GlUtil.setSampling(mipmap = false)
        val data = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder()).put(curves.toLut())
        data.position(0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 256, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data,
        )
        uploadedCurves = curves
        return curveTexture
    }

    /**
     * 3D texture for [lut], re-uploaded only when it changes. Half-float storage keeps smooth
     * gradients free of banding and is filterable on every GLES 3.0 device (and WebGL 2).
     * With no LUT it holds a 2x2x2 identity so the sampler stays valid.
     */
    private fun lutTexture(lut: CubeLut?): Int {
        if (lutTexture != 0 && (lut == null || lut === uploadedLut)) return lutTexture
        if (lutTexture == 0) lutTexture = GlUtil.genTexture()
        val table = lut ?: IDENTITY_LUT
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
        for (param in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R)) {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, param, GLES30.GL_CLAMP_TO_EDGE)
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        val data = ByteBuffer.allocateDirect(table.data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(table.data).position(0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F, table.size, table.size, table.size, 0,
            GLES30.GL_RGB, GLES30.GL_FLOAT, data,
        )
        uploadedLut = lut
        return lutTexture
    }

    /**
     * Texture array with one layer per mask index, holding the rasters of the brush masks.
     * Layers are re-uploaded only when their strokes change. Without brush masks it is a
     * 1x1 texture, so the sampler stays valid.
     */
    private fun brushTexture(masks: List<LocalAdjustment>, sourceWidth: Int, sourceHeight: Int): Int {
        val hasBrush = masks.take(LocalAdjustment.MAX).any { it.mask.type == MaskType.BRUSH }
        val size = if (hasBrush) {
            BrushRaster.size(sourceWidth, sourceHeight)
        } else {
            brushTextureSize ?: PxSize(1, 1)
        }
        if (brushTexture == 0 || size != brushTextureSize) {
            if (brushTexture == 0) brushTexture = GlUtil.genTexture()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, brushTexture)
            for (param in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T)) {
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, param, GLES30.GL_CLAMP_TO_EDGE)
            }
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R8, size.width, size.height, LocalAdjustment.MAX, 0,
                GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null,
            )
            brushTextureSize = size
            brushRasters.fill(null)
        }
        if (!hasBrush) return brushTexture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, brushTexture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        masks.take(LocalAdjustment.MAX).forEachIndexed { i, local ->
            if (local.mask.type != MaskType.BRUSH) return@forEachIndexed
            // A new raster is uploaded even when empty: the layer may hold another photo's paint.
            val created = brushRasters[i] == null
            val raster = brushRasters[i] ?: BrushRaster(sourceWidth, sourceHeight).also { brushRasters[i] = it }
            if (raster.sync(local.mask.strokes) || created) {
                val buffer = brushUpload?.takeIf { it.capacity() == raster.data.size }
                    ?: ByteBuffer.allocateDirect(raster.data.size).also { brushUpload = it }
                buffer.clear()
                buffer.put(raster.data).position(0)
                GLES30.glTexSubImage3D(
                    GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, raster.width, raster.height, 1,
                    GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buffer,
                )
            }
        }
        return brushTexture
    }

    private fun drawQuad(program: GlProgram, clipRect: FloatArray, srcRect: FloatArray) {
        program.vec4("uDst", clipRect)
        program.vec4("uSrc", srcRect)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        geo?.release(); geo = null
        blurA?.release(); blurA = null
        blurB?.release(); blurB = null
        GlUtil.deleteTexture(curveTexture); curveTexture = 0
        GlUtil.deleteTexture(lutTexture); lutTexture = 0
        GlUtil.deleteTexture(brushTexture); brushTexture = 0
        brushTextureSize = null
        brushRasters.fill(null)
        uploadedLut = null
        geometryProgram.release()
        blurProgram.release()
        colorProgram.release()
        GLES30.glDeleteBuffers(1, intArrayOf(quad), 0)
    }

    companion object {
        /** Whole viewport; y0 = -1 so the image top lands in buffer row 0. */
        val FULL_CLIP = floatArrayOf(-1f, -1f, 1f, 1f)
        val UNIT_RECT = floatArrayOf(0f, 0f, 1f, 1f)
        private val IDENTITY_LUT = CubeLut(null, 2, floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
    }
}

/** A linked program with cached uniform locations. */
internal class GlProgram(vertex: String, fragment: String) {
    private val id = GlUtil.compileProgram(vertex, fragment)
    private val locations = HashMap<String, Int>()

    fun use() = GLES30.glUseProgram(id)

    private fun loc(name: String) = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun float(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun int(name: String, v: Int) = GLES30.glUniform1i(loc(name), v)
    fun vec2(name: String, x: Float, y: Float) = GLES30.glUniform2f(loc(name), x, y)
    fun vec3(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(loc(name), x, y, z)
    fun vec4(name: String, v: FloatArray) = GLES30.glUniform4f(loc(name), v[0], v[1], v[2], v[3])
    fun vec3Array(name: String, v: FloatArray) = GLES30.glUniform3fv(loc(name), v.size / 3, v, 0)
    fun vec4Array(name: String, v: FloatArray) = GLES30.glUniform4fv(loc(name), v.size / 4, v, 0)
    fun mat3(name: String, m: FloatArray) = GLES30.glUniformMatrix3fv(loc(name), 1, false, m, 0)

    fun texture(name: String, unit: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(loc(name), unit)
    }

    fun release() = GLES30.glDeleteProgram(id)
}
