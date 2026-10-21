// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.RecipeVersion
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.ui.home.EditedThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The label of a version: its name, or "Version 2". */
@Composable
fun versionLabel(version: RecipeVersion): String =
    version.name ?: stringResource(R.string.version_number, version.id)

/**
 * The photo's versions as thumbnails: a tap opens one, a long press compares it with the
 * version being edited. Below, actions on the current version.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VersionsDialog(
    uri: Uri,
    versions: List<RecipeVersion>,
    current: Int,
    edit: EditState,
    onOpen: (Int) -> Unit,
    onNew: (copy: Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var comparing by remember { mutableStateOf<RecipeVersion?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val currentVersion = versions.firstOrNull { it.id == current } ?: RecipeVersion(current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.versions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(versions, key = { it.id }) { v ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(96.dp)
                                .combinedClickable(
                                    onClick = { onOpen(v.id) },
                                    onLongClick = { if (v.id != current) comparing = v },
                                ),
                        ) {
                            // The open version is drawn from the live edit, the others from their saved recipe.
                            VersionThumbnail(uri, v.id, if (v.id == current) edit else null, selected = v.id == current)
                            Text(
                                versionLabel(v),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.versions_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onNew(true) }, label = { Text(stringResource(R.string.version_duplicate)) })
                    AssistChip(onClick = { onNew(false) }, label = { Text(stringResource(R.string.version_new)) })
                    AssistChip(onClick = { renaming = true }, label = { Text(stringResource(R.string.version_rename)) })
                    AssistChip(
                        onClick = { confirmDelete = true },
                        enabled = versions.size > 1,
                        label = { Text(stringResource(R.string.version_delete)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )

    comparing?.let { other ->
        AlertDialog(
            onDismissRequest = { comparing = null },
            title = { Text(stringResource(R.string.version_compare_title, versionLabel(currentVersion), versionLabel(other))) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { VersionThumbnail(uri, current, edit, selected = true, large = true) }
                    Box(Modifier.weight(1f)) { VersionThumbnail(uri, other.id, null, selected = false, large = true) }
                }
            },
            confirmButton = {
                TextButton(onClick = { comparing = null; onOpen(other.id) }) {
                    Text(stringResource(R.string.version_open, versionLabel(other)))
                }
            },
            dismissButton = {
                TextButton(onClick = { comparing = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(currentVersion.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.version_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.version_number, current)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { renaming = false; onRename(name) }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.version_delete_title, versionLabel(currentVersion))) },
            text = { Text(stringResource(R.string.version_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.version_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun VersionThumbnail(uri: Uri, version: Int, live: EditState?, selected: Boolean, large: Boolean = false) {
    val context = LocalContext.current
    val size = if (large) 512 else 256
    // The open version renders from its saved recipe, which the editor writes on every change.
    val bitmap: Bitmap? by produceState<Bitmap?>(null, uri, version, live) {
        value = EditedThumbnails.load(context, uri, size, version)
            ?: withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, size) }
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 3.dp else 1.dp, border, RoundedCornerShape(8.dp)),
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
