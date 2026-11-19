// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.PhotoKeywordStore
import io.github.steve_linux.photograin.model.PhotoKeywords

@Composable
internal fun PhotoKeywordEditor(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PhotoKeywordStore.get(context) }
    val keywords by store.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable(uri.toString()) {
        mutableStateOf(keywords[uri.toString()].orEmpty().sortedBy(PhotoKeywords::key).joinToString(", "))
    }
    val parsed = remember(draft) { PhotoKeywords.parse(draft) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_keywords)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 4096) draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                isError = parsed == null,
                supportingText = {
                    Text(
                        if (parsed == null) stringResource(
                            R.string.photo_keywords_limit, PhotoKeywords.MAX_COUNT, PhotoKeywords.MAX_LENGTH,
                        ) else stringResource(R.string.photo_keywords_hint),
                    )
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    val words = parsed ?: return@TextButton
                    store.set(uri, words)
                    onDismiss()
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
