// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.balancePreview
import io.github.steve_linux.photograin.data.grayCardBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun WhiteBalanceBatchDialog(
    photos: List<Uri>,
    onApply: (Map<String, Pair<Float, Float>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selection by remember(photos) { mutableIntStateOf(0) }
    val selected = photos[selection]
    var reference by remember(photos) { mutableStateOf<Uri?>(null) }
    var area by remember(photos) { mutableStateOf(Offset(0.5f, 0.5f)) }
    var sampleSize by rememberSaveable { mutableFloatStateOf(0.10f) }
    val names by produceState(emptyMap<Uri, String>(), photos) {
        value = withContext(Dispatchers.IO) {
            photos.associateWith { ImageLoader.queryName(context, it) ?: it.lastPathSegment.orEmpty() }
        }
    }
    val source by produceState<Bitmap?>(null, selected) {
        value = null
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, selected, 450) }
    }
    val referencePhoto = reference
    val estimates by produceState<Map<Uri, Pair<Float, Float>>?>(null, photos, area, referencePhoto, sampleSize) {
        value = null
        value = withContext(Dispatchers.IO) {
            fun estimate(uri: Uri): Pair<Float, Float>? {
                val image = ImageLoader.thumbnail(context, uri, 320) ?: return null
                return try {
                    grayCardBalance(image, area.x, area.y, sampleSize)
                } finally {
                    image.recycle()
                }
            }
            if (referencePhoto != null) {
                estimate(referencePhoto)?.let { balance -> photos.associateWith { balance } }.orEmpty()
            } else buildMap {
                for (uri in photos) {
                    ensureActive()
                    estimate(uri)?.let { put(uri, it) }
                }
            }
        }
    }
    val currentImage = source
    val correction = estimates?.get(selected)
    val comparison by produceState<Pair<Bitmap, Bitmap>?>(null, selected, currentImage, correction) {
        value = null
        if (currentImage == null || correction == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val old = RecipeStore(context).load(selected)?.adjustments
            balancePreview(currentImage, old?.temperature ?: 0f, old?.tint ?: 0f) to
                balancePreview(currentImage, correction.first, correction.second)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_wb)) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(if (referencePhoto == null) R.string.batch_wb_hint else R.string.batch_wb_reference_hint))
                Row {
                    TextButton(onClick = { selection = (selection + photos.size - 1) % photos.size }) {
                        Text(stringResource(R.string.batch_wb_previous))
                    }
                    TextButton(onClick = { selection = (selection + 1) % photos.size }) {
                        Text(stringResource(R.string.batch_wb_next))
                    }
                }
                FilterChip(
                    selected = referencePhoto == selected,
                    onClick = { reference = if (referencePhoto == selected) null else selected },
                    label = { Text(stringResource(R.string.batch_wb_use_reference)) },
                )
                referencePhoto?.let {
                    Text(stringResource(R.string.match_reference, names[it] ?: it.lastPathSegment.orEmpty()))
                }
                Text(names[selected] ?: (selection + 1).toString())
                if (referencePhoto == null || referencePhoto == selected) {
                    currentImage?.let { SampleAreaImage(it, area, sampleSize, selected) { point -> area = point } }
                    Text(stringResource(R.string.batch_wb_sample_size))
                    Slider(value = sampleSize, onValueChange = { sampleSize = it }, valueRange = 0.025f..0.20f, steps = 6)
                }
                comparison?.let { (before, after) ->
                    Row {
                        for ((image, label) in listOf(before to R.string.match_before, after to R.string.match_after)) {
                            Column(Modifier.weight(1f)) {
                                Image(image.asImageBitmap(), null, Modifier.fillMaxWidth().height(96.dp))
                                Text(stringResource(label))
                            }
                        }
                    }
                }
                val results = estimates
                if (results == null) LinearProgressIndicator()
                else {
                    if (referencePhoto == null) Text(stringResource(R.string.batch_wb_count, results.size, photos.size))
                    if (correction == null) Text(stringResource(R.string.match_skip,
                        names[referencePhoto ?: selected].orEmpty()))
                    else Text(stringResource(R.string.batch_wb_correction, correction.first, correction.second))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !estimates.isNullOrEmpty(), onClick = {
                onApply(estimates.orEmpty().mapKeys { it.key.toString() })
            }) { Text(stringResource(R.string.match_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
