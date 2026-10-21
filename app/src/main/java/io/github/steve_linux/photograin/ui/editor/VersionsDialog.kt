// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/** Long edge of the large renders on the versions screen. */
private const val LARGE = 1600
private const val SMALL = 256

/**
 * The photo's versions on a screen of their own, like Darktable's darkroom: the version being
 * looked at fills the screen, the others run along a filmstrip below. A tap on the strip shows
 * a version large; Compare puts it next to the one being edited.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    // Saved so a turn of the phone keeps the screen as it was.
    var shown by rememberSaveable { mutableIntStateOf(current) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val currentVersion = versions.firstOrNull { it.id == current } ?: RecipeVersion(current)
    val shownVersion = versions.firstOrNull { it.id == shown } ?: currentVersion
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            val header: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close))
                    }
                    Text(
                        stringResource(R.string.versions_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = comparing,
                        onClick = { comparing = !comparing },
                        enabled = shown != current,
                        label = { Text(stringResource(R.string.version_compare)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            // The large view: the version picked on the strip, or it next to the open one.
            val large: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier.padding(8.dp)) {
                    if (comparing && shown != current) {
                        val pair = listOf(currentVersion, shownVersion)
                        if (landscape) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (v in pair) LargeVersion(uri, v, v.id == current, Modifier.weight(1f))
                            }
                        } else {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (v in pair) LargeVersion(uri, v, v.id == current, Modifier.weight(1f))
                            }
                        }
                    } else {
                        LargeVersion(uri, shownVersion, shown == current, Modifier.fillMaxSize())
                    }
                }
            }
            val controls: @Composable () -> Unit = {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(versions, key = { it.id }) { v ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(if (landscape) 64.dp else 80.dp).clickable { shown = v.id },
                        ) {
                            // The open version's thumbnail follows the live edit.
                            VersionThumbnail(uri, v.id, if (v.id == current) edit else null, selected = v.id == shown)
                            Text(
                                versionLabel(v),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    AssistChip(onClick = { onNew(true) }, label = { Text(stringResource(R.string.version_duplicate)) })
                    AssistChip(onClick = { onNew(false) }, label = { Text(stringResource(R.string.version_new)) })
                    AssistChip(onClick = { renaming = true }, label = { Text(stringResource(R.string.version_rename)) })
                    AssistChip(
                        onClick = { confirmDelete = true },
                        enabled = versions.size > 1,
                        label = { Text(stringResource(R.string.version_delete)) },
                    )
                }
                Button(
                    onClick = { if (shown == current) onDismiss() else onOpen(shown) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (shown == current) {
                            stringResource(R.string.version_keep_editing, versionLabel(currentVersion))
                        } else {
                            stringResource(R.string.version_open, versionLabel(shownVersion))
                        },
                    )
                }
            }

            if (landscape) {
                // Sideways the height is short, so the controls move to a column at the side
                // and the photos keep the full height.
                Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                    large(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        header()
                        controls()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    header()
                    large(Modifier.weight(1f).fillMaxWidth())
                    controls()
                }
            }
        }
    }

    // Rename and delete act on the open version, which the editor shows and saves.
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

/** A version as large as the space allows, with its name over the corner. */
@Composable
private fun LargeVersion(uri: Uri, version: RecipeVersion, open: Boolean, modifier: Modifier) {
    val bitmap = rememberVersionBitmap(uri, version.id, LARGE, null)
    Box(modifier.fillMaxSize()) {
        if (bitmap == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(
            if (open) stringResource(R.string.version_editing, versionLabel(version)) else versionLabel(version),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun VersionThumbnail(uri: Uri, version: Int, live: EditState?, selected: Boolean) {
    val bitmap = rememberVersionBitmap(uri, version, SMALL, live)
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
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** [version] rendered from its saved recipe at [size]; the original when it has no edit. [key] re-renders on change. */
@Composable
private fun rememberVersionBitmap(uri: Uri, version: Int, size: Int, key: Any?): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri, version, size, key) {
        value = EditedThumbnails.load(context, uri, size, version)
            ?: withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, size) }
    }
    return bitmap
}
