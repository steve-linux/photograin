// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.photoDigest
import io.github.steve_linux.photograin.data.photoSignature
import io.github.steve_linux.photograin.model.PhotoAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class IndexedPhoto(val uri: Uri, val name: String, val digest: String?, val visual: PhotoAnalysis.Signature?)
private data class SimilarGroup(val photos: List<IndexedPhoto>, val exact: Boolean)
private data class SimilarResult(val groups: List<SimilarGroup>, val unreadable: Int)

@Composable
internal fun SimilarPhotosDialog(photos: List<Uri>, visible: Boolean, onCompare: (List<Uri>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var done by remember(photos) { mutableIntStateOf(0) }
    val result by produceState<SimilarResult?>(null, photos) {
        done = 0
        val indexed = withContext(Dispatchers.IO) {
            photos.mapIndexed { index, uri ->
                ensureActive()
                val name = ImageLoader.queryName(context, uri) ?: uri.lastPathSegment.orEmpty()
                val hash = photoDigest(context, uri)
                val bitmap = ImageLoader.thumbnail(context, uri, 128)
                val visual = try { bitmap?.let(::photoSignature) } finally { bitmap?.recycle() }
                done = index + 1
                IndexedPhoto(uri, name, hash, visual)
            }
        }
        value = withContext(Dispatchers.Default) {
            val exact = indexed.filter { it.digest != null }.groupBy { it.digest }.values.filter { it.size >= 2 }
            val representatives = indexed.distinctBy { it.digest ?: it.uri.toString() }
            val visual = mutableListOf<MutableList<IndexedPhoto>>()
            for (photo in representatives) {
                ensureActive()
                val signature = photo.visual ?: continue
                val group = visual.firstOrNull { PhotoAnalysis.similar(checkNotNull(it.first().visual), signature) }
                if (group == null) visual.add(mutableListOf(photo)) else group.add(photo)
            }
            SimilarResult(exact.map { SimilarGroup(it, true) } + visual.filter { it.size >= 2 }.map { SimilarGroup(it, false) },
                indexed.count { it.digest == null && it.visual == null })
        }
    }
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.similar_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                val scan = result
                if (scan == null) {
                    Text(stringResource(R.string.similar_progress, done, photos.size))
                    LinearProgressIndicator(progress = { done.toFloat() / photos.size.coerceAtLeast(1) })
                } else {
                    if (scan.groups.isEmpty()) Text(stringResource(R.string.similar_empty))
                    if (scan.unreadable > 0) Text(stringResource(R.string.similar_unreadable, scan.unreadable))
                    for (group in scan.groups) {
                        TextButton(onClick = { onCompare(group.photos.map { it.uri }) }) {
                            Column {
                                Text(stringResource(if (group.exact) R.string.similar_exact else R.string.similar_visual, group.photos.size))
                                Text(group.photos.first().name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
