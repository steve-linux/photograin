// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Part of the crop rectangle grabbed by a drag. */
enum class CropHandle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE }

/** Pure crop-drag math, in coordinates normalized to the frame (0..1). */
object CropDrag {
    const val MIN_SIZE = 0.05f

    /** Finds the handle at (x, y); [tolX]/[tolY] is the touch tolerance in normalized units. */
    fun hitTest(c: CropRect, x: Float, y: Float, tolX: Float, tolY: Float): CropHandle? {
        val nearL = abs(x - c.left) < tolX
        val nearR = abs(x - c.right) < tolX
        val nearT = abs(y - c.top) < tolY
        val nearB = abs(y - c.bottom) < tolY
        val withinX = x > c.left - tolX && x < c.right + tolX
        val withinY = y > c.top - tolY && y < c.bottom + tolY
        return when {
            nearL && nearT -> CropHandle.TOP_LEFT
            nearR && nearT -> CropHandle.TOP_RIGHT
            nearR && nearB -> CropHandle.BOTTOM_RIGHT
            nearL && nearB -> CropHandle.BOTTOM_LEFT
            nearL && withinY -> CropHandle.LEFT
            nearR && withinY -> CropHandle.RIGHT
            nearT && withinX -> CropHandle.TOP
            nearB && withinX -> CropHandle.BOTTOM
            x > c.left && x < c.right && y > c.top && y < c.bottom -> CropHandle.MOVE
            else -> null
        }
    }

    /**
     * Applies a drag of ([dx], [dy]) to [start]. [ratio] is width / height in normalized
     * units (null = free-form); with a ratio the opposite corner or edge stays anchored.
     */
    fun drag(start: CropRect, handle: CropHandle, dx: Float, dy: Float, ratio: Float?): CropRect {
        if (handle == CropHandle.MOVE) return move(start, dx, dy)
        return if (ratio == null) free(start, handle, dx, dy) else locked(start, handle, dx, dy, ratio)
    }

    private fun move(c: CropRect, dx: Float, dy: Float): CropRect {
        val mx = dx.coerceIn(-c.left, 1f - c.right)
        val my = dy.coerceIn(-c.top, 1f - c.bottom)
        return CropRect(c.left + mx, c.top + my, c.right + mx, c.bottom + my)
    }

    private fun free(c: CropRect, h: CropHandle, dx: Float, dy: Float): CropRect {
        var l = c.left
        var t = c.top
        var r = c.right
        var b = c.bottom
        if (h == CropHandle.LEFT || h == CropHandle.TOP_LEFT || h == CropHandle.BOTTOM_LEFT) {
            l = (l + dx).coerceIn(0f, r - MIN_SIZE)
        }
        if (h == CropHandle.RIGHT || h == CropHandle.TOP_RIGHT || h == CropHandle.BOTTOM_RIGHT) {
            r = (r + dx).coerceIn(l + MIN_SIZE, 1f)
        }
        if (h == CropHandle.TOP || h == CropHandle.TOP_LEFT || h == CropHandle.TOP_RIGHT) {
            t = (t + dy).coerceIn(0f, b - MIN_SIZE)
        }
        if (h == CropHandle.BOTTOM || h == CropHandle.BOTTOM_LEFT || h == CropHandle.BOTTOM_RIGHT) {
            b = (b + dy).coerceIn(t + MIN_SIZE, 1f)
        }
        return CropRect(l, t, r, b)
    }

    private fun locked(c: CropRect, h: CropHandle, dx: Float, dy: Float, ratio: Float): CropRect {
        val minW = max(MIN_SIZE, MIN_SIZE * ratio)
        when (h) {
            CropHandle.LEFT, CropHandle.RIGHT -> {
                val cy = (c.top + c.bottom) / 2
                val anchorX = if (h == CropHandle.LEFT) c.right else c.left
                val room = if (h == CropHandle.LEFT) anchorX else 1f - anchorX
                val maxW = min(room, 2 * min(cy, 1f - cy) * ratio)
                val w = (c.width + if (h == CropHandle.LEFT) -dx else dx).coerceIn(minW, max(minW, maxW))
                val hh = w / ratio
                val l = if (h == CropHandle.LEFT) anchorX - w else anchorX
                return CropRect(l, cy - hh / 2, l + w, cy + hh / 2)
            }
            CropHandle.TOP, CropHandle.BOTTOM -> {
                val cx = (c.left + c.right) / 2
                val anchorY = if (h == CropHandle.TOP) c.bottom else c.top
                val room = if (h == CropHandle.TOP) anchorY else 1f - anchorY
                val maxH = min(room, 2 * min(cx, 1f - cx) / ratio)
                val minH = minW / ratio
                val hh = (c.height + if (h == CropHandle.TOP) -dy else dy).coerceIn(minH, max(minH, maxH))
                val w = hh * ratio
                val t = if (h == CropHandle.TOP) anchorY - hh else anchorY
                return CropRect(cx - w / 2, t, cx + w / 2, t + hh)
            }
            else -> {
                val left = h == CropHandle.TOP_LEFT || h == CropHandle.BOTTOM_LEFT
                val top = h == CropHandle.TOP_LEFT || h == CropHandle.TOP_RIGHT
                val ax = if (left) c.right else c.left
                val ay = if (top) c.bottom else c.top
                val wFromX = c.width + if (left) -dx else dx
                val wFromY = (c.height + if (top) -dy else dy) * ratio
                val maxW = min(if (left) ax else 1f - ax, (if (top) ay else 1f - ay) * ratio)
                val w = max(wFromX, wFromY).coerceIn(minW, max(minW, maxW))
                val hh = w / ratio
                val l = if (left) ax - w else ax
                val t = if (top) ay - hh else ay
                return CropRect(l, t, l + w, t + hh)
            }
        }
    }
}
