// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Serializable
enum class MaskType { LINEAR, RADIAL, BRUSH }

/**
 * One brush stroke of a [MaskType.BRUSH] mask: [points] are x, y pairs in source-normalized
 * coordinates, [radius] is in source long-edge units. [hardness] is the part of the radius
 * painted at full strength; [erase] removes paint instead of adding it.
 */
@Serializable
data class BrushStroke(
    val points: List<Float>,
    val radius: Float,
    val hardness: Float = 0.5f,
    val erase: Boolean = false,
) {
    val pointCount get() = points.size / 2
}

/**
 * Where a local adjustment applies. Positions are normalized to the source image as stored in
 * the file (x / width, y / height), so a mask stays on the same content through rotation, flips,
 * straightening and crop. Radii are fractions of the source long edge.
 *
 * Linear: full effect on the (x0, y0) side, fading out towards (x1, y1).
 * Radial: an ellipse centered on (x0, y0), turned by [angle] radians (clockwise on screen).
 * Brush: painted with [strokes], rasterized by [BrushRaster].
 */
@Serializable
data class Mask(
    val type: MaskType = MaskType.RADIAL,
    val x0: Float = 0.5f,
    val y0: Float = 0.5f,
    val x1: Float = 0.5f,
    val y1: Float = 0.5f,
    val radiusX: Float = 0.25f,
    val radiusY: Float = 0.2f,
    val angle: Float = 0f,
    /** Radial only: the outer part of the ellipse that fades, 0..1. */
    val feather: Float = 0.5f,
    val invert: Boolean = false,
    val strokes: List<BrushStroke> = emptyList(),
) {
    /**
     * Strength 0..1 at source-normalized ([x], [y]) for a source of width / long edge [aspectX]
     * and height / long edge [aspectY]. Mirrors maskWeight() in the color shader. Brush masks
     * need their raster (see [BrushRaster]) and read as 0 here.
     */
    fun weight(x: Float, y: Float, aspectX: Float, aspectY: Float): Float {
        val qx = x * aspectX
        val qy = y * aspectY
        val w = when (type) {
            MaskType.LINEAR -> {
                val ax = x0 * aspectX
                val ay = y0 * aspectY
                val dx = x1 * aspectX - ax
                val dy = y1 * aspectY - ay
                val t = ((qx - ax) * dx + (qy - ay) * dy) / max(dx * dx + dy * dy, 1e-8f)
                1f - smoothstep(0f, 1f, t)
            }
            MaskType.RADIAL -> {
                val dx = qx - x0 * aspectX
                val dy = qy - y0 * aspectY
                val cs = cos(angle)
                val sn = sin(angle)
                val u = (cs * dx + sn * dy) / max(radiusX, 1e-5f)
                val v = (-sn * dx + cs * dy) / max(radiusY, 1e-5f)
                1f - smoothstep(1f - max(feather, 0.001f), 1f, hypot(u, v))
            }
            MaskType.BRUSH -> 0f
        }
        return if (invert) 1f - w else w
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** Adjustments applied through a [Mask], added to the global ones where the mask is strong. */
@Serializable
data class LocalAdjustment(
    val mask: Mask = Mask(),
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val clarity: Float = 0f,
) {
    companion object {
        /** Most masks per photo; the shader has room for this many. */
        const val MAX = 8

        /**
         * A new mask of [type] placed on what is visible: [outputToSource] maps the output
         * (0..1) to the source (0..1), and the source is [sourceWidth] x [sourceHeight].
         * A linear mask darkens from the top of the view, an ellipse sits in the middle;
         * both start with a small exposure drop so the effect is visible at once.
         */
        fun create(type: MaskType, outputToSource: Affine, sourceWidth: Int, sourceHeight: Int): LocalAdjustment {
            val long = max(sourceWidth, sourceHeight).toDouble()
            val ax = sourceWidth / long
            val ay = sourceHeight / long
            fun at(x: Double, y: Double): Vec2 = outputToSource.apply(x, y)
            // Lengths of the output's axes in long-edge units.
            val o = at(0.0, 0.0)
            val ex = at(1.0, 0.0)
            val ey = at(0.0, 1.0)
            val wx = (ex.x - o.x) * ax
            val wy = (ex.y - o.y) * ay
            val viewW = hypot(wx, wy)
            val viewH = hypot((ey.x - o.x) * ax, (ey.y - o.y) * ay)
            if (type == MaskType.BRUSH) return LocalAdjustment(mask = Mask(MaskType.BRUSH), exposure = 0.5f)
            val mask = when (type) {
                MaskType.LINEAR -> {
                    val a = at(0.5, 0.15)
                    val b = at(0.5, 0.55)
                    Mask(MaskType.LINEAR, a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat())
                }
                else -> {
                    val c = at(0.5, 0.5)
                    val r = 0.3 * min(viewW, viewH)
                    Mask(
                        MaskType.RADIAL, c.x.toFloat(), c.y.toFloat(), c.x.toFloat(), c.y.toFloat(),
                        radiusX = (r * 1.25).toFloat(), radiusY = r.toFloat(),
                        // Lie along the view's horizontal, whatever the rotation.
                        angle = atan2(wy, wx).toFloat(),
                    )
                }
            }
            return LocalAdjustment(mask = mask, exposure = if (type == MaskType.LINEAR) -0.5f else 0.5f)
        }
    }
}

/** One slider of a local adjustment. */
class LocalParamSpec(
    val id: String,
    val min: Float,
    val max: Float,
    val get: (LocalAdjustment) -> Float,
    val set: (LocalAdjustment, Float) -> LocalAdjustment,
    val displayScale: Float = 100f,
    val decimals: Int = 0,
)

object LocalParams {
    val all: List<LocalParamSpec> = listOf(
        LocalParamSpec("exposure", -4f, 4f, { it.exposure }, { a, v -> a.copy(exposure = v) }, displayScale = 1f, decimals = 2),
        LocalParamSpec("contrast", -1f, 1f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
        LocalParamSpec("highlights", -1f, 1f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
        LocalParamSpec("shadows", -1f, 1f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
        LocalParamSpec("temperature", -1f, 1f, { it.temperature }, { a, v -> a.copy(temperature = v) }),
        LocalParamSpec("tint", -1f, 1f, { it.tint }, { a, v -> a.copy(tint = v) }),
        LocalParamSpec("saturation", -1f, 1f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
        LocalParamSpec("clarity", -1f, 1f, { it.clarity }, { a, v -> a.copy(clarity = v) }),
    )
}
