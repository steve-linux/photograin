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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
    var showWhiteBalance by rememberSaveable { mutableStateOf(false) }
    var showMatch by rememberSaveable { mutableStateOf(false) }
    var showQueueDetails by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingOptions by remember { mutableStateOf<ExportOptions?>(null) }
    val context = LocalContext.current
    val profiles = remember(context) { context.getSharedPreferences("export_profiles", android.content.Context.MODE_PRIVATE) }
    var showMulti by rememberSaveable { mutableStateOf(false) }
    var selectedProfiles by remember { mutableStateOf(emptySet<String>()) }
    var pendingMulti by remember { mutableStateOf(emptyList<ExportOptions>()) }
    var pendingLabels by remember { mutableStateOf(emptyList<String>()) }
    var showQueues by rememberSaveable { mutableStateOf(false) }
    val multiFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) viewModel.runProfiles(pendingMulti, tree, pendingLabels)
        pendingMulti = emptyList()
        pendingLabels = emptyList()
    }
    val queueFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.resumeQueue(tree)
    }
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
                        RecentThumbnail(item.uri, onClick = { if (state.queueView) showQueueDetails = true })
                        StatusBadge(item.status, Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (state.storageFailed) Text(stringResource(R.string.batch_queue_error), color = MaterialTheme.colorScheme.error)
                if (state.queueView) {
                    if (!state.running && state.queues.size > 1) androidx.compose.material3.TextButton(onClick = { showQueues = true }) {
                        Text(stringResource(R.string.batch_queue_history))
                    }
                    androidx.compose.material3.TextButton(onClick = { showQueueDetails = true }) {
                        Text(stringResource(R.string.batch_queue_details))
                    }
                    if (!state.running && state.canResume) {
                        Button(onClick = { viewModel.resumeQueue() }) {
                            Text(stringResource(R.string.batch_queue_resume))
                        }
                        androidx.compose.material3.TextButton(onClick = { queueFolderPicker.launch(null) }) {
                            Text(stringResource(R.string.batch_queue_new_folder))
                        }
                    }
                } else {
                    androidx.compose.material3.TextButton(onClick = { showMatch = true }, enabled = state.items.size >= 2 && !state.running) {
                        Text(stringResource(R.string.match_exposure))
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { showWhiteBalance = true }, enabled = state.items.isNotEmpty() && !state.running,
                    ) { Text(stringResource(R.string.batch_wb)) }
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
                }

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
                    if (!state.queueView) {
                        if (state.availableMasks > 0) {
                            FilterChip(selected = state.copyMasks, onClick = { viewModel.setCopyMasks(!state.copyMasks) },
                                label = { Text(stringResource(R.string.batch_copy_masks)) })
                        }
                        if (state.source != null && profiles.all.isNotEmpty()) {
                            androidx.compose.material3.TextButton(onClick = {
                                selectedProfiles = emptySet()
                                showMulti = true
                            }) { Text(stringResource(R.string.batch_multi_export)) }
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
    }

    if (showWhiteBalance && state.items.isNotEmpty()) WhiteBalanceBatchDialog(
        photos = state.items.map { it.uri },
        onApply = {
            showWhiteBalance = false
            viewModel.selectSource(BatchSource.MatchedWhiteBalance(it))
            viewModel.setCopyMasks(false)
            viewModel.run(null)
        },
        onDismiss = { showWhiteBalance = false },
    )
    if (showMatch) ExposureMatchDialog(
        photos = state.items.map { it.uri },
        onApply = {
            showMatch = false
            viewModel.selectSource(BatchSource.MatchedExposure(it))
            viewModel.setCopyMasks(false)
            viewModel.run(null)
        },
        onDismiss = { showMatch = false },
    )
    if (showQueues) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showQueues = false },
        title = { Text(stringResource(R.string.batch_queue_history)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                val dates = java.text.DateFormat.getDateTimeInstance()
                for (queue in state.queues) {
                    androidx.compose.material3.TextButton(onClick = {
                        showQueues = false
                        viewModel.openQueue(queue.id)
                    }) {
                        Text(stringResource(R.string.batch_queue_summary, dates.format(java.util.Date(queue.createdAt)),
                            queue.photos, queue.remaining))
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { showQueues = false }) {
            Text(stringResource(R.string.close))
        } },
    )
    if (showQueueDetails) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showQueueDetails = false },
        title = { Text(stringResource(R.string.batch_queue_details)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                for (item in state.items) {
                    val name = remember(item.uri) { io.github.steve_linux.photograin.data.ImageLoader.queryName(context, item.uri) ?: item.uri.lastPathSegment.orEmpty() }
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    if (item.profiles.isEmpty()) Text(stringResource(batchStatus(item.status)))
                    for ((profile, status) in item.profiles) {
                        Text(stringResource(R.string.batch_queue_row, profile, stringResource(batchStatus(status))))
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { showQueueDetails = false }) {
            Text(stringResource(R.string.close))
        } },
    )
    if (showMulti) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMulti = false },
            title = { Text(stringResource(R.string.batch_multi_export)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    for (name in profiles.all.keys.sorted()) {
                        FilterChip(selected = name in selectedProfiles, onClick = {
                            selectedProfiles = if (name in selectedProfiles) selectedProfiles - name else selectedProfiles + name
                        }, label = { Text(name) })
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = selectedProfiles.isNotEmpty(), onClick = {
                    val chosen = selectedProfiles.sorted().mapNotNull { name ->
                        profiles.getString(name, null)?.let {
                            runCatching { name to kotlinx.serialization.json.Json.decodeFromString(ExportOptions.serializer(), it) }.getOrNull()
                        }
                    }
                    showMulti = false
                    if (chosen.isNotEmpty()) {
                        val options = chosen.map { it.second }
                        val labels = chosen.map { it.first }
                        if (ExportDestination.isAutomatic(settings)) viewModel.runProfiles(options, labels = labels)
                        else {
                            pendingMulti = options
                            pendingLabels = labels
                            multiFolderPicker.launch(null)
                        }
                    }
                }) { Text(stringResource(R.string.batch_export)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMulti = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showExport) {
        ExportDialog(
            initial = settings.export,
            batch = true,
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


private fun batchStatus(status: ItemStatus): Int = when (status) {
    ItemStatus.PENDING -> R.string.batch_queue_pending
    ItemStatus.WORKING -> R.string.batch_queue_working
    ItemStatus.DONE -> R.string.batch_queue_done
    ItemStatus.FAILED -> R.string.batch_queue_failed
}
