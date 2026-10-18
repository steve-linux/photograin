// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.LoadedImage
import io.github.steve_linux.photograin.data.Metadata
import io.github.steve_linux.photograin.data.MetadataEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The original's size and the EXIF data worth knowing, grouped as the export dialog groups them. */
@Composable
fun PhotoInfoDialog(image: LoadedImage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries by produceState<List<MetadataEntry>?>(null, image.uri) {
        value = withContext(Dispatchers.IO) { Metadata.read(context, image.uri) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_info)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                image.displayName?.let { InfoRow(stringResource(R.string.metadata_file), it) }
                InfoRow(stringResource(R.string.metadata_size), "${image.sourceWidth} × ${image.sourceHeight}")
                val list = entries ?: return@Column
                if (list.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.metadata_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for ((group, items) in list.groupBy { it.group }) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(group.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    for (e in items) InfoRow(stringResource(e.label), e.value)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
