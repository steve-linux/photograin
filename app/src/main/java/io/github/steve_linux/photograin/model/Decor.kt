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
enum class TextFont { SANS, SERIF, MONO, HANDWRITING }

/**
 * Text drawn over the finished photo. (x, y) is the center of the text, normalized to the
 * framed output, so a caption can sit on a polaroid border. [size] (0..1) sets the letter
 * height relative to the output's short edge. [color] is ARGB in sRGB.
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
) {
    /** Letter size in pixels for an output whose short edge is [shortEdge] pixels. */
    fun sizePx(shortEdge: Int): Float = shortEdge * (0.02f + 0.16f * size.coerceIn(0f, 1f))

    companion object {
        const val MAX_ITEMS = 10
    }
}
