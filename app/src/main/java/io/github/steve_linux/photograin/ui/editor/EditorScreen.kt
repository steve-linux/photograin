// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Details
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.model.ToolGroup
import io.github.steve_linux.photograin.ui.theme.PhotoBackgroundDark
import io.github.steve_linux.photograin.ui.theme.PhotoBackgroundLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportOptions?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var lightCurves by rememberSaveable { mutableStateOf(false) }
    var colorHsl by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    val createDocument = rememberLauncherForActivityResult(CreateImageDocument()) { uri ->
        val options = pendingExport
        if (uri != null && options != null) viewModel.export(uri, options)
        pendingExport = null
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.messageShown()
        if (message == Message.LOAD_FAILED) {
            // The editor closes, so a snackbar would vanish with it.
            Toast.makeText(context, messageText(message), Toast.LENGTH_LONG).show()
            onClose()
        } else {
            snackbar.showSnackbar(context.getString(messageText(message)))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.image?.displayName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo))
                    }
                    IconToggleButton(checked = state.split != null, onCheckedChange = { viewModel.toggleSplit() }) {
                        Icon(Icons.Outlined.Compare, stringResource(R.string.compare))
                    }
                    IconButton(onClick = { showExport = true }, enabled = state.image != null) {
                        Icon(Icons.Outlined.Save, stringResource(R.string.export_title))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_adjustments)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menuOpen = false; viewModel.copyAdjustments() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.paste_adjustments)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                enabled = state.hasCopiedAdjustments,
                                onClick = { menuOpen = false; viewModel.pasteAdjustments() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset_all)) },
                                leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                                onClick = { menuOpen = false; viewModel.resetAll() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (tool in ToolGroup.entries) {
                    NavigationBarItem(
                        selected = state.tool == tool,
                        onClick = { viewModel.selectTool(tool) },
                        icon = { Icon(toolIcon(tool), contentDescription = null) },
                        // Six tabs share the width: keep each label on one line, even with large fonts.
                        label = {
                            Text(
                                stringResource(toolLabel(tool)),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.image == null) {
                    CircularProgressIndicator()
                } else {
                    val bg = if (isSystemInDarkTheme()) PhotoBackgroundDark else PhotoBackgroundLight
                    PhotoView(state, viewModel, bg)
                }
            }
            if (state.image != null) {
                if (state.tool == ToolGroup.LOOK) {
                    LookPanel(
                        adjustments = state.edit.adjustments,
                        lutLoaded = state.lut != null,
                        luts = state.luts,
                        userPresets = state.userPresets,
                        onApplyPreset = viewModel::applyPreset,
                        onSavePreset = viewModel::savePreset,
                        onDeletePreset = viewModel::deletePreset,
                        onImportLut = viewModel::importLut,
                        onSelectLut = viewModel::selectLut,
                        onDeleteLut = viewModel::deleteLut,
                        onAmount = viewModel::setLookAmount,
                        onAmountFinished = viewModel::commit,
                    )
                } else if (state.tool == ToolGroup.CROP) {
                    CropPanel(
                        geometry = state.edit.geometry,
                        onAspect = viewModel::setAspect,
                        onStraighten = viewModel::setStraighten,
                        onStraightenFinished = viewModel::commit,
                        onRotate = viewModel::rotate,
                        onFlipH = viewModel::flipHorizontal,
                        onFlipV = viewModel::flipVertical,
                        onReset = viewModel::resetGeometry,
                    )
                } else {
                    val advanced = state.tool == ToolGroup.LIGHT || state.tool == ToolGroup.COLOR
                    if (advanced) {
                        PanelModeSelector(
                            secondLabel = if (state.tool == ToolGroup.LIGHT) R.string.mode_curves else R.string.mode_hsl,
                            showSecond = if (state.tool == ToolGroup.LIGHT) lightCurves else colorHsl,
                            onSelect = { second ->
                                if (state.tool == ToolGroup.LIGHT) lightCurves = second else colorHsl = second
                            },
                        )
                    }
                    when {
                        state.tool == ToolGroup.LIGHT && lightCurves -> CurvesPanel(
                            curves = state.edit.adjustments.curves,
                            onChange = viewModel::setCurves,
                            onChangeFinished = viewModel::commit,
                        )
                        state.tool == ToolGroup.COLOR && colorHsl -> HslPanel(
                            hsl = state.edit.adjustments.hsl,
                            onChange = viewModel::setHsl,
                            onChangeFinished = viewModel::commit,
                        )
                        else -> AdjustmentPanel(
                            group = state.tool,
                            adjustments = state.edit.adjustments,
                            onChange = viewModel::setParam,
                            onChangeFinished = viewModel::commit,
                            onReset = viewModel::resetParam,
                        )
                    }
                }
            }
        }
    }

    if (showExport) {
        ExportDialog(
            sizeFor = viewModel::exportSize,
            onDismiss = { showExport = false },
            onExport = { options ->
                showExport = false
                pendingExport = options
                createDocument.launch(options.format.mimeType to exportName(state.image?.displayName, options))
            },
        )
    }
    state.exportProgress?.let { ExportProgressDialog(it) }
}

/** "Basic | Curves" or "Basic | HSL" switch above the Light and Color panels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelModeSelector(secondLabel: Int, showSecond: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegmentedButton(
            selected = !showSecond,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.mode_basic)) }
        SegmentedButton(
            selected = showSecond,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(secondLabel)) }
    }
}

private fun exportName(original: String?, options: ExportOptions): String {
    val base = original?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "photo"
    return "${base}_photograin.${options.format.extension}"
}

private fun toolIcon(tool: ToolGroup): ImageVector = when (tool) {
    ToolGroup.LOOK -> Icons.Outlined.Style
    ToolGroup.LIGHT -> Icons.Outlined.WbSunny
    ToolGroup.COLOR -> Icons.Outlined.Palette
    ToolGroup.DETAIL -> Icons.Outlined.Details
    ToolGroup.EFFECTS -> Icons.Outlined.AutoAwesome
    ToolGroup.CROP -> Icons.Outlined.Crop
}

private fun messageText(m: Message): Int = when (m) {
    Message.EXPORTED -> R.string.msg_exported
    Message.EXPORT_FAILED -> R.string.msg_export_failed
    Message.LOAD_FAILED -> R.string.msg_load_failed
    Message.ADJUSTMENTS_COPIED -> R.string.msg_copied
    Message.ADJUSTMENTS_PASTED -> R.string.msg_pasted
    Message.OUT_OF_MEMORY -> R.string.msg_out_of_memory
    Message.LUT_IMPORTED -> R.string.msg_lut_imported
    Message.LUT_INVALID -> R.string.msg_lut_invalid
    Message.LUT_MISSING -> R.string.look_lut_missing
    Message.PRESET_SAVED -> R.string.msg_preset_saved
}

/** ACTION_CREATE_DOCUMENT with the MIME type chosen at launch time: input is (mimeType, fileName). */
private class CreateImageDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>) =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}
