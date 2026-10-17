// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

@Serializable
data class CurvePoint(val x: Float, val y: Float)

/**
 * A tone curve through control points (x and y in 0..1, sorted by x, endpoints included).
 * Evaluated with monotone cubic interpolation (Fritsch-Carlson), so it never overshoots
 * between points and never creates tone reversals the user did not draw.
 */
@Serializable
data class ToneCurve(val points: List<CurvePoint> = IDENTITY_POINTS) {
    val isIdentity get() = points.all { abs(it.x - it.y) < 1e-4f }

    /** Samples the curve at [size] evenly spaced inputs from 0 to 1. */
    fun sample(size: Int = 256): FloatArray {
        val pts = points.sortedBy { it.x }
        val n = pts.size
        if (n < 2) return FloatArray(size) { it / (size - 1f) }
        val xs = DoubleArray(n) { pts[it].x.toDouble() }
        val ys = DoubleArray(n) { pts[it].y.toDouble() }
        val tangents = monotoneTangents(xs, ys)
        return FloatArray(size) { i ->
            val x = i / (size - 1.0)
            val y = when {
                x <= xs[0] -> ys[0]
                x >= xs[n - 1] -> ys[n - 1]
                else -> {
                    var k = 0
                    while (x > xs[k + 1]) k++
                    hermite(x, xs[k], xs[k + 1], ys[k], ys[k + 1], tangents[k], tangents[k + 1])
                }
            }
            y.coerceIn(0.0, 1.0).toFloat()
        }
    }

    /** Adds a point, or replaces one at (almost) the same x. Endpoints can move but not be removed. */
    fun withPoint(p: CurvePoint): ToneCurve {
        val clamped = CurvePoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))
        val others = points.filter { abs(it.x - clamped.x) > MIN_GAP }
        return ToneCurve((others + clamped).sortedBy { it.x })
    }

    /** Moves the point at [index], keeping it strictly between its neighbours. */
    fun movePoint(index: Int, p: CurvePoint): ToneCurve {
        if (index !in points.indices) return this
        val sorted = points.sortedBy { it.x }
        val lo = if (index == 0) 0f else sorted[index - 1].x + MIN_GAP
        val hi = if (index == sorted.lastIndex) 1f else sorted[index + 1].x - MIN_GAP
        val x = when (index) {
            0 -> p.x.coerceIn(0f, hi)
            sorted.lastIndex -> p.x.coerceIn(lo, 1f)
            else -> p.x.coerceIn(lo, hi)
        }
        return ToneCurve(sorted.toMutableList().also { it[index] = CurvePoint(x, p.y.coerceIn(0f, 1f)) })
    }

    fun removePoint(index: Int): ToneCurve {
        val sorted = points.sortedBy { it.x }
        if (index !in sorted.indices || index == 0 || index == sorted.lastIndex) return this
        return ToneCurve(sorted.filterIndexed { i, _ -> i != index })
    }

    companion object {
        const val MIN_GAP = 0.02f
        val IDENTITY_POINTS = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

        private fun monotoneTangents(xs: DoubleArray, ys: DoubleArray): DoubleArray {
            val n = xs.size
            val d = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
            val m = DoubleArray(n)
            m[0] = d[0]
            m[n - 1] = d[n - 2]
            for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
            for (i in 0 until n - 1) {
                if (d[i] == 0.0) {
                    m[i] = 0.0
                    m[i + 1] = 0.0
                    continue
                }
                val a = m[i] / d[i]
                val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9) {
                    val t = 3 / sqrt(s)
                    m[i] = t * a * d[i]
                    m[i + 1] = t * b * d[i]
                }
            }
            return m
        }

        private fun hermite(x: Double, x0: Double, x1: Double, y0: Double, y1: Double, m0: Double, m1: Double): Double {
            val h = x1 - x0
            val t = (x - x0) / h
            val t2 = t * t
            val t3 = t2 * t
            return (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * h * m0 +
                (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * h * m1
        }
    }
}

enum class CurveChannel { MASTER, RED, GREEN, BLUE }

/** One master curve applied to all channels, then one curve per channel. */
@Serializable
data class Curves(
    val master: ToneCurve = ToneCurve(),
    val red: ToneCurve = ToneCurve(),
    val green: ToneCurve = ToneCurve(),
    val blue: ToneCurve = ToneCurve(),
) {
    val isIdentity get() = master.isIdentity && red.isIdentity && green.isIdentity && blue.isIdentity

    operator fun get(c: CurveChannel) = when (c) {
        CurveChannel.MASTER -> master
        CurveChannel.RED -> red
        CurveChannel.GREEN -> green
        CurveChannel.BLUE -> blue
    }

    fun with(c: CurveChannel, curve: ToneCurve) = when (c) {
        CurveChannel.MASTER -> copy(master = curve)
        CurveChannel.RED -> copy(red = curve)
        CurveChannel.GREEN -> copy(green = curve)
        CurveChannel.BLUE -> copy(blue = curve)
    }

    /**
     * 256 RGBA8 texels: channel c at input i holds curve_c(master(i)), ready to upload as a
     * 256x1 lookup texture.
     */
    fun toLut(): ByteArray {
        val m = master.sample()
        val r = red.sample()
        val g = green.sample()
        val b = blue.sample()
        fun lookup(curve: FloatArray, v: Float) = curve[(v * 255f + 0.5f).toInt().coerceIn(0, 255)]
        val out = ByteArray(256 * 4)
        for (i in 0 until 256) {
            out[i * 4] = toByte(lookup(r, m[i]))
            out[i * 4 + 1] = toByte(lookup(g, m[i]))
            out[i * 4 + 2] = toByte(lookup(b, m[i]))
            out[i * 4 + 3] = 255.toByte()
        }
        return out
    }

    private fun toByte(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
}

/** The eight hue bands of the HSL tool. */
enum class HslBand(val hueDegrees: Float) {
    RED(0f), ORANGE(30f), YELLOW(60f), GREEN(120f), AQUA(180f), BLUE(240f), PURPLE(270f), MAGENTA(300f),
}

/** Per-band hue shift, saturation and luminance, each -1..1. */
@Serializable
data class Hsl(
    val hue: List<Float> = ZERO,
    val saturation: List<Float> = ZERO,
    val luminance: List<Float> = ZERO,
) {
    val isIdentity get() = (hue + saturation + luminance).all { it == 0f }

    fun withHue(b: HslBand, v: Float) = copy(hue = hue.replace(b.ordinal, v))
    fun withSaturation(b: HslBand, v: Float) = copy(saturation = saturation.replace(b.ordinal, v))
    fun withLuminance(b: HslBand, v: Float) = copy(luminance = luminance.replace(b.ordinal, v))

    private fun List<Float>.replace(i: Int, v: Float) =
        List(HslBand.entries.size) { if (it == i) v.coerceIn(-1f, 1f) else getOrElse(it) { 0f } }

    companion object {
        val ZERO = List(8) { 0f }
    }
}
