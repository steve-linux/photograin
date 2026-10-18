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
import io.github.steve_linux.photograin.model.ShapeItem
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

/** Which part of a shape is grabbed: one of its two points, or the whole shape. */
enum class ShapePart { START, END, BODY }

/** Screen position of normalized (x, y) of the framed output drawn in [frameRect]. */
private fun toScreen(frameRect: RectF, x: Float, y: Float) =
    Offset(frameRect.left + x * frameRect.width(), frameRect.top + y * frameRect.height())

/**
 * The shape part under [p]: the points of the [selected] shape first, then the topmost shape
 * whose line or box passes within [reach] pixels.
 */
fun hitShape(shapes: List<ShapeItem>, selected: Int?, frameRect: RectF, p: Offset, reach: Float): Pair<Int, ShapePart>? {
    if (selected != null && selected in shapes.indices) {
        val s = shapes[selected]
        if ((toScreen(frameRect, s.x1, s.y1) - p).getDistance() <= reach) return selected to ShapePart.END
        if ((toScreen(frameRect, s.x0, s.y0) - p).getDistance() <= reach) return selected to ShapePart.START
    }
    for (i in shapes.indices.reversed()) {
        val s = shapes[i]
        val a = toScreen(frameRect, s.x0, s.y0)
        val b = toScreen(frameRect, s.x1, s.y1)
        val hit = if (s.isBox) {
            p.x >= minOf(a.x, b.x) - reach && p.x <= maxOf(a.x, b.x) + reach &&
                p.y >= minOf(a.y, b.y) - reach && p.y <= maxOf(a.y, b.y) + reach
        } else {
            distanceToSegment(p, a, b) <= reach
        }
        if (hit) return i to ShapePart.BODY
    }
    return null
}

/** [start] with [part] moved by the drag from screen point [from] to [to]. */
fun dragShape(start: ShapeItem, part: ShapePart, frameRect: RectF, from: Offset, to: Offset): ShapeItem {
    val dx = (to.x - from.x) / frameRect.width()
    val dy = (to.y - from.y) / frameRect.height()
    fun c(v: Float) = v.coerceIn(0f, 1f)
    return when (part) {
        ShapePart.START -> start.copy(x0 = c(start.x0 + dx), y0 = c(start.y0 + dy))
        ShapePart.END -> start.copy(x1 = c(start.x1 + dx), y1 = c(start.y1 + dy))
        ShapePart.BODY -> start.copy(x0 = start.x0 + dx, y0 = start.y0 + dy, x1 = start.x1 + dx, y1 = start.y1 + dy)
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val d = b - a
    val len2 = d.x * d.x + d.y * d.y
    if (len2 < 1e-6f) return (p - a).getDistance()
    val t = (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / len2).coerceIn(0f, 1f)
    return (p - (a + d * t)).getDistance()
}

/**
 * The frame around the photo, the shapes and the texts, drawn over the preview the way the
 * export draws them. [frameRect] is the whole framed output on screen, [imageRect] the photo
 * inside it.
 */
@Composable
fun DecorOverlay(
    frame: Frame,
    frameRect: RectF,
    imageRect: RectF,
    total: PxSize,
    texts: List<TextItem>,
    selected: Int?,
    shapes: List<ShapeItem>,
    selectedShape: Int?,
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
        if (texts.isNotEmpty() || shapes.isNotEmpty()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val s = frameRect.width() / total.width
                native.save()
                native.translate(frameRect.left, frameRect.top)
                native.scale(s, s)
                // A tiled watermark covers the whole output: keep it inside the framed photo.
                native.clipRect(0f, 0f, total.width.toFloat(), total.height.toFloat())
                DecorPainter.drawShapes(native, total, shapes)
                DecorPainter.drawTexts(native, total, texts)
                native.restore()
            }
        }
        selectedShape?.let { shapes.getOrNull(it) }?.let { shape ->
            for ((x, y) in listOf(shape.x0 to shape.y0, shape.x1 to shape.y1)) {
                val p = toScreen(frameRect, x, y)
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = p)
                drawCircle(Color.White, radius = 8.dp.toPx(), center = p, style = Stroke(2.5.dp.toPx()))
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
