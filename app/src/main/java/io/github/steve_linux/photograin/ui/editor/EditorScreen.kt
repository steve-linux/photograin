// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.ToolGroup
import io.github.steve_linux.photograin.ui.theme.PhotoBackgroundDark
import io.github.steve_linux.photograin.ui.theme.PhotoBackgroundLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsStore.get(context) }.state.collectAsStateWithLifecycle()
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportOptions?>(null) }
    // The dialog reopens with the last choices for this photo session, else the settings' defaults.
    var lastExport by remember { mutableStateOf<ExportOptions?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var lightCurves by rememberSaveable { mutableStateOf(false) }
    var colorHsl by rememberSaveable { mutableStateOf(false) }

    // The button on the photo, or a tap on it where a tap has no other use, hides the bars and
    // panels so the photo fills the screen; the same again, or back, brings them back.
    var fullPhoto by rememberSaveable { mutableStateOf(false) }
    val photoOnly = state.tool in PHOTO_ONLY_TOOLS && state.picker == Picker.NONE

    BackHandler(onBack = onClose)
    BackHandler(enabled = fullPhoto) { fullPhoto = false }

    val createDocument = rememberLauncherForActivityResult(CreateImageDocument()) { uri ->
        val options = pendingExport
        if (uri != null && options != null) viewModel.export(uri, options)
        pendingExport = null
    }

    // The settings name no place to save, or it could not be used: ask with the system dialog.
    LaunchedEffect(state.askDestination) {
        val request = state.askDestination ?: return@LaunchedEffect
        viewModel.destinationAsked()
        pendingExport = request.options
        createDocument.launch(request.options.format.mimeType to request.fileName)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.messageShown()
        if (message == Message.LOAD_FAILED) {
            // The editor closes, so a snackbar would vanish with it.
            Toast.makeText(context, messageText(message), Toast.LENGTH_LONG).show()
            onClose()
        } else {
            // Outside this effect: clearing the message restarts it, which would cancel the snackbar.
            val text = context.getString(messageText(message))
            scope.launch { snackbar.showSnackbar(text) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!fullPhoto) TopAppBar(
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
                    IconButton(onClick = { fullPhoto = true }, enabled = state.image != null) {
                        Icon(Icons.Outlined.Fullscreen, stringResource(R.string.photo_full))
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photo_info)) },
                                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showInfo = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!fullPhoto) NavigationBar {
                for (tool in ToolGroup.entries) {
                    NavigationBarItem(
                        selected = state.tool == tool,
                        onClick = { viewModel.selectTool(tool) },
                        icon = { Icon(toolIcon(tool), contentDescription = null) },
                        // Seven tabs share the width: keep each label on one line, shrinking a long one to fit.
                        label = {
                            val style = MaterialTheme.typography.labelSmall
                            BasicText(
                                stringResource(toolLabel(tool)),
                                style = style.copy(color = LocalContentColor.current),
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
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
                    val bg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) PhotoBackgroundDark else PhotoBackgroundLight
                    PhotoView(state, viewModel, bg, onTap = if (photoOnly) ({ fullPhoto = !fullPhoto }) else null)
                    // With the bars hidden, this is the way back where a tap on the photo is taken.
                    if (fullPhoto) {
                        FilledTonalIconButton(
                            onClick = { fullPhoto = false },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        ) {
                            Icon(Icons.Outlined.FullscreenExit, stringResource(R.string.photo_exit_full))
                        }
                    }
                }
            }
            if (state.image != null && !fullPhoto) {
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
                } else if (state.tool == ToolGroup.EFFECTS) {
                    EffectsModeSelector(state.effectsMode, viewModel::setEffectsMode)
                    when (state.effectsMode) {
                        1 -> FramePanel(state.edit.frame, viewModel::setFrame, viewModel::commit)
                        3 -> ShapesPanel(
                            shapes = state.edit.shapes,
                            selected = state.activeShape,
                            onAdd = viewModel::addShape,
                            onSelect = viewModel::selectShape,
                            onChange = viewModel::setShape,
                            onDelete = viewModel::deleteShape,
                            onChangeFinished = viewModel::commit,
                        )
                        2 -> TextPanel(
                            texts = state.edit.texts,
                            selected = state.activeText,
                            onAdd = viewModel::addText,
                            onSelect = viewModel::selectText,
                            onChange = viewModel::setText,
                            onDelete = viewModel::deleteText,
                            onChangeFinished = viewModel::commit,
                        )
                        else -> AdjustmentPanel(
                            group = ToolGroup.EFFECTS,
                            adjustments = state.edit.adjustments,
                            onChange = viewModel::setParam,
                            onChangeFinished = viewModel::commit,
                            onReset = viewModel::resetParam,
                        )
                    }
                } else if (state.tool == ToolGroup.LOCAL) {
                    PanelModeSelector(
                        secondLabel = R.string.mode_heal,
                        showSecond = state.retouchMode,
                        onSelect = viewModel::setRetouchMode,
                        firstLabel = R.string.mode_masks,
                    )
                    if (state.retouchMode) {
                        RetouchPanel(
                            spots = state.edit.retouch,
                            selected = state.activeSpot,
                            type = state.spotType,
                            size = state.spotSize,
                            onType = viewModel::setSpotType,
                            onSize = viewModel::setSpotSize,
                            onSpot = viewModel::setSpot,
                            onDelete = viewModel::deleteSpot,
                            onChangeFinished = viewModel::commit,
                        )
                    } else {
                        LocalPanel(
                            masks = state.edit.masks,
                            selected = state.activeMask,
                            showMask = state.showMask,
                            onAdd = viewModel::addMask,
                            onSelect = viewModel::selectMask,
                            onDelete = viewModel::deleteMask,
                            onParam = viewModel::setMaskParam,
                            onResetParam = viewModel::resetMaskParam,
                            onShape = viewModel::setMaskShape,
                            onInvert = viewModel::setMaskInvert,
                            onShowMask = viewModel::setShowMask,
                            onChangeFinished = viewModel::commit,
                            brush = state.brush,
                            onBrush = viewModel::setBrush,
                            picking = state.picker == Picker.MASK_COLOR,
                            onPicking = { viewModel.setPicker(if (it) Picker.MASK_COLOR else Picker.NONE) },
                        )
                    }
                } else if (state.tool == ToolGroup.CROP) {
                    CropPanel(
                        geometry = state.edit.geometry,
                        onAspect = viewModel::setAspect,
                        onStraighten = viewModel::setStraighten,
                        onStraightenFinished = viewModel::commit,
                        onPerspective = viewModel::setPerspective,
                        onRotate = viewModel::rotate,
                        onFlipH = viewModel::flipHorizontal,
                        onFlipV = viewModel::flipVertical,
                        onReset = viewModel::resetGeometry,
                        quadDraft = state.quadDraft,
                        onAutoStraighten = viewModel::autoStraighten,
                        onStartQuad = viewModel::startQuad,
                        onApplyQuad = viewModel::applyQuad,
                        onCancelQuad = viewModel::cancelQuad,
                        onRemoveQuad = viewModel::removeQuad,
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
                        else -> {
                            if (state.tool == ToolGroup.COLOR) {
                                WhiteBalancePicker(
                                    picking = state.picker == Picker.WHITE_BALANCE,
                                    onPicking = { viewModel.setPicker(if (it) Picker.WHITE_BALANCE else Picker.NONE) },
                                )
                            }
                            AdjustmentPanel(
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
    }

    if (showInfo) {
        state.image?.let { PhotoInfoDialog(it, onDismiss = { showInfo = false }) }
    }
    if (showExport) {
        ExportDialog(
            initial = lastExport ?: settings.export,
            sizeFor = viewModel::exportSize,
            onDismiss = { showExport = false },
            onExport = { options ->
                showExport = false
                lastExport = options
                viewModel.export(options)
            },
        )
    }
    state.exportProgress?.let { ExportProgressDialog(it) }
}

/** "Basic | Curves" or "Basic | HSL" switch above the Light and Color panels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelModeSelector(
    secondLabel: Int,
    showSecond: Boolean,
    onSelect: (Boolean) -> Unit,
    firstLabel: Int = R.string.mode_basic,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegmentedButton(
            selected = !showSecond,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(firstLabel)) }
        SegmentedButton(
            selected = showSecond,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(secondLabel)) }
    }
}

private fun toolIcon(tool: ToolGroup): ImageVector = when (tool) {
    ToolGroup.LOOK -> Icons.Outlined.Style
    ToolGroup.LIGHT -> Icons.Outlined.WbSunny
    ToolGroup.COLOR -> Icons.Outlined.Palette
    ToolGroup.DETAIL -> Icons.Outlined.Details
    ToolGroup.EFFECTS -> Icons.Outlined.AutoAwesome
    ToolGroup.LOCAL -> Icons.Outlined.Gradient
    ToolGroup.CROP -> Icons.Outlined.Crop
}

private fun messageText(m: Message): Int = when (m) {
    Message.EXPORTED -> R.string.msg_exported
    Message.EXPORTED_TO_PICTURES -> R.string.msg_exported_pictures
    Message.EXPORTED_TO_DOWNLOADS -> R.string.msg_exported_downloads
    Message.EXPORT_FAILED -> R.string.msg_export_failed
    Message.LOAD_FAILED -> R.string.msg_load_failed
    Message.ADJUSTMENTS_COPIED -> R.string.msg_copied
    Message.ADJUSTMENTS_PASTED -> R.string.msg_pasted
    Message.OUT_OF_MEMORY -> R.string.msg_out_of_memory
    Message.LUT_IMPORTED -> R.string.msg_lut_imported
    Message.LUT_INVALID -> R.string.msg_lut_invalid
    Message.LUT_MISSING -> R.string.look_lut_missing
    Message.PRESET_SAVED -> R.string.msg_preset_saved
    Message.HORIZON_NOT_FOUND -> R.string.msg_horizon_not_found
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

/**
 * Tools where a tap on the photo has no other use, so it can hide the controls. In Effects, text
 * and shapes claim the touches that land on them, so a tap elsewhere still hides the controls.
 */
private val PHOTO_ONLY_TOOLS = setOf(ToolGroup.LOOK, ToolGroup.LIGHT, ToolGroup.COLOR, ToolGroup.DETAIL, ToolGroup.EFFECTS)
