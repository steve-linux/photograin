// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    sizeFor: (ExportOptions) -> PxSize?,
    onDismiss: () -> Unit,
    onExport: (ExportOptions) -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.JPEG) }
    var quality by rememberSaveable { mutableStateOf(92f) }
    var maxEdge by rememberSaveable { mutableStateOf<Int?>(null) }
    var keepLocation by rememberSaveable { mutableStateOf(false) }
    var profile by rememberSaveable { mutableStateOf(ColorProfile.SRGB) }
    val options = ExportOptions(format, quality.toInt(), maxEdge, keepLocation, profile)
    val size = sizeFor(options)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                Text(stringResource(R.string.export_format), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (f in ExportFormat.entries) {
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) })
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
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 50f..100f)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.export_size), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (preset in SIZE_PRESETS) {
                        FilterChip(
                            selected = maxEdge == preset,
                            onClick = { maxEdge = preset },
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
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (p in ColorProfile.entries) {
                        FilterChip(selected = profile == p, onClick = { profile = p }, label = { Text(profileLabel(p)) })
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
                    Text(stringResource(R.string.export_keep_location), Modifier.weight(1f))
                    Switch(checked = keepLocation, onCheckedChange = { keepLocation = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(options) }) { Text(stringResource(R.string.export_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
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
