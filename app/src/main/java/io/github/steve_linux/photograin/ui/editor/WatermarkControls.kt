// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ExportWatermark
import io.github.steve_linux.photograin.data.WatermarkCorner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WatermarkControls(watermark: ExportWatermark?, onChange: (ExportWatermark?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(watermark)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            invalid = false
            try {
                val logo = withContext(Dispatchers.IO) { ExportWatermark.importLogo(context, uri) }
                onChange((current ?: ExportWatermark()).copy(logo = logo))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                invalid = true
            } catch (_: OutOfMemoryError) {
                invalid = true
            } finally {
                loading = false
            }
        }
    }
    FilterChip(selected = open || watermark?.active == true, onClick = { open = !open },
        label = { Text(stringResource(R.string.export_watermark)) })
    if (!open) return
    val settings = watermark ?: ExportWatermark()
    Column {
        OutlinedTextField(
            value = settings.text,
            onValueChange = { onChange(settings.copy(text = it.take(120), logo = null)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.watermark_text)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        TextButton(enabled = !loading, onClick = { picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }) {
            Text(stringResource(R.string.watermark_logo))
        }
        if (invalid) Text(stringResource(R.string.watermark_invalid))
        val labels = listOf(R.string.watermark_top_left, R.string.watermark_top_right,
            R.string.watermark_bottom_left, R.string.watermark_bottom_right)
        for (corner in WatermarkCorner.entries) FilterChip(
            selected = settings.corner == corner, onClick = { onChange(settings.copy(corner = corner)) },
            label = { Text(stringResource(labels[corner.ordinal])) },
        )
        Text(stringResource(R.string.watermark_margin))
        Slider(value = settings.margin, onValueChange = { onChange(settings.copy(margin = it)) }, valueRange = 0f..0.15f)
        Text(stringResource(R.string.watermark_scale))
        Slider(value = settings.scale, onValueChange = { onChange(settings.copy(scale = it)) }, valueRange = 0.025f..0.4f)
        Text(stringResource(R.string.watermark_opacity))
        Slider(value = settings.opacity, onValueChange = { onChange(settings.copy(opacity = it)) })
        if (settings.active) {
            val preview = remember(settings) {
                val bitmap = Bitmap.createBitmap(320, 190, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.DKGRAY)
                try {
                    settings.draw(canvas, bitmap.width, bitmap.height)
                    bitmap
                } catch (_: IllegalArgumentException) {
                    bitmap.recycle()
                    null
                }
            }
            if (preview == null) Text(stringResource(R.string.watermark_invalid))
            else Image(preview.asImageBitmap(), stringResource(R.string.export_watermark), Modifier.fillMaxWidth().height(130.dp))
            TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.watermark_remove)) }
        }
    }
}
