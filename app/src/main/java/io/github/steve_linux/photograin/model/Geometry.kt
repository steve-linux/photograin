// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.math.then
import io.github.steve_linux.photograin.math.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.hypot
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

/**
 * Four points marking something that should be rectangular (a facade, a sheet of paper),
 * normalized (0..1) to the rotated frame before perspective and straightening: top-left,
 * top-right, bottom-right, bottom-left.
 */
@Serializable
data class Quad(
    val x0: Float = 0.2f, val y0: Float = 0.2f,
    val x1: Float = 0.8f, val y1: Float = 0.2f,
    val x2: Float = 0.8f, val y2: Float = 0.8f,
    val x3: Float = 0.2f, val y3: Float = 0.8f,
) {
    val points: List<Pair<Float, Float>> get() = listOf(x0 to y0, x1 to y1, x2 to y2, x3 to y3)

    fun withPoint(index: Int, x: Float, y: Float): Quad {
        val cx = x.coerceIn(0f, 1f)
        val cy = y.coerceIn(0f, 1f)
        return when (index) {
            0 -> copy(x0 = cx, y0 = cy)
            1 -> copy(x1 = cx, y1 = cy)
            2 -> copy(x2 = cx, y2 = cy)
            else -> copy(x3 = cx, y3 = cy)
        }
    }

    /**
     * Whether the points make a convex shape, in order and of some size, in a frame of the given
     * proportions; anything else has no sensible rectangle to become.
     */
    fun isUsable(frameWidth: Double, frameHeight: Double): Boolean {
        val p = points.map { (x, y) -> Vec2(x * frameWidth, y * frameHeight) }
        var sign = 0
        var area = 0.0
        for (k in 0 until 4) {
            val a = p[k]
            val b = p[(k + 1) % 4]
            val c = p[(k + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0 || (sign != 0 && s != sign)) return false
            sign = s
            area += a.x * b.y - b.x * a.y
        }
        // Clockwise on screen (y down) and at least 1% of the frame.
        return sign > 0 && area / 2 >= 0.01 * frameWidth * frameHeight
    }

    companion object {
        private fun of(p: List<Pair<Float, Float>>) =
            Quad(p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second, p[3].first, p[3].second)
    }

    /** The same shape after a clockwise quarter turn of the frame, corners renamed to stay in order. */
    fun rotatedClockwise(): Quad = points.map { (x, y) -> (1f - y) to x }.let { of(listOf(it[3], it[0], it[1], it[2])) }

    fun flippedHorizontally(): Quad = points.map { (x, y) -> (1f - x) to y }.let { of(listOf(it[1], it[0], it[3], it[2])) }

    fun flippedVertically(): Quad = points.map { (x, y) -> x to (1f - y) }.let { of(listOf(it[3], it[2], it[1], it[0])) }
}

/** User-controlled geometry: rotation, mirroring, perspective, straightening and crop. */
@Serializable
data class Geometry(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Fine rotation in degrees, -45..45. */
    val straighten: Float = 0f,
    /**
     * Perspective correction, -1..1. Positive [vertical] widens the top (for buildings shot
     * from below); positive [horizontal] widens the left side.
     */
    val vertical: Float = 0f,
    val horizontal: Float = 0f,
    /** Four-point perspective: when set, it replaces [vertical] and [horizontal]. */
    val quad: Quad? = null,
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

    /**
     * Keystone correction in the oriented frame, around its center: in coordinates where the
     * frame spans -1..1, w = 1 + h*x + v*y, so the side with w < 1 grows and the other shrinks.
     */
    private val perspective: Homography = quadCorrection() ?: run {
        val hw = frameWidth / 2
        val hh = frameHeight / 2
        val toUnit = Affine.translate(-hw, -hh).then(Affine.scale(1 / hw, 1 / hh))
        val keystone = Homography(
            g = geometry.horizontal * PERSPECTIVE_STRENGTH,
            h = geometry.vertical * PERSPECTIVE_STRENGTH,
        )
        toUnit.then(keystone).then(toUnit.inverse())
    }

    /**
     * The four points taken to a rectangle with their average width and height, around their
     * middle, so what they mark keeps its place and size.
     */
    private fun quadCorrection(): Homography? {
        val q = geometry.quad?.takeIf { it.isUsable(frameWidth, frameHeight) } ?: return null
        val p = q.points.map { (x, y) -> Vec2(x * frameWidth, y * frameHeight) }
        fun dist(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
        val w = (dist(p[0], p[1]) + dist(p[3], p[2])) / 2
        val h = (dist(p[0], p[3]) + dist(p[1], p[2])) / 2
        val cx = p.sumOf { it.x } / 4
        val cy = p.sumOf { it.y } / 4
        val rect = listOf(
            Vec2(cx - w / 2, cy - h / 2), Vec2(cx + w / 2, cy - h / 2),
            Vec2(cx + w / 2, cy + h / 2), Vec2(cx - w / 2, cy + h / 2),
        )
        return Homography.fromPoints(p, rect)
    }

    /** Source pixel -> straightened frame pixel. */
    val sourceToFrame: Homography = orient.then(perspective).then(straighten)

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
    fun outputToSource(crop: CropRect = geometry.crop): Homography {
        val frameToOutput = Affine.translate(-crop.left * frameWidth, -crop.top * frameHeight)
        return sourceToFrame.then(frameToOutput).inverse()
    }

    /**
     * Position in the whole output (0..1) -> position in the source (0..1). The same at any
     * resolution, so masks line up between the preview and the export.
     */
    fun normalizedOutputToSource(): Homography {
        val c = geometry.crop
        return Affine.scale(c.width * frameWidth, c.height * frameHeight)
            .then(outputToSource())
            .then(Affine.scale(1.0 / sourceWidth, 1.0 / sourceHeight))
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

    /**
     * How much more source, at worst, a block of the frame needs than at its middle: perspective
     * squeezes the far side, so a tile there covers a larger piece of the photo. At most 5.
     */
    fun perspectiveStretch(): Double {
        val inv = sourceToFrame.inverse()
        fun scaleAt(x: Double, y: Double): Double {
            val d = 1.0
            val o = inv.apply(x, y)
            val px = inv.apply(x + d, y)
            val py = inv.apply(x, y + d)
            return max(hypot(px.x - o.x, px.y - o.y), hypot(py.x - o.x, py.y - o.y))
        }
        val centre = scaleAt(frameWidth / 2, frameHeight / 2)
        var worst = 1.0
        for (i in 0..4) for (j in 0..4) {
            val s = scaleAt(frameWidth * i / 4, frameHeight * j / 4)
            if (s.isFinite() && s > 0) worst = max(worst, s / centre)
        }
        return worst.coerceAtMost(5.0)
    }

    companion object {
        /** Keystone at a slider value of 1: the near side shrinks to 1 / 1.3 of its size. */
        const val PERSPECTIVE_STRENGTH = 0.3

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

/** Rotates the whole edit by a clockwise quarter turn, carrying the crop and perspective along. */
fun Geometry.rotatedClockwise(): Geometry = copy(
    quarterTurns = (quarterTurns + 1).mod(4),
    vertical = horizontal,
    horizontal = -vertical,
    quad = quad?.rotatedClockwise(),
    crop = CropRect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right),
)

/** Mirrors left-right; the crop and the straighten angle are mirrored too. */
fun Geometry.flippedHorizontally(): Geometry = copy(
    flipH = !flipH,
    straighten = -straighten,
    horizontal = -horizontal,
    quad = quad?.flippedHorizontally(),
    crop = CropRect(1f - crop.right, crop.top, 1f - crop.left, crop.bottom),
)

/** Mirrors top-bottom; the crop and the straighten angle are mirrored too. */
fun Geometry.flippedVertically(): Geometry = copy(
    flipV = !flipV,
    straighten = -straighten,
    vertical = -vertical,
    quad = quad?.flippedVertically(),
    crop = CropRect(crop.left, 1f - crop.bottom, crop.right, 1f - crop.top),
)
