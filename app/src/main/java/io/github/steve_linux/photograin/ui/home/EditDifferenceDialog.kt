// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.render.Exporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EditDifferenceDialog(uri: Uri, version: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val result by produceState<Result<List<Bitmap>>?>(null, uri, version) {
        value = try {
            Result.success(withContext(Dispatchers.IO) { renderDifference(context, uri, version) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Result.failure(e)
        }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_difference)) },
        text = {
            val photos = result?.getOrNull()
            if (photos == null) {
                if (result == null) CircularProgressIndicator()
                else Text(stringResource(R.string.edit_difference_unavailable))
            } else {
                val labels = listOf(R.string.difference_original, R.string.difference_edited, R.string.difference_delta)
                Row(Modifier.fillMaxWidth().pointerInput(photos) {
                    detectTransformGestures { _, movement, factor, _ ->
                        zoom = (zoom * factor).coerceIn(1f, 8f)
                        val bound = (zoom - 1f) / 2f
                        pan = Offset(
                            (pan.x + movement.x * 3 / size.width).coerceIn(-bound, bound),
                            (pan.y + movement.y / size.height).coerceIn(-bound, bound),
                        )
                    }
                }) {
                    for ((index, bitmap) in photos.withIndex()) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(labels[index]))
                            Box(Modifier.fillMaxWidth().height(190.dp).clipToBounds()) {
                                Image(bitmap.asImageBitmap(), null, Modifier.matchParentSize().graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x * size.width
                                    translationY = pan.y * size.height
                                }, contentScale = ContentScale.Fit)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private suspend fun renderDifference(context: Context, uri: Uri, version: Int): List<Bitmap> {
    val edit = requireNotNull(RecipeStore(context).load(uri, version))
    val edited = requireNotNull(EditedThumbnails.load(context, uri, 1000, version))
    val base = ImageLoader.baseOrientation(context, uri)
    val original = RegionSource.open(context, uri, edit.rawHighlight.libRawMode, edit.rawWhiteBalance).use {
        val reference = EditState(
            geometry = edit.geometry,
            frame = edit.frame,
            adjustments = EditState().adjustments.copy(lensDistortion = edit.adjustments.lensDistortion),
        )
        Exporter.render(it, base, reference, null, 1000, ColorProfile.SRGB) {}
    }
    check(original.width == edited.width && original.height == edited.height)
    val width = edited.width
    val difference = Bitmap.createBitmap(width, edited.height, Bitmap.Config.ARGB_8888)
    val a = IntArray(width)
    val b = IntArray(width)
    for (y in 0 until edited.height) {
        original.getPixels(a, 0, width, 0, y, width, 1)
        edited.getPixels(b, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            val left = a[x]
            val right = b[x]
            fun delta(shift: Int): Int {
                val lhs = ((left ushr shift) and 255) * (left ushr 24) / 255
                val rhs = ((right ushr shift) and 255) * (right ushr 24) / 255
                return (kotlin.math.abs(lhs - rhs) * 3).coerceAtMost(255)
            }
            a[x] = android.graphics.Color.rgb(delta(16), delta(8), delta(0))
        }
        difference.setPixels(a, 0, width, 0, y, width, 1)
    }
    return listOf(original, edited, difference)
}
