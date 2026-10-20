// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.ParamSpec
import io.github.steve_linux.photograin.model.Params
import io.github.steve_linux.photograin.model.Quad
import io.github.steve_linux.photograin.model.ToolGroup
import java.util.Locale
import kotlin.math.abs

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
                help = helpFor(paramLabel(spec.id)),
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

/** The white balance eyedropper: on, the next tap on the photo makes that spot neutral. */
@Composable
fun WhiteBalancePicker(picking: Boolean, onPicking: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = picking,
            onClick = { onPicking(!picking) },
            label = { Text(stringResource(R.string.wb_pick_label)) },
            leadingIcon = {
                Icon(Icons.Outlined.Colorize, contentDescription = stringResource(R.string.wb_pick), Modifier.size(18.dp))
            },
        )
        if (picking) {
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.wb_pick_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    @StringRes help: Int? = null,
) {
    var showHelp by remember { mutableStateOf(false) }
    // One row per slider (name, track, value) leaves more of the screen to the photo.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // A double tap on the name or value resets the slider; a long press explains it.
        val reset = Modifier.pointerInput(help) {
            detectTapGestures(
                onDoubleTap = { onReset() },
                onLongPress = { if (help != null) showHelp = true },
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            modifier = reset.width(96.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            modifier = Modifier.weight(1f).clearOfEdgeGestures(),
        )
        Text(
            display,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = reset.width(48.dp),
        )
    }
    if (showHelp && help != null) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(label) },
            text = { Text(stringResource(help)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

/**
 * Insets a full-width control from the screen edges where gesture navigation reads a swipe as
 * "back", so grabbing a slider at either end of its track does not close the screen.
 */
@Composable
private fun Modifier.clearOfEdgeGestures(): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val gestures = WindowInsets.systemGestures
    val windowWidth = LocalWindowInfo.current.containerSize.width
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val left = bounds?.let { gestures.getLeft(density, direction) - it.left } ?: 0f
    val right = bounds?.let { gestures.getRight(density, direction) - (windowWidth - it.right) } ?: 0f
    return this
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .absolutePadding(
            left = with(density) { left.coerceAtLeast(0f).toDp() },
            right = with(density) { right.coerceAtLeast(0f).toDp() },
        )
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
    quadDraft: Quad?,
    onAutoStraighten: () -> Unit,
    onStartQuad: () -> Unit,
    onApplyQuad: () -> Unit,
    onCancelQuad: () -> Unit,
    onRemoveQuad: () -> Unit,
) {
    if (quadDraft != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.quad_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancelQuad) { Text(stringResource(R.string.look_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApplyQuad) { Text(stringResource(R.string.quad_apply)) }
            }
        }
        return
    }
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
            help = helpFor(R.string.straighten),
            value = geometry.straighten,
            range = -45f..45f,
            // Tiny automatic angles would otherwise read "-0.0°".
            display = String.format(Locale.getDefault(), "%.1f°", if (abs(geometry.straighten) < 0.05f) 0f else geometry.straighten),
            onChange = onStraighten,
            onChangeFinished = onStraightenFinished,
            onReset = {
                onStraighten(0f)
                onStraightenFinished()
            },
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onAutoStraighten,
                label = { Text(stringResource(R.string.straighten_auto)) },
                leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onStartQuad,
                label = { Text(stringResource(if (geometry.quad != null) R.string.quad_edit else R.string.quad_start)) },
                leadingIcon = { Icon(Icons.Outlined.CropFree, null, Modifier.size(18.dp)) },
            )
            if (geometry.quad != null) {
                AssistChip(onClick = onRemoveQuad, label = { Text(stringResource(R.string.quad_remove)) })
            }
        }
        // The four points replace the two keystone sliders.
        if (geometry.quad == null) PerspectiveSliders(geometry, onPerspective, onStraightenFinished)
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

@Composable
private fun PerspectiveSliders(
    geometry: Geometry,
    onPerspective: (vertical: Float, horizontal: Float) -> Unit,
    onStraightenFinished: () -> Unit,
) {
    Column {
        ParamSlider(
            label = stringResource(R.string.perspective_vertical),
            help = helpFor(R.string.perspective_vertical),
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
            help = helpFor(R.string.perspective_horizontal),
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

/** The explanation shown on a long press of the slider named [label], if there is one. */
@StringRes
internal fun helpFor(@StringRes label: Int): Int? = when (label) {
    R.string.param_exposure -> R.string.help_param_exposure
    R.string.param_contrast -> R.string.help_param_contrast
    R.string.param_highlights -> R.string.help_param_highlights
    R.string.param_shadows -> R.string.help_param_shadows
    R.string.param_whites -> R.string.help_param_whites
    R.string.param_blacks -> R.string.help_param_blacks
    R.string.param_temperature -> R.string.help_param_temperature
    R.string.param_tint -> R.string.help_param_tint
    R.string.param_vibrance -> R.string.help_param_vibrance
    R.string.param_saturation -> R.string.help_param_saturation
    R.string.param_sharpness -> R.string.help_param_sharpness
    R.string.param_clarity -> R.string.help_param_clarity
    R.string.param_dehaze -> R.string.help_param_dehaze
    R.string.param_noise_reduction -> R.string.help_param_noise_reduction
    R.string.param_color_noise_reduction -> R.string.help_param_color_noise_reduction
    R.string.param_vignette -> R.string.help_param_vignette
    R.string.param_grain -> R.string.help_param_grain
    R.string.param_grain_size -> R.string.help_param_grain_size
    R.string.frame_width -> R.string.help_frame_width
    R.string.text_size -> R.string.help_text_size
    R.string.text_opacity -> R.string.help_text_opacity
    R.string.text_angle -> R.string.help_text_angle
    R.string.shape_width -> R.string.help_shape_width
    R.string.hsl_hue -> R.string.help_hsl_hue
    R.string.hsl_saturation -> R.string.help_hsl_saturation
    R.string.hsl_luminance -> R.string.help_hsl_luminance
    R.string.brush_size -> R.string.help_brush_size
    R.string.brush_hardness -> R.string.help_brush_hardness
    R.string.local_feather -> R.string.help_local_feather
    R.string.range_low -> R.string.help_range_low
    R.string.range_high -> R.string.help_range_high
    R.string.range_smooth -> R.string.help_range_smooth
    R.string.range_tolerance -> R.string.help_range_tolerance
    R.string.look_amount -> R.string.help_look_amount
    R.string.retouch_opacity -> R.string.help_retouch_opacity
    R.string.straighten -> R.string.help_straighten
    R.string.perspective_vertical -> R.string.help_perspective_vertical
    R.string.perspective_horizontal -> R.string.help_perspective_horizontal
    else -> null
}
