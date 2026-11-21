// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AdjustmentSelection
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.CropGuide
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

/** Find and edit global adjustments across tool groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdjustmentFinder(adjustments: Adjustments, viewModel: EditorViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("editor_tools", android.content.Context.MODE_PRIVATE) }
    var query by rememberSaveable { mutableStateOf("") }
    var changedOnly by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorite_params", emptySet()).orEmpty().toSet()) }
    val visible = Params.all.filter { spec ->
        val label = context.getString(paramLabel(spec.id))
        (query.isBlank() || label.contains(query.trim(), ignoreCase = true) ||
            context.getString(toolLabel(spec.group)).contains(query.trim(), ignoreCase = true)) &&
            (!changedOnly || spec.get(adjustments) != spec.default) &&
            (!favoritesOnly || spec.id in favorites)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjustment_finder)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.adjustment_search)) },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = changedOnly,
                        onClick = { changedOnly = !changedOnly },
                        label = { Text(stringResource(R.string.adjustment_changed)) },
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text(stringResource(R.string.adjustment_favorites)) },
                    )
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (visible.isEmpty()) Text(stringResource(R.string.adjustment_no_results))
                    for (spec in visible) {
                        androidx.compose.runtime.key(spec.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val favorite = spec.id in favorites
                                IconButton(onClick = {
                                    favorites = if (favorite) favorites - spec.id else favorites + spec.id
                                    prefs.edit().putStringSet("favorite_params", favorites).apply()
                                }) {
                                    Text(
                                        if (favorite) "★" else "☆",
                                        modifier = Modifier.semantics {
                                            contentDescription = context.getString(
                                                if (favorite) R.string.adjustment_unfavorite else R.string.adjustment_favorite,
                                            )
                                        },
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    ParamSlider(
                                        label = stringResource(paramLabel(spec.id)),
                                        value = spec.get(adjustments),
                                        range = spec.min..spec.max,
                                        display = formatValue(spec, spec.get(adjustments)),
                                        onChange = { viewModel.setParam(spec, it) },
                                        onChangeFinished = viewModel::commit,
                                        onReset = { viewModel.resetParam(spec) },
                                        help = helpFor(paramLabel(spec.id)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
internal fun SelectivePasteDialog(
    onApply: (AdjustmentSelection) -> Unit,
    onDismiss: () -> Unit,
    @StringRes title: Int = R.string.paste_selective,
    @StringRes confirmLabel: Int = R.string.paste_adjustments,
    sourceName: String? = null,
    onApplyAll: (() -> Unit)? = null,
) {
    var groups by remember { mutableStateOf(setOf(ToolGroup.LIGHT, ToolGroup.COLOR)) }
    var curves by rememberSaveable { mutableStateOf(false) }
    var hsl by rememberSaveable { mutableStateOf(false) }
    var lut by rememberSaveable { mutableStateOf(false) }
    val selection = AdjustmentSelection(groups, curves, hsl, lut)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                if (onApplyAll != null) {
                    if (!sourceName.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.apply_previous_source, sourceName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = { onApplyAll(); onDismiss() }) {
                        Text(stringResource(R.string.apply_previous_all))
                    }
                }
                for (group in listOf(ToolGroup.LIGHT, ToolGroup.COLOR, ToolGroup.DETAIL, ToolGroup.EFFECTS)) {
                    FilterChip(
                        selected = group in groups,
                        onClick = { groups = if (group in groups) groups - group else groups + group },
                        label = { Text(stringResource(toolLabel(group))) },
                    )
                }
                FilterChip(selected = curves, onClick = { curves = !curves }, label = { Text(stringResource(R.string.mode_curves)) })
                FilterChip(selected = hsl, onClick = { hsl = !hsl }, label = { Text(stringResource(R.string.mode_hsl)) })
                FilterChip(selected = lut, onClick = { lut = !lut }, label = { Text("LUT") })
            }
        },
        confirmButton = {
            TextButton(enabled = !selection.isEmpty, onClick = {
                onApply(selection)
                onDismiss()
            }) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** The white balance eyedropper: on, the next tap on the photo makes that spot neutral. */
@Composable
fun WhiteBalancePicker(picking: Boolean, onAuto: () -> Unit, onPicking: (Boolean) -> Unit) {
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
        } else {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onAuto) {
                Text(stringResource(R.string.wb_auto))
            }
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
    val resetLabel = stringResource(R.string.editor_reset_slider, label, display)
    val resetValue by rememberUpdatedState(onReset)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            modifier = Modifier.width(92.dp).pointerInput(help) {
                detectTapGestures(
                    onDoubleTap = { resetValue() },
                    onLongPress = { if (help != null) showHelp = true },
                )
            },
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.weight(1f).clearOfEdgeGestures()
                .semantics { contentDescription = label },
        )
        Surface(
            onClick = onReset,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics { contentDescription = resetLabel },
        ) {
            Box(
                Modifier.widthIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    display,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
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

@StringRes
private fun cropGuideLabel(guide: CropGuide): Int = when (guide) {
    CropGuide.NONE -> R.string.crop_guide_none
    CropGuide.THIRDS -> R.string.crop_guide_thirds
    CropGuide.GRID -> R.string.crop_guide_grid
    CropGuide.CENTER -> R.string.crop_guide_center
    CropGuide.DIAGONAL -> R.string.crop_guide_diagonal
    CropGuide.TRIANGLES -> R.string.crop_guide_triangles
    CropGuide.GOLDEN_RATIO -> R.string.crop_guide_golden_ratio
    CropGuide.GOLDEN_SPIRAL -> R.string.crop_guide_golden_spiral
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropPanel(
    geometry: Geometry,
    cropGuide: CropGuide,
    cropGuideOrientation: Int,
    onCropGuide: (CropGuide) -> Unit,
    onCycleCropGuideOrientation: () -> Unit,
    onAspect: (AspectRatio) -> Unit,
    onStraighten: (Float) -> Unit,
    onStraightenFinished: () -> Unit,
    onPerspective: (vertical: Float, horizontal: Float) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReset: () -> Unit,
    quadDraft: Quad?,
    verticalGuides: Boolean,
    onStartVerticalGuides: () -> Unit,
    onSuggestVerticalGuides: () -> Unit,
    onAutoStraighten: () -> Unit,
    onStartQuad: () -> Unit,
    onApplyQuad: () -> Unit,
    onCancelQuad: () -> Unit,
    onRemoveQuad: () -> Unit,
) {
    if (quadDraft != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(if (verticalGuides) R.string.vertical_guides_hint else R.string.quad_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancelQuad) { Text(stringResource(R.string.look_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApplyQuad, enabled = quadDraft.isUsable(1.0, 1.0)) { Text(stringResource(R.string.quad_apply)) }
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
        var guidesOpen by remember { mutableStateOf(false) }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                AssistChip(
                    onClick = { guidesOpen = true },
                    label = { Text(stringResource(R.string.crop_guide_picker, stringResource(cropGuideLabel(cropGuide)))) },
                )
                DropdownMenu(expanded = guidesOpen, onDismissRequest = { guidesOpen = false }) {
                    for (guide in CropGuide.entries) {
                        DropdownMenuItem(
                            text = { Text(stringResource(cropGuideLabel(guide))) },
                            onClick = {
                                guidesOpen = false
                                onCropGuide(guide)
                            },
                        )
                    }
                }
            }
            if (cropGuide.orientations > 1) {
                AssistChip(
                    onClick = onCycleCropGuideOrientation,
                    label = {
                        Text(stringResource(R.string.crop_guide_orientation, cropGuideOrientation + 1, cropGuide.orientations))
                    },
                )
            }
        }
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
            AssistChip(
                onClick = onStartVerticalGuides,
                label = { Text(stringResource(R.string.vertical_guides)) },
            )
            AssistChip(onClick = onSuggestVerticalGuides, label = { Text(stringResource(R.string.vertical_auto)) })
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
    "lensDistortion" -> R.string.param_lens_distortion
    "defringe" -> R.string.param_defringe
    "localAmount" -> R.string.param_local_amount
    "localHueShift" -> R.string.param_local_hue_shift
    "localSharpness" -> R.string.param_local_sharpness
    "localBlur" -> R.string.param_local_blur
    "localPixelate" -> R.string.param_local_pixelate
    "dehaze" -> R.string.param_dehaze
    "noiseReduction" -> R.string.param_noise_reduction
    "colorNoiseReduction" -> R.string.param_color_noise_reduction
    "vignette" -> R.string.param_vignette
    "vignetteMidpoint" -> R.string.param_vignette_midpoint
    "vignetteRoundness" -> R.string.param_vignette_roundness
    "vignetteFeather" -> R.string.param_vignette_feather
    "vignetteHighlights" -> R.string.param_vignette_highlights
    "grainRoughness" -> R.string.param_grain_roughness
    "grain" -> R.string.param_grain
    "grainSize" -> R.string.param_grain_size
    "bloom" -> R.string.param_bloom
    "halation" -> R.string.param_halation
    "midtoneGamma" -> R.string.param_midtonegamma
    "shadowContrast" -> R.string.param_shadowcontrast
    "midtoneContrast" -> R.string.param_midtonecontrast
    "highlightContrast" -> R.string.param_highlightcontrast
    "shadowTone" -> R.string.param_shadowtone
    "midtoneTone" -> R.string.param_midtonetone
    "highlightTone" -> R.string.param_highlighttone
    "globalHue" -> R.string.param_globalhue
    "redBlueSwap" -> R.string.param_redblueswap
    "softFocus" -> R.string.param_softfocus
    "matte" -> R.string.param_matte
    "sepia" -> R.string.param_sepia
    "cyanotype" -> R.string.param_cyanotype
    "negative" -> R.string.param_negative
    "solarize" -> R.string.param_solarize
    "posterize" -> R.string.param_posterize
    "threshold" -> R.string.param_threshold
    "bleachBypass" -> R.string.param_bleachbypass
    "crossProcess" -> R.string.param_crossprocess
    "orton" -> R.string.param_orton
    "brightness" -> R.string.param_brightness
    "shadowFloor" -> R.string.param_shadowfloor
    "whiteCeiling" -> R.string.param_whiteceiling
    "shadowSoftClip" -> R.string.param_shadowsoftclip
    "highlightSoftClip" -> R.string.param_highlightsoftclip
    "lumaContrast" -> R.string.param_lumacontrast
    "shadowExposure" -> R.string.param_shadowexposure
    "highlightExposure" -> R.string.param_highlightexposure
    "redGamma" -> R.string.param_redgamma
    "greenGamma" -> R.string.param_greengamma
    "blueGamma" -> R.string.param_bluegamma
    "redGain" -> R.string.param_redgain
    "greenGain" -> R.string.param_greengain
    "blueGain" -> R.string.param_bluegain
    "redOffset" -> R.string.param_redoffset
    "greenOffset" -> R.string.param_greenoffset
    "blueOffset" -> R.string.param_blueoffset
    "shadowSaturation" -> R.string.param_shadowsaturation
    "midtoneSaturation" -> R.string.param_midtonesaturation
    "highlightSaturation" -> R.string.param_highlightsaturation
    "shadowTint" -> R.string.param_shadowtint
    "midtoneTint" -> R.string.param_midtonetint
    "highlightTint" -> R.string.param_highlighttint
    "cycleRgb" -> R.string.param_cyclergb
    "monochromeRed" -> R.string.param_monochromered
    "monochromeGreen" -> R.string.param_monochromegreen
    "monochromeBlue" -> R.string.param_monochromeblue
    "lumaSharpen" -> R.string.param_lumasharpen
    "chromaSharpen" -> R.string.param_chromasharpen
    "textureAmount" -> R.string.param_textureamount
    "edgeDetect" -> R.string.param_edgedetect
    "emboss" -> R.string.param_emboss
    "pencilSketch" -> R.string.param_pencilsketch
    "highPass" -> R.string.param_highpass
    "channelInvertRed" -> R.string.param_channelinvertred
    "channelInvertGreen" -> R.string.param_channelinvertgreen
    "channelInvertBlue" -> R.string.param_channelinvertblue
    "lumaInvert" -> R.string.param_lumainvert
    "thresholdColor" -> R.string.param_thresholdcolor
    "posterizeLuma" -> R.string.param_posterizeluma
    "thermalMap" -> R.string.param_thermalmap
    "purpleGold" -> R.string.param_purplegold
    "tealOrange" -> R.string.param_tealorange
    "infraredLook" -> R.string.param_infraredlook
    "filmToe" -> R.string.param_filmtoe
    "filmShoulder" -> R.string.param_filmshoulder
    "lightLeak" -> R.string.param_lightleak
    "tiltShift" -> R.string.param_tiltshift
    "radialBlur" -> R.string.param_radialblur
    "scanlines" -> R.string.param_scanlines
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
    R.string.param_local_amount -> R.string.help_local_amount
    R.string.param_local_hue_shift -> R.string.help_local_hue_shift
    R.string.param_local_sharpness -> R.string.help_local_sharpness
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
    R.string.param_bloom -> R.string.help_param_bloom
    R.string.param_halation -> R.string.help_param_halation
    R.string.param_midtonegamma -> R.string.help_param_midtonegamma
    R.string.param_shadowcontrast -> R.string.help_param_shadowcontrast
    R.string.param_midtonecontrast -> R.string.help_param_midtonecontrast
    R.string.param_highlightcontrast -> R.string.help_param_highlightcontrast
    R.string.param_shadowtone -> R.string.help_param_shadowtone
    R.string.param_midtonetone -> R.string.help_param_midtonetone
    R.string.param_highlighttone -> R.string.help_param_highlighttone
    R.string.param_globalhue -> R.string.help_param_globalhue
    R.string.param_redblueswap -> R.string.help_param_redblueswap
    R.string.param_softfocus -> R.string.help_param_softfocus
    R.string.param_matte -> R.string.help_param_matte
    R.string.param_sepia -> R.string.help_param_sepia
    R.string.param_cyanotype -> R.string.help_param_cyanotype
    R.string.param_negative -> R.string.help_param_negative
    R.string.param_solarize -> R.string.help_param_solarize
    R.string.param_posterize -> R.string.help_param_posterize
    R.string.param_threshold -> R.string.help_param_threshold
    R.string.param_bleachbypass -> R.string.help_param_bleachbypass
    R.string.param_crossprocess -> R.string.help_param_crossprocess
    R.string.param_orton -> R.string.help_param_orton
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
