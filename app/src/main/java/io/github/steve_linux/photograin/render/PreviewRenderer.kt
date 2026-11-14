// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Bitmap
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LensMapping
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.Subjects
import java.nio.ByteBuffer
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
    val onHistogram: ((RgbHistogram) -> Unit)? = null,
    val showClipping: Boolean = false,
    val lens: LensMapping? = null,
)

/** Renders [PreviewFrame]s into a [GLSurfaceView] on demand. */
class PreviewRenderer : GLSurfaceView.Renderer {
    @Volatile
    private var pending: PreviewFrame? = null

    private var pipeline: Pipeline? = null
    private var sourceTexture = 0
    private var uploadedBitmap: Bitmap? = null
    private var intermediateKey: Any? = null
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private val mainThread = Handler(Looper.getMainLooper())
    private var histogramTarget: RenderTarget? = null
    private var histogramPixels: ByteBuffer? = null
    private var histogramKey: List<Any?>? = null

    /** Called from the UI thread; the owner must call requestRender() afterwards. */
    fun submit(frame: PreviewFrame) {
        pending = frame
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context means every GL object is gone.
        pipeline = Pipeline()
        sourceTexture = 0
        uploadedBitmap = null
        intermediateKey = null
        histogramTarget = null
        histogramKey = null
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
        val key = listOf(bitmap, frame.base, frame.geometry, frame.lens)
        if (key != intermediateKey) {
            val toUv = mapper.outputToSource()
                .then(Affine.scale(1.0 / bitmap.width, 1.0 / bitmap.height))
            p.renderIntermediate(sourceTexture, toUv, 0f, 0f, out.width, out.height, out.longEdge, lens = frame.lens)
            intermediateKey = key
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        val r = frame.imageRect
        val clip = floatArrayOf(
            r.left / surfaceWidth * 2f - 1f,
            1f - r.top / surfaceHeight * 2f,
            r.right / surfaceWidth * 2f - 1f,
            1f - r.bottom / surfaceHeight * 2f,
        )
        p.renderColor(
            adjustments = frame.adjustments,
            lut = frame.lut,
            clipRect = clip,
            geoUvRect = Pipeline.UNIT_RECT,
            imageMap = Pipeline.UNIT_RECT,
            outputWidth = out.width,
            outputHeight = out.height,
            compareSplit = frame.compareSplit,
            composite = true,
            working = frame.profile,
            background = frame.background,
            masks = frame.masks,
            posToSource = mapper.normalizedOutputToSource(),
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            subjects = frame.subjects,
            lens = frame.lens,
            showMask = frame.showMask,
            showClipping = frame.showClipping,
        )
        try {
            sampleHistogram(p, frame, bitmap, mapper, out.width, out.height)
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }
    }

    private fun sampleHistogram(p: Pipeline, frame: PreviewFrame, bitmap: Bitmap, mapper: GeometryMapper, width: Int, height: Int) {
        val publish = frame.onHistogram ?: return
        // Ignore display-only changes such as zoom, pan, split position and clipping colors.
        val key = listOf(bitmap, frame.base, frame.geometry, frame.adjustments, frame.lut, frame.profile, frame.masks, frame.subjects, frame.lens, publish)
        if (key == histogramKey) return
        val target = histogramTarget ?: RenderTarget(SAMPLE_SIZE, SAMPLE_SIZE, mipmap = false, halfFloat = false)
            .also { histogramTarget = it }
        target.bind()
        p.renderColor(
            adjustments = frame.adjustments,
            lut = frame.lut,
            clipRect = Pipeline.FULL_CLIP,
            geoUvRect = Pipeline.UNIT_RECT,
            imageMap = Pipeline.UNIT_RECT,
            outputWidth = width,
            outputHeight = height,
            compareSplit = -1f,
            composite = false,
            working = frame.profile,
            masks = frame.masks,
            posToSource = mapper.normalizedOutputToSource(),
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            subjects = frame.subjects,
            lens = frame.lens,
            histogramSample = true,
        )
        val pixels = histogramPixels ?: ByteBuffer.allocateDirect(SAMPLE_SIZE * SAMPLE_SIZE * 4).also { histogramPixels = it }
        pixels.clear()
        GLES30.glReadPixels(0, 0, SAMPLE_SIZE, SAMPLE_SIZE, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        pixels.rewind()
        val histogram = RgbHistogram.fromRgba(pixels)
        histogramKey = key
        mainThread.post { publish(histogram) }
    }

    private companion object {
        const val SAMPLE_SIZE = 128
        const val TAG = "PreviewRenderer"
    }
}
