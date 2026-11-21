// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.ui.home.EditedThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EditorReferenceLayout(
    reference: Uri?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Row(modifier) {
        Box(Modifier.weight(1f).fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center, content = content)
        if (reference != null) key(reference) {
            ReferencePhoto(reference, onClose, Modifier.weight(1f).fillMaxSize())
        }
    }
}

private data class ReferencePreview(val name: String?, val bitmap: Bitmap?)

@Composable
private fun ReferencePhoto(uri: Uri, onClose: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val photo by produceState<ReferencePreview?>(null, context, uri) {
        value = withContext(Dispatchers.IO) {
            val name = ImageLoader.queryName(context, uri)
            val saved = RecipeStore(context).load(uri)
            val bitmap = if (saved == null || saved == EditState()) ImageLoader.thumbnail(context, uri, 1400)
                else EditedThumbnails.render(context, uri, 1400, saved)
            ReferencePreview(name, bitmap)
        }
    }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_reference), style = MaterialTheme.typography.labelMedium)
                photo?.name?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, stringResource(R.string.editor_reference_close))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            val bitmap = photo?.bitmap
            when {
                bitmap != null -> Image(
                    bitmap.asImageBitmap(), stringResource(R.string.editor_reference),
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                )
                photo == null -> CircularProgressIndicator()
                else -> Text(stringResource(R.string.editor_reference_error), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
