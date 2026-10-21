// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RecipeVersion
import io.github.steve_linux.photograin.ui.editor.versionLabel
import io.github.steve_linux.photograin.ui.guide.GuideEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<Uri>,
    onOpenPicker: () -> Unit,
    onOpen: (uri: Uri, version: Int) -> Unit,
    onBatch: () -> Unit,
    showTip: Boolean,
    onDismissTip: () -> Unit,
    onSettings: () -> Unit,
    onGuide: () -> Unit,
) {
    var comparing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Each version of a photo gets its own tile, as Darktable shows duplicates.
    val entries by produceState(recents.map { HomeEntry(it, RecipeVersion(1), 1) }, recents) {
        value = withContext(Dispatchers.IO) {
            val store = RecipeStore(context)
            recents.flatMap { uri ->
                val versions = store.versions(uri)
                versions.map { HomeEntry(uri, it, versions.size) }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (recents.isNotEmpty()) {
                        IconButton(onClick = { comparing = !comparing }) {
                            Icon(
                                if (comparing) Icons.Outlined.GridView else Icons.Outlined.Compare,
                                contentDescription = stringResource(
                                    if (comparing) R.string.home_show_grid else R.string.home_show_compare,
                                ),
                            )
                        }
                    }
                    TextButton(onClick = onBatch) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_batch))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPicker,
                icon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                text = { Text(stringResource(R.string.open_photo)) },
            )
        },
    ) { padding ->
        if (recents.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                GuideEntry(onGuide)
                if (showTip) {
                    Spacer(Modifier.height(12.dp))
                    PresetTip(onDismissTip)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { GuideEntry(onGuide) }
                if (showTip) {
                    item(span = { GridItemSpan(maxLineSpan) }) { PresetTip(onDismissTip) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.recent_edits),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                if (comparing) {
                    item(span = { GridItemSpan(maxLineSpan) }) { CompareHeader() }
                    items(entries, key = { "compare ${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                        CompareRow(entry, onClick = { onOpen(entry.uri, entry.version.id) })
                    }
                } else {
                    items(entries, key = { it.key }) { entry ->
                        if (entry.count > 1) {
                            VersionTile(entry, onClick = { onOpen(entry.uri, entry.version.id) })
                        } else {
                            RecentThumbnail(entry.uri, onClick = { onOpen(entry.uri, 1) })
                        }
                    }
                }
            }
        }
    }
}

/** Explains where presets are saved, which new users rarely find on their own. */
@Composable
private fun PresetTip(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_tip_title), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            // {icon} in the text stands for the editor's compare button, drawn inline.
            val body = stringResource(R.string.home_tip_body)
            val text = buildAnnotatedString {
                val parts = body.split("{icon}", limit = 2)
                append(parts[0])
                if (parts.size == 2) {
                    appendInlineContent(COMPARE_ICON, "[compare]")
                    append(parts[1])
                }
            }
            val iconContent = mapOf(
                COMPARE_ICON to InlineTextContent(
                    Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter),
                ) { Icon(Icons.Outlined.Compare, contentDescription = stringResource(R.string.compare)) },
            )
            Text(
                text,
                inlineContent = iconContent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_tip_dismiss))
            }
        }
    }
}

private const val COMPARE_ICON = "compare"

@Composable
internal fun RecentThumbnail(uri: Uri, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb: Bitmap? by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, 384) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One version of a recent photo; [count] is how many the photo has. */
private data class HomeEntry(val uri: Uri, val version: RecipeVersion, val count: Int) {
    val key get() = "$uri#${version.id}"
}

/** A version of a photo with several: its own edit, and its name in a corner. */
@Composable
private fun VersionTile(entry: HomeEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb: Bitmap? by produceState<Bitmap?>(null, entry.uri, entry.version.id) {
        value = EditedThumbnails.load(context, entry.uri, 384, entry.version.id)
            ?: withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, entry.uri, 384) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        VersionBadge(entry.version, Modifier.align(Alignment.BottomStart).padding(4.dp))
    }
}

@Composable
private fun VersionBadge(version: RecipeVersion, modifier: Modifier = Modifier) {
    Text(
        versionLabel(version),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun CompareHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (label in listOf(R.string.home_original, R.string.home_edited)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The original and the edited photo side by side, whole, so the change is easy to judge. */
@Composable
private fun CompareRow(entry: HomeEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val uri = entry.uri
    val original: Bitmap? by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, COMPARE_SIZE) }
    }
    // (rendered, bitmap): the right side stays empty until the render is done.
    val edited by produceState<Pair<Boolean, Bitmap?>>(false to null, uri, entry.version.id) {
        value = true to EditedThumbnails.load(context, uri, COMPARE_SIZE, entry.version.id)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        // Without a recipe the photo is unchanged, so both sides show the original.
        for (bitmap in listOf(original, if (edited.first) edited.second ?: original else null)) {
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (entry.count > 1 && bitmap != null && bitmap !== original) {
                    VersionBadge(entry.version, Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
            }
        }
    }
}

private const val COMPARE_SIZE = 512
