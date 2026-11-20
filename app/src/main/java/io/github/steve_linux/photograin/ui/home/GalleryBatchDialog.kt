// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoKeywords
import io.github.steve_linux.photograin.model.PhotoRating

@Composable
internal fun GalleryBatchDialog(
    count: Int,
    albums: List<String>,
    onStars: (Int) -> Unit,
    onFlag: (PhotoFlag) -> Unit,
    onKeywords: (Set<String>) -> Boolean,
    onAlbum: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keywords by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    val words = PhotoKeywords.parse(keywords)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_selected, count)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                PhotoReviewControls(PhotoRating(), onStars = { onStars(it); onDismiss() }, onFlag = { onFlag(it); onDismiss() })
                TextButton(onClick = { onStars(0); onDismiss() }) { Text(stringResource(R.string.gallery_clear_stars)) }
                OutlinedTextField(
                    value = keywords, onValueChange = { keywords = it; rejected = false },
                    isError = words == null || rejected,
                    label = { Text(stringResource(R.string.photo_keywords)) },
                )
                if (rejected) Text(stringResource(R.string.gallery_keywords_limit))
                TextButton(enabled = !words.isNullOrEmpty(), onClick = {
                    if (onKeywords(checkNotNull(words))) onDismiss() else rejected = true
                }) { Text(stringResource(R.string.gallery_add_keywords)) }
                for (name in albums) TextButton(onClick = { onAlbum(name); onDismiss() }) {
                    Text(stringResource(R.string.gallery_add_album, name))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
