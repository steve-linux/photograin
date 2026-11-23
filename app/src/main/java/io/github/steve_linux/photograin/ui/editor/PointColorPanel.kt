// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.PointColor
import kotlin.math.roundToInt

/**
 * Point Color: colors picked on the photo with the eyedropper, each with its own hue,
 * saturation and luminance shift and how wide a range of nearby colors it reaches.
 */
@Composable
internal fun PointColorPanel(
    points: List<PointColor>,
    selected: Int?,
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onChange: (PointColor) -> Unit,
    onChangeFinished: () -> Unit,
    onDelete: () -> Unit,
    showRange: Boolean,
    onShowRange: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = picking,
                enabled = points.size < PointColor.MAX || picking,
                onClick = { onPicking(!picking) },
                label = { Text(stringResource(R.string.point_color_pick)) },
                leadingIcon = { Icon(Icons.Outlined.Colorize, contentDescription = null, Modifier.size(18.dp)) },
            )
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                points.forEachIndexed { index, point -> Swatch(point, index == selected) { onSelect(index) } }
            }
        }
        if (picking || points.isEmpty()) {
            Text(
                stringResource(if (points.size >= PointColor.MAX) R.string.point_color_full else R.string.point_color_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        val point = selected?.let { points.getOrNull(it) } ?: return@Column
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = showRange,
                onClick = { onShowRange(!showRange) },
                label = { Text(stringResource(R.string.point_color_show_range)) },
                leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f, fill = false),
            )
            Box(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.point_color_delete))
            }
        }
        PointSlider(R.string.hsl_hue, point.hueShift, onChangeFinished, { onChange(point.copy(hueShift = it)) })
        PointSlider(R.string.hsl_saturation, point.saturationShift, onChangeFinished, { onChange(point.copy(saturationShift = it)) })
        PointSlider(R.string.hsl_luminance, point.luminanceShift, onChangeFinished, { onChange(point.copy(luminanceShift = it)) })
        ParamSlider(
            label = stringResource(R.string.point_color_range),
            value = point.range,
            range = 0f..1f,
            display = "${(point.range * 100).roundToInt()}",
            onChange = { onChange(point.copy(range = it)) },
            onChangeFinished = onChangeFinished,
            onReset = { onChange(point.copy(range = PointColor().range)); onChangeFinished() },
            help = R.string.help_point_color_range,
        )
    }
}

@Composable
private fun PointSlider(label: Int, value: Float, onChangeFinished: () -> Unit, onChange: (Float) -> Unit) {
    val shown = (value * 100).roundToInt()
    ParamSlider(
        label = stringResource(label),
        value = value,
        range = -1f..1f,
        display = if (shown > 0) "+$shown" else "$shown",
        onChange = onChange,
        onChangeFinished = onChangeFinished,
        onReset = { onChange(0f); onChangeFinished() },
    )
}

@Composable
private fun Swatch(point: PointColor, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.point_color_swatch)
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape)
            .padding(if (selected) 4.dp else 2.dp)
            .clip(CircleShape)
            .background(Color(0xFF000000.toInt() or PointColor.swatch(point)))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
    )
}
