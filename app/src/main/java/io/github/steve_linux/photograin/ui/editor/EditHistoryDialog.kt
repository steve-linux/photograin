// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.EditChanges
import io.github.steve_linux.photograin.model.EditState

/** Selection is staged until Restore, so closing the dialog never changes the photo. */
@Composable
internal fun EditHistoryDialog(
    states: List<EditState>, current: Int, onRestore: (Int) -> Unit, onDismiss: () -> Unit,
) {
    var selected by rememberSaveable(current, states.size) { mutableIntStateOf(current) }
    val summaries = remember(states) { states.zipWithNext { a, b -> EditChanges.between(a, b) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_title)) },
        text = {
            Column {
                Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall)
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    state = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0)),
                ) {
                    itemsIndexed(states) { index, _ ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = index }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected == index, onClick = { selected = index })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (index == 0) stringResource(R.string.history_initial)
                                    else stringResource(R.string.history_step, index),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (index > 0) {
                                    val summary = summaries[index - 1]
                                    val labels = summary.parameters.map { stringResource(paramLabel(it)) } +
                                        summary.areas.map { stringResource(areaLabel(it)) }
                                    Text(labels.joinToString(" · "), maxLines = 3, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (index == current || index > current) Text(
                                    stringResource(if (index == current) R.string.history_current else R.string.history_redo),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected in states.indices && selected != current, onClick = { onRestore(selected) }) {
                Text(stringResource(R.string.history_restore))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private fun areaLabel(area: EditChanges.Area) = when (area) {
    EditChanges.Area.CURVES -> R.string.mode_curves
    EditChanges.Area.HSL -> R.string.mode_hsl
    EditChanges.Area.GRADING -> R.string.mode_grading
    EditChanges.Area.MONOCHROME -> R.string.mode_monochrome
    EditChanges.Area.LOOK -> R.string.tool_look
    EditChanges.Area.GEOMETRY -> R.string.tool_crop
    EditChanges.Area.MASKS -> R.string.guide_local_title
    EditChanges.Area.RETOUCH -> R.string.tool_local
    EditChanges.Area.FRAME -> R.string.mode_frame
    EditChanges.Area.TEXT -> R.string.mode_text
    EditChanges.Area.SHAPES -> R.string.history_shapes
    EditChanges.Area.OPTICS -> R.string.optics_title
}
