// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.RetouchType

/** Healing, clone and red eye: the type and size of the next spot, and the selected spot's settings. */
@Composable
fun RetouchPanel(
    spots: List<RetouchSpot>,
    selected: Int?,
    type: RetouchType,
    size: Float,
    onType: (RetouchType) -> Unit,
    onSize: (Float) -> Unit,
    onSpot: (RetouchSpot) -> Unit,
    onDelete: (Int) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val index = selected?.takeIf { it in spots.indices }
    val spot = index?.let { spots[it] }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val current = spot?.type ?: type
            // The chips scroll on narrow screens, so the delete button always keeps its place.
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = current == RetouchType.HEAL,
                    onClick = { onType(RetouchType.HEAL) },
                    label = { Text(stringResource(R.string.retouch_heal)) },
                    leadingIcon = { Icon(Icons.Outlined.Healing, contentDescription = null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = current == RetouchType.CLONE,
                    onClick = { onType(RetouchType.CLONE) },
                    label = { Text(stringResource(R.string.retouch_clone)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = current == RetouchType.RED_EYE,
                    onClick = { onType(RetouchType.RED_EYE) },
                    label = { Text(stringResource(R.string.retouch_red_eye)) },
                    leadingIcon = { Icon(Icons.Outlined.RemoveRedEye, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
            if (index != null) {
                IconButton(onClick = { onDelete(index) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.retouch_delete))
                }
            }
        }
        if (spot == null) {
            Text(
                stringResource(
                    when {
                        type == RetouchType.RED_EYE -> R.string.retouch_hint_red_eye
                        spots.isEmpty() -> R.string.retouch_hint_empty
                        else -> R.string.retouch_hint_select
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            ParamSlider(
                label = stringResource(R.string.brush_size),
                help = helpFor(R.string.brush_size),
                value = size,
                range = 0f..1f,
                display = "${Math.round(size * 100)}",
                onChange = onSize,
                onChangeFinished = {},
                onReset = { onSize(0.3f) },
            )
            return@Column
        }
        ParamSlider(
            label = stringResource(R.string.brush_size),
            help = helpFor(R.string.brush_size),
            value = spot.radius,
            range = MIN_RADIUS..MAX_RADIUS,
            display = "${Math.round((spot.radius - MIN_RADIUS) / (MAX_RADIUS - MIN_RADIUS) * 100)}",
            onChange = { onSpot(spot.copy(radius = it)) },
            onChangeFinished = onChangeFinished,
            onReset = {},
        )
        ParamSlider(
            label = stringResource(R.string.local_feather),
            help = helpFor(R.string.local_feather),
            value = spot.feather,
            range = 0f..1f,
            display = "${Math.round(spot.feather * 100)}",
            onChange = { onSpot(spot.copy(feather = it)) },
            onChangeFinished = onChangeFinished,
            onReset = {
                onSpot(spot.copy(feather = 0.5f))
                onChangeFinished()
            },
        )
        ParamSlider(
            label = stringResource(R.string.retouch_opacity),
            help = helpFor(R.string.retouch_opacity),
            value = spot.opacity,
            range = 0f..1f,
            display = "${Math.round(spot.opacity * 100)}",
            onChange = { onSpot(spot.copy(opacity = it)) },
            onChangeFinished = onChangeFinished,
            onReset = {
                onSpot(spot.copy(opacity = 1f))
                onChangeFinished()
            },
        )
    }
}

/** Radius range of a spot, in source long-edge units. */
private const val MIN_RADIUS = 0.003f
private const val MAX_RADIUS = 0.12f
