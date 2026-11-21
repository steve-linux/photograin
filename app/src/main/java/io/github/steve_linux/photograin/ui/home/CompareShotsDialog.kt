// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.model.BurstReview
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CompareShotsDialog(photos: List<Uri>, onPick: ((Uri) -> Unit)? = null, isSimilar: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val canRank = onPick != null && !isSimilar
    var request by remember(photos) { mutableStateOf<Offset?>(null) }
    val region = request
    val ranking = key(photos, region) {
        val result by produceState<Map<Uri, Float>?>(null) {
            if (region == null) return@produceState
            value = withContext(Dispatchers.IO) {
                buildMap {
                    for (uri in photos) {
                        ensureActive()
                        val image = loadComparisonImage(context, uri) ?: continue
                        try {
                            put(uri, focusedSharpness(image, region))
                        } finally {
                            image.recycle()
                        }
                    }
                }
            }
        }
        result
    }
    val scores = ranking
    val ranked = remember(photos, scores) { scores?.let { BurstReview.ranked(photos, it) }.orEmpty() }
    val ordered = if (ranked.size >= 2) ranked else photos
    var candidate by remember(ordered) { mutableIntStateOf(1) }
    val compared = if (onPick == null) photos else listOf(ordered.first(), ordered[candidate])
    val first = compared.first()
    val firstImage = comparisonImage(context, first)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val rankingPending = region != null && scores == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isSimilar) R.string.similar_title else if (onPick == null) R.string.compare_shots else R.string.burst_title)) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                if (onPick != null) {
                    Text(stringResource(R.string.burst_hint))
                    if (canRank) {
                        TextButton(enabled = !rankingPending, onClick = {
                            val point = firstImage?.let { image -> focusPoint(image, viewport, zoom, pan) }
                            request = point ?: Offset(0.5f, 0.5f)
                        }) { Text(stringResource(R.string.burst_rank_action)) }
                        if (rankingPending) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (scores != null) {
                            if (ranked.size < 2) Text(stringResource(R.string.burst_rank_unavailable))
                            else Text(stringResource(R.string.burst_rank_count, ranked.size, photos.size))
                            TextButton(onClick = { request = null }) {
                                Text(stringResource(R.string.burst_rank_reset))
                            }
                        }
                    }
                    if (ordered.size > 2) TextButton(onClick = { candidate = 1 + candidate % (ordered.size - 1) }) {
                        Text(stringResource(R.string.burst_candidate, candidate + 1, ordered.size))
                    }
                }
                Row(Modifier.fillMaxWidth().height(240.dp).pointerInput(photos) {
                    detectTransformGestures { _, movement, factor, _ ->
                        zoom = (zoom * factor).coerceIn(1f, 8f)
                        val bound = (zoom - 1f) / 2f
                        val delta = Offset(movement.x * 2 / size.width, movement.y / size.height)
                        val position = pan + delta
                        pan = Offset(position.x.coerceIn(-bound, bound), position.y.coerceIn(-bound, bound))
                    }
                }) {
                    for ((index, uri) in compared.withIndex()) key(uri, index) {
                        val bitmap = if (index == 0) firstImage else comparisonImage(context, uri)
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (isSimilar || canRank) {
                                val name by produceState("", uri) {
                                    value = withContext(Dispatchers.IO) { ImageLoader.queryName(context, uri).orEmpty() }
                                }
                                val label = if (ranked.size >= 2) {
                                    stringResource(R.string.burst_rank_position, if (index == 0) 1 else candidate + 1, name)
                                } else name
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            var paneSize by remember { mutableStateOf(IntSize.Zero) }
                            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {
                                paneSize = it
                                if (index == 0) viewport = it
                            }) {
                                if (bitmap != null) {
                                    Image(bitmap.asImageBitmap(), null, Modifier.matchParentSize().graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = pan.x * size.width
                                        translationY = pan.y * size.height
                                    }, contentScale = ContentScale.Fit)
                                    if (onPick != null) {
                                        val point = focusPoint(bitmap, paneSize, zoom, pan)
                                        val liveScore by produceState<Float?>(null, bitmap,
                                            (point.x * 100).roundToInt(), (point.y * 100).roundToInt(), scores != null) {
                                            value = null
                                            if (scores != null) return@produceState
                                            delay(160)
                                            value = withContext(Dispatchers.Default) { focusedSharpness(bitmap, point) }
                                        }
                                        Text("+", Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)),
                                            color = Color.White)
                                        (scores?.get(uri) ?: liveScore)?.let {
                                            Text(
                                                stringResource(R.string.burst_sharpness, it.roundToInt()),
                                                Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.65f))
                                                    .padding(horizontal = 3.dp),
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }
                            }
                            if (onPick != null) {
                                TextButton(onClick = { onPick(uri) }, enabled = bitmap != null) {
                                    Text(stringResource(R.string.burst_pick))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun comparisonImage(context: Context, uri: Uri): Bitmap? = key(uri) {
    val bitmap by produceState<Bitmap?>(null, context, uri) {
        value = withContext(Dispatchers.IO) { loadComparisonImage(context, uri) }
    }
    bitmap
}

private fun loadComparisonImage(context: Context, uri: Uri): Bitmap? = try {
    val image = ImageLoader.load(context, uri, 1600)
    val transform = Matrix().apply {
        if (image.base.mirror) setScale(-1f, 1f)
        postRotate(image.base.quarterTurns * 90f)
    }
    Bitmap.createBitmap(image.preview, 0, 0, image.preview.width, image.preview.height, transform, true)
        .also { if (it !== image.preview) image.preview.recycle() }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

private fun focusPoint(image: Bitmap, size: IntSize, zoom: Float, pan: Offset): Offset {
    if (size.width <= 0 || size.height <= 0) return Offset(0.5f, 0.5f)
    val fit = minOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
    return Offset(
        (0.5f - pan.x / zoom * size.width / (image.width * fit)).coerceIn(0f, 1f),
        (0.5f - pan.y / zoom * size.height / (image.height * fit)).coerceIn(0f, 1f),
    )
}

private fun focusedSharpness(image: Bitmap, point: Offset): Float {
    val side = (minOf(image.width, image.height) / 4).coerceAtLeast(1)
    val left = (point.x * image.width - side / 2f).roundToInt().coerceIn(0, image.width - side)
    val top = (point.y * image.height - side / 2f).roundToInt().coerceIn(0, image.height - side)
    val sampleSide = 96
    val patch = Bitmap.createBitmap(image, left, top, side, side)
    val pixels = IntArray(sampleSide * sampleSide)
    try {
        val sample = Bitmap.createScaledBitmap(patch, sampleSide, sampleSide, true)
        try {
            sample.getPixels(pixels, 0, sampleSide, 0, 0, sampleSide, sampleSide)
        } finally {
            if (sample !== patch) sample.recycle()
        }
    } finally {
        if (patch !== image) patch.recycle()
    }
    val luma = FloatArray(pixels.size) {
        val pixel = pixels[it]
        0.2126f * (pixel shr 16 and 255) + 0.7152f * (pixel shr 8 and 255) + 0.0722f * (pixel and 255)
    }
    return BurstReview.sharpness(luma, sampleSide)
}
