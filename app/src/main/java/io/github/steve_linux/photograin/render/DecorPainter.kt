// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.ShapeItem
import io.github.steve_linux.photograin.model.ShapeType
import io.github.steve_linux.photograin.model.TextFont
import io.github.steve_linux.photograin.model.TextItem
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws text items and shapes with the platform's 2D engine, the same way on screen and in the
 * export, so a caption lands where it was placed.
 */
object DecorPainter {
    /** Draws [texts] on [canvas], whose units are pixels of a [total]-sized framed output. */
    fun drawTexts(canvas: Canvas, total: PxSize, texts: List<TextItem>) {
        for (item in texts) {
            if (item.text.isBlank()) continue
            val paint = paint(item, total)
            val cx = item.x * total.width
            val cy = item.y * total.height
            canvas.save()
            canvas.rotate(item.angle, cx, cy)
            if (item.tiled) {
                // Copies on a grid through (cx, cy), wide enough to cover the output at any angle.
                val b = bounds(item, total)
                val stepX = b.width() * 1.5f + paint.textSize
                val stepY = b.height() * 2.5f + paint.textSize
                val reach = hypot(total.width.toFloat(), total.height.toFloat())
                val nx = ceil(reach / stepX).toInt()
                val ny = ceil(reach / stepY).toInt()
                for (j in -ny..ny) for (i in -nx..nx) {
                    // Every other row shifted by half a step, like a brick wall.
                    val shift = if (j % 2 == 0) 0f else stepX / 2
                    drawBlock(canvas, item, paint, cx + i * stepX + shift, cy + j * stepY)
                }
            } else {
                drawBlock(canvas, item, paint, cx, cy)
            }
            canvas.restore()
        }
    }

    /** Draws [shapes] on [canvas], whose units are pixels of a [total]-sized framed output. */
    fun drawShapes(canvas: Canvas, total: PxSize, shapes: List<ShapeItem>) {
        val short = min(total.width, total.height)
        for (shape in shapes) {
            val x0 = shape.x0 * total.width
            val y0 = shape.y0 * total.height
            val x1 = shape.x1 * total.width
            val y1 = shape.y1 * total.height
            val stroke = shape.strokePx(short)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shape.color
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                style = if (shape.fill && shape.isBox) Paint.Style.FILL else Paint.Style.STROKE
            }
            when (shape.type) {
                ShapeType.LINE -> canvas.drawLine(x0, y0, x1, y1, paint)
                ShapeType.ARROW -> {
                    val angle = atan2(y1 - y0, x1 - x0)
                    val head = max(stroke * 4f, short * 0.02f)
                    // The shaft stops at the head, so a thick line does not poke through its tip.
                    val back = head * 0.8f
                    canvas.drawLine(x0, y0, x1 - cos(angle) * back, y1 - sin(angle) * back, paint)
                    val path = Path().apply {
                        moveTo(x1, y1)
                        lineTo(x1 - cos(angle - 0.45f) * head, y1 - sin(angle - 0.45f) * head)
                        lineTo(x1 - cos(angle + 0.45f) * head, y1 - sin(angle + 0.45f) * head)
                        close()
                    }
                    canvas.drawPath(path, Paint(paint).apply { style = Paint.Style.FILL_AND_STROKE })
                }
                ShapeType.RECT -> canvas.drawRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), paint)
                ShapeType.ELLIPSE -> canvas.drawOval(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), paint)
            }
        }
    }

    /** The box [item] covers before its turn, in pixels of a [total]-sized output (one copy if tiled). */
    fun bounds(item: TextItem, total: PxSize): RectF {
        val paint = paint(item, total)
        val lines = item.text.lines()
        val width = lines.maxOf { paint.measureText(it) }
        val metrics = paint.fontMetrics
        val height = paint.fontSpacing * (lines.size - 1) + (metrics.descent - metrics.ascent)
        val cx = item.x * total.width
        val cy = item.y * total.height
        return RectF(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
    }

    /** The lines of [item] centered as a block on ([cx], [cy]). */
    private fun drawBlock(canvas: Canvas, item: TextItem, paint: Paint, cx: Float, cy: Float) {
        val lines = item.text.lines()
        val lineHeight = paint.fontSpacing
        val metrics = paint.fontMetrics
        val blockHeight = lineHeight * (lines.size - 1) + (metrics.descent - metrics.ascent)
        var baseline = cy - blockHeight / 2 - metrics.ascent
        for (line in lines) {
            canvas.drawText(line, cx, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun paint(item: TextItem, total: PxSize) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val size = item.sizePx(min(total.width, total.height))
        textSize = size
        color = item.color
        alpha = (alpha * item.opacity.coerceIn(0f, 1f)).toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(family(item.font), if (item.bold) Typeface.BOLD else Typeface.NORMAL)
        if (item.shadow) {
            val shadowAlpha = (0x99 * item.opacity.coerceIn(0f, 1f)).toInt()
            setShadowLayer(size * 0.08f, 0f, size * 0.04f, shadowAlpha shl 24)
        }
    }

    private fun family(font: TextFont): Typeface = when (font) {
        TextFont.SANS -> Typeface.SANS_SERIF
        TextFont.SERIF -> Typeface.SERIF
        TextFont.MONO -> Typeface.MONOSPACE
        TextFont.HANDWRITING -> Typeface.create("cursive", Typeface.NORMAL)
    }
}
