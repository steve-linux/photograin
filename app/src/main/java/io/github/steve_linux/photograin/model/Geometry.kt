// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

/** Crop rectangle, normalized (0..1) to the rotated and straightened frame. */
@Serializable
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val FULL = CropRect()
    }
}

/** Aspect ratio presets offered by the crop tool. A null ratio means free-form. */
@Serializable
enum class AspectRatio(val ratio: Float?) {
    FREE(null),
    ORIGINAL(null),
    SQUARE(1f),
    R4_3(4f / 3f),
    R3_2(3f / 2f),
    R16_9(16f / 9f),
    R5_4(5f / 4f),
}

/** Orientation stored in the file (EXIF), as a mirror followed by clockwise quarter turns. */
data class BaseOrientation(val mirror: Boolean = false, val quarterTurns: Int = 0) {
    companion object {
        /** Maps an EXIF orientation tag (1..8) to a [BaseOrientation]. */
        fun fromExif(tag: Int) = when (tag) {
            2 -> BaseOrientation(mirror = true, quarterTurns = 0)
            3 -> BaseOrientation(mirror = false, quarterTurns = 2)
            4 -> BaseOrientation(mirror = true, quarterTurns = 2)
            5 -> BaseOrientation(mirror = true, quarterTurns = 3)
            6 -> BaseOrientation(mirror = false, quarterTurns = 1)
            7 -> BaseOrientation(mirror = true, quarterTurns = 1)
            8 -> BaseOrientation(mirror = false, quarterTurns = 3)
            else -> BaseOrientation()
        }
    }
}

/** User-controlled geometry: rotation, mirroring, straightening and crop. */
@Serializable
data class Geometry(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Fine rotation in degrees, -45..45. */
    val straighten: Float = 0f,
    val crop: CropRect = CropRect.FULL,
    val aspect: AspectRatio = AspectRatio.FREE,
)

/** Size in pixels. */
data class PxSize(val width: Int, val height: Int) {
    val longEdge get() = max(width, height)
}

/**
 * Resolves the full geometric mapping between the source image (as stored in the file)
 * and the final output image, for a given source resolution.
 */
class GeometryMapper(
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val base: BaseOrientation,
    private val geometry: Geometry,
) {
    /** Maps source pixels to the oriented frame (EXIF + user rotation/flip), before straightening. */
    private val orient: Affine
    val frameWidth: Double
    val frameHeight: Double

    init {
        var w = sourceWidth.toDouble()
        var h = sourceHeight.toDouble()
        var t = Affine.IDENTITY
        if (base.mirror) t = t.then(Affine(a = -1.0, tx = w))
        val turns = (base.quarterTurns + geometry.quarterTurns).mod(4)
        repeat(turns) {
            // Clockwise quarter turn of a w x h image: (x, y) -> (h - y, x).
            t = t.then(Affine(a = 0.0, b = 1.0, c = -1.0, d = 0.0, tx = h, ty = 0.0))
            val tmp = w; w = h; h = tmp
        }
        if (geometry.flipH) t = t.then(Affine(a = -1.0, tx = w))
        if (geometry.flipV) t = t.then(Affine(d = -1.0, ty = h))
        orient = t
        frameWidth = w
        frameHeight = h
    }

    private val straighten: Affine =
        Affine.rotateAround(geometry.straighten.toDouble() * PI / 180, frameWidth / 2, frameHeight / 2)

    /** Source pixel -> straightened frame pixel. */
    val sourceToFrame: Affine = orient.then(straighten)

    /** Output size in pixels at this source resolution. */
    val outputSize: PxSize
        get() {
            val c = geometry.crop
            return PxSize(
                max(1, (c.width * frameWidth).roundToInt()),
                max(1, (c.height * frameHeight).roundToInt()),
            )
        }

    /** Output pixel -> source pixel, the transform the renderer needs. */
    fun outputToSource(crop: CropRect = geometry.crop): Affine {
        val frameToOutput = Affine.translate(-crop.left * frameWidth, -crop.top * frameHeight)
        return sourceToFrame.then(frameToOutput).inverse()
    }

    /** True when every corner of [crop] lies on actual image content (inside the straightened source). */
    fun isCropValid(crop: CropRect, tolerancePx: Double = 0.5): Boolean {
        val inv = sourceToFrame.inverse()
        val corners = listOf(
            Vec2(crop.left * frameWidth, crop.top * frameHeight),
            Vec2(crop.right * frameWidth, crop.top * frameHeight),
            Vec2(crop.right * frameWidth, crop.bottom * frameHeight),
            Vec2(crop.left * frameWidth, crop.bottom * frameHeight),
        )
        return corners.all {
            val s = inv.apply(it)
            s.x >= -tolerancePx && s.y >= -tolerancePx &&
                s.x <= sourceWidth + tolerancePx && s.y <= sourceHeight + tolerancePx
        }
    }

    /**
     * Shrinks [crop] around its center (keeping its aspect ratio) until it fits on image content.
     * Used after straightening, so no empty corners end up in the output.
     */
    fun fitCrop(crop: CropRect): CropRect {
        if (isCropValid(crop)) return crop
        val cx = (crop.left + crop.right) / 2
        val cy = (crop.top + crop.bottom) / 2
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val mid = (lo + hi) / 2
            if (isCropValid(scaled(crop, cx, cy, mid))) lo = mid else hi = mid
        }
        return scaled(crop, cx, cy, lo)
    }

    private fun scaled(c: CropRect, cx: Float, cy: Float, s: Double): CropRect {
        val hw = c.width / 2 * s.toFloat()
        val hh = c.height / 2 * s.toFloat()
        return CropRect(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    /** Largest crop of the given output aspect ratio (width / height) centered in the frame, fitted to content. */
    fun centeredCrop(ratio: Float): CropRect {
        val frameRatio = (frameWidth / frameHeight).toFloat()
        val (nw, nh) = if (ratio > frameRatio) 1f to frameRatio / ratio else ratio / frameRatio to 1f
        return fitCrop(CropRect((1 - nw) / 2, (1 - nh) / 2, (1 + nw) / 2, (1 + nh) / 2))
    }

    companion object {
        /** Clamps a crop to the 0..1 frame and enforces a minimum size. */
        fun clampCrop(c: CropRect, minSize: Float = 0.02f): CropRect {
            val l = c.left.coerceIn(0f, 1f - minSize)
            val t = c.top.coerceIn(0f, 1f - minSize)
            val r = c.right.coerceIn(l + minSize, 1f)
            val b = c.bottom.coerceIn(t + minSize, 1f)
            return CropRect(l, t, r, b)
        }
    }
}

/** Rotates the whole edit by a clockwise quarter turn, carrying the crop along. */
fun Geometry.rotatedClockwise(): Geometry = copy(
    quarterTurns = (quarterTurns + 1).mod(4),
    crop = CropRect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right),
)

/** Mirrors left-right; the crop and the straighten angle are mirrored too. */
fun Geometry.flippedHorizontally(): Geometry = copy(
    flipH = !flipH,
    straighten = -straighten,
    crop = CropRect(1f - crop.right, crop.top, 1f - crop.left, crop.bottom),
)

/** Mirrors top-bottom; the crop and the straighten angle are mirrored too. */
fun Geometry.flippedVertically(): Geometry = copy(
    flipV = !flipV,
    straighten = -straighten,
    crop = CropRect(crop.left, 1f - crop.bottom, crop.right, 1f - crop.top),
)
