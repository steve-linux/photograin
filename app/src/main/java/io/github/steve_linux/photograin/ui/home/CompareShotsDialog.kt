// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CompareShotsDialog(photos: List<Uri>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_shots)) },
        text = {
            Row(Modifier.fillMaxWidth().height(240.dp).pointerInput(photos) {
                detectTransformGestures { _, movement, factor, _ ->
                    zoom = (zoom * factor).coerceIn(1f, 8f)
                    val bound = (zoom - 1f) / 2f
                    val delta = Offset(movement.x * 2 / size.width, movement.y / size.height)
                    val position = pan + delta
                    pan = Offset(position.x.coerceIn(-bound, bound), position.y.coerceIn(-bound, bound))
                }
            }) {
                for (uri in photos) {
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
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                        bitmap?.let {
                            Image(it.asImageBitmap(), null, Modifier.matchParentSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x * size.width
                                translationY = pan.y * size.height
                            }, contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
