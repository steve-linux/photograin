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
import io.github.steve_linux.photograin.model.Subjects
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
    /** Whether render targets can hold half floats; without them every pass rounds to 8 bits. */
    val halfFloatTargets = GlUtil.hasExtension("GL_EXT_color_buffer_half_float") ||
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
        lensAmount: Float = 0f,
        lensSize: PxSize = PxSize(width, height),
    ) {
        val geoTarget = geo?.takeIf { it.matches(width, height) } ?: run {
            geo?.release()
            RenderTarget(width, height, mipmap = true, halfFloat = halfFloatTargets).also { geo = it }
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        geoTarget.bind()
        geometryProgram.use()
        geometryProgram.texture("uTex", 0, sourceTexture)
        geometryProgram.mat3("uM", outputToUv.toGlMat3())
        geometryProgram.float("uLensAmount", lensAmount.coerceIn(-1f, 1f))
        geometryProgram.vec2("uLensSize", lensSize.width.toFloat(), lensSize.height.toFloat())
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
            RenderTarget(bw, bh, mipmap = false, halfFloat = halfFloatTargets).also { blurA = it }
        }
        val b = blurB?.takeIf { it.matches(bw, bh) } ?: run {
            blurB?.release()
            RenderTarget(bw, bh, mipmap = false, halfFloat = halfFloatTargets).also { blurB = it }
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
     * @param subjects what the subject masks cover; until it is known they are empty.
     * @param showMask index of the mask to highlight, or -1.
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
        subjects: Subjects? = null,
        showMask: Int = -1,
        showExposureWarning: Boolean = false,
        histogramSample: Boolean = false,
        outputSharpen: Float = 0f,
        focusPeaking: Boolean = false,
        proof: ColorProfile? = null,
        gamutWarning: Boolean = false,
    ) {
        val geoTarget = checkNotNull(geo) { "renderIntermediate() must run first" }
        val blurred = checkNotNull(blurB)
        val p = colorProgram
        p.use()
        p.int("uExposureWarning", if (showExposureWarning) 1 else 0)
        p.int("uFocusPeaking", if (focusPeaking) 1 else 0)
        p.int("uHistogramSample", if (histogramSample) 1 else 0)
        p.texture("uGeo", 0, geoTarget.texture)
        p.texture("uBlur", 1, blurred.texture)
        p.vec2("uGeoTexel", 1f / geoTarget.width, 1f / geoTarget.height)
        p.vec4("uImgMap", imageMap)
        val long = max(outputWidth, outputHeight).toFloat()
        p.vec2("uAspect", outputWidth / long, outputHeight / long)
        p.vec2("uLensSize", outputWidth.toFloat(), outputHeight.toFloat())
        p.float("uLensAmount", adjustments.lensDistortion.coerceIn(-1f, 1f))
        p.float("uLongEdgePx", long)

        val wb = ShaderParams.whiteBalanceGains(adjustments)
        p.vec3("uWb", wb[0], wb[1], wb[2])
        p.float("uExposure", adjustments.exposure)
        // Pack global scalars to leave room for local adjustments on minimum-limit GLES 3 devices.
        p.vec4("uBasicTone", floatArrayOf(adjustments.contrast, adjustments.highlights, adjustments.shadows, adjustments.clarity))
        p.vec4("uToneDetail", floatArrayOf(adjustments.whites, adjustments.blacks, adjustments.textureAmount, adjustments.dehaze))
        p.float("uVibrance", adjustments.vibrance)
        p.float("uSaturation", adjustments.saturation)
        p.float("uSharpness", adjustments.sharpness)
        p.float("uOutputSharpen", outputSharpen.coerceIn(0f, 1f))
        p.float("uVignette", adjustments.vignette)
        p.float("uVignetteMidpoint", adjustments.vignetteMidpoint.coerceIn(0f, 1f))
        p.float("uVignetteRoundness", adjustments.vignetteRoundness.coerceIn(0f, 1f))
        p.float("uVignetteFeather", adjustments.vignetteFeather.coerceIn(0f, 1f))
        p.float("uVignetteHighlights", adjustments.vignetteHighlights.coerceIn(0f, 1f))
        p.float("uGrainRoughness", adjustments.grainRoughness.coerceIn(0f, 1f))
        p.float("uBloom", adjustments.bloom)
        p.float("uHalation", adjustments.halation)
        p.float("uMidtoneGamma", adjustments.midtoneGamma)
        p.float("uShadowContrast", adjustments.shadowContrast)
        p.float("uMidtoneContrast", adjustments.midtoneContrast)
        p.float("uHighlightContrast", adjustments.highlightContrast)
        p.float("uShadowTone", adjustments.shadowTone)
        p.float("uMidtoneTone", adjustments.midtoneTone)
        p.float("uHighlightTone", adjustments.highlightTone)
        p.float("uGlobalHue", adjustments.globalHue)
        p.float("uRedBlueSwap", adjustments.redBlueSwap)
        p.float("uSoftFocus", adjustments.softFocus)
        p.float("uMatte", adjustments.matte)
        p.float("uSepia", adjustments.sepia)
        p.float("uCyanotype", adjustments.cyanotype)
        p.float("uNegative", adjustments.negative)
        p.float("uSolarize", adjustments.solarize)
        p.float("uPosterize", adjustments.posterize)
        p.float("uThreshold", adjustments.threshold)
        p.float("uBleachBypass", adjustments.bleachBypass)
        p.float("uCrossProcess", adjustments.crossProcess)
        p.float("uOrton", adjustments.orton)
        p.float("uBrightness", adjustments.brightness)
        p.float("uShadowFloor", adjustments.shadowFloor)
        p.float("uWhiteCeiling", adjustments.whiteCeiling)
        p.float("uShadowSoftClip", adjustments.shadowSoftClip)
        p.float("uHighlightSoftClip", adjustments.highlightSoftClip)
        p.float("uLumaContrast", adjustments.lumaContrast)
        p.float("uShadowExposure", adjustments.shadowExposure)
        p.float("uHighlightExposure", adjustments.highlightExposure)
        p.vec3("uRgbGamma", adjustments.redGamma, adjustments.greenGamma, adjustments.blueGamma)
        p.vec3("uRgbGain", adjustments.redGain, adjustments.greenGain, adjustments.blueGain)
        p.vec3("uRgbOffset", adjustments.redOffset, adjustments.greenOffset, adjustments.blueOffset)
        p.float("uShadowSaturation", adjustments.shadowSaturation)
        p.float("uMidtoneSaturation", adjustments.midtoneSaturation)
        p.float("uHighlightSaturation", adjustments.highlightSaturation)
        p.float("uShadowTint", adjustments.shadowTint)
        p.float("uMidtoneTint", adjustments.midtoneTint)
        p.float("uHighlightTint", adjustments.highlightTint)
        p.float("uCycleRgb", adjustments.cycleRgb)
        p.float("uMonochromeRed", adjustments.monochromeRed)
        p.float("uMonochromeGreen", adjustments.monochromeGreen)
        p.float("uMonochromeBlue", adjustments.monochromeBlue)
        p.float("uLumaSharpen", adjustments.lumaSharpen)
        p.float("uChromaSharpen", adjustments.chromaSharpen)
        p.float("uEdgeDetect", adjustments.edgeDetect)
        p.float("uEmboss", adjustments.emboss)
        p.float("uPencilSketch", adjustments.pencilSketch)
        p.float("uHighPass", adjustments.highPass)
        p.float("uChannelInvertRed", adjustments.channelInvertRed)
        p.float("uChannelInvertGreen", adjustments.channelInvertGreen)
        p.float("uChannelInvertBlue", adjustments.channelInvertBlue)
        p.float("uLumaInvert", adjustments.lumaInvert)
        p.float("uThresholdColor", adjustments.thresholdColor)
        p.float("uPosterizeLuma", adjustments.posterizeLuma)
        p.float("uThermalMap", adjustments.thermalMap)
        p.float("uPurpleGold", adjustments.purpleGold)
        p.float("uTealOrange", adjustments.tealOrange)
        p.float("uInfraredLook", adjustments.infraredLook)
        p.float("uFilmToe", adjustments.filmToe)
        p.float("uFilmShoulder", adjustments.filmShoulder)
        p.float("uLightLeak", adjustments.lightLeak)
        p.float("uTiltShift", adjustments.tiltShift)
        p.float("uRadialBlur", adjustments.radialBlur)
        p.float("uScanlines", adjustments.scanlines)
        p.float("uDefringe", adjustments.defringe)
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
        fun sampledColor(name: String, color: Int?) {
            val rgb = color ?: 0
            val srgb = ColorProfile.SRGB
            val linear = Mat3.apply(srgb.conversionTo(working), doubleArrayOf(
                srgb.decode(((rgb ushr 16) and 255) / 255.0),
                srgb.decode(((rgb ushr 8) and 255) / 255.0),
                srgb.decode((rgb and 255) / 255.0),
            ))
            p.vec4(name, floatArrayOf(srgb.encode(linear[0]).toFloat(), srgb.encode(linear[1]).toFloat(),
                srgb.encode(linear[2]).toFloat(), if (color == null) 0f else 1f))
        }
        sampledColor("uFilmBase", adjustments.filmBase)
        sampledColor("uColorReference", adjustments.colorReference)
        sampledColor("uColorKey", adjustments.colorKey)
        p.float("uFilmExposure", adjustments.filmExposure)
        p.float("uFilmBlack", adjustments.filmBlack)
        p.float("uColorKeyTolerance", adjustments.colorKeyTolerance)
        val grading = adjustments.grading
        val tones = listOf(grading.shadows, grading.midtones, grading.highlights, grading.global)
        val activeGrading = tones.any { it.saturation != 0f || it.luminance != 0f }
        p.int("uGradingOn", if (activeGrading) 1 else 0)
        if (activeGrading) {
            p.vec4Array("uGrading", tones.flatMap { listOf(it.hue / 360f, it.saturation, it.luminance, 0f) }.toFloatArray())
            p.vec2("uGradeMix", grading.balance.coerceIn(-1f, 1f), grading.blending.coerceIn(0f, 1f))
        }
        val gradient = adjustments.gradientMap
        p.float("uGradientAmount", gradient.amount.coerceIn(0f, 1f))
        if (gradient.amount > 0f) {
            fun setColor(name: String, color: Int) {
                val srgb = ColorProfile.SRGB
                val linear = Mat3.apply(srgb.conversionTo(working), doubleArrayOf(
                    srgb.decode(((color ushr 16) and 255) / 255.0),
                    srgb.decode(((color ushr 8) and 255) / 255.0),
                    srgb.decode((color and 255) / 255.0),
                ))
                p.vec3(name, srgb.encode(linear[0]).toFloat(), srgb.encode(linear[1]).toFloat(), srgb.encode(linear[2]).toFloat())
            }
            setColor("uGradientShadow", gradient.shadow)
            setColor("uGradientMiddle", gradient.middle)
            setColor("uGradientHighlight", gradient.highlight)
            p.float("uGradientMidpoint", gradient.midpoint.coerceIn(0.05f, 0.95f))
        }
        val lutOn = lut != null && adjustments.look.isActive
        p.float("uLutOn", if (lutOn) 1f else 0f)
        // The sampler must always point at a 3D texture, even when unused.
        val lutTex = lutTexture(if (lutOn) lut else uploadedLut)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        p.int("uLut", 3)
        if (lutOn) {
            val n = lut!!.size.toFloat()
            p.vec3("uLutMix", adjustments.look.amount.coerceIn(0f, 1f), (n - 1f) / n, 0.5f / n)
        }
        val luma = working.luma
        p.vec3("uLuma", luma[0].toFloat(), luma[1].toFloat(), luma[2].toFloat())
        val lutConvert = lutOn && working != ColorProfile.SRGB
        p.float("uLutConvert", if (lutConvert) 1f else 0f)
        if (lutConvert) {
            p.mat3("uToSrgb", Mat3.toGl(working.conversionTo(ColorProfile.SRGB)))
            p.mat3("uFromSrgb", Mat3.toGl(ColorProfile.SRGB.conversionTo(working)))
        }
        p.int("uProof", if (proof != null) 1 else 0)
        p.int("uGamutWarning", if (gamutWarning) 1 else 0)
        if (proof != null) {
            p.mat3("uProofM", Mat3.toGl(working.conversionTo(proof)))
            p.mat3("uProofToScreen", Mat3.toGl(proof.conversionTo(ColorProfile.SRGB)))
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
        if (shown >= 0) {
            val mask = masks[shown]
            p.int("uMaskOverlayMode", mask.overlayMode.shaderId)
            sampledColor("uMaskOverlayColor", mask.overlayColor)
            p.float("uMaskOverlayOpacity", mask.overlayOpacity.coerceIn(0f, 1f))
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, brushTexture(masks, sourceWidth, sourceHeight, subjects))
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
            p.vec4Array("uLocEffects", mu.locEffects)
            p.vec4Array("uLocToneDetail", mu.locToneDetail)
            p.vec4Array("uLocDetail", mu.locDetail)
        }
        val cutout = LocalAdjustment.cutoutIndex(masks).takeIf { it < mu.count } ?: -1
        p.int("uCutout", cutout)
        p.vec4("uCutoutFill", if (cutout >= 0) ShaderParams.cutoutFill(masks[cutout], working) else FloatArray(4))
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
     * Texture array with one layer per mask index, holding the rasters of the brush and
     * subject masks. Layers are re-uploaded only when their strokes or [subjects] change.
     * Without such masks it is a 1x1 texture, so the sampler stays valid.
     */
    private fun brushTexture(masks: List<LocalAdjustment>, sourceWidth: Int, sourceHeight: Int, subjects: Subjects?): Int {
        val hasBrush = masks.take(LocalAdjustment.MAX).any { LocalAdjustment.usesRaster(it.mask.type) || it.mask.type == MaskType.DEPTH }
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
            val isDepth = local.mask.type == MaskType.DEPTH
            if (!isDepth && !LocalAdjustment.usesRaster(local.mask.type)) return@forEachIndexed
            // A new raster is uploaded even when empty: the layer may hold another photo's paint.
            val created = brushRasters[i] == null
            val raster = brushRasters[i] ?: BrushRaster(sourceWidth, sourceHeight).also { brushRasters[i] = it }
            val original = when (local.mask.type) {
                MaskType.SUBJECT -> subjects?.of(local.mask)
                MaskType.DEPTH -> local.mask.depth?.map?.raster
                else -> null
            }
            val base = original?.resizedTo(raster.width, raster.height)?.data
            val strokes = if (isDepth) emptyList() else local.mask.strokes
            if (raster.sync(strokes, base) || created) {
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
