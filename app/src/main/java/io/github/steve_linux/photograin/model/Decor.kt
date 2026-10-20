// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
enum class FrameStyle { NONE, BORDER, POLAROID }

/**
 * A plain border around the photo, which makes the output larger. [width] (0..1) sets the
 * border; POLAROID leaves three times as much room at the bottom, for a caption.
 * [color] is ARGB in sRGB.
 */
@Serializable
data class Frame(
    val style: FrameStyle = FrameStyle.NONE,
    val width: Float = 0.3f,
    val color: Int = WHITE,
) {
    /** Where the photo sits in the framed output, for a photo of [image] pixels. */
    fun layout(image: PxSize): FrameLayout {
        if (style == FrameStyle.NONE) return FrameLayout(image, 0, 0)
        val side = (min(image.width, image.height) * MAX_FRACTION * width.coerceIn(0f, 1f)).roundToInt()
        val bottom = if (style == FrameStyle.POLAROID) max(side * 3, (min(image.width, image.height) * 0.12).roundToInt()) else side
        return FrameLayout(PxSize(image.width + 2 * side, image.height + side + bottom), side, side)
    }

    companion object {
        const val WHITE = -0x1

        /** Border at full width, as a fraction of the photo's short edge. */
        const val MAX_FRACTION = 0.15
    }
}

/** The framed output's [size] and the top-left corner of the photo inside it. */
data class FrameLayout(val size: PxSize, val imageLeft: Int, val imageTop: Int)

@Serializable
enum class TextFont { SANS, SERIF, MONO, HANDWRITING, LIGHT, THIN, CONDENSED, BLACK, SMALL_CAPS, CASUAL, SERIF_MONO }

/**
 * Text drawn over the finished photo. (x, y) is the center of the text, normalized to the
 * framed output, so a caption can sit on a polaroid border. [size] (0..1) sets the letter
 * height relative to the output's short edge. [color] is ARGB in sRGB, [opacity] 0..1 on top
 * of it, [angle] a turn in degrees (clockwise) around the center. [tiled] repeats the text
 * over the whole output, as a watermark; (x, y) then places one of the copies.
 */
@Serializable
data class TextItem(
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.3f,
    val color: Int = Frame.WHITE,
    val font: TextFont = TextFont.SANS,
    val bold: Boolean = false,
    val shadow: Boolean = true,
    /** Shadow color, ARGB in sRGB; its alpha scales the shadow's own strength. */
    val shadowColor: Int = SHADOW_BLACK,
    /** How far the shadow falls from the letters, 0..1 (up to a quarter of the letter size). */
    val shadowDistance: Float = 0.4f,
    /** Direction the shadow falls, in degrees clockwise from the right; 45 is down and to the right. */
    val shadowAngle: Float = 45f,
    /** Softness of the shadow's edge, 0 (sharp) .. 1. */
    val shadowBlur: Float = 0.3f,
    /** Strength of the shadow, 0..1, on top of the text's own [opacity]. */
    val shadowOpacity: Float = 0.8f,
    val opacity: Float = 1f,
    val angle: Float = 0f,
    val tiled: Boolean = false,
) {
    /** Letter size in pixels for an output whose short edge is [shortEdge] pixels. */
    fun sizePx(shortEdge: Int): Float = shortEdge * (0.02f + 0.16f * size.coerceIn(0f, 1f))

    companion object {
        const val MAX_ITEMS = 10
        const val SHADOW_BLACK = -0x1000000
    }
}

@Serializable
enum class ShapeType { LINE, ARROW, RECT, ELLIPSE }

/**
 * A line, arrow, rectangle or ellipse drawn over the finished photo, from (x0, y0) to (x1, y1)
 * normalized to the framed output: the ends of a line (an arrow points at the second), the
 * opposite corners of the box around a rectangle or ellipse. [width] (0..1) sets the stroke
 * relative to the output's short edge; [fill] paints a rectangle or ellipse solid.
 */
@Serializable
data class ShapeItem(
    val type: ShapeType,
    val x0: Float = 0.35f,
    val y0: Float = 0.35f,
    val x1: Float = 0.65f,
    val y1: Float = 0.65f,
    val color: Int = RED,
    val width: Float = 0.3f,
    val fill: Boolean = false,
) {
    /** Stroke width in pixels for an output whose short edge is [shortEdge] pixels. */
    fun strokePx(shortEdge: Int): Float = shortEdge * (0.002f + 0.04f * width.coerceIn(0f, 1f))

    /** A rectangle or ellipse, which can be filled. */
    val isBox: Boolean get() = type == ShapeType.RECT || type == ShapeType.ELLIPSE

    companion object {
        const val MAX_ITEMS = 20
        const val RED = 0xFFC62828.toInt()
    }
}
