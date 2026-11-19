// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.math

import kotlin.math.max

/** CPU coordinate mapping matching the radial correction in Shaders.LENS_WARP. */
internal class LensWarp(width: Int, height: Int, amount: Float) {
    private val ax = width / max(width, height).toDouble()
    private val ay = height / max(width, height).toDouble()
    private val k = 0.40 * amount.coerceIn(-1f, 1f)
    private val zoom = 1.0 + max(k, 0.0) * (ax * ax + ay * ay) * 0.25

    fun apply(x: Double, y: Double): Vec2 {
        if (k == 0.0) return Vec2(x, y)
        val dx = x - 0.5
        val dy = y - 0.5
        val scale = (1.0 + k * ((dx * ax) * (dx * ax) + (dy * ay) * (dy * ay))) / zoom
        return Vec2(0.5 + dx * scale, 0.5 + dy * scale)
    }

    fun inverse(x: Double, y: Double): Vec2 {
        if (k == 0.0) return Vec2(x, y)
        val dx = x - 0.5
        val dy = y - 0.5
        val limit = minOf(
            if (dx == 0.0) Double.POSITIVE_INFINITY else 0.5 / kotlin.math.abs(dx),
            if (dy == 0.0) Double.POSITIVE_INFINITY else 0.5 / kotlin.math.abs(dy),
        )
        if (!limit.isFinite()) return Vec2(x, y)
        var lo = 0.0
        var hi = limit
        repeat(24) {
            val middle = (lo + hi) * 0.5
            val mapped = apply(0.5 + dx * middle, 0.5 + dy * middle)
            val progress = (mapped.x - 0.5) * dx + (mapped.y - 0.5) * dy
            if (progress < dx * dx + dy * dy) lo = middle else hi = middle
        }
        val scale = (lo + hi) * 0.5
        return Vec2(0.5 + dx * scale, 0.5 + dy * scale)
    }
}
