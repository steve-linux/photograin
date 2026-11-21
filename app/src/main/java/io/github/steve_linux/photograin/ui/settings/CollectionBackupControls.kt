// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.CollectionBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CollectionBackupControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember(context) { CollectionBackup(context.applicationContext) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<CollectionBackup.RestoreResult?>(null) }
    var archive by remember { mutableStateOf<Uri?>(null) }

    fun start(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = false
        exported = false
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = true
            } finally {
                busy = false
            }
        }
    }
    fun restore(originals: List<Uri>) {
        val source = archive ?: return
        report = null
        start { report = withContext(Dispatchers.IO) { backup.restore(source, originals) } }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) start {
            withContext(Dispatchers.IO) { backup.export(uri) }
            exported = true
        }
    }
    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            archive = uri
            report = null
            error = false
        }
    }
    val originalsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) restore(uris)
    }
    TextButton(enabled = !busy, onClick = { exportPicker.launch("Photograin-collection.zip") }) {
        Text(stringResource(R.string.collection_export))
    }
    TextButton(enabled = !busy, onClick = { archivePicker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
        Text(stringResource(R.string.collection_import))
    }
    if (busy) LinearProgressIndicator()
    if (error) Text(stringResource(R.string.collection_error))
    if (exported) Text(stringResource(R.string.collection_saved))

    if (archive != null && !busy && !error && report == null) {
        AlertDialog(
            onDismissRequest = { archive = null },
            title = { Text(stringResource(R.string.collection_import)) },
            text = { Text(stringResource(R.string.collection_relink_hint)) },
            confirmButton = {
                TextButton(onClick = { originalsPicker.launch(arrayOf("image/*", "application/octet-stream")) }) {
                    Text(stringResource(R.string.collection_pick_originals))
                }
            },
            dismissButton = {
                TextButton(onClick = { restore(emptyList()) }) {
                    Text(stringResource(R.string.collection_restore_linked))
                }
            },
        )
    }
    report?.let { result ->
        fun close() { report = null; archive = null }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text(stringResource(R.string.collection_import)) },
            text = {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.collection_result, result.restored, result.missing.size))
                    result.missing.take(20).forEach { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.close)) } },
            dismissButton = if (result.missing.isEmpty()) null else {
                { TextButton(onClick = { originalsPicker.launch(arrayOf("image/*", "application/octet-stream")) }) {
                    Text(stringResource(R.string.collection_pick_originals))
                } }
            },
        )
    }
}
