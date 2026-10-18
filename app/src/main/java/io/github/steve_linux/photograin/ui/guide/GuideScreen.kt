// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.steve_linux.photograin.R

/** One topic of the guide; [inline] is drawn where the text says {icon}. */
private data class Topic(val icon: ImageVector, val title: Int, val body: Int, val inline: ImageVector? = null)

private val TOPICS = listOf(
    Topic(Icons.Outlined.AddPhotoAlternate, R.string.guide_open_title, R.string.guide_open_body),
    Topic(Icons.Outlined.Compare, R.string.guide_compare_title, R.string.guide_compare_body, Icons.Outlined.Compare),
    Topic(Icons.Outlined.Style, R.string.guide_look_title, R.string.guide_look_body),
    Topic(Icons.Outlined.WbSunny, R.string.guide_light_title, R.string.guide_light_body),
    Topic(Icons.Outlined.Palette, R.string.guide_color_title, R.string.guide_color_body),
    Topic(Icons.Outlined.AutoAwesome, R.string.guide_detail_title, R.string.guide_detail_body),
    Topic(Icons.Outlined.Gradient, R.string.guide_local_title, R.string.guide_local_body, Icons.Outlined.Visibility),
    Topic(Icons.Outlined.Healing, R.string.guide_heal_title, R.string.guide_heal_body),
    Topic(Icons.Outlined.TextFields, R.string.guide_decor_title, R.string.guide_decor_body),
    Topic(Icons.Outlined.Crop, R.string.guide_crop_title, R.string.guide_crop_body),
    Topic(Icons.Outlined.ContentCopy, R.string.guide_copy_title, R.string.guide_copy_body),
    Topic(Icons.Outlined.Save, R.string.guide_export_title, R.string.guide_export_body, Icons.Outlined.Save),
    Topic(Icons.Outlined.Settings, R.string.guide_settings_title, R.string.guide_settings_body, Icons.Outlined.Settings),
)

private const val INLINE_ICON = "icon"

/** A short tour of every feature, for people new to photo editing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TOPICS) { TopicCard(it) }
        }
    }
}

@Composable
private fun TopicCard(topic: Topic) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                topic.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(topic.title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                val body = stringResource(topic.body)
                val parts = body.split("{icon}", limit = 2)
                val text = buildAnnotatedString {
                    append(parts[0])
                    if (parts.size == 2) {
                        appendInlineContent(INLINE_ICON, "[icon]")
                        append(parts[1])
                    }
                }
                val inline = topic.inline?.let { icon ->
                    mapOf(
                        INLINE_ICON to InlineTextContent(
                            Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter),
                        ) { Icon(icon, contentDescription = null) },
                    )
                }.orEmpty()
                Text(
                    text,
                    inlineContent = inline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The home screen's way into the guide. */
@Composable
fun GuideEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.guide_entry_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.guide_entry_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
