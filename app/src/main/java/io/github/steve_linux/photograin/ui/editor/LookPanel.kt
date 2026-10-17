// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.LutEntry
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.Preset

/** Presets (built-in and saved) and creative LUTs with their strength. */
@Composable
fun LookPanel(
    look: Look,
    lutLoaded: Boolean,
    luts: List<LutEntry>,
    userPresets: List<Preset>,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onImportLut: (Uri) -> Unit,
    onSelectLut: (LutEntry?) -> Unit,
    onDeleteLut: (LutEntry) -> Unit,
    onAmount: (Float) -> Unit,
    onAmountFinished: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    // .cube files have no registered MIME type, so the picker has to offer every file.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportLut(uri)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SectionTitle(R.string.look_presets)
        ChipRow {
            for (preset in Preset.BUILT_IN) {
                AssistChip(
                    onClick = { onApplyPreset(preset) },
                    label = { Text(stringResource(presetLabel(preset.builtIn))) },
                )
            }
            for (preset in userPresets) {
                InputChip(
                    selected = false,
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.look_delete, preset.name),
                            modifier = Modifier.size(18.dp).clickableIcon { onDeletePreset(preset) },
                        )
                    },
                )
            }
            AssistChip(
                onClick = { naming = true },
                label = { Text(stringResource(R.string.look_save_preset)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
            )
        }

        SectionTitle(R.string.look_lut)
        ChipRow {
            FilterChip(
                selected = look.lutId == null,
                onClick = { onSelectLut(null) },
                label = { Text(stringResource(R.string.look_none)) },
            )
            for (entry in luts) {
                InputChip(
                    selected = look.lutId == entry.id,
                    onClick = { onSelectLut(entry) },
                    label = { Text(entry.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.look_delete, entry.name),
                            modifier = Modifier.size(18.dp).clickableIcon { onDeleteLut(entry) },
                        )
                    },
                )
            }
            AssistChip(
                onClick = { picker.launch(arrayOf("*/*")) },
                label = { Text(stringResource(R.string.look_import_lut)) },
                leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)) },
            )
        }

        if (look.lutId != null) {
            if (!lutLoaded) {
                Text(
                    stringResource(R.string.look_lut_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            ParamSlider(
                label = stringResource(R.string.look_amount),
                value = look.amount,
                range = 0f..1f,
                display = "${(look.amount * 100).toInt()}%",
                onChange = onAmount,
                onChangeFinished = onAmountFinished,
                onReset = { onAmount(1f); onAmountFinished() },
            )
        }
    }

    if (naming) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.look_save_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.look_preset_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { naming = false; onSavePreset(name) }) {
                    Text(stringResource(R.string.look_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) { Text(stringResource(R.string.look_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

private fun Modifier.clickableIcon(onClick: () -> Unit) = clickable(onClick = onClick)

@StringRes
private fun presetLabel(key: String?): Int = when (key) {
    "vivid" -> R.string.preset_vivid
    "warm" -> R.string.preset_warm
    "cool" -> R.string.preset_cool
    "soft" -> R.string.preset_soft
    "film" -> R.string.preset_film
    "bw" -> R.string.preset_bw
    "bw_contrast" -> R.string.preset_bw_contrast
    else -> R.string.preset_natural
}
