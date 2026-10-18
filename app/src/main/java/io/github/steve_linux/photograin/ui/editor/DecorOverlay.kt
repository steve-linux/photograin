// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.model.Frame
import io.github.steve_linux.photograin.model.FrameStyle
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.TextItem
import io.github.steve_linux.photograin.render.DecorPainter

/** Screen box of [item] for a [total]-sized framed output drawn in [frameRect]. */
fun textScreenBounds(item: TextItem, total: PxSize, frameRect: RectF): RectF {
    val b = DecorPainter.bounds(item, total)
    val s = frameRect.width() / total.width
    return RectF(frameRect.left + b.left * s, frameRect.top + b.top * s, frameRect.left + b.right * s, frameRect.top + b.bottom * s)
}

/** Index of the topmost text under [p], allowing [reach] pixels around each box. */
fun hitText(texts: List<TextItem>, total: PxSize, frameRect: RectF, p: Offset, reach: Float): Int? =
    texts.indices.reversed().firstOrNull { i ->
        val b = textScreenBounds(texts[i], total, frameRect)
        p.x >= b.left - reach && p.x <= b.right + reach && p.y >= b.top - reach && p.y <= b.bottom + reach
    }

/**
 * The frame around the photo and the texts, drawn over the preview the way the export draws
 * them. [frameRect] is the whole framed output on screen, [imageRect] the photo inside it.
 */
@Composable
fun DecorOverlay(
    frame: Frame,
    frameRect: RectF,
    imageRect: RectF,
    total: PxSize,
    texts: List<TextItem>,
    selected: Int?,
) {
    Canvas(Modifier.fillMaxSize()) {
        if (frame.style != FrameStyle.NONE) {
            val c = Color(frame.color)
            val f = frameRect
            val r = imageRect
            // One shape with a hole, reaching a pixel into the photo: separate bands would
            // leave half-covered pixels where they meet, and the photo's edges rarely fall on
            // whole pixels, both of which show the dark background as thin lines.
            val border = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(f.left, f.top, f.right, f.bottom))
                addRect(Rect(r.left + 1, r.top + 1, r.right - 1, r.bottom - 1))
            }
            drawPath(border, c)
        }
        if (texts.isNotEmpty()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val s = frameRect.width() / total.width
                native.save()
                native.translate(frameRect.left, frameRect.top)
                native.scale(s, s)
                DecorPainter.drawTexts(native, total, texts)
                native.restore()
            }
        }
        val item = selected?.let { texts.getOrNull(it) } ?: return@Canvas
        val b = textScreenBounds(item, total, frameRect)
        val pad = 6.dp.toPx()
        val dashed = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val topLeft = Offset(b.left - pad, b.top - pad)
        val size = Size(b.width() + 2 * pad, b.height() + 2 * pad)
        drawRect(Color.Black.copy(alpha = 0.4f), topLeft, size, style = Stroke(3.dp.toPx()))
        drawRect(Color.White, topLeft, size, style = Stroke(1.5.dp.toPx(), pathEffect = dashed))
    }
}
