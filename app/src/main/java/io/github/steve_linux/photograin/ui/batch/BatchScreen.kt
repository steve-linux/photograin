// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ExportDestination
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.ui.editor.ExportDialog
import io.github.steve_linux.photograin.ui.editor.presetLabel
import io.github.steve_linux.photograin.ui.home.RecentThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(viewModel: BatchViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by SettingsStore.get(LocalContext.current).state.collectAsStateWithLifecycle()
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingOptions by remember { mutableStateOf<ExportOptions?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        val options = pendingOptions
        if (tree != null && options != null) viewModel.run(options, tree)
        pendingOptions = null
    }

    BackHandler(onBack = { if (state.running) viewModel.stop() else onClose() })
    // A long run should not stop because the screen went dark.
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_title, state.items.size)) },
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
                items(state.items, key = { it.uri.toString() }) { item ->
                    Box {
                        RecentThumbnail(item.uri, onClick = {})
                        StatusBadge(item.status, Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.batch_apply_what),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.copied?.let { copied ->
                        val source = BatchSource.Copied(copied)
                        FilterChip(
                            selected = state.source == source,
                            enabled = !state.running,
                            onClick = { viewModel.selectSource(source) },
                            label = { Text(stringResource(R.string.batch_copied)) },
                        )
                    }
                    for (preset in Preset.BUILT_IN + state.userPresets) {
                        val source = BatchSource.FromPreset(preset)
                        FilterChip(
                            selected = state.source == source,
                            enabled = !state.running,
                            onClick = { viewModel.selectSource(source) },
                            label = { Text(preset.builtIn?.let { stringResource(presetLabel(it)) } ?: preset.name) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.batch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                if (state.running) {
                    LinearProgressIndicator(
                        progress = { state.processed.toFloat() / state.items.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.batch_progress, state.processed, state.items.size),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = viewModel::stop) { Text(stringResource(R.string.batch_stop)) }
                    }
                } else {
                    state.result?.let { (ok, failed) ->
                        Text(
                            stringResource(R.string.batch_done, ok, failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.run(null) },
                            enabled = state.source != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.batch_save_only)) }
                        Button(
                            onClick = { showExport = true },
                            enabled = state.source != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.batch_export)) }
                    }
                }
            }
        }
    }

    if (showExport) {
        ExportDialog(
            initial = settings.export,
            sizeFor = { null },
            onDismiss = { showExport = false },
            onExport = { options ->
                showExport = false
                if (ExportDestination.isAutomatic(settings)) {
                    viewModel.run(options)
                } else {
                    pendingOptions = options
                    folderPicker.launch(null)
                }
            },
        )
    }
}

@Composable
private fun StatusBadge(status: ItemStatus, modifier: Modifier) {
    val bg = Modifier.size(24.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
    when (status) {
        ItemStatus.PENDING -> Unit
        ItemStatus.WORKING -> Box(modifier.then(bg).padding(3.dp)) { CircularProgressIndicator(strokeWidth = 2.dp) }
        ItemStatus.DONE -> Icon(
            Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = modifier.then(bg),
        )
        ItemStatus.FAILED -> Icon(
            Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = modifier.then(bg),
        )
    }
}
