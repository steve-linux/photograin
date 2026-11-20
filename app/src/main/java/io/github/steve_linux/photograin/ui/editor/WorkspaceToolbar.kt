// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Details
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LensBlur
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
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
            if (hasActions) WorkspaceItem(item, R.string.workspace_actions, Icons.Outlined.AutoFixHigh, actions, onActions)
            WorkspaceItem(item, R.string.tool_look, Icons.Outlined.Filter, !actions && tool == ToolGroup.LOOK, onLooks)
            WorkspaceItem(item, R.string.tool_crop, Icons.Outlined.Crop, !actions && tool == ToolGroup.CROP, onCrop)
            WorkspaceItem(item, R.string.workspace_edit, Icons.Outlined.Tune, !actions && tool in colorAndToneTools, onEdit)
            WorkspaceItem(item, R.string.mode_masks, Icons.Outlined.LensBlur, !actions && tool == ToolGroup.LOCAL && !retouch) {
                onLocal(false)
            }
            WorkspaceItem(item, R.string.tool_local, Icons.Outlined.Healing, !actions && tool == ToolGroup.LOCAL && retouch) {
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
    val color = if (selected) colors.onSurface else colors.onSurfaceVariant
    Box(
        modifier.heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        if (selected) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.52f),
                thickness = 2.dp,
                color = colors.primary,
            )
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 2.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
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

/** Compact mode tabs shared by the editing panels. */
@Composable
internal fun WorkspaceModeTabs(
    labels: List<Int>,
    selected: Int,
    scrollable: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier).selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                val bringIntoView = remember { BringIntoViewRequester() }
                var placed by remember { mutableStateOf(false) }
                LaunchedEffect(active, scrollable, placed) {
                    if (active && scrollable && placed) bringIntoView.bringIntoView()
                }
                Box(
                    modifier = (if (scrollable) Modifier.width(112.dp) else Modifier.weight(1f)).heightIn(min = 44.dp)
                        .bringIntoViewRequester(bringIntoView)
                        .onGloballyPositioned { placed = true }
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (active) colors.onSurface else colors.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp),
                    )
                    if (active) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)
                                .background(colors.primary),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}
