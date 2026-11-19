// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.ImageLayer
import kotlin.math.roundToInt

@Composable
internal fun ImageLayersPanel(layers: List<ImageLayer>, viewModel: EditorViewModel) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val current = layers.firstOrNull { it.instanceId == selected } ?: layers.lastOrNull()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            selected = null
            viewModel.addImageLayer(it)
        }
    }
    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TextButton(enabled = layers.size < 8, onClick = { picker.launch(arrayOf("image/*")) }) {
            Text(stringResource(R.string.image_layer_add))
        }
        for ((index, layer) in layers.withIndex()) {
            FilterChip(
                selected = current?.instanceId == layer.instanceId,
                onClick = { selected = layer.instanceId },
                label = { Text(stringResource(R.string.image_layer_number, index + 1)) },
            )
        }
        if (current != null) {
            val index = layers.indexOf(current)
            TextButton(onClick = { viewModel.alignImageLayer(current.instanceId, 0.5f, 0.5f) }) {
                Text(stringResource(R.string.layer_center))
            }
            TextButton(onClick = {
                val x = (current.x * 3).roundToInt() / 3f
                val y = (current.y * 3).roundToInt() / 3f
                viewModel.alignImageLayer(current.instanceId, x, y)
            }) { Text(stringResource(R.string.layer_snap_thirds)) }
            FilterChip(selected = current.visible, onClick = {
                viewModel.changeImageLayer(current.instanceId) { it.copy(visible = !it.visible) }
                viewModel.commit()
            }, label = { Text(stringResource(R.string.layer_visible)) })
            TextButton(enabled = index > 0, onClick = { viewModel.moveImageLayer(current.instanceId, -1) }) {
                Text(stringResource(R.string.layer_lower))
            }
            TextButton(enabled = index < layers.lastIndex, onClick = { viewModel.moveImageLayer(current.instanceId, 1) }) {
                Text(stringResource(R.string.layer_raise))
            }
            TextButton(enabled = layers.size < 8, onClick = { selected = viewModel.duplicateImageLayer(current.instanceId) }) {
                Text(stringResource(R.string.layer_duplicate))
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                Text(stringResource(R.string.layer_blend_export))
                val labels = listOf(R.string.layer_normal, R.string.layer_multiply, R.string.layer_screen, R.string.layer_soft)
                for (blend in io.github.steve_linux.photograin.model.LayerBlend.entries) {
                    FilterChip(selected = current.blend == blend, onClick = {
                        viewModel.changeImageLayer(current.instanceId) { it.copy(blend = blend) }
                        viewModel.commit()
                    }, label = { Text(stringResource(labels[blend.ordinal])) })
                }
            }
            LayerSlider(R.string.layer_mask, current.maskRadius, 0f..1f, 0f, viewModel::commit) {
                viewModel.changeImageLayer(current.instanceId) { layer -> layer.copy(maskRadius = it) }
            }
            LayerSlider(R.string.image_layer_x, current.x, 0f..1f, 0.5f, viewModel::commit) {
                viewModel.changeImageLayer(current.instanceId) { layer -> layer.copy(x = it) }
            }
            LayerSlider(R.string.image_layer_y, current.y, 0f..1f, 0.5f, viewModel::commit) {
                viewModel.changeImageLayer(current.instanceId) { layer -> layer.copy(y = it) }
            }
            LayerSlider(R.string.image_layer_size, current.size, 0.05f..2f, 0.5f, viewModel::commit) {
                viewModel.changeImageLayer(current.instanceId) { layer -> layer.copy(size = it) }
            }
            LayerSlider(R.string.image_layer_opacity, current.opacity, 0f..1f, 1f, viewModel::commit) {
                viewModel.changeImageLayer(current.instanceId) { layer -> layer.copy(opacity = it) }
            }
            LayerSlider(R.string.image_layer_angle, current.angle, -180f..180f, 0f, viewModel::commit, degrees = true) {
                viewModel.changeImageLayer(current.instanceId) { layer -> layer.copy(angle = it) }
            }
            TextButton(onClick = { viewModel.removeImageLayer(current.instanceId) }) {
                Text(stringResource(R.string.image_layer_remove))
            }
        }
    }
}

@Composable
private fun LayerSlider(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    onFinished: () -> Unit,
    degrees: Boolean = false,
    onChange: (Float) -> Unit,
) {
    ParamSlider(
        label = stringResource(label),
        value = value,
        range = range,
        display = if (degrees) "${value.roundToInt()}°" else "${(value * 100).roundToInt()}%",
        onChange = onChange,
        onChangeFinished = onFinished,
        onReset = { onChange(default); onFinished() },
    )
}
