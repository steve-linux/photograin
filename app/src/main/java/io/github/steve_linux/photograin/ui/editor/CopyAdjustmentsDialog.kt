// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.AdjustmentGroup
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CopiedAdjustments

@Composable
internal fun CopyAdjustmentsDialog(
    adjustments: Adjustments,
    onDismiss: () -> Unit,
    onCopy: (Set<AdjustmentGroup>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(ArrayList(AdjustmentGroup.entries)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_adjustments)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.copy_groups_hint), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selected = ArrayList(AdjustmentGroup.entries) }) {
                        Text(stringResource(R.string.copy_groups_all))
                    }
                    TextButton(onClick = { selected = ArrayList(CopiedAdjustments.modifiedGroups(adjustments)) }) {
                        Text(stringResource(R.string.copy_groups_modified))
                    }
                    TextButton(onClick = { selected = arrayListOf() }) {
                        Text(stringResource(R.string.copy_groups_none))
                    }
                }
                for (group in AdjustmentGroup.entries) {
                    val checked = group in selected
                    Row(
                        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) {
                            selected = ArrayList(if (it) selected + group else selected - group)
                        }.heightIn(min = 48.dp).padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(stringResource(groupLabel(group)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onCopy(selected.toSet()) }) {
                Text(stringResource(R.string.copy_groups_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.look_cancel)) }
        },
    )
}

private fun groupLabel(group: AdjustmentGroup) = when (group) {
    AdjustmentGroup.LIGHT -> R.string.tool_light
    AdjustmentGroup.COLOR -> R.string.tool_color
    AdjustmentGroup.DETAIL -> R.string.tool_detail
    AdjustmentGroup.EFFECTS -> R.string.tool_effects
    AdjustmentGroup.LOOK -> R.string.tool_look
}
