// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.AiUpscaler
import io.github.steve_linux.photograin.data.ObjectRemover
import io.github.steve_linux.photograin.data.SubjectSegmenter

/**
 * The AI tools, one tap away: elsewhere they sit deep in Retouch or in the export dialog,
 * where people who do not know they exist never find them.
 */
enum class QuickAction(val label: Int, val icon: ImageVector) {
    REMOVE_BACKGROUND(R.string.quick_remove_background, Icons.Outlined.ContentCut),
    REMOVE_OBJECT(R.string.quick_remove_object, Icons.Outlined.AutoFixHigh),
    ENHANCE(R.string.quick_enhance, Icons.Outlined.HighQuality),
    ;

    val available: Boolean
        get() = when (this) {
            REMOVE_BACKGROUND -> SubjectSegmenter.available
            REMOVE_OBJECT -> ObjectRemover.available
            ENHANCE -> AiUpscaler.available
        }

    companion object {
        fun availableEntries() = entries.filter { it.available }
    }
}

/** A row of buttons above the photo in the editor. */
@Composable
fun QuickActionsBar(onAction: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    val actions = QuickAction.availableEntries()
    if (actions.isEmpty()) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (action in actions) {
            FilledTonalButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A card on the home screen: pick an action, then the photo, and the editor opens on the tool. */
@Composable
fun QuickActionsCard(onAction: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    val actions = QuickAction.availableEntries()
    if (actions.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.quick_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.quick_body), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (action in actions) {
                    FilledTonalButton(
                        onClick = { onAction(action) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(action.icon, contentDescription = null)
                            Text(
                                stringResource(action.label),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
