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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.AiUpscaler
import io.github.steve_linux.photograin.data.ExportFormat
import io.github.steve_linux.photograin.data.ExportMetadataEdits
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.MetadataGroup
import io.github.steve_linux.photograin.model.HighlightGain
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.PxSize

private val SIZE_PRESETS = listOf<Int?>(null, 4096, 2048, 1080)
private val UPSCALE_FACTORS = listOf(2, 4)

private val BUILT_IN_PROFILES = listOf(
    R.string.export_profile_message to ExportOptions(quality = 85, maxLongEdge = 2048, keepMetadata = false),
    R.string.export_profile_print to ExportOptions(format = ExportFormat.TIFF, maxLongEdge = 3543, printPpi = 300),
    R.string.export_profile_archive to ExportOptions(format = ExportFormat.JXL, quality = 100),
)

@Composable
fun ExportDialog(
    initial: ExportOptions,
    sizeFor: (ExportOptions) -> PxSize?,
    onDismiss: () -> Unit,
    onExport: (ExportOptions) -> Unit,
    batch: Boolean = false,
) {
    var options by remember { mutableStateOf(initial) }
    var limitText by rememberSaveable { mutableStateOf(initial.maxFileSizeKb?.toString().orEmpty()) }
    var showMetadataEdits by rememberSaveable { mutableStateOf(false) }
    var author by rememberSaveable { mutableStateOf(initial.metadataEdits.author) }
    var copyright by rememberSaveable { mutableStateOf(initial.metadataEdits.copyright) }
    var description by rememberSaveable { mutableStateOf(initial.metadataEdits.description) }
    var date by rememberSaveable { mutableStateOf(initial.metadataEdits.date) }
    val invalidDate = options.keepMetadata && !ExportMetadataEdits.validDate(date)
    val limit = limitText.toIntOrNull()?.takeIf { it > 0 }
    val invalidLimit = options.format.supportsSizeLimit && limitText.isNotEmpty() && limit == null
    var customEdge by rememberSaveable { mutableStateOf(initial.maxLongEdge?.takeUnless { it in SIZE_PRESETS }?.toString().orEmpty()) }
    var customSize by rememberSaveable { mutableStateOf(customEdge.isNotEmpty()) }
    val edge = customEdge.toIntOrNull()?.takeIf { it > 0 }
    val effectiveOptions = if (customSize) options.copy(maxLongEdge = edge, upscale = 1, aiUpscale = false) else options
    val context = androidx.compose.ui.platform.LocalContext.current
    val profiles = remember(context) { context.getSharedPreferences("export_profiles", android.content.Context.MODE_PRIVATE) }
    var profileNames by remember { mutableStateOf(profiles.all.keys.sorted()) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    var printCm by rememberSaveable { mutableStateOf("") }
    var ppiText by rememberSaveable { mutableStateOf(initial.printPpi?.toString().orEmpty()) }
    var batchStart by rememberSaveable { mutableStateOf(initial.batchStart.toString()) }
    val ppi = ppiText.toIntOrNull()?.takeIf { it in 1..2400 }
    val cm = printCm.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val printEdge = if (cm != null && ppi != null) (cm * ppi / 2.54).toInt().takeIf { it in 1..99999 } else null
    val ready = (ppiText.isBlank() || ppi != null) && (printCm.isBlank() || printEdge != null) &&
        (!batch || options.batchPrefix.isBlank() || (batchStart.toIntOrNull() ?: 0) in 1..999999) && !invalidLimit && !invalidDate && (!customSize || edge != null)
    val configured = (if (printEdge != null) effectiveOptions.copy(maxLongEdge = printEdge, upscale = 1, aiUpscale = false) else effectiveOptions).copy(
        printPpi = ppi,
        ultraHdr = options.ultraHdr && options.format == ExportFormat.JPEG && android.os.Build.VERSION.SDK_INT >= 34,
        batchStart = batchStart.toIntOrNull() ?: 1,
        maxFileSizeKb = limit.takeIf { options.format.supportsSizeLimit },
        metadataEdits = ExportMetadataEdits(author, copyright, description, date),
    )
    fun applyProfile(stored: ExportOptions) {
        options = stored
        ppiText = stored.printPpi?.toString().orEmpty()
        printCm = ""
        batchStart = stored.batchStart.toString()
        limitText = stored.maxFileSizeKb?.toString().orEmpty()
        customEdge = stored.maxLongEdge?.takeUnless { it in SIZE_PRESETS }?.toString().orEmpty()
        customSize = customEdge.isNotEmpty()
        author = stored.metadataEdits.author
        copyright = stored.metadataEdits.copyright
        description = stored.metadataEdits.description
        date = stored.metadataEdits.date
        showProfiles = false
    }
    if (showProfiles) {
        AlertDialog(
            onDismissRequest = { showProfiles = false },
            title = { Text(stringResource(R.string.export_profiles)) },
            text = {
                androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    for ((label, preset) in BUILT_IN_PROFILES) {
                        TextButton(onClick = { applyProfile(preset) }) { Text(stringResource(label)) }
                    }
                    for (name in profileNames) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val stored = profiles.getString(name, null)?.let {
                                    runCatching { kotlinx.serialization.json.Json.decodeFromString(ExportOptions.serializer(), it) }.getOrNull()
                                }
                                if (stored != null) applyProfile(stored)
                            }, modifier = Modifier.weight(1f)) { Text(name) }
                            TextButton(onClick = {
                                profiles.edit().remove(name).apply()
                                profileNames = profiles.all.keys.sorted()
                            }) { Text(stringResource(R.string.export_profile_remove)) }
                        }
                    }
                    OutlinedTextField(value = profileName, onValueChange = { if (it.length <= 64) profileName = it },
                        label = { Text(stringResource(R.string.export_profile_name)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = ready && profileName.isNotBlank(), onClick = {
                    val name = profileName.trim()
                    profiles.edit().putString(name, kotlinx.serialization.json.Json.encodeToString(
                        ExportOptions.serializer(), configured,
                    )).apply()
                    profileNames = profiles.all.keys.sorted()
                    showProfiles = false
                }) { Text(stringResource(R.string.export_profile_save)) }
            },
            dismissButton = { TextButton(onClick = { showProfiles = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    val size = sizeFor(configured)
    // An enlargement smaller than asked for was capped to keep the photo in memory.
    val limited = effectiveOptions.upscale > 1 && size != null &&
        sizeFor(options.copy(upscale = 1, maxLongEdge = null))?.let { size.longEdge < it.longEdge * options.upscale - 1 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { showProfiles = true }) { Text(stringResource(R.string.export_profiles)) }
                if (options.format == ExportFormat.JPEG && android.os.Build.VERSION.SDK_INT >= 34) {
                    FilterChip(selected = options.ultraHdr, onClick = { options = options.copy(ultraHdr = !options.ultraHdr) },
                        label = { Text(stringResource(R.string.export_ultra_hdr)) })
                    if (options.ultraHdr) {
                        ParamSlider(
                            label = stringResource(R.string.export_hdr_gain),
                            value = options.hdrHighlightStops,
                            range = HighlightGain.MIN_STOPS..HighlightGain.MAX_STOPS,
                            display = stringResource(R.string.export_hdr_stops, options.hdrHighlightStops),
                            onChange = { options = options.copy(hdrHighlightStops = it) },
                            onChangeFinished = {},
                            onReset = { options = options.copy(hdrHighlightStops = HighlightGain.DEFAULT_STOPS) },
                        )
                        ParamSlider(
                            label = stringResource(R.string.export_hdr_threshold),
                            value = options.hdrHighlightThreshold,
                            range = 0f..HighlightGain.MAX_THRESHOLD,
                            display = "${(options.hdrHighlightThreshold * 100).toInt()}%",
                            onChange = { options = options.copy(hdrHighlightThreshold = it) },
                            onChangeFinished = {},
                            onReset = { options = options.copy(hdrHighlightThreshold = HighlightGain.DEFAULT_THRESHOLD) },
                        )
                    }
                }
                Text(stringResource(R.string.output_sharpen))
                Slider(value = options.outputSharpen, onValueChange = { options = options.copy(outputSharpen = it) })
                OutlinedTextField(value = ppiText, onValueChange = { if (it.length <= 4) ppiText = it },
                    singleLine = true, label = { Text(stringResource(R.string.print_ppi)) })
                OutlinedTextField(value = printCm, onValueChange = { if (it.length <= 8) printCm = it },
                    singleLine = true, label = { Text(stringResource(R.string.print_cm)) })
                printEdge?.let { Text("$it px") }
                if (batch) {
                    OutlinedTextField(value = options.batchPrefix, onValueChange = { options = options.copy(batchPrefix = it.take(64)) },
                        singleLine = true, label = { Text(stringResource(R.string.batch_prefix)) })
                    if (options.batchPrefix.isNotBlank()) {
                        OutlinedTextField(value = batchStart, onValueChange = { if (it.length <= 6) batchStart = it },
                            singleLine = true, label = { Text(stringResource(R.string.batch_start_number)) })
                        Text("${options.batchPrefix}-${batchStart.padStart(3, '0')}.${options.format.extension}")
                    }
                }
                ExportOptionsEditor(
                    effectiveOptions,
                    onChange = { options = it },
                    onSizeChange = { customSize = false },
                    customSizeSelected = customSize,
                    customSizeControls = {
                        FilterChip(
                            selected = customSize,
                            onClick = { customSize = !customSize },
                            label = { Text(stringResource(R.string.export_custom_size)) },
                        )
                    },
                    customSizeField = if (customSize) {
                        {
                            OutlinedTextField(
                                value = customEdge,
                                onValueChange = { input ->
                                    if (input.length <= 5 && input.all { it in '0'..'9' }) customEdge = input
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.export_custom_edge)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = customEdge.isNotEmpty() && edge == null,
                            )
                        }
                    } else null,
                    modifier = Modifier,
                    metadataEditor = {
                        FilterChip(
                            selected = showMetadataEdits,
                            onClick = { showMetadataEdits = !showMetadataEdits },
                            label = { Text(stringResource(R.string.export_edit_exif)) },
                        )
                        if (showMetadataEdits) {
                            MetadataEditField(author, { author = it }, R.string.export_exif_author)
                            MetadataEditField(copyright, { copyright = it }, R.string.export_exif_copyright)
                            MetadataEditField(description, { description = it }, R.string.export_exif_description)
                            MetadataEditField(date, { date = it }, R.string.export_exif_date, invalidDate)
                        }
                    },
                    size = size,
                    upscaleLimited = limited,
                    sizeLimitField = if (options.format.supportsSizeLimit) {
                        {
                            OutlinedTextField(
                                value = limitText,
                                onValueChange = { input ->
                                    if (input.length <= 7 && input.all { it in '0'..'9' }) limitText = input
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.export_file_limit)) },
                                supportingText = { Text(stringResource(R.string.export_file_limit_hint)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = invalidLimit,
                            )
                        }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { onExport(configured) },
            ) { Text(stringResource(R.string.export_action)) }
        },
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
    /** Enlarging is offered per photo; the settings keep only a size limit as default. */
    allowUpscale: Boolean = true,
    upscaleLimited: Boolean = false,
    sizeLimitField: (@Composable () -> Unit)? = null,
    onSizeChange: () -> Unit = {},
    customSizeSelected: Boolean = false,
    customSizeControls: (@Composable () -> Unit)? = null,
    customSizeField: (@Composable () -> Unit)? = null,
    metadataEditor: (@Composable () -> Unit)? = null,
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
        if (format.deep) {
            Text(
                stringResource(R.string.export_tiff_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        sizeLimitField?.invoke()
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.export_size), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (preset in SIZE_PRESETS) {
                FilterChip(
                    selected = !customSizeSelected && options.upscale == 1 && maxEdge == preset,
                    onClick = {
                        onSizeChange()
                        onChange(options.copy(maxLongEdge = preset, upscale = 1))
                    },
                    label = { Text(preset?.let { "$it px" } ?: stringResource(R.string.export_size_full)) },
                )
            }
            customSizeControls?.invoke()
            if (allowUpscale) {
                for (factor in UPSCALE_FACTORS) {
                    FilterChip(
                        selected = !customSizeSelected && options.upscale == factor,
                        onClick = {
                            onSizeChange()
                            onChange(options.copy(maxLongEdge = null, upscale = factor))
                        },
                        label = { Text(stringResource(R.string.export_upscale, factor)) },
                    )
                }
            }
        }
        customSizeField?.invoke()
        size?.let {
            Text(
                stringResource(R.string.export_dimensions, it.width, it.height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (options.upscale > 1) {
            if (AiUpscaler.available) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.export_upscale_method), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !options.aiUpscale,
                        onClick = { onChange(options.copy(aiUpscale = false)) },
                        label = { Text(stringResource(R.string.export_upscale_bicubic)) },
                    )
                    FilterChip(
                        selected = options.aiUpscale,
                        onClick = { onChange(options.copy(aiUpscale = true)) },
                        label = { Text(stringResource(R.string.export_upscale_ai)) },
                    )
                }
            }
            val ai = options.aiUpscale && AiUpscaler.available
            Text(
                stringResource(if (ai) R.string.export_upscale_ai_hint else R.string.export_upscale_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upscaleLimited) {
                Text(
                    stringResource(R.string.export_upscale_limited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        if (options.keepMetadata) {
            // Location keeps its own flag so photos shared before this choice existed stay private.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in MetadataGroup.entries) {
                    val kept = g in options.keptGroups
                    FilterChip(
                        selected = kept,
                        onClick = {
                            onChange(
                                if (g == MetadataGroup.LOCATION) {
                                    options.copy(keepLocation = !kept)
                                } else {
                                    options.copy(dropped = if (kept) options.dropped + g else options.dropped - g)
                                },
                            )
                        },
                        label = { Text(stringResource(g.label)) },
                    )
                }
            }
            Text(
                stringResource(R.string.export_metadata_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            metadataEditor?.invoke()
        }
    }
}

@Composable
private fun MetadataEditField(value: String, onChange: (String) -> Unit, label: Int, error: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 512) onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        isError = error,
        singleLine = true,
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
