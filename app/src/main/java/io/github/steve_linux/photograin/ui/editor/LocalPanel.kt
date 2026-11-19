// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixOff
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Background
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParamSpec
import io.github.steve_linux.photograin.model.LocalParams
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskOverlayMode
import io.github.steve_linux.photograin.model.MaskRange
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.RangeType
import java.util.Locale

/**
 * Local adjustments: the list of masks, and the controls of the selected one. A subject mask
 * waits for [findingSubject], or for [selectingObject] once an object is tapped; any mask can
 * cut the photo out with [onBackground].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalPanel(
    masks: List<LocalAdjustment>,
    selected: Int?,
    showMask: Boolean,
    onAdd: (MaskType) -> Unit,
    onSky: () -> Unit,
    onSelect: (Int?) -> Unit,
    onDelete: (Int) -> Unit,
    onDuplicate: (Int, Boolean, String) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onSelectionInverted: (Boolean) -> Unit,
    onParam: (LocalParamSpec, Float) -> Unit,
    onResetParam: (LocalParamSpec) -> Unit,
    onShape: (Mask) -> Unit,
    onInvert: (Boolean) -> Unit,
    onRename: (Int, String) -> Unit,
    onOverlayMode: (MaskOverlayMode) -> Unit,
    onOverlayColor: (Int?) -> Unit,
    onOverlayOpacity: (Float) -> Unit,
    onCombine: (Int?, io.github.steve_linux.photograin.model.MaskCombination) -> Unit,
    onShowMask: (Boolean) -> Unit,
    onChangeFinished: () -> Unit,
    brush: BrushSettings,
    onBrush: (BrushSettings) -> Unit,
    pickingDepth: Boolean,
    onPickingDepth: (Boolean) -> Unit,
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
    findingSubject: Boolean,
    selectingObject: Boolean,
    onClearObject: () -> Unit,
    onRemoveBackground: () -> Unit,
    onBackground: (Background, Int?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        TextButton(enabled = masks.size < LocalAdjustment.MAX, onClick = onSky) {
            Text(stringResource(R.string.mask_sky))
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            masks.forEachIndexed { i, local ->
                FilterChip(
                    selected = i == selected,
                    onClick = { onSelect(if (i == selected) null else i) },
                    label = {
                        val title = maskTitle(local, i)
                        Text(if (local.enabled) title else stringResource(R.string.mask_disabled_name, title))
                    },
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
            // The quickest way to a cutout: the subject, with the background made transparent.
            AssistChip(
                enabled = canAdd,
                onClick = onRemoveBackground,
                label = { Text(stringResource(R.string.local_remove_background)) },
                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, Modifier.size(18.dp)) },
            )
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
        var renameOpen by remember(index) { mutableStateOf(false) }
        var moreOpen by remember(index) { mutableStateOf(false) }
        val copyName = stringResource(R.string.mask_copy_name, maskTitle(local, index))
        if (renameOpen) {
            var name by remember { mutableStateOf(local.name.orEmpty()) }
            AlertDialog(
                onDismissRequest = { renameOpen = false },
                title = { Text(stringResource(R.string.mask_rename)) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(64) },
                        label = { Text(stringResource(R.string.mask_name)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onRename(index, name); renameOpen = false }) {
                        Text(stringResource(R.string.quad_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameOpen = false }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = local.mask.invert, onCheckedChange = onInvert)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.local_invert), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            // Highlighted while the mask is shown, like the split view button.
            IconToggleButton(checked = showMask, onCheckedChange = onShowMask) {
                Icon(Icons.Outlined.Visibility, contentDescription = stringResource(R.string.local_show_mask))
            }
            Box {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.mask_options))
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mask_rename)) },
                        onClick = { moreOpen = false; renameOpen = true },
                    )
                    DropdownMenuItem(
                        enabled = masks.size < LocalAdjustment.MAX,
                        text = { Text(stringResource(R.string.mask_duplicate)) },
                        onClick = { moreOpen = false; onDuplicate(index, false, copyName) },
                    )
                    DropdownMenuItem(
                        enabled = masks.size < LocalAdjustment.MAX,
                        text = { Text(stringResource(R.string.mask_duplicate_invert)) },
                        onClick = { moreOpen = false; onDuplicate(index, true, copyName) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (local.invertSelection) R.string.mask_restore_selection else R.string.mask_invert_selection))
                        },
                        onClick = { moreOpen = false; onSelectionInverted(!local.invertSelection) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (local.enabled) R.string.mask_disable else R.string.mask_enable)) },
                        onClick = { moreOpen = false; onEnabled(!local.enabled) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.local_delete)) },
                        onClick = { moreOpen = false; onDelete(index) },
                    )
                }
            }
        }
        LocalSlider(local, LocalParams.amount, onParam, onResetParam, onChangeFinished)
        if (showMask) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (mode in MaskOverlayMode.entries) {
                    FilterChip(
                        selected = local.overlayMode == mode,
                        onClick = { onOverlayMode(mode) },
                        label = { Text(stringResource(overlayModeLabel(mode))) },
                    )
                }
            }
            if (local.overlayMode.usesColorOverlay) {
                Text(stringResource(R.string.mask_overlay_color))
                ColorRow(local.overlayColor ?: 0xffff1919.toInt()) { onOverlayColor(it) }
                ParamSlider(
                    label = stringResource(R.string.mask_overlay_opacity),
                    value = local.overlayOpacity,
                    range = 0f..1f,
                    display = "${(local.overlayOpacity * 100).toInt()}%",
                    onChange = onOverlayOpacity,
                    onChangeFinished = onChangeFinished,
                    onReset = { onOverlayOpacity(0.6f); onChangeFinished() },
                )
                if (local.overlayColor != null) {
                    TextButton(onClick = { onOverlayColor(null) }) {
                        Text(stringResource(R.string.mask_overlay_reset))
                    }
                }
            }
        }
        local.mask.depth?.let { depth ->
            Text(stringResource(R.string.depth_focus_hint), style = MaterialTheme.typography.bodySmall)
            FilterChip(
                selected = pickingDepth,
                onClick = { onPickingDepth(!pickingDepth) },
                label = { Text(stringResource(R.string.depth_pick_focus)) },
            )
            DepthSlider(R.string.depth_center, depth.center, 0.5f, onChangeFinished) {
                onShape(local.mask.copy(depth = depth.copy(center = it)))
            }
            DepthSlider(R.string.depth_width, depth.width, 0.2f, onChangeFinished) {
                onShape(local.mask.copy(depth = depth.copy(width = it)))
            }
            DepthSlider(R.string.depth_transition, depth.transition, 0.25f, onChangeFinished) {
                onShape(local.mask.copy(depth = depth.copy(transition = it)))
            }
        }
        val tapped = local.mask.points.isNotEmpty()
        if (local.mask.type == MaskType.SUBJECT && (if (tapped) selectingObject else findingSubject)) {
            Text(
                stringResource(if (tapped) R.string.local_selecting_object else R.string.local_finding_subject),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        if (LocalAdjustment.usesRaster(local.mask.type)) {
            if (tapped) {
                TextButton(onClick = onClearObject) { Text(stringResource(R.string.local_clear_object)) }
            } else if (local.mask.strokes.isEmpty()) {
                Text(
                    stringResource(if (local.mask.type == MaskType.SUBJECT) R.string.local_hint_subject else R.string.local_hint_brush),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                FilterChip(
                    selected = brush.lasso,
                    onClick = { onBrush(brush.copy(lasso = !brush.lasso)) },
                    label = { Text(stringResource(R.string.brush_lasso)) },
                    leadingIcon = { Icon(Icons.Outlined.Gesture, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
            if (brush.lasso) {
                // The lasso fills what it surrounds: size and hardness do not apply.
                Text(
                    stringResource(R.string.brush_lasso_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                ParamSlider(
                    label = stringResource(R.string.brush_size),
                    help = helpFor(R.string.brush_size),
                    value = brush.size,
                    range = 0f..1f,
                    display = "${Math.round(brush.size * 100)}",
                    onChange = { onBrush(brush.copy(size = it)) },
                    onChangeFinished = {},
                    onReset = { onBrush(brush.copy(size = BrushSettings().size)) },
                )
                ParamSlider(
                    label = stringResource(R.string.brush_hardness),
                    help = helpFor(R.string.brush_hardness),
                    value = brush.hardness,
                    range = 0f..1f,
                    display = "${Math.round(brush.hardness * 100)}",
                    onChange = { onBrush(brush.copy(hardness = it)) },
                    onChangeFinished = {},
                    onReset = { onBrush(brush.copy(hardness = BrushSettings().hardness)) },
                )
                ParamSlider(
                    label = stringResource(R.string.brush_flow),
                    help = R.string.help_brush_flow,
                    value = brush.flow,
                    range = 0f..1f,
                    display = "${Math.round(brush.flow * 100)}%",
                    onChange = { onBrush(brush.copy(flow = it)) },
                    onChangeFinished = {},
                    onReset = { onBrush(brush.copy(flow = BrushSettings().flow)) },
                )
                ParamSlider(
                    label = stringResource(R.string.brush_density),
                    help = R.string.help_brush_density,
                    value = brush.density,
                    range = 0f..1f,
                    display = "${Math.round(brush.density * 100)}%",
                    onChange = { onBrush(brush.copy(density = it)) },
                    onChangeFinished = {},
                    onReset = { onBrush(brush.copy(density = BrushSettings().density)) },
                )
            }
        }
        if (local.mask.type == MaskType.RADIAL) {
            ParamSlider(
                label = stringResource(R.string.local_feather),
                help = helpFor(R.string.local_feather),
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
        if (masks.size > 1) {
            Text(stringResource(R.string.mask_combine))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = local.combineWith == null,
                    onClick = { onCombine(null, local.combination) },
                    label = { Text(stringResource(R.string.mask_combine_none)) },
                )
                masks.forEachIndexed { index, candidate ->
                    if (index != selected) {
                        FilterChip(
                            selected = local.combineWith == index,
                            onClick = { onCombine(index, local.combination) },
                            label = { Text(maskTitle(candidate, index)) },
                        )
                    }
                }
            }
            val other = local.combineWith
            if (other != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(R.string.mask_union, R.string.mask_subtract, R.string.mask_intersect)
                    for (operation in io.github.steve_linux.photograin.model.MaskCombination.entries) {
                        FilterChip(
                            selected = local.combination == operation,
                            onClick = { onCombine(other, operation) },
                            label = { Text(stringResource(labels[operation.ordinal])) },
                        )
                    }
                }
            }
        }
        BackgroundSection(local, onBackground)
        RangeSection(local.mask, onShape, onChangeFinished, picking, onPicking)
        for (spec in LocalParams.all) {
            LocalSlider(local, spec, onParam, onResetParam, onChangeFinished)
        }
    }
}

@Composable
private fun LocalSlider(
    local: LocalAdjustment,
    spec: LocalParamSpec,
    onParam: (LocalParamSpec, Float) -> Unit,
    onResetParam: (LocalParamSpec) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val label = paramLabel(spec.id)
    val value = spec.get(local)
    ParamSlider(
        label = stringResource(label),
        help = helpFor(label),
        value = value,
        range = spec.min..spec.max,
        display = formatLocal(spec, value),
        onChange = { onParam(spec, it) },
        onChangeFinished = onChangeFinished,
        onReset = { onResetParam(spec) },
    )
}

@Composable
private fun DepthSlider(label: Int, value: Float, default: Float, onFinished: () -> Unit, onChange: (Float) -> Unit) {
    ParamSlider(
        label = stringResource(label),
        value = value,
        range = 0f..1f,
        display = "${(value * 100).toInt()}%",
        onChange = onChange,
        onChangeFinished = onFinished,
        onReset = { onChange(default); onFinished() },
    )
}

private fun formatLocal(spec: LocalParamSpec, value: Float): String {
    val shown = value * spec.displayScale
    if (spec == LocalParams.amount) return "${Math.round(shown)}%"
    if (spec == LocalParams.hueShift) {
        val degrees = Math.round(shown)
        return if (degrees > 0) "+$degrees°" else "$degrees°"
    }
    return if (spec.decimals == 0) {
        val n = Math.round(shown)
        if (n > 0) "+$n" else "$n"
    } else {
        String.format(Locale.getDefault(), if (shown > 0) "+%.${spec.decimals}f" else "%.${spec.decimals}f", shown)
    }
}

/** Cuts the photo out along the mask: outside it, transparency or a color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundSection(local: LocalAdjustment, onBackground: (Background, Int?) -> Unit) {
    Text(
        stringResource(R.string.background_title),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in Background.entries) {
            FilterChip(
                selected = local.background == b,
                onClick = { onBackground(b, null) },
                label = { Text(stringResource(backgroundLabel(b))) },
            )
        }
    }
    when (local.background) {
        Background.KEEP -> Unit
        Background.TRANSPARENT -> Text(
            stringResource(R.string.background_transparent_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Background.COLOR -> ColorRow(local.backgroundColor) { onBackground(Background.COLOR, it) }
    }
}

private fun backgroundLabel(b: Background): Int = when (b) {
    Background.KEEP -> R.string.background_keep
    Background.TRANSPARENT -> R.string.background_transparent
    Background.COLOR -> R.string.background_color
}

/** Narrows the mask to a brightness or color range; the color comes from a tap on the photo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeSection(
    mask: Mask,
    onShape: (Mask) -> Unit,
    onChangeFinished: () -> Unit,
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
) {
    val range = mask.range
    fun set(r: MaskRange) = onShape(mask.copy(range = r))
    Text(
        stringResource(R.string.range_title),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (type in RangeType.entries) {
            FilterChip(
                selected = range.type == type,
                onClick = {
                    set(range.copy(type = type))
                    onChangeFinished()
                    // A new color range starts by asking for its color.
                    onPicking(type == RangeType.COLOR && range.type != RangeType.COLOR)
                },
                label = { Text(stringResource(rangeLabel(type))) },
            )
        }
    }
    when (range.type) {
        RangeType.NONE -> Unit
        RangeType.LUMINANCE -> {
            ParamSlider(
                label = stringResource(R.string.range_low),
                help = helpFor(R.string.range_low),
                value = range.low,
                range = 0f..1f,
                display = "${Math.round(range.low * 100)}",
                onChange = { set(range.copy(low = it.coerceAtMost(range.high))) },
                onChangeFinished = onChangeFinished,
                onReset = { set(range.copy(low = MaskRange().low)); onChangeFinished() },
            )
            ParamSlider(
                label = stringResource(R.string.range_high),
                help = helpFor(R.string.range_high),
                value = range.high,
                range = 0f..1f,
                display = "${Math.round(range.high * 100)}",
                onChange = { set(range.copy(high = it.coerceAtLeast(range.low))) },
                onChangeFinished = onChangeFinished,
                onReset = { set(range.copy(high = MaskRange().high)); onChangeFinished() },
            )
            ParamSlider(
                label = stringResource(R.string.range_smooth),
                help = helpFor(R.string.range_smooth),
                value = range.smooth,
                range = 0f..1f,
                display = "${Math.round(range.smooth * 100)}",
                onChange = { set(range.copy(smooth = it)) },
                onChangeFinished = onChangeFinished,
                onReset = { set(range.copy(smooth = MaskRange().smooth)); onChangeFinished() },
            )
        }
        RangeType.COLOR -> {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .background(Color.hsv(range.hue * 360f, 0.75f, 0.9f)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (picking) R.string.range_pick_hint else R.string.range_color),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconToggleButton(checked = picking, onCheckedChange = onPicking) {
                    Icon(Icons.Outlined.Colorize, contentDescription = stringResource(R.string.range_pick))
                }
            }
            ParamSlider(
                label = stringResource(R.string.range_tolerance),
                help = helpFor(R.string.range_tolerance),
                value = range.tolerance,
                range = 0f..1f,
                display = "${Math.round(range.tolerance * 100)}",
                onChange = { set(range.copy(tolerance = it)) },
                onChangeFinished = onChangeFinished,
                onReset = { set(range.copy(tolerance = MaskRange().tolerance)); onChangeFinished() },
            )
        }
    }
}

private fun rangeLabel(type: RangeType): Int = when (type) {
    RangeType.NONE -> R.string.range_none
    RangeType.LUMINANCE -> R.string.range_luminance
    RangeType.COLOR -> R.string.range_color_chip
}

@Composable
private fun maskTitle(mask: LocalAdjustment, index: Int): String =
    mask.name ?: stringResource(if (mask.autoSky) R.string.mask_sky_number else maskName(mask.mask.type), index + 1)

private fun maskName(type: MaskType): Int = when (type) {
    MaskType.LINEAR -> R.string.local_linear_n
    MaskType.RADIAL -> R.string.local_radial_n
    MaskType.BRUSH -> R.string.local_brush_n
    MaskType.FULL -> R.string.local_full_n
    MaskType.SUBJECT -> R.string.local_subject_n
    MaskType.DEPTH -> R.string.depth_mask_name
}

private fun addLabel(type: MaskType): Int = when (type) {
    MaskType.LINEAR -> R.string.local_add_linear
    MaskType.RADIAL -> R.string.local_add_radial
    MaskType.BRUSH -> R.string.local_add_brush
    MaskType.FULL -> R.string.local_add_full
    MaskType.SUBJECT -> R.string.local_add_subject
    MaskType.DEPTH -> R.string.depth_add_mask
}

private fun overlayModeLabel(mode: MaskOverlayMode): Int = when (mode) {
    MaskOverlayMode.COLOR -> R.string.mask_view_color
    MaskOverlayMode.COLOR_ON_MONO -> R.string.mask_view_color_on_mono
    MaskOverlayMode.IMAGE_ON_MONO -> R.string.mask_view_image_on_mono
    MaskOverlayMode.IMAGE_ON_BLACK -> R.string.mask_view_image_on_black
    MaskOverlayMode.IMAGE_ON_WHITE -> R.string.mask_view_image_on_white
    MaskOverlayMode.MASK_ONLY -> R.string.mask_view_mask_only
}
