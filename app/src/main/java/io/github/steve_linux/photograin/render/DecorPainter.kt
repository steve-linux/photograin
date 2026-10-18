// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.TextFont
import io.github.steve_linux.photograin.model.TextItem
import kotlin.math.min

/**
 * Draws text items with the platform's text engine, the same way on screen and in the
 * export, so a caption lands where it was placed.
 */
object DecorPainter {
    /** Draws [texts] on [canvas], whose units are pixels of a [total]-sized framed output. */
    fun drawTexts(canvas: Canvas, total: PxSize, texts: List<TextItem>) {
        for (item in texts) {
            if (item.text.isBlank()) continue
            val paint = paint(item, total)
            val lines = item.text.lines()
            val lineHeight = paint.fontSpacing
            val cx = item.x * total.width
            // Lines are centered as a block on (x, y).
            val metrics = paint.fontMetrics
            val blockHeight = lineHeight * (lines.size - 1) + (metrics.descent - metrics.ascent)
            var baseline = item.y * total.height - blockHeight / 2 - metrics.ascent
            for (line in lines) {
                canvas.drawText(line, cx, baseline, paint)
                baseline += lineHeight
            }
        }
    }

    /** The box [item] covers, in pixels of a [total]-sized output. */
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

    private fun paint(item: TextItem, total: PxSize) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val size = item.sizePx(min(total.width, total.height))
        textSize = size
        color = item.color
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(family(item.font), if (item.bold) Typeface.BOLD else Typeface.NORMAL)
        if (item.shadow) setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0x99000000.toInt())
    }

    private fun family(font: TextFont): Typeface = when (font) {
        TextFont.SANS -> Typeface.SANS_SERIF
        TextFont.SERIF -> Typeface.SERIF
        TextFont.MONO -> Typeface.MONOSPACE
        TextFont.HANDWRITING -> Typeface.create("cursive", Typeface.NORMAL)
    }
}
