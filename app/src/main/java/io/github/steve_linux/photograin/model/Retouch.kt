// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class RetouchType { HEAL, CLONE, RED_EYE, REMOVE }

/**
 * A circle of the photo replaced by another part of it. Positions are source-normalized
 * (x / width, y / height) like masks, [radius] is in source long-edge units. (x, y) is the
 * spot being fixed, (sourceX, sourceY) where its replacement comes from.
 *
 * [RetouchType.CLONE] copies the source as it is; [RetouchType.HEAL] also carries over the
 * brightness and color around the spot, so the patch blends in. [RetouchType.RED_EYE] takes
 * nothing from elsewhere (its source is the spot itself): it turns the red of a flash-lit pupil
 * back to dark. [RetouchType.REMOVE] paints over an object with [strokes] and lets the
 * inpainting model fill that area from its surroundings (see [Removal]); its circle only
 * surrounds the strokes, for picking it, and its source is the spot itself.
 */
@Serializable
data class RetouchSpot(
    val type: RetouchType = RetouchType.HEAL,
    val x: Float,
    val y: Float,
    val sourceX: Float,
    val sourceY: Float,
    val radius: Float,
    /** The outer part of the circle that fades into the photo, 0..1. */
    val feather: Float = 0.5f,
    val opacity: Float = 1f,
    /** The painted area of a [RetouchType.REMOVE] spot; empty for the others. */
    val strokes: List<BrushStroke> = emptyList(),
)

/**
 * The pixel work of retouching, on interleaved RGB float patches (0..255). Free of Android
 * APIs so it can be tested on the JVM.
 */
object Retouch {
    /** Most spots per photo, to keep the export's work bounded. */
    const val MAX_SPOTS = 100

    /**
     * Replaces a spot in [dst], a [w] x [h] patch centered on the spot, with [src], the patch of
     * the same size around the source. (cx, cy) is the spot center inside the patch and [r] its
     * radius, in patch pixels. Returns the blended patch.
     */
    fun apply(
        dst: FloatArray,
        src: FloatArray,
        w: Int,
        h: Int,
        cx: Double,
        cy: Double,
        r: Double,
        spot: RetouchSpot,
    ): FloatArray {
        val replacement = when (spot.type) {
            RetouchType.HEAL -> heal(dst, src, w, h, cx, cy, r)
            RetouchType.CLONE -> src
            RetouchType.RED_EYE -> redEye(dst)
            // Filled by the inpainting model instead, in Retoucher.
            RetouchType.REMOVE -> dst
        }
        val out = FloatArray(dst.size)
        val inner = r * (1 - spot.feather.coerceIn(0f, 1f))
        val opacity = spot.opacity.coerceIn(0f, 1f)
        for (y in 0 until h) for (x in 0 until w) {
            val d = hypot(x + 0.5 - cx, y + 0.5 - cy)
            val a = opacity * (1 - smoothstep(inner, r, d)).toFloat()
            val i = (y * w + x) * 3
            for (c in 0..2) out[i + c] = dst[i + c] + (replacement[i + c] - dst[i + c]) * a
        }
        return out
    }

    /**
     * [src] shifted by a smooth correction that matches [dst] around the circle: the difference
     * between the two patches outside the circle is interpolated across it (a membrane, as in
     * seamless cloning), on a coarse grid since the correction is smooth anyway.
     */
    fun heal(dst: FloatArray, src: FloatArray, w: Int, h: Int, cx: Double, cy: Double, r: Double): FloatArray {
        val step = max(1, ceil(max(w, h) / GRID.toDouble()).toInt())
        val gw = ceil(w / step.toDouble()).toInt()
        val gh = ceil(h / step.toDouble()).toInt()
        val grid = FloatArray(gw * gh * 3)
        val fixed = BooleanArray(gw * gh)
        // Coarse cells: the mean difference of the pixels outside the circle; cells fully inside are free.
        for (gy in 0 until gh) for (gx in 0 until gw) {
            var n = 0
            val sum = DoubleArray(3)
            for (y in gy * step until min(h, (gy + 1) * step)) for (x in gx * step until min(w, (gx + 1) * step)) {
                if (hypot(x + 0.5 - cx, y + 0.5 - cy) < r) continue
                val i = (y * w + x) * 3
                for (c in 0..2) sum[c] += (dst[i + c] - src[i + c]).toDouble()
                n++
            }
            val g = gy * gw + gx
            if (n > 0) {
                fixed[g] = true
                for (c in 0..2) grid[g * 3 + c] = (sum[c] / n).toFloat()
            }
        }
        if (fixed.none { it }) return src
        // Free cells start at the mean of the fixed ones, then relax towards the membrane.
        val mean = FloatArray(3)
        var count = 0
        for (g in fixed.indices) if (fixed[g]) {
            for (c in 0..2) mean[c] += grid[g * 3 + c]
            count++
        }
        for (g in fixed.indices) if (!fixed[g]) for (c in 0..2) grid[g * 3 + c] = mean[c] / count
        repeat(ITERATIONS) {
            for (gy in 0 until gh) for (gx in 0 until gw) {
                val g = gy * gw + gx
                if (fixed[g]) continue
                for (c in 0..2) {
                    var s = 0f
                    var n = 0
                    if (gx > 0) { s += grid[(g - 1) * 3 + c]; n++ }
                    if (gx < gw - 1) { s += grid[(g + 1) * 3 + c]; n++ }
                    if (gy > 0) { s += grid[(g - gw) * 3 + c]; n++ }
                    if (gy < gh - 1) { s += grid[(g + gw) * 3 + c]; n++ }
                    val old = grid[g * 3 + c]
                    grid[g * 3 + c] = old + SOR * (s / n - old)
                }
            }
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            // Bilinear between cell centers.
            val fx = ((x + 0.5) / step - 0.5).coerceIn(0.0, gw - 1.0)
            val fy = ((y + 0.5) / step - 0.5).coerceIn(0.0, gh - 1.0)
            val x0 = floor(fx).toInt()
            val y0 = floor(fy).toInt()
            val x1 = min(x0 + 1, gw - 1)
            val y1 = min(y0 + 1, gh - 1)
            val tx = (fx - x0).toFloat()
            val ty = (fy - y0).toFloat()
            val i = (y * w + x) * 3
            for (c in 0..2) {
                val a = grid[(y0 * gw + x0) * 3 + c] * (1 - tx) + grid[(y0 * gw + x1) * 3 + c] * tx
                val b = grid[(y1 * gw + x0) * 3 + c] * (1 - tx) + grid[(y1 * gw + x1) * 3 + c] * tx
                out[i + c] = (src[i + c] + a * (1 - ty) + b * ty).coerceIn(0f, 255f)
            }
        }
        return out
    }

    /**
     * [pixels] with red pupils made dark: in each pixel that is clearly red, the red falls to
     * the mean of green and blue, which stay as they are, so the pupil keeps its catchlight
     * and shading. Skin and lips are much less saturated and are left alone.
     */
    fun redEye(pixels: FloatArray): FloatArray {
        val out = pixels.copyOf()
        for (i in 0 until pixels.size / 3) {
            val t = redness(pixels[i * 3], pixels[i * 3 + 1], pixels[i * 3 + 2])
            if (t <= 0.0) continue
            val r = pixels[i * 3]
            val target = (pixels[i * 3 + 1] + pixels[i * 3 + 2]) / 2
            out[i * 3] = r + (target - r) * t.toFloat()
        }
        return out
    }

    /**
     * How much the pixel looks like a flash-lit pupil, 0..1: red well above both green and blue,
     * and bright enough for the red to be the flash rather than a dark tone.
     */
    fun redness(r: Float, g: Float, b: Float): Double {
        val ratio = r / ((g + b) / 2 + 8)
        return smoothstep(1.5, 2.2, ratio.toDouble()) * smoothstep(40.0, 80.0, r.toDouble())
    }

    /**
     * The red pupil near pixel (cx, cy) of an image read through [pixel] (x, y -> RGB 0..255) of
     * [width] x [height], looked for within [reach] pixels: the red patch nearest the tap, as
     * a center and a radius that covers it with a little margin. Null when there is no red
     * there.
     */
    fun findRedEye(
        width: Int,
        height: Int,
        cx: Double,
        cy: Double,
        reach: Double,
        pixel: (Int, Int) -> FloatArray,
    ): Triple<Double, Double, Double>? {
        val x0 = max(0, floor(cx - reach).toInt())
        val y0 = max(0, floor(cy - reach).toInt())
        val x1 = min(width, ceil(cx + reach).toInt())
        val y1 = min(height, ceil(cy + reach).toInt())
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0) return null
        val red = BooleanArray(w * h)
        var seed = -1
        var seedDistance = Double.MAX_VALUE
        for (y in 0 until h) for (x in 0 until w) {
            val p = pixel(x0 + x, y0 + y)
            if (redness(p[0], p[1], p[2]) < 0.5) continue
            red[y * w + x] = true
            val d = hypot(x0 + x + 0.5 - cx, y0 + y + 0.5 - cy)
            if (d < seedDistance) {
                seedDistance = d
                seed = y * w + x
            }
        }
        if (seed < 0) return null
        // The connected red patch around the reddest pixel nearest the tap.
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        stack.addLast(seed)
        seen[seed] = true
        var n = 0
        var sx = 0.0
        var sy = 0.0
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % w
            val y = i / w
            n++
            sx += x
            sy += y
            for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (!red[j] || seen[j]) continue
                seen[j] = true
                stack.addLast(j)
            }
        }
        if (n < 4) return null
        // A disc of the same area, a little larger so the soft edge of the pupil is covered too.
        val radius = sqrt(n / PI) * 1.3 + 1
        return Triple(x0 + sx / n + 0.5, y0 + sy / n + 0.5, min(radius, reach))
    }

    /**
     * Picks a source for a spot at pixel (cx, cy) of radius [r] in an image read through
     * [pixel] (x, y -> RGB 0..255) of [width] x [height]: among nearby circles that do not
     * overlap the spot, the one whose surroundings look most like the spot's own (brightness
     * differences are ignored for healing, which corrects them). Returns the source center.
     */
    fun findSource(
        width: Int,
        height: Int,
        cx: Double,
        cy: Double,
        r: Double,
        ignoreMean: Boolean,
        pixel: (Int, Int) -> FloatArray,
    ): Pair<Double, Double> {
        val ring = ringOffsets(r)
        val target = sampleRing(ring, cx, cy, width, height, pixel)
        var best: Pair<Double, Double>? = null
        var bestScore = Double.MAX_VALUE
        for (dist in doubleArrayOf(2.3, 3.2)) for (k in 0 until 16) {
            val a = 2 * PI * k / 16
            val sx = cx + cos(a) * r * dist
            val sy = cy + sin(a) * r * dist
            if (sx - r < 0 || sy - r < 0 || sx + r > width || sy + r > height) continue
            val cand = sampleRing(ring, sx, sy, width, height, pixel)
            // Prefer the nearer circles a little: the light changes less over a short distance.
            val score = difference(target, cand, ignoreMean) * (1 + 0.1 * (dist - 2.3))
            if (score < bestScore) {
                bestScore = score
                best = sx to sy
            }
        }
        // Too close to the edges for any candidate: take the side with the most room.
        return best ?: (if (cx < width / 2.0) cx + 2.3 * r else cx - 2.3 * r) to cy
    }

    private fun ringOffsets(r: Double): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        for (rr in doubleArrayOf(1.15, 1.4)) for (k in 0 until 24) {
            val a = 2 * PI * k / 24
            out += cos(a) * r * rr to sin(a) * r * rr
        }
        // The middle counts too, so a plain area is not filled with a busy one.
        for (rr in doubleArrayOf(0.0, 0.5)) for (k in 0 until if (rr == 0.0) 1 else 8) {
            val a = 2 * PI * k / 8
            out += cos(a) * r * rr to sin(a) * r * rr
        }
        return out
    }

    private fun sampleRing(
        ring: List<Pair<Double, Double>>,
        cx: Double,
        cy: Double,
        width: Int,
        height: Int,
        pixel: (Int, Int) -> FloatArray,
    ): FloatArray {
        val out = FloatArray(ring.size * 3)
        ring.forEachIndexed { i, (dx, dy) ->
            val x = (cx + dx).roundToInt().coerceIn(0, width - 1)
            val y = (cy + dy).roundToInt().coerceIn(0, height - 1)
            val p = pixel(x, y)
            out[i * 3] = p[0]; out[i * 3 + 1] = p[1]; out[i * 3 + 2] = p[2]
        }
        return out
    }

    private fun difference(a: FloatArray, b: FloatArray, ignoreMean: Boolean): Double {
        val n = a.size / 3
        val mean = DoubleArray(3)
        if (ignoreMean) for (i in 0 until n) for (c in 0..2) mean[c] += (a[i * 3 + c] - b[i * 3 + c]).toDouble() / n
        var s = 0.0
        // The spot's own pixels (the last 9 samples) are what is being removed: only the ring counts for matching.
        val ringCount = n - 9
        for (i in 0 until n) for (c in 0..2) {
            val d = a[i * 3 + c] - b[i * 3 + c] - mean[c]
            s += if (i < ringCount) d * d else 0.0
        }
        // A candidate with a busy middle would bring new detail in: count its variance.
        var busy = 0.0
        for (c in 0..2) {
            var m = 0.0
            for (i in ringCount until n) m += b[i * 3 + c] / 9.0
            for (i in ringCount until n) busy += (b[i * 3 + c] - m) * (b[i * 3 + c] - m) / 9.0
        }
        return s / ringCount + 0.5 * busy
    }

    private fun hypot(x: Double, y: Double) = sqrt(x * x + y * y)

    private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
        if (e1 <= e0) return if (x < e0) 0.0 else 1.0
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    private const val GRID = 48
    private const val ITERATIONS = 300
    private const val SOR = 1.85f
}
