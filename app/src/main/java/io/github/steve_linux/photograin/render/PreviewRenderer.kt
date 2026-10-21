// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Bitmap
import android.graphics.RectF
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
import io.github.steve_linux.photograin.model.SubjectMask
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
    /** The photo's subject, for subject masks. */
    val subject: SubjectMask? = null,
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
        val key = Triple(bitmap, frame.base, frame.geometry)
        if (key != intermediateKey) {
            val toUv = mapper.outputToSource()
                .then(Affine.scale(1.0 / bitmap.width, 1.0 / bitmap.height))
            p.renderIntermediate(sourceTexture, toUv, 0f, 0f, out.width, out.height, out.longEdge)
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
            subject = frame.subject,
            showMask = frame.showMask,
        )
    }

    private companion object {
        const val TAG = "PreviewRenderer"
    }
}
