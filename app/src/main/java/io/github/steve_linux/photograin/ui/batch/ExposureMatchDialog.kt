// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.exposurePreview
import io.github.steve_linux.photograin.data.regionLuminance
import io.github.steve_linux.photograin.model.PhotoAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.pow

private data class ExposureEstimate(val uri: Uri, val name: String, val current: Float, val delta: Float?)

@Composable
internal fun ExposureMatchDialog(photos: List<Uri>, onApply: (Map<String, Float>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var referenceIndex by remember(photos) { mutableIntStateOf(0) }
    val reference = photos[referenceIndex]
    var area by remember(reference) { mutableStateOf(Offset(0.5f, 0.5f)) }
    val referenceImage by produceState<Bitmap?>(null, reference) {
        value = null
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, reference, 450) }
    }
    val names by produceState(emptyMap<Uri, String>(), photos) {
        value = withContext(Dispatchers.IO) {
            photos.associateWith { ImageLoader.queryName(context, it) ?: it.lastPathSegment.orEmpty() }
        }
    }
    val activeImage = referenceImage
    val estimates by produceState<List<ExposureEstimate>?>(null, reference, area, activeImage) {
        value = null
        if (activeImage == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val recipes = RecipeStore(context)
            val refStops = recipes.load(reference)?.adjustments?.exposure ?: 0f
            val refLuma = regionLuminance(activeImage, area.x, area.y)?.times(2.0.pow(refStops.toDouble()))
            if (refLuma == null) return@withContext emptyList()
            photos.map { uri ->
                ensureActive()
                val stops = recipes.load(uri)?.adjustments?.exposure ?: 0f
                val preview = if (uri == reference) null else ImageLoader.thumbnail(context, uri, 450)
                val sample = try { preview?.let { regionLuminance(it, area.x, area.y) } } finally { preview?.recycle() }
                val delta = if (uri == reference) 0f else sample?.let {
                    PhotoAnalysis.exposureDelta(refLuma, it * 2.0.pow(stops.toDouble()))
                }
                ExposureEstimate(uri, ImageLoader.queryName(context, uri) ?: uri.lastPathSegment.orEmpty(), stops,
                    delta?.let { (stops + it).coerceIn(-4f, 4f) - stops })
            }
        }
    }
    val results = estimates
    val corrections = results.orEmpty().filter { it.delta != null && it.uri != reference }
        .associate { it.uri.toString() to checkNotNull(it.delta) }
    var previewUri by remember(reference, area) { mutableStateOf<Uri?>(null) }
    val candidate = results?.firstOrNull { it.uri == previewUri && it.delta != null }
    val preview by produceState<Pair<Bitmap, Bitmap>?>(null, candidate) {
        value = null
        val entry = candidate ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val raw = ImageLoader.thumbnail(context, entry.uri, 350) ?: return@withContext null
            try {
                exposurePreview(raw, entry.current) to exposurePreview(raw, entry.current + checkNotNull(entry.delta))
            } finally {
                raw.recycle()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_exposure)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.match_hint))
                TextButton(onClick = { referenceIndex = (referenceIndex + 1) % photos.size }) {
                    Text(stringResource(R.string.match_reference, names[reference] ?: (referenceIndex + 1).toString()))
                }
                activeImage?.let { image ->
                    Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height).pointerInput(reference) {
                        detectTapGestures { point -> area = Offset(
                            (point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f),
                        ) }
                    }) {
                        Image(image.asImageBitmap(), stringResource(R.string.match_target), Modifier.matchParentSize(),
                            contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.matchParentSize()) {
                            val width = size.width * 0.16f
                            val height = size.height * 0.16f
                            val left = (area.x * size.width - width / 2).coerceIn(0f, size.width - width)
                            val top = (area.y * size.height - height / 2).coerceIn(0f, size.height - height)
                            drawRect(Color.White, Offset(left, top), Size(width, height), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                preview?.let { (before, after) ->
                    Row {
                        for ((image, label) in listOf(before to R.string.match_before, after to R.string.match_after)) {
                            Column(Modifier.weight(1f)) {
                                Image(image.asImageBitmap(), null, Modifier.fillMaxWidth().height(96.dp))
                                Text(stringResource(label))
                            }
                        }
                    }
                }
                if (results == null) LinearProgressIndicator()
                else if (results.isEmpty()) Text(stringResource(R.string.match_unavailable))
                results.orEmpty().filterNot { it.uri == reference }.forEach { entry ->
                    val delta = entry.delta
                    if (delta == null) Text(stringResource(R.string.match_skip, entry.name))
                    else TextButton(onClick = { previewUri = entry.uri }) {
                        Text(stringResource(R.string.match_delta, entry.name, delta))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = corrections.isNotEmpty(), onClick = { onApply(corrections) }) {
                Text(stringResource(R.string.match_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
