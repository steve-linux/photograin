// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Details
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.ToolGroup

internal val colorAndToneTools = listOf(ToolGroup.LIGHT, ToolGroup.COLOR, ToolGroup.EFFECTS, ToolGroup.DETAIL)

@Composable
internal fun WorkspaceToolbar(
    tool: ToolGroup,
    retouch: Boolean,
    actions: Boolean,
    hasActions: Boolean,
    onActions: () -> Unit,
    onLooks: () -> Unit,
    onCrop: () -> Unit,
    onEdit: () -> Unit,
    onLocal: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            val item = Modifier.weight(1f)
            if (hasActions) WorkspaceItem(item, R.string.workspace_actions, Icons.Outlined.AutoAwesome, actions, onActions)
            WorkspaceItem(item, R.string.tool_look, Icons.Outlined.Style, !actions && tool == ToolGroup.LOOK, onLooks)
            WorkspaceItem(item, R.string.tool_crop, Icons.Outlined.Crop, !actions && tool == ToolGroup.CROP, onCrop)
            WorkspaceItem(item, R.string.workspace_edit, Icons.Outlined.Tune, !actions && tool in colorAndToneTools, onEdit)
            WorkspaceItem(item, R.string.mode_masks, Icons.Outlined.Gradient, !actions && tool == ToolGroup.LOCAL && !retouch) {
                onLocal(false)
            }
            WorkspaceItem(item, R.string.tool_local, Icons.Outlined.Brush, !actions && tool == ToolGroup.LOCAL && retouch) {
                onLocal(true)
            }
        }
    }
}

@Composable
internal fun EditCategoryBar(selected: ToolGroup, onSelect: (ToolGroup) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).selectableGroup()) {
        for (tool in colorAndToneTools) {
            val icon = when (tool) {
                ToolGroup.LIGHT -> Icons.Outlined.WbSunny
                ToolGroup.COLOR -> Icons.Outlined.Palette
                ToolGroup.EFFECTS -> Icons.Outlined.AutoAwesome
                else -> Icons.Outlined.Details
            }
            WorkspaceItem(Modifier.weight(1f), toolLabel(tool), icon, selected == tool) { onSelect(tool) }
        }
    }
}

@Composable
private fun WorkspaceItem(
    modifier: Modifier,
    @StringRes label: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val color = if (selected) colors.primary else colors.onSurface
    Box(
        modifier.heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        if (selected) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.7f),
                thickness = 2.dp,
                color = color,
            )
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 2.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            BasicText(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall.copy(color = color),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
            )
        }
    }
}
