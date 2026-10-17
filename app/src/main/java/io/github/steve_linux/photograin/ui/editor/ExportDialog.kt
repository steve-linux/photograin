// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ExportFormat
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.PxSize

private val SIZE_PRESETS = listOf<Int?>(null, 4096, 2048, 1080)

@Composable
fun ExportDialog(
    initial: ExportOptions,
    sizeFor: (ExportOptions) -> PxSize?,
    onDismiss: () -> Unit,
    onExport: (ExportOptions) -> Unit,
) {
    var options by remember { mutableStateOf(initial) }
    val size = sizeFor(options)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            ExportOptionsEditor(options, onChange = { options = it }, Modifier.verticalScroll(rememberScrollState()), size)
        },
        confirmButton = { TextButton(onClick = { onExport(options) }) { Text(stringResource(R.string.export_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Format, quality, size, color profile and metadata choices; also the defaults in the settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportOptionsEditor(
    options: ExportOptions,
    onChange: (ExportOptions) -> Unit,
    modifier: Modifier = Modifier,
    size: PxSize? = null,
) {
    val format = options.format
    val quality = options.quality.toFloat()
    val maxEdge = options.maxLongEdge
    val profile = options.colorProfile
    Column(modifier) {
        Text(stringResource(R.string.export_format), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in ExportFormat.entries) {
                FilterChip(selected = format == f, onClick = { onChange(options.copy(format = f)) }, label = { Text(f.label) })
            }
        }
        if (format.lossy) {
            Spacer(Modifier.height(12.dp))
            val label = if (format == ExportFormat.JXL && quality.toInt() >= 100) {
                stringResource(R.string.export_quality_lossless)
            } else {
                stringResource(R.string.export_quality, quality.toInt())
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
            Slider(value = quality, onValueChange = { onChange(options.copy(quality = it.toInt())) }, valueRange = 50f..100f)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.export_size), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (preset in SIZE_PRESETS) {
                FilterChip(
                    selected = maxEdge == preset,
                    onClick = { onChange(options.copy(maxLongEdge = preset)) },
                    label = { Text(preset?.let { "$it px" } ?: stringResource(R.string.export_size_full)) },
                )
            }
        }
        size?.let {
            Text(
                stringResource(R.string.export_dimensions, it.width, it.height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.export_color_profile), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in ColorProfile.entries) {
                FilterChip(selected = profile == p, onClick = { onChange(options.copy(colorProfile = p)) }, label = { Text(profileLabel(p)) })
            }
        }
        if (profile != ColorProfile.SRGB) {
            Text(
                stringResource(R.string.export_color_profile_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.export_keep_metadata), Modifier.weight(1f))
            Switch(checked = options.keepMetadata, onCheckedChange = { onChange(options.copy(keepMetadata = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.export_keep_location), Modifier.weight(1f))
            Switch(
                checked = options.keepMetadata && options.keepLocation,
                enabled = options.keepMetadata,
                onCheckedChange = { onChange(options.copy(keepLocation = it)) },
            )
        }
    }
}

@Composable
fun ExportProgressDialog(progress: Float) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.exporting)) },
        text = {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        },
        confirmButton = {},
    )
}

private fun profileLabel(profile: ColorProfile) = when (profile) {
    ColorProfile.SRGB -> "sRGB"
    ColorProfile.DISPLAY_P3 -> "Display P3"
    ColorProfile.ADOBE_RGB -> "Adobe RGB"
}
