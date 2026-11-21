// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.model.ToneHistogram
import kotlin.math.sqrt

@Composable
internal fun HistogramOverlay(histogram: ToneHistogram, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.Black.copy(alpha = 0.78f)) {
        Canvas(Modifier.size(136.dp, 70.dp)) {
            val peak = sqrt((histogram.channels.maxOf { it.max() }).coerceAtLeast(1).toFloat())
            val colors = listOf(Color(0xffff6666), Color(0xff74ff74), Color(0xff709cff), Color.White)
            for ((channel, counts) in histogram.channels.withIndex()) {
                fun point(bin: Int) = Offset(
                    size.width * bin / 255f,
                    size.height * (1f - sqrt(counts[bin].toFloat()) / peak),
                )
                for (bin in 1..255) {
                    drawLine(colors[channel], point(bin - 1), point(bin), strokeWidth = 1.dp.toPx())
                }
            }
        }
    }
}

@Composable
internal fun WaveformOverlay(histogram: ToneHistogram, modifier: Modifier = Modifier, parade: Boolean = false) {
    val density = histogram.waveform ?: return
    Surface(modifier, color = Color.Black.copy(alpha = 0.85f)) {
        Canvas(Modifier.size(if (parade) 228.dp else 176.dp, 96.dp)) {
            val grid = ToneHistogram.SCOPE_SIZE
            val panelWidth = if (parade) size.width / 3 else size.width
            val width = panelWidth / grid
            val height = size.height / grid
            val colors = listOf(Color(0xffff6464), Color(0xff6cfb83), Color(0xff638bff))
            if (parade) {
                for (channel in 1..2) {
                    val x = panelWidth * channel
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height))
                }
                for (step in 1..3) {
                    val y = size.height * step / 4
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y))
                }
            }
            for ((channel, samples) in density.withIndex()) for (index in samples.indices) {
                if (samples[index] == 0) continue
                val origin = if (parade) channel * panelWidth else 0f
                drawRect(
                    color = colors[channel],
                    topLeft = Offset(origin + index % grid * width, (grid - 1 - index / grid) * height),
                    size = Size(width, height),
                    alpha = (0.18f + sqrt(samples[index].toFloat()) * 0.12f).coerceAtMost(0.95f),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

@Composable
internal fun VectorscopeOverlay(histogram: ToneHistogram, modifier: Modifier = Modifier) {
    val density = histogram.vectorscope ?: return
    Surface(modifier, color = Color.Black.copy(alpha = 0.85f)) {
        Canvas(Modifier.size(136.dp)) {
            val grid = ToneHistogram.SCOPE_SIZE
            val cell = size.width / grid
            val guide = Color.White.copy(alpha = 0.3f)
            drawCircle(guide, radius = size.width * 0.48f, style = androidx.compose.ui.graphics.drawscope.Stroke())
            drawLine(guide, Offset(center.x, 0f), Offset(center.x, size.height))
            drawLine(guide, Offset(0f, center.y), Offset(size.width, center.y))
            val targets = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow)
            for (target in targets) {
                val index = ToneHistogram.chromaIndex(
                    (target.red * 255).toInt(), (target.green * 255).toInt(), (target.blue * 255).toInt(),
                )
                drawCircle(
                    target.copy(alpha = 0.85f), radius = 2.dp.toPx(),
                    center = Offset((index % grid + 0.5f) * cell, (grid - 0.5f - index / grid) * cell),
                )
            }
            for ((index, count) in density.withIndex()) {
                if (count == 0) continue
                drawRect(
                    Color.White,
                    topLeft = Offset(index % grid * cell, (grid - 1 - index / grid) * cell),
                    size = Size(cell, cell),
                    alpha = (0.22f + sqrt(count.toFloat()) * 0.12f).coerceAtMost(0.95f),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
