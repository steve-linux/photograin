// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.model.CropDrag
import io.github.steve_linux.photograin.model.CropHandle
import io.github.steve_linux.photograin.model.CropRect

/**
 * Crop frame drawn over the uncropped photo. [imageRect] is where the frame is on screen;
 * [aspect] is the locked output ratio (width / height in pixels), or null for free-form.
 */
@Composable
fun CropOverlay(
    crop: CropRect,
    imageRect: RectF,
    aspect: Float?,
    frameWidth: Float,
    frameHeight: Float,
    onCropChange: (CropRect) -> Unit,
    onCropEnd: () -> Unit,
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentRect by rememberUpdatedState(imageRect)
    val normalizedRatio = aspect?.let { it * frameHeight / frameWidth }
    val currentRatio by rememberUpdatedState(normalizedRatio)
    var handle by remember { mutableStateOf<CropHandle?>(null) }
    var start by remember { mutableStateOf(crop) }
    var total by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val tol = 28.dp.toPx()
                detectDragGestures(
                    onDragStart = { p ->
                        val r = currentRect
                        handle = CropDrag.hitTest(
                            currentCrop,
                            (p.x - r.left) / r.width(),
                            (p.y - r.top) / r.height(),
                            tol / r.width(),
                            tol / r.height(),
                        )
                        start = currentCrop
                        total = Offset.Zero
                    },
                    onDragEnd = {
                        if (handle != null) onCropEnd()
                        handle = null
                    },
                    onDragCancel = {
                        if (handle != null) onCropEnd()
                        handle = null
                    },
                    onDrag = { change, amount ->
                        val h = handle ?: return@detectDragGestures
                        change.consume()
                        total += amount
                        val r = currentRect
                        onCropChange(CropDrag.drag(start, h, total.x / r.width(), total.y / r.height(), currentRatio))
                    },
                )
            },
    ) {
        val l = imageRect.left + crop.left * imageRect.width()
        val t = imageRect.top + crop.top * imageRect.height()
        val r = imageRect.left + crop.right * imageRect.width()
        val b = imageRect.top + crop.bottom * imageRect.height()
        val shade = Color.Black.copy(alpha = 0.55f)

        // Dim everything outside the crop.
        drawRect(shade, Offset(0f, 0f), Size(size.width, t))
        drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(shade, Offset(0f, t), Size(l, b - t))
        drawRect(shade, Offset(r, t), Size(size.width - r, b - t))

        // Rule of thirds, more visible while dragging.
        val grid = Color.White.copy(alpha = if (handle != null) 0.6f else 0.3f)
        for (i in 1..2) {
            val x = l + (r - l) * i / 3
            val y = t + (b - t) * i / 3
            drawLine(grid, Offset(x, t), Offset(x, b), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx())
        }
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(1.5.dp.toPx()))

        // Corner handles.
        val len = 20.dp.toPx()
        val sw = 4.dp.toPx()
        for ((cx, cy) in listOf(l to t, r to t, r to b, l to b)) {
            val dx = if (cx == l) len else -len
            val dy = if (cy == t) len else -len
            drawLine(Color.White, Offset(cx, cy), Offset(cx + dx, cy), strokeWidth = sw)
            drawLine(Color.White, Offset(cx, cy), Offset(cx, cy + dy), strokeWidth = sw)
        }
    }
}
