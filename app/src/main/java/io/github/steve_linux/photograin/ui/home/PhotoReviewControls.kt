// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoReviewControls(
    rating: PhotoRating,
    onStars: (Int) -> Unit,
    onFlag: (PhotoFlag) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.Center) {
        Row {
            for (stars in 1..5) {
                val filled = rating.stars >= stars
                IconToggleButton(
                    checked = rating.stars == stars,
                    onCheckedChange = { onStars(stars) },
                    modifier = Modifier.size(44.dp, 48.dp),
                ) {
                    Icon(
                        if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.photo_stars, stars),
                        tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    if (rating.flag == PhotoFlag.REJECTED) Icons.Outlined.Close else Icons.Outlined.Flag,
                    contentDescription = stringResource(flagStateLabel(rating.flag)),
                    tint = if (rating.flag == PhotoFlag.NONE) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for ((flag, label) in listOf(
                    PhotoFlag.PICKED to R.string.photo_mark_pick,
                    PhotoFlag.REJECTED to R.string.photo_mark_reject,
                    PhotoFlag.NONE to R.string.photo_unflag,
                )) {
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = { menu = false; onFlag(flag) },
                        leadingIcon = if (rating.flag == flag) {
                            { Text("✓", color = MaterialTheme.colorScheme.primary) }
                        } else null,
                    )
                }
            }
        }
    }
}

private fun flagStateLabel(flag: PhotoFlag): Int = when (flag) {
    PhotoFlag.PICKED -> R.string.photo_flag_picked
    PhotoFlag.REJECTED -> R.string.photo_flag_rejected
    PhotoFlag.NONE -> R.string.photo_flag_none
}

internal fun ratingBadge(rating: PhotoRating): String = buildString {
    when (rating.flag) {
        PhotoFlag.PICKED -> append("⚑ ")
        PhotoFlag.REJECTED -> append("× ")
        PhotoFlag.NONE -> Unit
    }
    if (rating.stars > 0) append("${rating.stars}★")
}.trimEnd()
