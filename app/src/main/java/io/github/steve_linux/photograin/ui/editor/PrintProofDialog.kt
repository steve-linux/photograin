// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.PrintProof
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.ui.home.EditedThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PrintProofDialog(uri: Uri, edit: EditState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var profileUri by remember { mutableStateOf<Uri?>(null) }
    var gamut by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) profileUri = it
    }
    val picked = profileUri
    val profileResult by produceState<Result<PrintProof.Profile>?>(null, picked) {
        value = null
        if (picked != null) value = withContext(Dispatchers.IO) { runCatching { PrintProof.read(context, picked) } }
    }
    val baseResult by produceState<Result<Bitmap>?>(null, uri, edit) {
        value = null
        val bitmap = EditedThumbnails.render(context, uri, 1080, edit)
        value = bitmap?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Cannot render preview"))
    }
    val source = baseResult?.getOrNull()
    val profile = profileResult?.getOrNull()
    val proofResult by produceState<Result<Bitmap>?>(null, source, profile, gamut) {
        value = null
        if (source == null || profile == null) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { requireNotNull(PrintProof.preview(source, profile, gamut)) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.print_proof)) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.print_proof_hint))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.print_proof_import))
                }
                profile?.let { Text(it.name) }
                FilterChip(
                    selected = gamut, enabled = profile != null, onClick = { gamut = !gamut },
                    label = { Text(stringResource(R.string.color_proof_gamut)) },
                )
                when {
                    baseResult?.isFailure == true -> Text(stringResource(R.string.print_proof_preview_error))
                    profileResult?.isFailure == true || proofResult?.isFailure == true ->
                        Text(stringResource(R.string.print_proof_invalid))
                    baseResult == null || (picked != null && proofResult == null) -> LinearProgressIndicator()
                }
                val simulated = proofResult?.getOrNull()
                if (simulated != null) {
                    TextButton(onClick = { showOriginal = !showOriginal }) {
                        Text(stringResource(if (showOriginal) R.string.print_proof_show_result else R.string.print_proof_show_source))
                    }
                }
                val shown = if (showOriginal) source else simulated ?: source
                shown?.let {
                    Image(it.asImageBitmap(), stringResource(R.string.print_proof), Modifier.fillMaxWidth().heightIn(max = 290.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
