// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.ParamSpec
import io.github.steve_linux.photograin.model.Params
import io.github.steve_linux.photograin.model.ToolGroup
import java.util.Locale

@Composable
fun AdjustmentPanel(
    group: ToolGroup,
    adjustments: Adjustments,
    onChange: (ParamSpec, Float) -> Unit,
    onChangeFinished: () -> Unit,
    onReset: (ParamSpec) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        for (spec in Params.forGroup(group)) {
            ParamSlider(
                label = stringResource(paramLabel(spec.id)),
                value = spec.get(adjustments),
                range = spec.min..spec.max,
                display = formatValue(spec, spec.get(adjustments)),
                onChange = { onChange(spec, it) },
                onChangeFinished = onChangeFinished,
                onReset = { onReset(spec) },
            )
        }
    }
}

@Composable
internal fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropPanel(
    geometry: Geometry,
    onAspect: (AspectRatio) -> Unit,
    onStraighten: (Float) -> Unit,
    onStraightenFinished: () -> Unit,
    onPerspective: (vertical: Float, horizontal: Float) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        // Wrapped rather than scrolled, so every ratio is in sight.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (aspect in AspectRatio.entries) {
                FilterChip(
                    selected = geometry.aspect == aspect,
                    onClick = { onAspect(aspect) },
                    label = { Text(stringResource(aspectLabel(aspect))) },
                )
            }
        }
        ParamSlider(
            label = stringResource(R.string.straighten),
            value = geometry.straighten,
            range = -45f..45f,
            display = String.format(Locale.getDefault(), "%.1f°", geometry.straighten),
            onChange = onStraighten,
            onChangeFinished = onStraightenFinished,
            onReset = {
                onStraighten(0f)
                onStraightenFinished()
            },
        )
        ParamSlider(
            label = stringResource(R.string.perspective_vertical),
            value = geometry.vertical,
            range = -1f..1f,
            display = signedPercent(geometry.vertical),
            onChange = { onPerspective(it, geometry.horizontal) },
            onChangeFinished = onStraightenFinished,
            onReset = {
                onPerspective(0f, geometry.horizontal)
                onStraightenFinished()
            },
        )
        ParamSlider(
            label = stringResource(R.string.perspective_horizontal),
            value = geometry.horizontal,
            range = -1f..1f,
            display = signedPercent(geometry.horizontal),
            onChange = { onPerspective(geometry.vertical, it) },
            onChangeFinished = onStraightenFinished,
            onReset = {
                onPerspective(geometry.vertical, 0f)
                onStraightenFinished()
            },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRotate) {
                Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = stringResource(R.string.rotate))
            }
            IconButton(onClick = onFlipH) {
                Icon(Icons.Outlined.Flip, contentDescription = stringResource(R.string.flip_horizontal))
            }
            IconButton(onClick = onFlipV) {
                Icon(
                    Icons.Outlined.Flip,
                    contentDescription = stringResource(R.string.flip_vertical),
                    modifier = Modifier.rotate(90f),
                )
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.reset_crop))
            }
        }
    }
}

private fun signedPercent(v: Float): String {
    val n = Math.round(v * 100)
    return if (n > 0) "+$n" else "$n"
}

private fun formatValue(spec: ParamSpec, value: Float): String {
    val shown = value * spec.displayScale
    return if (spec.decimals == 0) {
        val n = Math.round(shown)
        if (n > 0 && spec.min < 0f) "+$n" else "$n"
    } else {
        String.format(Locale.getDefault(), if (shown > 0) "+%.${spec.decimals}f" else "%.${spec.decimals}f", shown)
    }
}

@StringRes
internal fun paramLabel(id: String): Int = when (id) {
    "exposure" -> R.string.param_exposure
    "contrast" -> R.string.param_contrast
    "highlights" -> R.string.param_highlights
    "shadows" -> R.string.param_shadows
    "whites" -> R.string.param_whites
    "blacks" -> R.string.param_blacks
    "temperature" -> R.string.param_temperature
    "tint" -> R.string.param_tint
    "vibrance" -> R.string.param_vibrance
    "saturation" -> R.string.param_saturation
    "sharpness" -> R.string.param_sharpness
    "clarity" -> R.string.param_clarity
    "dehaze" -> R.string.param_dehaze
    "noiseReduction" -> R.string.param_noise_reduction
    "colorNoiseReduction" -> R.string.param_color_noise_reduction
    "vignette" -> R.string.param_vignette
    "grain" -> R.string.param_grain
    "grainSize" -> R.string.param_grain_size
    else -> error("No label for $id")
}

@StringRes
private fun aspectLabel(a: AspectRatio): Int = when (a) {
    AspectRatio.FREE -> R.string.aspect_free
    AspectRatio.ORIGINAL -> R.string.aspect_original
    AspectRatio.SQUARE -> R.string.aspect_square
    AspectRatio.R4_3 -> R.string.aspect_4_3
    AspectRatio.R3_2 -> R.string.aspect_3_2
    AspectRatio.R16_9 -> R.string.aspect_16_9
    AspectRatio.R5_4 -> R.string.aspect_5_4
}

@StringRes
fun toolLabel(t: ToolGroup): Int = when (t) {
    ToolGroup.LOOK -> R.string.tool_look
    ToolGroup.LIGHT -> R.string.tool_light
    ToolGroup.COLOR -> R.string.tool_color
    ToolGroup.DETAIL -> R.string.tool_detail
    ToolGroup.EFFECTS -> R.string.tool_effects
    ToolGroup.LOCAL -> R.string.tool_local
    ToolGroup.CROP -> R.string.tool_crop
}
