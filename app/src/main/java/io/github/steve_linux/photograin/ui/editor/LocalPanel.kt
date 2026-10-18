// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixOff
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParamSpec
import io.github.steve_linux.photograin.model.LocalParams
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import java.util.Locale

/** Linear and radial masks: the list, and the sliders of the selected one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalPanel(
    masks: List<LocalAdjustment>,
    selected: Int?,
    showMask: Boolean,
    onAdd: (MaskType) -> Unit,
    onSelect: (Int?) -> Unit,
    onDelete: (Int) -> Unit,
    onParam: (LocalParamSpec, Float) -> Unit,
    onResetParam: (LocalParamSpec) -> Unit,
    onShape: (Mask) -> Unit,
    onInvert: (Boolean) -> Unit,
    onShowMask: (Boolean) -> Unit,
    onChangeFinished: () -> Unit,
    brush: BrushSettings,
    onBrush: (BrushSettings) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            masks.forEachIndexed { i, local ->
                FilterChip(
                    selected = i == selected,
                    onClick = { onSelect(if (i == selected) null else i) },
                    label = { Text(stringResource(maskName(local.mask.type), i + 1)) },
                )
            }
            val canAdd = masks.size < LocalAdjustment.MAX
            for (type in MaskType.entries) {
                AssistChip(
                    enabled = canAdd,
                    onClick = { onAdd(type) },
                    label = { Text(stringResource(addLabel(type))) },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        val index = selected?.takeIf { it in masks.indices }
        if (index == null) {
            Text(
                stringResource(if (masks.isEmpty()) R.string.local_hint_empty else R.string.local_hint_select),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }
        val local = masks[index]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = local.mask.invert, onCheckedChange = onInvert)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.local_invert), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onShowMask(!showMask) }) {
                Icon(
                    if (showMask) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.local_show_mask),
                )
            }
            IconButton(onClick = { onDelete(index) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.local_delete))
            }
        }
        if (local.mask.type == MaskType.BRUSH) {
            if (local.mask.strokes.isEmpty()) {
                Text(
                    stringResource(R.string.local_hint_brush),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !brush.erase,
                    onClick = { onBrush(brush.copy(erase = false)) },
                    label = { Text(stringResource(R.string.brush_paint)) },
                    leadingIcon = { Icon(Icons.Outlined.Brush, contentDescription = null, Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = brush.erase,
                    onClick = { onBrush(brush.copy(erase = true)) },
                    label = { Text(stringResource(R.string.brush_erase)) },
                    leadingIcon = { Icon(Icons.Outlined.AutoFixOff, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
            ParamSlider(
                label = stringResource(R.string.brush_size),
                value = brush.size,
                range = 0f..1f,
                display = "${Math.round(brush.size * 100)}",
                onChange = { onBrush(brush.copy(size = it)) },
                onChangeFinished = {},
                onReset = { onBrush(brush.copy(size = BrushSettings().size)) },
            )
            ParamSlider(
                label = stringResource(R.string.brush_hardness),
                value = brush.hardness,
                range = 0f..1f,
                display = "${Math.round(brush.hardness * 100)}",
                onChange = { onBrush(brush.copy(hardness = it)) },
                onChangeFinished = {},
                onReset = { onBrush(brush.copy(hardness = BrushSettings().hardness)) },
            )
        }
        if (local.mask.type == MaskType.RADIAL) {
            ParamSlider(
                label = stringResource(R.string.local_feather),
                value = local.mask.feather,
                range = 0f..1f,
                display = "${Math.round(local.mask.feather * 100)}",
                onChange = { onShape(local.mask.copy(feather = it)) },
                onChangeFinished = onChangeFinished,
                onReset = {
                    onShape(local.mask.copy(feather = Mask().feather))
                    onChangeFinished()
                },
            )
        }
        for (spec in LocalParams.all) {
            val value = spec.get(local)
            ParamSlider(
                label = stringResource(paramLabel(spec.id)),
                value = value,
                range = spec.min..spec.max,
                display = formatLocal(spec, value),
                onChange = { onParam(spec, it) },
                onChangeFinished = onChangeFinished,
                onReset = { onResetParam(spec) },
            )
        }
    }
}

private fun formatLocal(spec: LocalParamSpec, value: Float): String {
    val shown = value * spec.displayScale
    return if (spec.decimals == 0) {
        val n = Math.round(shown)
        if (n > 0) "+$n" else "$n"
    } else {
        String.format(Locale.getDefault(), if (shown > 0) "+%.${spec.decimals}f" else "%.${spec.decimals}f", shown)
    }
}

private fun maskName(type: MaskType): Int = when (type) {
    MaskType.LINEAR -> R.string.local_linear_n
    MaskType.RADIAL -> R.string.local_radial_n
    MaskType.BRUSH -> R.string.local_brush_n
}

private fun addLabel(type: MaskType): Int = when (type) {
    MaskType.LINEAR -> R.string.local_add_linear
    MaskType.RADIAL -> R.string.local_add_radial
    MaskType.BRUSH -> R.string.local_add_brush
}
