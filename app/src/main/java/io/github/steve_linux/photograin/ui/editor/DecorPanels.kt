// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Frame
import io.github.steve_linux.photograin.model.FrameStyle
import io.github.steve_linux.photograin.model.ShapeItem
import io.github.steve_linux.photograin.model.ShapeType
import io.github.steve_linux.photograin.model.TextFont
import io.github.steve_linux.photograin.model.TextItem
import kotlinx.coroutines.delay

/** Colors offered for frames and text, ARGB in sRGB. */
private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFF3EBDD, 0xFFBDBDBD, 0xFF333333, 0xFF000000, 0xFFC62828, 0xFFF9A825, 0xFF1565C0,
).map { it.toInt() }

/** "Effects | Frame | Text" switch above the Effects panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsModeSelector(mode: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.mode_effects, R.string.mode_frame, R.string.mode_text, R.string.mode_shapes)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = mode == i,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
            ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FramePanel(frame: Frame, onChange: (Frame) -> Unit, onChangeFinished: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (style in FrameStyle.entries) {
                FilterChip(
                    selected = frame.style == style,
                    onClick = {
                        onChange(frame.copy(style = style))
                        onChangeFinished()
                    },
                    label = { Text(stringResource(frameLabel(style))) },
                )
            }
        }
        if (frame.style == FrameStyle.NONE) return@Column
        ParamSlider(
            label = stringResource(R.string.frame_width),
            help = helpFor(R.string.frame_width),
            value = frame.width,
            range = 0.02f..1f,
            display = "${Math.round(frame.width * 100)}",
            onChange = { onChange(frame.copy(width = it)) },
            onChangeFinished = onChangeFinished,
            onReset = {
                onChange(frame.copy(width = Frame().width))
                onChangeFinished()
            },
        )
        ColorRow(frame.color) {
            onChange(frame.copy(color = it))
            onChangeFinished()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextPanel(
    texts: List<TextItem>,
    selected: Int?,
    onAdd: (String) -> Unit,
    onSelect: (Int?) -> Unit,
    onChange: (TextItem) -> Unit,
    onDelete: (Int) -> Unit,
    onChangeFinished: () -> Unit,
) {
    // null: no dialog; -1: a new text; otherwise the index being rewritten.
    var editing by remember { mutableStateOf<Int?>(null) }
    val index = selected?.takeIf { it in texts.indices }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            texts.forEachIndexed { i, item ->
                FilterChip(
                    selected = i == index,
                    onClick = { onSelect(if (i == index) null else i) },
                    label = { Text(item.text.lines().first(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp)) },
                )
            }
            AssistChip(
                enabled = texts.size < TextItem.MAX_ITEMS,
                onClick = { editing = -1 },
                label = { Text(stringResource(R.string.text_add)) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
        if (index == null) {
            Text(
                stringResource(if (texts.isEmpty()) R.string.text_hint_empty else R.string.text_hint_select),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            val item = texts[index]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = item.bold, onCheckedChange = { onChange(item.copy(bold = it)); onChangeFinished() })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.text_bold), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(16.dp))
                Switch(checked = item.shadow, onCheckedChange = { onChange(item.copy(shadow = it)); onChangeFinished() })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.text_shadow), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { editing = index }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.text_edit))
                }
                IconButton(onClick = { onDelete(index) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.text_delete))
                }
            }
            ParamSlider(
                label = stringResource(R.string.text_size),
                help = helpFor(R.string.text_size),
                value = item.size,
                range = 0f..1f,
                display = "${Math.round(item.size * 100)}",
                onChange = { onChange(item.copy(size = it)) },
                onChangeFinished = onChangeFinished,
                onReset = {
                    onChange(item.copy(size = TextItem("").size))
                    onChangeFinished()
                },
            )
            ParamSlider(
                label = stringResource(R.string.text_opacity),
                help = helpFor(R.string.text_opacity),
                value = item.opacity,
                range = 0.05f..1f,
                display = "${Math.round(item.opacity * 100)}",
                onChange = { onChange(item.copy(opacity = it)) },
                onChangeFinished = onChangeFinished,
                onReset = {
                    onChange(item.copy(opacity = 1f))
                    onChangeFinished()
                },
            )
            ParamSlider(
                label = stringResource(R.string.text_angle),
                help = helpFor(R.string.text_angle),
                value = item.angle,
                range = -180f..180f,
                display = "${Math.round(item.angle)}°",
                onChange = { onChange(item.copy(angle = it)) },
                onChangeFinished = onChangeFinished,
                onReset = {
                    onChange(item.copy(angle = 0f))
                    onChangeFinished()
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = item.tiled,
                    onCheckedChange = { tiled ->
                        // A watermark starts see-through and slanted, as watermarks usually are.
                        val start = if (tiled && item.opacity == 1f && item.angle == 0f) item.copy(opacity = 0.35f, angle = -30f) else item
                        onChange(start.copy(tiled = tiled))
                        onChangeFinished()
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.text_tiled), style = MaterialTheme.typography.labelLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (font in TextFont.entries) {
                    FilterChip(
                        selected = item.font == font,
                        onClick = { onChange(item.copy(font = font)); onChangeFinished() },
                        label = { Text(stringResource(fontLabel(font))) },
                    )
                }
            }
            ColorRow(item.color) {
                onChange(item.copy(color = it))
                onChangeFinished()
            }
        }
    }

    editing?.let { target ->
        TextDialog(
            initial = if (target >= 0) texts.getOrNull(target)?.text.orEmpty() else "",
            onDismiss = { editing = null },
            onDone = { text ->
                editing = null
                if (target < 0) {
                    onAdd(text)
                } else {
                    texts.getOrNull(target)?.let { onChange(it.copy(text = text)) }
                    onChangeFinished()
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapesPanel(
    shapes: List<ShapeItem>,
    selected: Int?,
    onAdd: (ShapeType) -> Unit,
    onSelect: (Int?) -> Unit,
    onChange: (ShapeItem) -> Unit,
    onDelete: (Int) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val index = selected?.takeIf { it in shapes.indices }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shapes.forEachIndexed { i, shape ->
                FilterChip(
                    selected = i == index,
                    onClick = { onSelect(if (i == index) null else i) },
                    label = { Text(stringResource(shapeName(shape.type), i + 1)) },
                )
            }
            for (type in ShapeType.entries) {
                AssistChip(
                    enabled = shapes.size < ShapeItem.MAX_ITEMS,
                    onClick = { onAdd(type) },
                    label = { Text(stringResource(shapeLabel(type))) },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        if (index == null) {
            Text(
                stringResource(if (shapes.isEmpty()) R.string.shape_hint_empty else R.string.shape_hint_select),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }
        val shape = shapes[index]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (shape.isBox) {
                Switch(checked = shape.fill, onCheckedChange = { onChange(shape.copy(fill = it)); onChangeFinished() })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shape_fill), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onDelete(index) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.shape_delete))
            }
        }
        if (!(shape.isBox && shape.fill)) {
            ParamSlider(
                label = stringResource(R.string.shape_width),
                help = helpFor(R.string.shape_width),
                value = shape.width,
                range = 0f..1f,
                display = "${Math.round(shape.width * 100)}",
                onChange = { onChange(shape.copy(width = it)) },
                onChangeFinished = onChangeFinished,
                onReset = {
                    onChange(shape.copy(width = ShapeItem(shape.type).width))
                    onChangeFinished()
                },
            )
        }
        ColorRow(shape.color) {
            onChange(shape.copy(color = it))
            onChangeFinished()
        }
    }
}

private fun shapeName(t: ShapeType): Int = when (t) {
    ShapeType.LINE -> R.string.shape_line_n
    ShapeType.ARROW -> R.string.shape_arrow_n
    ShapeType.RECT -> R.string.shape_rect_n
    ShapeType.ELLIPSE -> R.string.shape_ellipse_n
}

private fun shapeLabel(t: ShapeType): Int = when (t) {
    ShapeType.LINE -> R.string.shape_line
    ShapeType.ARROW -> R.string.shape_arrow
    ShapeType.RECT -> R.string.shape_rect
    ShapeType.ELLIPSE -> R.string.shape_ellipse
}

@Composable
private fun TextDialog(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_dialog_title)) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 1, maxLines = 4, modifier = Modifier.focusRequester(focus))
            // Open with the keyboard up, ready to type: wait for the dialog's window to take focus first.
            LaunchedEffect(Unit) {
                delay(150)
                focus.requestFocus()
                keyboard?.show()
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onDone(text.trim()) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (c in SWATCHES) {
            val ring = if (c == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(if (c == selected) 3.dp else 1.dp, ring, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { onPick(c) },
            )
        }
    }
}

private fun frameLabel(s: FrameStyle): Int = when (s) {
    FrameStyle.NONE -> R.string.frame_none
    FrameStyle.BORDER -> R.string.frame_border
    FrameStyle.POLAROID -> R.string.frame_polaroid
}

private fun fontLabel(f: TextFont): Int = when (f) {
    TextFont.SANS -> R.string.font_sans
    TextFont.SERIF -> R.string.font_serif
    TextFont.MONO -> R.string.font_mono
    TextFont.HANDWRITING -> R.string.font_handwriting
}
