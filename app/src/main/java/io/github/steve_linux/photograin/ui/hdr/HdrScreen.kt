// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.hdr

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.HdrMerger
import io.github.steve_linux.photograin.model.HdrPlan
import io.github.steve_linux.photograin.model.HdrProblem
import io.github.steve_linux.photograin.ui.home.RecentThumbnail

/**
 * Merges the picked brackets: shows them, the two options and the progress, then hands the
 * saved merge to [onMerged] so it opens in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HdrScreen(viewModel: HdrViewModel, onClose: () -> Unit, onMerged: (Uri) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        viewModel.saveTo(uri)
    }
    LaunchedEffect(state.askDestination) {
        state.askDestination?.let { saveAs.launch(it) }
    }
    LaunchedEffect(state.saved) {
        state.saved?.let(onMerged)
    }
    BackHandler(onBack = { if (state.running) viewModel.cancel() else onClose() })
    // A merge takes a while on a phone: the screen should not go dark meanwhile.
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hdr_title, state.uris.size)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !state.running) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(state.uris, key = { it.toString() }) { uri -> RecentThumbnail(uri, onClick = {}) }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.hdr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OptionRow(
                    title = stringResource(R.string.hdr_align),
                    body = stringResource(R.string.hdr_align_body),
                    checked = state.align,
                    enabled = !state.running,
                    onChange = viewModel::setAlign,
                )
                OptionRow(
                    title = stringResource(R.string.hdr_deghost),
                    body = stringResource(R.string.hdr_deghost_body),
                    checked = state.deghost,
                    enabled = !state.running,
                    onChange = viewModel::setDeghost,
                )

                val message: String? = when {
                    state.problem == HdrProblem.TOO_FEW -> stringResource(R.string.hdr_too_few, HdrPlan.MIN_FRAMES)
                    state.problem == HdrProblem.TOO_MANY -> stringResource(R.string.hdr_too_many, HdrPlan.MAX_FRAMES)
                    state.problem == HdrProblem.DIFFERENT_SHAPES -> stringResource(R.string.hdr_different_shapes)
                    state.error == HdrError.UNREADABLE -> stringResource(R.string.hdr_unreadable)
                    state.error == HdrError.OUT_OF_MEMORY -> stringResource(R.string.hdr_out_of_memory)
                    state.error == HdrError.FAILED -> stringResource(R.string.hdr_failed)
                    state.error == HdrError.SAVE_FAILED -> stringResource(R.string.msg_export_failed)
                    else -> null
                }
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else if (state.megapixels > 0f && !state.running) {
                    Text(
                        stringResource(
                            if (state.reduced) R.string.hdr_size_reduced else R.string.hdr_size,
                            state.megapixels,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.running) {
                    val stage = when (state.stage) {
                        HdrMerger.Stage.DECODE -> R.string.hdr_stage_decode
                        HdrMerger.Stage.ALIGN -> R.string.hdr_stage_align
                        else -> R.string.hdr_stage_fuse
                    }
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(stage), modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.look_cancel)) }
                    }
                } else {
                    Button(
                        onClick = viewModel::merge,
                        enabled = state.canMerge,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text(stringResource(R.string.hdr_merge)) }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, body: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
