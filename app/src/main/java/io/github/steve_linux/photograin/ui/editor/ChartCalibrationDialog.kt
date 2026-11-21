// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.sampleColor
import io.github.steve_linux.photograin.model.ChartCalibration
import io.github.steve_linux.photograin.ui.batch.SampleAreaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ChartSample(val point: Offset, val hex: String, val patch: ChartCalibration.Patch)

@Composable
internal fun ChartCalibrationDialog(uri: Uri, onSave: (String, DoubleArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var point by remember(uri) { mutableStateOf<Offset?>(null) }
    var hex by remember(uri) { mutableStateOf("") }
    val defaultName = stringResource(R.string.chart_calibration)
    var name by remember(uri) { mutableStateOf(defaultName) }
    val samples = remember(uri) { mutableStateListOf<ChartSample>() }
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, 900) }
    }
    val inputImage = bitmap
    val selected = point
    val measured = remember(inputImage, selected) {
        if (inputImage == null || selected == null) null
        else sampleColor(inputImage, selected.x, selected.y, 0.035f, grayCard = false)
    }
    val reference = hex.takeIf { it.length == 6 }?.toIntOrNull(16)
    val matrix = remember(samples.toList()) { ChartCalibration.fit(samples.map { it.patch }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(defaultName) },
        text = {
            Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.chart_hint))
                if (inputImage != null) {
                    SampleAreaImage(inputImage, selected ?: Offset(0.5f, 0.5f), 0.035f, uri) { point = it }
                } else {
                    Text(stringResource(R.string.chart_loading))
                }
                OutlinedTextField(
                    value = hex, onValueChange = { hex = it.removePrefix("#").take(6) },
                    label = { Text(stringResource(R.string.chart_reference)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                )
                TextButton(
                    enabled = measured != null && selected != null && reference != null && samples.size < 48,
                    onClick = {
                        val sample = ChartSample(checkNotNull(selected), hex.uppercase(),
                            ChartCalibration.Patch(checkNotNull(measured), checkNotNull(reference)))
                        val index = samples.indexOfFirst { (it.point - sample.point).getDistance() < 0.018f }
                        if (index < 0) samples.add(sample) else samples[index] = sample
                        point = null
                        hex = ""
                    },
                ) { Text(stringResource(R.string.chart_add)) }
                for (sample in samples) {
                    Row {
                        TextButton(onClick = { point = sample.point; hex = sample.hex }) { Text("#${sample.hex}") }
                        TextButton(onClick = { samples.remove(sample) }) { Text(stringResource(R.string.chart_remove)) }
                    }
                }
                if (samples.size >= 3 && matrix == null) Text(stringResource(R.string.chart_diversity))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.chart_name)) }, singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = matrix != null && name.isNotBlank(), onClick = {
                matrix?.let { onSave(name.trim(), it) }
            }) { Text(stringResource(R.string.chart_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
