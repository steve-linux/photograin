// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.render.RgbHistogram

@Composable
internal fun HistogramPanel(
    histogram: RgbHistogram?,
    autoLightEnabled: Boolean,
    onAutoLight: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showClipping: Boolean,
    onClippingChange: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(
                onClick = onAutoLight,
                enabled = autoLightEnabled,
                label = { Text(stringResource(R.string.auto_light)) },
                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
            )
            FilterChip(
                selected = expanded,
                onClick = { onExpandedChange(!expanded) },
                label = { Text(stringResource(R.string.histogram_rgb)) },
            )
            if (expanded) {
                FilterChip(
                    selected = showClipping,
                    onClick = { onClippingChange(!showClipping) },
                    label = { Text(stringResource(R.string.histogram_clipping)) },
                )
            }
        }
        if (expanded) {
            val description = stringResource(R.string.histogram_rgb)
            val grid = MaterialTheme.colorScheme.outlineVariant
            Canvas(
                Modifier.fillMaxWidth().height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = description },
            ) {
                for (quarter in 1..3) {
                    val x = size.width * quarter / 4f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height))
                }
                val data = histogram ?: return@Canvas
                val peak = maxOf(data.red.max(), data.green.max(), data.blue.max(), 1)
                val channels = listOf(data.red to Color(0xFFEF5350), data.green to Color(0xFF66BB6A), data.blue to Color(0xFF42A5F5))
                val binWidth = size.width / 256
                for ((bins, color) in channels) {
                    val path = Path().apply {
                        moveTo(0f, size.height)
                        for (i in bins.indices) {
                            val y = size.height * (1f - bins[i].toFloat() / peak)
                            lineTo(i * binWidth, y)
                            lineTo((i + 1) * binWidth, y)
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.55f))
                }
            }
            if (showClipping) {
                Text(
                    stringResource(R.string.histogram_clipping_legend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
