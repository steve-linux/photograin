// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.opengl.GLES30
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.model.Adjustments
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
     * [longEdgePx] is the long edge of the whole output at this resolution.
     */
    fun renderIntermediate(
        sourceTexture: Int,
        outputToUv: Affine,
        originX: Float,
        originY: Float,
        width: Int,
        height: Int,
        longEdgePx: Int,
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
     */
    fun renderColor(
        adjustments: Adjustments,
        clipRect: FloatArray,
        geoUvRect: FloatArray,
        imageMap: FloatArray,
        outputWidth: Int,
        outputHeight: Int,
        compareSplit: Float,
        composite: Boolean,
        background: FloatArray = floatArrayOf(0f, 0f, 0f),
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
        p.float("uCompare", compareSplit)
        p.float("uComposite", if (composite) 1f else 0f)
        p.vec3("uBg", background[0], background[1], background[2])
        drawQuad(p, clipRect, geoUvRect)
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
        geometryProgram.release()
        blurProgram.release()
        colorProgram.release()
        GLES30.glDeleteBuffers(1, intArrayOf(quad), 0)
    }

    companion object {
        /** Whole viewport; y0 = -1 so the image top lands in buffer row 0. */
        val FULL_CLIP = floatArrayOf(-1f, -1f, 1f, 1f)
        val UNIT_RECT = floatArrayOf(0f, 0f, 1f, 1f)
    }
}

/** A linked program with cached uniform locations. */
internal class GlProgram(vertex: String, fragment: String) {
    private val id = GlUtil.compileProgram(vertex, fragment)
    private val locations = HashMap<String, Int>()

    fun use() = GLES30.glUseProgram(id)

    private fun loc(name: String) = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun float(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun vec2(name: String, x: Float, y: Float) = GLES30.glUniform2f(loc(name), x, y)
    fun vec3(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(loc(name), x, y, z)
    fun vec4(name: String, v: FloatArray) = GLES30.glUniform4f(loc(name), v[0], v[1], v[2], v[3])
    fun mat3(name: String, m: FloatArray) = GLES30.glUniformMatrix3fv(loc(name), 1, false, m, 0)

    fun texture(name: String, unit: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(loc(name), unit)
    }

    fun release() = GLES30.glDeleteProgram(id)
}
