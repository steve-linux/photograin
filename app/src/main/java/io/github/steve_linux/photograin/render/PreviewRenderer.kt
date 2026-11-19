// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Bitmap
import android.graphics.RectF
import java.nio.ByteBuffer
import io.github.steve_linux.photograin.model.ToneHistogram
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.Subjects
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** What the preview should show. Instances are immutable and handed over from the UI thread. */
data class PreviewFrame(
    val bitmap: Bitmap?,
    val base: BaseOrientation,
    val geometry: Geometry,
    val adjustments: Adjustments,
    /** The table for [Adjustments.look], once loaded. */
    val lut: CubeLut?,
    /** Where the image goes inside the surface, in surface pixels. */
    val imageRect: RectF,
    /** See [Pipeline.renderColor]; -1 disables, 2 shows the original everywhere. */
    val compareSplit: Float,
    val background: FloatArray,
    /** Color space of [bitmap]; the screen is treated as sRGB. */
    val profile: ColorProfile = ColorProfile.SRGB,
    val masks: List<LocalAdjustment> = emptyList(),
    /** Index of the mask to tint red, or -1. */
    val showMask: Int = -1,
    /** What the subject masks cover. */
    val subjects: Subjects? = null,
    val showExposureWarning: Boolean = false,
    val focusPeaking: Boolean = false,
    val edgeExtension: RectF? = null,
    val proof: ColorProfile? = null,
    val gamutWarning: Boolean = false,
)

/** Renders [PreviewFrame]s into a [GLSurfaceView] on demand. */
class PreviewRenderer : GLSurfaceView.Renderer {
    @Volatile
    private var pending: PreviewFrame? = null

    private var pipeline: Pipeline? = null
    private var sourceTexture = 0
    private var uploadedBitmap: Bitmap? = null
    private var intermediateKey: Any? = null
    @Volatile
    internal var onHistogram: ((ToneHistogram) -> Unit)? = null
    private var histogramTarget: RenderTarget? = null
    private val histogramPixels = ByteBuffer.allocateDirect(ToneHistogram.SCOPE_SIZE * ToneHistogram.SCOPE_SIZE * 4)
    private var histogramKey: List<Any?>? = null
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    /** Called from the UI thread; the owner must call requestRender() afterwards. */
    fun submit(frame: PreviewFrame) {
        pending = frame
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context means every GL object is gone.
        pipeline = Pipeline()
        histogramTarget = null
        histogramKey = null
        sourceTexture = 0
        uploadedBitmap = null
        intermediateKey = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val frame = pending
        val bg = frame?.background ?: floatArrayOf(0f, 0f, 0f)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(bg[0], bg[1], bg[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val bitmap = frame?.bitmap ?: return
        if (bitmap.isRecycled) return
        val p = pipeline ?: return
        try {
            draw(p, frame, bitmap)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Preview rendering failed", e)
        }
    }

    private fun draw(p: Pipeline, frame: PreviewFrame, bitmap: Bitmap) {
        if (uploadedBitmap !== bitmap) {
            sourceTexture = GlUtil.uploadBitmap(bitmap, sourceTexture)
            uploadedBitmap = bitmap
            intermediateKey = null
        }

        val mapper = GeometryMapper(bitmap.width, bitmap.height, frame.base, frame.geometry)
        val out = mapper.outputSize
        val lens = frame.adjustments.lensDistortion
        val key = listOf(bitmap, frame.base, frame.geometry, lens)
        if (key != intermediateKey) {
            val toUv = mapper.outputToSource()
                .then(Affine.scale(1.0 / bitmap.width, 1.0 / bitmap.height))
            p.renderIntermediate(sourceTexture, toUv, 0f, 0f, out.width, out.height, out.longEdge, lensAmount = lens, lensSize = out)
            intermediateKey = key
        }

        val listener = onHistogram
        val keyForHistogram = listener?.let {
            listOf(key, frame.adjustments, frame.lut, frame.masks, frame.subjects, frame.profile)
        }
        if (listener == null) histogramKey = null
        if (listener != null && keyForHistogram != histogramKey) {
            val edge = ToneHistogram.SCOPE_SIZE
            val target = histogramTarget ?: RenderTarget(edge, edge, mipmap = false, halfFloat = false)
                .also { histogramTarget = it }
            target.bind()
            drawColor(p, frame, bitmap, mapper, Pipeline.FULL_CLIP, histogram = true)
            histogramPixels.clear()
            GLES30.glReadPixels(0, 0, edge, edge, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, histogramPixels)
            val histogram = ToneHistogram(withWaveform = true, withVectorscope = true)
            for (offset in 0 until histogramPixels.capacity() step 4) {
                val alpha = histogramPixels.get(offset + 3).toInt() and 255
                if (alpha < 240) continue
                val red = ((histogramPixels.get(offset).toInt() and 255) * 255 / alpha).coerceAtMost(255)
                val green = ((histogramPixels.get(offset + 1).toInt() and 255) * 255 / alpha).coerceAtMost(255)
                val blue = ((histogramPixels.get(offset + 2).toInt() and 255) * 255 / alpha).coerceAtMost(255)
                histogram.add(red, green, blue)
                histogram.addWaveform(offset / 4 % edge, red, green, blue)
            }
            histogramKey = keyForHistogram
            listener(histogram)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        val r = frame.imageRect
        val edge = frame.edgeExtension
        if (edge != null) {
            val x = floatArrayOf(edge.left, r.left, r.right, edge.right)
            val y = floatArrayOf(edge.top, r.top, r.bottom, edge.bottom)
            val u = floatArrayOf(0f, 0f, 1f, 1f)
            for (row in 0..2) for (col in 0..2) {
                if (row == 1 && col == 1) continue
                val region = RectF(x[col], y[row], x[col + 1], y[row + 1])
                val uv = floatArrayOf(u[col], u[row], u[col + 1] - u[col], u[row + 1] - u[row])
                drawColor(p, frame, bitmap, mapper, clip(region), histogram = false, uv = uv)
            }
        }
        drawColor(p, frame, bitmap, mapper, clip(r), histogram = false)
    }

    private fun clip(r: RectF) = floatArrayOf(
        r.left / surfaceWidth * 2f - 1f,
        1f - r.top / surfaceHeight * 2f,
        r.right / surfaceWidth * 2f - 1f,
        1f - r.bottom / surfaceHeight * 2f,
    )

    private fun drawColor(
        p: Pipeline,
        frame: PreviewFrame,
        bitmap: Bitmap,
        mapper: GeometryMapper,
        clip: FloatArray,
        histogram: Boolean,
        uv: FloatArray = Pipeline.UNIT_RECT,
    ) {
        val out = mapper.outputSize
        p.renderColor(
            adjustments = frame.adjustments,
            lut = frame.lut,
            clipRect = clip,
            geoUvRect = uv,
            imageMap = uv,
            outputWidth = out.width,
            outputHeight = out.height,
            compareSplit = if (histogram) -1f else frame.compareSplit,
            composite = !histogram,
            working = frame.profile,
            background = frame.background,
            masks = frame.masks,
            posToSource = mapper.normalizedOutputToSource(),
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            subjects = frame.subjects,
            showMask = if (histogram) -1 else frame.showMask,
            showExposureWarning = !histogram && frame.showExposureWarning,
            histogramSample = histogram,
            focusPeaking = !histogram && frame.focusPeaking && uv === Pipeline.UNIT_RECT,
            proof = frame.proof.takeUnless { histogram },
            gamutWarning = !histogram && frame.gamutWarning,
        )
    }

    private companion object {
        const val TAG = "PreviewRenderer"
    }
}
