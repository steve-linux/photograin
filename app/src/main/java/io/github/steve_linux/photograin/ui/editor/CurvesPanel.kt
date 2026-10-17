// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.CurveChannel
import io.github.steve_linux.photograin.model.CurvePoint
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.ToneCurve
import kotlin.math.hypot

/**
 * Tone curve editor. Drag a point to move it, drag elsewhere on the curve to add one,
 * double-tap a point to remove it.
 */
@Composable
fun CurvesPanel(curves: Curves, onChange: (Curves) -> Unit, onChangeFinished: () -> Unit) {
    var channel by rememberSaveable { mutableStateOf(CurveChannel.MASTER) }
    val curve = curves[channel]
    val currentCurves by rememberUpdatedState(curves)
    val currentChannel by rememberUpdatedState(channel)
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    val lineColor = channelColor(channel, MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (c in CurveChannel.entries) {
                FilterChip(
                    selected = channel == c,
                    onClick = { channel = c },
                    label = { Text(stringResource(channelLabel(c))) },
                )
            }
            IconButton(onClick = {
                onChange(curves.with(channel, ToneCurve()))
                onChangeFinished()
            }) {
                Icon(Icons.Outlined.RestartAlt, stringResource(R.string.reset_curve))
            }
        }
        Canvas(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { p ->
                        val c = currentCurves[currentChannel]
                        val i = nearestPoint(c, p, size.width.toFloat(), size.height.toFloat(), 28.dp.toPx())
                        if (i >= 0) {
                            onChange(currentCurves.with(currentChannel, c.removePoint(i)))
                            onChangeFinished()
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            var c = currentCurves[currentChannel]
                            var i = nearestPoint(c, p, w, h, 28.dp.toPx())
                            if (i < 0) {
                                // Start a new point on the curve itself, so the shape does not jump.
                                val x = (p.x / w).coerceIn(0f, 1f)
                                val y = c.sample()[(x * 255).toInt()]
                                c = c.withPoint(CurvePoint(x, y))
                                onChange(currentCurves.with(currentChannel, c))
                                i = c.points.indexOfFirst { kotlin.math.abs(it.x - x) < 1e-6f }
                            }
                            dragIndex = i
                            dragStart = c.points.getOrNull(i)?.let { Offset(it.x * w, (1 - it.y) * h) } ?: p
                            dragTotal = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            if (dragIndex < 0) return@detectDragGestures
                            change.consume()
                            dragTotal += amount
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val pos = dragStart + dragTotal
                            val c = currentCurves[currentChannel]
                            val moved = c.movePoint(dragIndex, CurvePoint(pos.x / w, 1f - pos.y / h))
                            onChange(currentCurves.with(currentChannel, moved))
                        },
                        onDragEnd = {
                            if (dragIndex >= 0) onChangeFinished()
                            dragIndex = -1
                        },
                        onDragCancel = {
                            if (dragIndex >= 0) onChangeFinished()
                            dragIndex = -1
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            for (i in 1..3) {
                drawLine(gridColor, Offset(w * i / 4, 0f), Offset(w * i / 4, h))
                drawLine(gridColor, Offset(0f, h * i / 4), Offset(w, h * i / 4))
            }
            drawRect(gridColor, style = Stroke(1.dp.toPx()))
            drawLine(gridColor, Offset(0f, h), Offset(w, 0f))

            val samples = curve.sample(128)
            val path = Path()
            samples.forEachIndexed { i, y ->
                val px = w * i / (samples.size - 1)
                val py = h * (1 - y)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, lineColor, style = Stroke(2.dp.toPx()))
            curve.points.forEachIndexed { i, pt ->
                val center = Offset(pt.x * w, (1 - pt.y) * h)
                drawCircle(lineColor, radius = if (i == dragIndex) 8.dp.toPx() else 6.dp.toPx(), center = center)
            }
        }
    }
}

private fun nearestPoint(curve: ToneCurve, p: Offset, w: Float, h: Float, tolerance: Float): Int {
    var best = -1
    var bestDist = tolerance
    curve.points.forEachIndexed { i, pt ->
        val d = hypot(pt.x * w - p.x, (1 - pt.y) * h - p.y)
        if (d < bestDist) {
            best = i
            bestDist = d
        }
    }
    return best
}

private fun channelColor(c: CurveChannel, master: Color) = when (c) {
    CurveChannel.MASTER -> master
    CurveChannel.RED -> Color(0xFFE5534B)
    CurveChannel.GREEN -> Color(0xFF57AB5A)
    CurveChannel.BLUE -> Color(0xFF539BF5)
}

private fun channelLabel(c: CurveChannel) = when (c) {
    CurveChannel.MASTER -> R.string.channel_rgb
    CurveChannel.RED -> R.string.channel_red
    CurveChannel.GREEN -> R.string.channel_green
    CurveChannel.BLUE -> R.string.channel_blue
}
