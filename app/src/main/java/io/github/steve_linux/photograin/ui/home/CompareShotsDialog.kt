// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.model.BurstReview
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CompareShotsDialog(photos: List<Uri>, onPick: ((Uri) -> Unit)? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var candidate by remember(photos) { mutableIntStateOf(1) }
    val compared = if (onPick == null) photos else listOf(photos[0], photos[candidate])
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (onPick == null) R.string.compare_shots else R.string.burst_title)) },
        text = {
            Column {
                if (onPick != null) {
                    Text(stringResource(R.string.burst_hint))
                    if (photos.size > 2) TextButton(onClick = { candidate = 1 + candidate % (photos.size - 1) }) {
                        Text(stringResource(R.string.burst_candidate, candidate + 1, photos.size))
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
                    for (uri in compared) key(uri) {
                        val bitmap by produceState<Bitmap?>(null, uri) {
                            value = withContext(Dispatchers.IO) {
                                try {
                                    val image = ImageLoader.load(context, uri, 1600)
                                    val transform = Matrix().apply {
                                        if (image.base.mirror) setScale(-1f, 1f)
                                        postRotate(image.base.quarterTurns * 90f)
                                    }
                                    Bitmap.createBitmap(image.preview, 0, 0, image.preview.width, image.preview.height, transform, true)
                                        .also { if (it !== image.preview) image.preview.recycle() }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    null
                                } catch (_: OutOfMemoryError) {
                                    null
                                }
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                                val image = bitmap
                                if (image != null) {
                                    Image(image.asImageBitmap(), null, Modifier.matchParentSize().graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = pan.x * size.width
                                        translationY = pan.y * size.height
                                    }, contentScale = ContentScale.Fit)
                                    if (onPick != null) {
                                        val fit = minOf(maxWidth.value / image.width, maxHeight.value / image.height).coerceAtLeast(1e-6f)
                                        val x = (0.5f - pan.x / zoom * maxWidth.value / (image.width * fit)).coerceIn(0f, 1f)
                                        val y = (0.5f - pan.y / zoom * maxHeight.value / (image.height * fit)).coerceIn(0f, 1f)
                                        val score by produceState<Float?>(null, image, (x * 100).roundToInt(), (y * 100).roundToInt()) {
                                            value = null
                                            delay(160)
                                            value = withContext(Dispatchers.Default) { focusedSharpness(image, x, y) }
                                        }
                                        Text("+", Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)),
                                            color = Color.White)
                                        score?.let {
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

private fun focusedSharpness(image: Bitmap, x: Float, y: Float): Float {
    val side = (minOf(image.width, image.height) / 4).coerceAtLeast(1)
    val left = (x * image.width - side / 2f).roundToInt().coerceIn(0, image.width - side)
    val top = (y * image.height - side / 2f).roundToInt().coerceIn(0, image.height - side)
    val sampleSide = 96
    val patch = Bitmap.createBitmap(image, left, top, side, side)
    val sample = Bitmap.createScaledBitmap(patch, sampleSide, sampleSide, true)
    val pixels = IntArray(sampleSide * sampleSide)
    sample.getPixels(pixels, 0, sampleSide, 0, 0, sampleSide, sampleSide)
    if (sample !== patch) sample.recycle()
    if (patch !== image) patch.recycle()
    val luma = FloatArray(pixels.size) {
        val pixel = pixels[it]
        0.2126f * (pixel shr 16 and 255) + 0.7152f * (pixel shr 8 and 255) + 0.0722f * (pixel and 255)
    }
    return BurstReview.sharpness(luma, sampleSide)
}
