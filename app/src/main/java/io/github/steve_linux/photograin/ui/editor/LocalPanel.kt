// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixOff
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.steve_linux.photograin.model.MaskOperation
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
    selectedPart: Int,
    onSelectPart: (Int) -> Unit,
    onAddPart: (MaskType, MaskOperation) -> Unit,
    onDeletePart: () -> Unit,
    showMask: Boolean,
    onAdd: (MaskType) -> Unit,
    onSelect: (Int?) -> Unit,
    onDelete: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onParam: (LocalParamSpec, Float) -> Unit,
    onResetParam: (LocalParamSpec) -> Unit,
    onShape: (Mask) -> Unit,
    onInvert: (Boolean) -> Unit,
    onShowMask: (Boolean) -> Unit,
    onChangeFinished: () -> Unit,
    brush: BrushSettings,
    onBrush: (BrushSettings) -> Unit,
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
    findingSubject: Boolean,
    findingSky: Boolean,
    selectingObject: Boolean,
    onClearObject: () -> Unit,
    onRemoveBackground: () -> Unit,
    onBackground: (Background, Int?) -> Unit,
) {
    var showNewMask by rememberSaveable(selected) { mutableStateOf(false) }
    var section by rememberSaveable(selected) { mutableStateOf(0) }
    val activeSection = if (selected == null) 0 else section
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
        if (selected != null) {
            WorkspaceModeTabs(
                listOf(R.string.panel_selection, R.string.tool_light, R.string.tool_color, R.string.tool_effects),
                activeSection,
            ) {
                if (it != 0) onPicking(false)
                section = it
            }
        }
        key(activeSection) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    masks.forEachIndexed { i, local ->
                        FilterChip(
                            selected = i == selected,
                            onClick = { onSelect(if (i == selected) null else i) },
                            label = { Text(stringResource(maskName(local.mask.type), i + 1)) },
                        )
                    }
                }
                if (activeSection == 0 && selected != null) {
                    TextButton(onClick = { showNewMask = !showNewMask }) { Text(stringResource(R.string.mask_new)) }
                }
                if (activeSection == 0 && (selected == null || showNewMask)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val whole = masks[index]
                val local = whole.copy(mask = whole.mask.selection(selectedPart))
                if (activeSection == 0) {
                    ComponentControls(whole.mask, selectedPart, onSelectPart, onAddPart, onDeletePart)
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
                    IconButton(enabled = masks.size < LocalAdjustment.MAX, onClick = { onDuplicate(index) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.mask_duplicate))
                    }
                    IconButton(onClick = { onDelete(index) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.local_delete))
                    }
                }
                if (activeSection == 0) {
                    if (local.mask.type == MaskType.SKY && findingSky) {
                        Text(stringResource(R.string.local_finding_sky), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                    if (local.mask.type == MaskType.SKY) {
                        Text(
                            stringResource(R.string.local_hint_sky),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
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
                    BackgroundSection(local, onBackground)
                    RangeSection(local.mask, onShape, onChangeFinished, picking, onPicking)
                }
                val ids = when (activeSection) {
                    1 -> setOf("exposure", "contrast", "highlights", "shadows")
                    2 -> setOf("temperature", "tint", "saturation")
                    3 -> setOf("texture", "clarity", "dehaze")
                    else -> emptySet()
                }
                for (spec in LocalParams.all.filter { it.id in ids }) {
                    val value = spec.get(local)
                    ParamSlider(
                        label = stringResource(paramLabel(spec.id)),
                        help = helpFor(paramLabel(spec.id)),
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

private fun maskName(type: MaskType): Int = when (type) {
    MaskType.LINEAR -> R.string.local_linear_n
    MaskType.RADIAL -> R.string.local_radial_n
    MaskType.BRUSH -> R.string.local_brush_n
    MaskType.FULL -> R.string.local_full_n
    MaskType.SUBJECT -> R.string.local_subject_n
    MaskType.SKY -> R.string.local_sky_n
}

private fun addLabel(type: MaskType): Int = when (type) {
    MaskType.LINEAR -> R.string.local_add_linear
    MaskType.RADIAL -> R.string.local_add_radial
    MaskType.BRUSH -> R.string.local_add_brush
    MaskType.FULL -> R.string.local_add_full
    MaskType.SUBJECT -> R.string.local_add_subject
    MaskType.SKY -> R.string.local_add_sky
}

/** Keep the component picker collapsed until a combination operation is requested. */
@Composable
private fun ComponentControls(
    mask: Mask,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: (MaskType, MaskOperation) -> Unit,
    onDelete: () -> Unit,
) {
    var operation by rememberSaveable { mutableStateOf<MaskOperation?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == 0, { onSelect(0) }, label = { Text(stringResource(R.string.mask_base)) })
        mask.parts.forEachIndexed { i, part ->
            FilterChip(selected == i + 1, { onSelect(i + 1) }, label = {
                val symbol = when (part.operation) {
                    MaskOperation.ADD -> "+ "
                    MaskOperation.SUBTRACT -> "− "
                    MaskOperation.INTERSECT -> "∩ "
                }
                Text(symbol + stringResource(maskName(part.mask.type), i + 1))
            })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            for (op in MaskOperation.entries) {
                TextButton(enabled = mask.parts.size < Mask.MAX_PARTS, onClick = { operation = if (operation == op) null else op }) {
                    Text(stringResource(when (op) {
                        MaskOperation.ADD -> R.string.mask_add_selection
                        MaskOperation.SUBTRACT -> R.string.mask_subtract_selection
                        MaskOperation.INTERSECT -> R.string.mask_intersect_selection
                    }))
                }
            }
        }
        if (selected > 0) IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, stringResource(R.string.mask_delete_selection))
        }
    }
    operation?.let { op ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in MaskType.entries.filter { it != MaskType.FULL }) {
                AssistChip(onClick = { onAdd(type, op); operation = null }, label = { Text(stringResource(maskName(type), mask.parts.size + 1)) })
            }
        }
    }
    if (mask.parts.isNotEmpty()) Text(
        stringResource(R.string.mask_components_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
