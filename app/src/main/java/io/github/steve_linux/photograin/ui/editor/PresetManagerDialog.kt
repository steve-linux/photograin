// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Preset
import kotlinx.coroutines.delay

@Composable
internal fun PresetManagerDialog(
    presets: List<Preset>, onRename: (String, String) -> Unit, onDuplicate: (String, String) -> Unit,
    onDelete: (Preset) -> Unit, onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var namingId by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicating by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = presets.filter { it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_manage)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.preset_search)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                if (visible.isEmpty()) Text(stringResource(R.string.preset_no_results), Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(visible, key = { it.id }) { preset ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { namingId = preset.id; duplicating = false }) {
                                    Icon(Icons.Outlined.Edit, stringResource(R.string.preset_rename_named, preset.name))
                                }
                                IconButton(onClick = { namingId = preset.id; duplicating = true }) {
                                    Icon(Icons.Outlined.ContentCopy, stringResource(R.string.preset_duplicate_named, preset.name))
                                }
                                IconButton(onClick = { deletingId = preset.id }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.look_delete, preset.name))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    presets.firstOrNull { it.id == namingId }?.let { preset ->
        val initial = if (duplicating) stringResource(R.string.preset_copy_name, preset.name) else preset.name
        var name by rememberSaveable(namingId, duplicating) { mutableStateOf(initial.take(40)) }
        val focus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { namingId = null },
            title = { Text(stringResource(if (duplicating) R.string.preset_duplicate else R.string.preset_rename)) },
            text = {
                val keyboard = LocalSoftwareKeyboardController.current
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, modifier = Modifier.focusRequester(focus),
                    label = { Text(stringResource(R.string.look_preset_name)) })
                LaunchedEffect(Unit) { delay(150); focus.requestFocus(); keyboard?.show() }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                if (duplicating) onDuplicate(preset.id, name) else onRename(preset.id, name)
                namingId = null
                query = ""
            }) { Text(stringResource(R.string.look_save)) } },
            dismissButton = { TextButton(onClick = { namingId = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    presets.firstOrNull { it.id == deletingId }?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.look_delete, preset.name)) },
            text = { Text(stringResource(R.string.preset_delete_hint)) },
            confirmButton = { TextButton(onClick = { onDelete(preset); deletingId = null }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
