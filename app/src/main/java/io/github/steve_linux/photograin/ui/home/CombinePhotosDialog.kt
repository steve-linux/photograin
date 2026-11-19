// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CompositionMode(val title: Int, val filename: String) {
    COLLAGE(R.string.combine_collage, "collage"),
    CONTACT(R.string.combine_contact, "contact-sheet"),
    STRIP(R.string.combine_strip, "horizontal-strip"),
    PANORAMA(R.string.combine_panorama, "panorama"),
    EXPOSURE(R.string.combine_exposure, "double-exposure"),
    HDR(R.string.combine_hdr, "exposure-fusion"),
    FOCUS(R.string.combine_focus, "focus-stack"),
    MEDIAN(R.string.combine_median, "median-stack"),
    LIGHTEN(R.string.combine_lighten, "lighten-stack"),
}

@Composable
internal fun CombinePhotosDialog(selection: List<Uri> = emptyList(), onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf(selection) }
    var mode by remember { mutableStateOf(if (selection.isEmpty()) CompositionMode.COLLAGE else CompositionMode.CONTACT) }
    var opacity by remember { mutableFloatStateOf(0.5f) }
    var screen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) {
            photos = it
            result = null
        }
    }
    val inputs = photos.take(when (mode) {
        CompositionMode.EXPOSURE -> 2
        CompositionMode.PANORAMA -> 6
        CompositionMode.HDR, CompositionMode.FOCUS -> 5
        CompositionMode.MEDIAN, CompositionMode.LIGHTEN -> 7
        else -> 12
    })
    val preview by produceState<Bitmap?>(null, inputs, mode, opacity, screen) {
        value = null
        result = null
        if (inputs.size < (if (mode == CompositionMode.CONTACT) 1 else 2)) return@produceState
        value = try {
            withContext(Dispatchers.IO) { composePhotos(context, inputs, mode, opacity, screen, 240) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: PanoramaAlignmentException) {
            result = R.string.combine_panorama_failed
            null
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            saving = true
            result = null
            scope.launch {
                result = try {
                    withContext(Dispatchers.IO) {
                        val bitmap = composePhotos(context, inputs, mode, opacity, screen, 1200)
                        try {
                            val stream = context.contentResolver.openOutputStream(uri, "wt")
                                ?: error("Cannot open destination")
                            stream.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    R.string.msg_exported
                } catch (e: CancellationException) {
                    throw e
                } catch (_: PanoramaAlignmentException) {
                    R.string.combine_panorama_failed
                } catch (_: Exception) {
                    R.string.msg_export_failed
                } catch (_: OutOfMemoryError) {
                    R.string.msg_out_of_memory
                } finally {
                    saving = false
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.combine_photos)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (choice in CompositionMode.entries) {
                    FilterChip(
                        selected = mode == choice,
                        onClick = { mode = choice; result = null },
                        enabled = !saving,
                        label = { Text(stringResource(choice.title)) },
                    )
                }
                TextButton(enabled = !saving, onClick = { picker.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.combine_select))
                }
                if (mode == CompositionMode.MEDIAN) Text(stringResource(R.string.combine_median_hint))
                if (mode == CompositionMode.FOCUS) Text(stringResource(R.string.combine_focus_hint))
                if (mode == CompositionMode.HDR) Text(stringResource(R.string.combine_hdr_hint))
                if (mode == CompositionMode.PANORAMA) Text(stringResource(R.string.combine_panorama_hint))
                if (inputs.size >= 2) Text(stringResource(R.string.combine_count, inputs.size))
                if (mode == CompositionMode.EXPOSURE) {
                    Text(stringResource(R.string.combine_opacity))
                    Slider(value = opacity, onValueChange = { opacity = it }, enabled = !saving)
                    if (Build.VERSION.SDK_INT >= 29) {
                        FilterChip(
                            selected = screen, onClick = { screen = !screen }, enabled = !saving,
                            label = { Text(stringResource(R.string.combine_screen)) },
                        )
                    }
                }
                preview?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 200.dp))
                }
                if (saving) Text(stringResource(R.string.exporting))
                result?.let { Text(stringResource(it)) }
            }
        },
        confirmButton = {
            TextButton(enabled = preview != null && !saving, onClick = { destination.launch("${mode.filename}.png") }) {
                Text(stringResource(R.string.export_action))
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun composePhotos(
    context: Context,
    uris: List<Uri>,
    mode: CompositionMode,
    opacity: Float,
    screen: Boolean,
    cell: Int,
): Bitmap {
    if (mode == CompositionMode.PANORAMA) return stitchPanorama(context, uris, cell)
    if (mode == CompositionMode.LIGHTEN) return fuseExposures(context, uris, if (cell > 240) 2000 else 480, lightenStack = true)
    if (mode == CompositionMode.MEDIAN) return fuseExposures(context, uris, if (cell > 240) 2000 else 480, medianStack = true)
    if (mode == CompositionMode.HDR || mode == CompositionMode.FOCUS) {
        return fuseExposures(context, uris, if (cell > 240) 2400 else 480, focusStack = mode == CompositionMode.FOCUS)
    }
    val columns = when (mode) {
        CompositionMode.COLLAGE, CompositionMode.CONTACT -> if (uris.size <= 4) 2 else 3
        CompositionMode.STRIP -> uris.size
        CompositionMode.EXPOSURE, CompositionMode.PANORAMA, CompositionMode.HDR, CompositionMode.FOCUS, CompositionMode.MEDIAN, CompositionMode.LIGHTEN -> 1
    }
    val grid = mode == CompositionMode.COLLAGE || mode == CompositionMode.CONTACT
    val rows = if (grid) (uris.size + columns - 1) / columns else 1
    val result = Bitmap.createBitmap(columns * cell, rows * cell, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(if (grid) Color.WHITE else Color.BLACK)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    try {
        for ((index, uri) in uris.withIndex()) {
            val photo = ImageLoader.load(context, uri, cell * 2)
            val bitmap = photo.preview
            try {
                val oriented = Matrix().apply {
                    if (photo.base.mirror) setScale(-1f, 1f)
                    postRotate(photo.base.quarterTurns * 90f)
                }
                val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                oriented.mapRect(bounds)
                val col = if (mode == CompositionMode.EXPOSURE) 0 else index % columns
                val row = if (mode == CompositionMode.EXPOSURE) 0 else index / columns
                val inset = if (grid) cell * 0.025f else 0f
                val box = RectF(col * cell + inset, row * cell + inset, (col + 1) * cell - inset, (row + 1) * cell - inset)
                if (mode == CompositionMode.CONTACT) box.bottom -= cell * 0.12f
                val widthScale = box.width() / bounds.width()
                val heightScale = box.height() / bounds.height()
                val scale = if (mode == CompositionMode.CONTACT) minOf(widthScale, heightScale) else maxOf(widthScale, heightScale)
                oriented.postScale(scale, scale)
                oriented.postTranslate(box.centerX() - bounds.centerX() * scale, box.centerY() - bounds.centerY() * scale)
                val overlay = mode == CompositionMode.EXPOSURE && index == 1
                paint.alpha = if (overlay) (opacity * 255).toInt() else 255
                if (Build.VERSION.SDK_INT >= 29) paint.blendMode = if (overlay && screen) BlendMode.SCREEN else null
                canvas.save()
                canvas.clipRect(box)
                canvas.drawBitmap(bitmap, oriented, paint)
                canvas.restore()
                if (mode == CompositionMode.CONTACT) {
                    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = cell * 0.045f
                    }
                    val name = photo.displayName ?: (index + 1).toString()
                    val end = label.breakText(name, true, box.width(), null)
                    canvas.drawText(name.take(end), box.left, (row + 1) * cell - inset, label)
                }
            } finally {
                bitmap.recycle()
            }
        }
        return result
    } catch (e: Throwable) {
        result.recycle()
        throw e
    }
}
