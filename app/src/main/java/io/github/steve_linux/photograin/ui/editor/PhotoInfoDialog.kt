// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.LoadedImage
import io.github.steve_linux.photograin.data.Metadata
import io.github.steve_linux.photograin.data.MetadataEntry
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.model.PhotoLibraryQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The original's size and the EXIF data worth knowing, grouped as the export dialog groups them. */
@Composable
fun PhotoInfoDialog(image: LoadedImage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context) }
    var rating by remember(image.uri, preferences) { mutableIntStateOf(preferences.rating(image.uri)) }
    var flag by remember(image.uri, preferences) { mutableStateOf(preferences.flag(image.uri)) }
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
                PhotoFlagPicker(flag) { chosen ->
                    preferences.setFlag(image.uri, chosen)
                    flag = chosen
                }
                PhotoRatingPicker(rating) { stars ->
                    preferences.setRating(image.uri, stars)
                    rating = stars
                }
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

/** Pick or reject, as Lightroom flags photos; choosing the same flag again clears it. */
@Composable
private fun PhotoFlagPicker(flag: PhotoLibraryQuery.Flag, onFlag: (PhotoLibraryQuery.Flag) -> Unit) {
    Text(
        stringResource(R.string.photo_flag),
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = flag == PhotoLibraryQuery.Flag.PICK,
            onClick = { onFlag(if (flag == PhotoLibraryQuery.Flag.PICK) PhotoLibraryQuery.Flag.NONE else PhotoLibraryQuery.Flag.PICK) },
            label = { Text(stringResource(R.string.photo_flag_pick)) },
            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = flag == PhotoLibraryQuery.Flag.REJECT,
            onClick = { onFlag(if (flag == PhotoLibraryQuery.Flag.REJECT) PhotoLibraryQuery.Flag.NONE else PhotoLibraryQuery.Flag.REJECT) },
            label = { Text(stringResource(R.string.photo_flag_reject)) },
            leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun PhotoRatingPicker(rating: Int, onRating: (Int) -> Unit) {
    Text(
        stringResource(R.string.photo_rating),
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val buttonWidth = minOf(48.dp, maxWidth / 5)
        Row(Modifier.selectableGroup()) {
            for (stars in 1..5) {
                Box(
                    Modifier.size(buttonWidth, 48.dp).selectable(
                        selected = rating == stars,
                        role = Role.RadioButton,
                        onClick = { onRating(stars) },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (stars <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = pluralStringResource(R.plurals.photo_rating_stars, stars, stars),
                        tint = if (stars <= rating) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (rating != 0) {
        TextButton(onClick = { onRating(0) }) { Text(stringResource(R.string.photo_rating_clear)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
