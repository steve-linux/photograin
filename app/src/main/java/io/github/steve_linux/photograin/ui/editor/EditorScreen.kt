// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.OPEN_TYPES
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.PhotoRatingStore
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating
import io.github.steve_linux.photograin.ui.home.PhotoReviewControls
import io.github.steve_linux.photograin.ui.home.ratingBadge
import io.github.steve_linux.photograin.model.HslControl
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.ToneHistogram
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.ObjectRemover
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.ToolGroup
import io.github.steve_linux.photograin.ui.guide.guideTopicFor
import io.github.steve_linux.photograin.ui.theme.PhotoBackgroundDark
import io.github.steve_linux.photograin.ui.theme.PhotoBackgroundLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit, onGuide: (topic: Int) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsStore.get(context) }.state.collectAsStateWithLifecycle()
    val ratingStore = remember(context) { PhotoRatingStore.get(context) }
    val ratings by ratingStore.state.collectAsStateWithLifecycle()
    val currentUri = state.image?.uri
    val currentRating = currentUri?.let { ratings[it.toString()] } ?: PhotoRating()

    fun rate(stars: Int) {
        currentUri?.let { ratingStore.rate(it, stars) }
    }

    fun toggleStars(stars: Int) {
        currentUri?.let { ratingStore.toggleStars(it, stars) }
    }

    fun setFlag(flag: PhotoFlag) {
        currentUri?.let { ratingStore.flag(it, flag) }
    }

    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportOptions?>(null) }
    // The dialog reopens with the last choices for this photo session, else the settings' defaults.
    var lastExport by remember(state.image?.uri) { mutableStateOf<ExportOptions?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var referenceUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) referenceUri = uri
    }
    var showOcr by rememberSaveable { mutableStateOf(false) }
    var showAdjustmentFinder by rememberSaveable { mutableStateOf(false) }
    var showSelectivePaste by rememberSaveable { mutableStateOf(false) }
    var showApplyPrevious by rememberSaveable(state.image?.uri) { mutableStateOf(false) }
    val previousAdjustments = state.previousAdjustments
    var showRawDevelopment by rememberSaveable { mutableStateOf(false) }
    var showSampledTools by rememberSaveable { mutableStateOf(false) }
    var showGrading by rememberSaveable { mutableStateOf(false) }
    var showGradient by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showVersions by rememberSaveable { mutableStateOf(false) }
    var lightCurves by rememberSaveable { mutableStateOf(false) }
    var showLensProfile by rememberSaveable { mutableStateOf(false) }
    var proofEnabled by rememberSaveable { mutableStateOf(false) }
    var gamutWarning by rememberSaveable { mutableStateOf(false) }
    var showPrintProof by rememberSaveable(state.image?.uri) { mutableStateOf(false) }
    var showChartCalibration by rememberSaveable(state.image?.uri) { mutableStateOf(false) }
    var showProofOptions by rememberSaveable { mutableStateOf(false) }
    var proofProfile by rememberSaveable { mutableStateOf<io.github.steve_linux.photograin.model.ColorProfile?>(null) }
    var lightScope by rememberSaveable { mutableStateOf<LightScope?>(null) }
    var histogram by remember(state.image) { mutableStateOf<ToneHistogram?>(null) }
    var focusPeaking by rememberSaveable { mutableStateOf(false) }
    var exposureWarning by rememberSaveable { mutableStateOf(false) }
    var colorHsl by rememberSaveable { mutableStateOf(false) }
    var hslControl by rememberSaveable { mutableStateOf(HslControl.SATURATION) }

    // The full screen button in the top bar, there in every tool, hides the bars and panels so the
    // photo fills the screen; the button on the photo, or back, brings them back. A tap on the photo
    // does not do it: people tapped to edit and lost the controls.
    var fullPhoto by rememberSaveable { mutableStateOf(false) }
    var panelExpanded by rememberSaveable { mutableStateOf(true) }
    var quickActionsOpen by rememberSaveable { mutableStateOf(false) }
    var showFilmstrip by rememberSaveable { mutableStateOf(false) }
    val previousPhoto = state.adjacentPhoto(-1)
    val nextPhoto = state.adjacentPhoto(1)
    val hasQuickActions = remember { QuickAction.availableEntries().isNotEmpty() }

    fun selectEditorTool(tool: ToolGroup) {
        panelExpanded = true
        viewModel.selectTool(tool)
    }

    val keyboardFocus = remember { FocusRequester() }
    LaunchedEffect(keyboardFocus) { keyboardFocus.requestFocus() }

    fun toggleHistogram() {
        lightScope = if (lightScope == LightScope.HISTOGRAM) null else LightScope.HISTOGRAM
    }

    fun toggleFullPhoto() {
        if (!fullPhoto) viewModel.setPicker(Picker.NONE)
        fullPhoto = !fullPhoto
    }

    BackHandler(onBack = onClose)
    BackHandler(enabled = referenceUri != null) { referenceUri = null }
    BackHandler(enabled = fullPhoto) { fullPhoto = false }

    var showGpx by rememberSaveable { mutableStateOf(false) }
    var gpxOffset by rememberSaveable { mutableStateOf("+00:00") }
    val gpxPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importPhotoLocation(uri, gpxOffset) }
    val depthMapPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importDepthMask(uri) }
    val lensCatalogPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showLensProfile = false
            viewModel.importLensCatalog(uri)
        }
    }
    var showPresetTransfer by rememberSaveable { mutableStateOf(false) }
    var presetToExport by remember { mutableStateOf<io.github.steve_linux.photograin.model.Preset?>(null) }
    val presetImport = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.transferPreset(uri) }
    val presetExport = rememberLauncherForActivityResult(CreateImageDocument()) { uri ->
        val preset = presetToExport
        if (uri != null && preset != null) viewModel.transferPreset(uri, preset)
        presetToExport = null
    }
    var showProjectTransfer by rememberSaveable { mutableStateOf(false) }
    var projectOriginal by rememberSaveable { mutableStateOf(false) }
    var showRecipeTransfer by rememberSaveable { mutableStateOf(false) }
    val recipeImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.transferProject(uri, restore = true, singleRecipe = true)
    }
    val recipeExport = rememberLauncherForActivityResult(CreateImageDocument()) { uri ->
        if (uri != null) viewModel.transferProject(uri, restore = false, singleRecipe = true)
    }
    val projectImport = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.transferProject(uri, restore = true) }
    val projectExport = rememberLauncherForActivityResult(CreateImageDocument()) { uri ->
        if (uri != null) viewModel.transferProject(uri, restore = false, includeOriginal = projectOriginal)
    }
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

    // The export dialog set to enlarge with AI, which is what "improve quality" means to most people.
    fun enhance() {
        lastExport = (lastExport ?: settings.export).copy(upscale = 2, maxLongEdge = null, aiUpscale = true)
        showExport = true
    }

    fun quickAction(action: QuickAction) {
        fullPhoto = false
        panelExpanded = true
        quickActionsOpen = false
        if (action == QuickAction.ENHANCE) enhance() else viewModel.quickAction(action)
    }

    LaunchedEffect(state.pendingAction, state.image != null) {
        val action = state.pendingAction ?: return@LaunchedEffect
        if (state.image == null) return@LaunchedEffect
        viewModel.pendingActionStarted()
        quickAction(action)
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
        modifier = Modifier.onKeyEvent { event ->
            if (state.image == null || event.type != KeyEventType.KeyDown ||
                event.isAltPressed || event.isMetaPressed
            ) return@onKeyEvent false

            val action: (() -> Unit) = when {
                event.isCtrlPressed && event.key == Key.Z -> {
                    if (event.isShiftPressed) viewModel::redo else viewModel::undo
                }
                event.isCtrlPressed && event.isShiftPressed && event.key == Key.Y && previousAdjustments != null ->
                    ({ showApplyPrevious = true })
                event.isShiftPressed -> return@onKeyEvent false
                event.isCtrlPressed -> when (event.key) {
                    Key.C -> viewModel::copyAdjustments
                    Key.V -> viewModel::pasteAdjustments
                    Key.Y -> if (previousAdjustments != null) viewModel::applyPreviousAdjustments else return@onKeyEvent false
                    Key.S -> ({ showExport = true })
                    Key.Zero -> ({
                        fullPhoto = false
                        selectEditorTool(ToolGroup.LIGHT)
                        toggleHistogram()
                    })
                    else -> return@onKeyEvent false
                }
                else -> when (event.key) {
                    Key.Backslash -> viewModel::toggleSplit
                    Key.F -> ::toggleFullPhoto
                    Key.I -> ({ showInfo = true })
                    Key.Zero -> ({ rate(0) })
                    Key.One -> ({ rate(1) })
                    Key.Two -> ({ rate(2) })
                    Key.Three -> ({ rate(3) })
                    Key.Four -> ({ rate(4) })
                    Key.Five -> ({ rate(5) })
                    Key.P -> ({ setFlag(PhotoFlag.PICKED) })
                    Key.X -> ({ setFlag(PhotoFlag.REJECTED) })
                    Key.U -> ({ setFlag(PhotoFlag.NONE) })
                    Key.DirectionLeft -> previousPhoto?.takeIf { state.canBrowseFilmstrip }?.let { photo ->
                        { viewModel.browseTo(photo) }
                    } ?: return@onKeyEvent false
                    Key.DirectionRight -> nextPhoto?.takeIf { state.canBrowseFilmstrip }?.let { photo ->
                        { viewModel.browseTo(photo) }
                    } ?: return@onKeyEvent false
                    else -> return@onKeyEvent false
                }
            }
            if (event.nativeKeyEvent.repeatCount == 0) action()
            true
        }.focusRequester(keyboardFocus).focusable(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!fullPhoto) TopAppBar(
                title = {
                    Column {
                        Text(state.image?.displayName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val version = state.versions.takeIf { it.size > 1 }?.firstOrNull { it.id == state.version }
                        val subtitle = listOfNotNull(
                            ratingBadge(currentRating).takeIf { it.isNotEmpty() },
                            version?.let { versionLabel(it) },
                        ).joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
                    if (hasQuickActions) {
                        IconToggleButton(
                            checked = quickActionsOpen,
                            onCheckedChange = { quickActionsOpen = it },
                            enabled = state.image != null,
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, stringResource(R.string.quick_title))
                        }
                    }
                    FilledTonalIconButton(onClick = { showExport = true }, enabled = state.image != null) {
                        Icon(Icons.Outlined.Save, stringResource(R.string.export_title))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gpx_title)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showGpx = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sampled_tools)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showSampledTools = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.focus_peaking)) },
                                enabled = state.image != null,
                                trailingIcon = { if (focusPeaking) Icon(Icons.Outlined.Check, null) },
                                onClick = { focusPeaking = !focusPeaking; menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_history)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showHistory = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.project_transfer)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showProjectTransfer = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipe_transfer)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showRecipeTransfer = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.preset_transfer)) },
                                onClick = { menuOpen = false; showPresetTransfer = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.color_proof)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showProofOptions = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_reference_choose)) },
                                leadingIcon = { Icon(Icons.Outlined.Compare, null) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; referencePicker.launch(OPEN_TYPES) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.print_proof)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showPrintProof = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chart_calibration)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showChartCalibration = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.adjustment_finder)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showAdjustmentFinder = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.versions_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Layers, null) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showVersions = true },
                            )
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
                                text = { Text(stringResource(R.string.paste_selective)) },
                                enabled = state.hasCopiedAdjustments && state.image != null,
                                onClick = { menuOpen = false; showSelectivePaste = true },
                            )
                            if (previousAdjustments != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.apply_previous)) },
                                    onClick = { menuOpen = false; showApplyPrevious = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset_all)) },
                                leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                                onClick = { menuOpen = false; viewModel.resetAll() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tool_help)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
                                onClick = {
                                    menuOpen = false
                                    onGuide(guideTopicFor(state.tool, state.retouchMode))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.exposure_warning)) },
                                leadingIcon = { Icon(Icons.Outlined.WarningAmber, null) },
                                trailingIcon = {
                                    if (exposureWarning) Icon(Icons.Outlined.Check, stringResource(R.string.exposure_warning_on))
                                },
                                enabled = state.image != null,
                                onClick = {
                                    exposureWarning = !exposureWarning
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blur_background)) },
                                enabled = state.image != null && state.edit.masks.size < LocalAdjustment.MAX,
                                onClick = { menuOpen = false; viewModel.blurBackground() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.privacy_brush)) },
                                enabled = state.image != null && state.edit.masks.size < LocalAdjustment.MAX,
                                onClick = { menuOpen = false; viewModel.privacyBrush() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ocr_title)) },
                                enabled = state.image != null,
                                onClick = { menuOpen = false; showOcr = true },
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
            if (!fullPhoto && state.image != null) {
                EditorToolDock(
                    selected = state.tool,
                    expanded = panelExpanded,
                    onSelect = ::selectEditorTool,
                    onTogglePanel = {
                        panelExpanded = !panelExpanded
                        if (!panelExpanded) viewModel.setPicker(Picker.NONE)
                    },
                    onGuide = { onGuide(guideTopicFor(it, state.retouchMode)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.image != null && !fullPhoto && quickActionsOpen) {
                QuickActionsBar(onAction = ::quickAction)
            }
            EditorReferenceLayout(
                reference = referenceUri,
                onClose = { referenceUri = null },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (state.image == null) {
                    CircularProgressIndicator()
                } else {
                    val bg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) PhotoBackgroundDark else PhotoBackgroundLight
                    val showExposureWarning = exposureWarning && !proofEnabled && state.tool != ToolGroup.CROP
                    val scopeVisible = lightScope != null && state.tool == ToolGroup.LIGHT && !fullPhoto
                    PhotoView(
                        state, viewModel, bg, showExposureWarning = showExposureWarning,
                        focusPeaking = focusPeaking,
                        proof = if (proofEnabled) proofProfile ?: settings.export.colorProfile else null,
                        gamutWarning = gamutWarning,
                        onHistogram = if (scopeVisible) ({ histogram = it }) else null,
                        hslControl = hslControl,
                    )
                    if (scopeVisible) {
                        histogram?.let {
                            val overlay = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            when (lightScope) {
                                LightScope.HISTOGRAM -> HistogramOverlay(it, overlay)
                                LightScope.WAVEFORM -> WaveformOverlay(it, overlay)
                                LightScope.PARADE -> WaveformOverlay(it, overlay, parade = true)
                                LightScope.VECTORSCOPE -> VectorscopeOverlay(it, overlay)
                                null -> Unit
                            }
                        }
                    }
                    if (state.picker in listOf(Picker.FILM_BASE, Picker.COLOR_REFERENCE, Picker.COLOR_KEY)) {
                        Surface(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = { viewModel.setPicker(Picker.NONE) }) {
                                Text(stringResource(R.string.sampled_tap_hint))
                            }
                        }
                    }
                    if (showExposureWarning) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                text = stringResource(R.string.exposure_warning_legend),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row {
                            if (!fullPhoto && state.filmstrip.size > 1) {
                                IconToggleButton(checked = showFilmstrip, onCheckedChange = { showFilmstrip = it }) {
                                    Icon(Icons.Outlined.PhotoLibrary, stringResource(R.string.filmstrip_title))
                                }
                            }
                            if (!fullPhoto && state.tool != ToolGroup.CROP) {
                                IconToggleButton(
                                    checked = state.split != null,
                                    onCheckedChange = { viewModel.toggleSplit() },
                                ) { Icon(Icons.Outlined.Compare, stringResource(R.string.compare)) }
                            }
                            IconButton(onClick = ::toggleFullPhoto) {
                                Icon(
                                    if (fullPhoto) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                    stringResource(if (fullPhoto) R.string.photo_exit_full else R.string.photo_full),
                                )
                            }
                        }
                    }
                }
            }
            if (showFilmstrip && !fullPhoto && state.filmstrip.size > 1) {
                FilmstripBar(
                    state.filmstrip, currentUri, state.canBrowseFilmstrip, previousPhoto, nextPhoto, ratings, viewModel::browseTo,
                )
                if (currentUri != null) {
                    PhotoReviewControls(currentRating, ::toggleStars, ::setFlag, Modifier.fillMaxWidth())
                }
            }
            if (state.image != null && !fullPhoto && panelExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (state.tool == ToolGroup.LOOK) {
                    LookPanel(
                        adjustments = state.edit.adjustments,
                        lutLoaded = state.lut != null,
                        luts = state.luts,
                        userPresets = state.userPresets,
                        activePreset = state.activePreset,
                        onApplyPreset = viewModel::applyPreset,
                        onPresetAmount = viewModel::setPresetAmount,
                        onPresetAmountFinished = viewModel::finishPresetAmount,
                        onImportPreset = { presetImport.launch(arrayOf("*/*")) },
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
                    if (state.effectsMode == 0) androidx.compose.material3.TextButton(onClick = { showGradient = true }) {
                        Text(stringResource(R.string.gradient_map))
                    }
                    if (state.effectsMode == 2 || state.effectsMode == 3) {
                        androidx.compose.material3.TextButton(onClick = viewModel::centerSelectedDecoration) {
                            Text(stringResource(R.string.layer_center))
                        }
                    }
                    when (state.effectsMode) {
                        4 -> ImageLayersPanel(state.edit.images, viewModel)
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
                            canRemove = ObjectRemover.available,
                            removing = state.removing,
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
                            onAdd = { type ->
                                if (type == MaskType.DEPTH) {
                                    depthMapPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                                } else {
                                    viewModel.addMask(type)
                                }
                            },
                            onSky = viewModel::selectSky,
                            onSelect = viewModel::selectMask,
                            onDelete = viewModel::deleteMask,
                            onDuplicate = viewModel::duplicateMask,
                            onEnabled = viewModel::setMaskEnabled,
                            onSelectionInverted = viewModel::setMaskSelectionInverted,
                            onParam = viewModel::setMaskParam,
                            onResetParam = viewModel::resetMaskParam,
                            onShape = viewModel::setMaskShape,
                            onInvert = viewModel::setMaskInvert,
                            onRename = viewModel::renameMask,
                            onOverlayMode = viewModel::setMaskOverlayMode,
                            onOverlayColor = viewModel::setMaskOverlayColor,
                            onOverlayOpacity = viewModel::setMaskOverlayOpacity,
                            onComponents = viewModel::setMaskComponents,
                            onShowMask = viewModel::setShowMask,
                            onChangeFinished = viewModel::commit,
                            brush = state.brush,
                            onBrush = viewModel::setBrush,
                            pickingDepth = state.picker == Picker.DEPTH,
                            onPickingDepth = { viewModel.setPicker(if (it) Picker.DEPTH else Picker.NONE) },
                            picking = state.picker == Picker.MASK_COLOR,
                            onPicking = { viewModel.setPicker(if (it) Picker.MASK_COLOR else Picker.NONE) },
                            findingSubject = state.findingSubject,
                            selectingObject = state.selectingObject,
                            onClearObject = viewModel::clearObject,
                            onRemoveBackground = viewModel::removeBackground,
                            onBackground = viewModel::setMaskBackground,
                        )
                    }
                } else if (state.tool == ToolGroup.CROP) {
                    CropPanel(
                        geometry = state.edit.geometry,
                        cropGuide = state.cropGuide,
                        cropGuideOrientation = state.cropGuideOrientation,
                        onCropGuide = viewModel::setCropGuide,
                        onCycleCropGuideOrientation = viewModel::cycleCropGuideOrientation,
                        onAspect = viewModel::setAspect,
                        onStraighten = viewModel::setStraighten,
                        onStraightenFinished = viewModel::commit,
                        onPerspective = viewModel::setPerspective,
                        onRotate = viewModel::rotate,
                        onFlipH = viewModel::flipHorizontal,
                        onFlipV = viewModel::flipVertical,
                        onReset = viewModel::resetGeometry,
                        quadDraft = state.quadDraft,
                        verticalGuides = state.verticalGuides,
                        onStartVerticalGuides = viewModel::startVerticalGuides,
                        onSuggestVerticalGuides = viewModel::suggestVerticalGuides,
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
                                if (state.tool == ToolGroup.LIGHT) {
                                    lightCurves = second
                                } else if (colorHsl != second) {
                                    viewModel.setPicker(Picker.NONE)
                                    colorHsl = second
                                }
                            },
                        )
                    }
                    if (state.tool == ToolGroup.COLOR) {
                        androidx.compose.material3.TextButton(onClick = { showGrading = true }) {
                            Text(stringResource(R.string.color_grading))
                        }
                    }
                    if (state.tool == ToolGroup.LIGHT) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            androidx.compose.material3.TextButton(onClick = viewModel::autoLight) {
                                Text(stringResource(R.string.auto_light))
                            }
                            for (scopeMode in LightScope.entries) {
                                FilterChip(
                                    selected = lightScope == scopeMode,
                                    onClick = { lightScope = if (lightScope == scopeMode) null else scopeMode },
                                    label = { Text(stringResource(scopeMode.label)) },
                                )
                            }
                        }
                    }
                    if (state.tool == ToolGroup.DETAIL && state.image?.rawHighlight != null) {
                        androidx.compose.material3.TextButton(onClick = { showRawDevelopment = true }) {
                            Text(stringResource(R.string.raw_development))
                        }
                    }
                    if (state.tool == ToolGroup.DETAIL) {
                        androidx.compose.material3.TextButton(onClick = { showLensProfile = true }) {
                            Text(stringResource(R.string.lens_profile))
                        }
                    }
                    when {
                        state.tool == ToolGroup.LIGHT && lightCurves -> CurvesPanel(
                            curves = state.edit.adjustments.curves,
                            onChange = viewModel::setCurves,
                            onChangeFinished = viewModel::commit,
                        )
                        state.tool == ToolGroup.COLOR && colorHsl -> HslPanel(
                            hsl = state.edit.adjustments.hsl,
                            band = state.hslBand,
                            onBand = viewModel::selectHslBand,
                            targeting = state.picker == Picker.HSL,
                            onTargeting = { viewModel.setPicker(if (it) Picker.HSL else Picker.NONE) },
                            control = hslControl,
                            onControl = { hslControl = it },
                            onChange = viewModel::setHsl,
                            onChangeFinished = viewModel::commit,
                        )
                        else -> {
                            if (state.tool == ToolGroup.COLOR) {
                                WhiteBalancePicker(
                                    picking = state.picker == Picker.WHITE_BALANCE,
                                    onAuto = viewModel::autoWhiteBalance,
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

    if (showLensProfile) {
        val amount = state.lensProfileAmount
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLensProfile = false },
            title = { Text(stringResource(R.string.lens_profile)) },
            text = {
                Column {
                    Text(stringResource(when {
                        state.lensProfileKey == null -> R.string.lens_profile_missing_exif
                        amount == null -> R.string.lens_profile_no_match
                        else -> R.string.lens_profile_matched
                    }))
                    androidx.compose.material3.TextButton(onClick = { lensCatalogPicker.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.lens_catalog_import))
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = state.lensProfileKey != null,
                    onClick = { viewModel.saveLensProfile(); showLensProfile = false },
                ) { Text(stringResource(R.string.lens_profile_save)) }
            },
            dismissButton = {
                if (amount != null) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.applyLensProfile(); showLensProfile = false }) {
                        Text(stringResource(R.string.lens_profile_apply))
                    }
                    if (state.hasLocalLensProfile) androidx.compose.material3.TextButton(onClick = viewModel::deleteLensProfile) {
                        Text(stringResource(R.string.lens_profile_delete))
                    }
                } else {
                    androidx.compose.material3.TextButton(onClick = { showLensProfile = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            },
        )
    }
    if (showOcr) state.image?.let { OcrDialog(it, onDismiss = { showOcr = false }) }
    if (showRecipeTransfer) {
        val baseName = state.image?.displayName?.substringBeforeLast('.')?.take(120)
            ?.takeIf { it.isNotBlank() } ?: "photograin"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRecipeTransfer = false },
            title = { Text(stringResource(R.string.recipe_transfer)) },
            text = {
                Column {
                    Text(stringResource(R.string.recipe_transfer_hint))
                    androidx.compose.material3.TextButton(onClick = {
                        showRecipeTransfer = false
                        recipeImport.launch(arrayOf("*/*"))
                    }) { Text(stringResource(R.string.recipe_import)) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRecipeTransfer = false
                    recipeExport.launch("application/zip" to "$baseName.photograin.zip")
                }) { Text(stringResource(R.string.recipe_export)) }
            },
        )
    }
    if (showProjectTransfer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProjectTransfer = false },
            title = { Text(stringResource(R.string.project_transfer)) },
            text = {
                Column {
                    Text(stringResource(R.string.project_restore_hint))
                    androidx.compose.material3.FilterChip(
                        selected = projectOriginal,
                        onClick = { projectOriginal = !projectOriginal },
                        label = { Text(stringResource(R.string.project_include_original)) },
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        showProjectTransfer = false
                        projectImport.launch(arrayOf("*/*"))
                    }) { Text(stringResource(R.string.project_import)) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showProjectTransfer = false
                    projectExport.launch("application/zip" to "photograin-project.zip")
                }) { Text(stringResource(R.string.project_export)) }
            },
        )
    }
    state.image?.uri?.let { uri ->
        if (showPrintProof) PrintProofDialog(uri, state.edit) { showPrintProof = false }
        if (showChartCalibration) ChartCalibrationDialog(
            uri,
            onSave = { name, matrix ->
                showChartCalibration = false
                viewModel.saveChartCalibration(name, matrix)
            },
            onDismiss = { showChartCalibration = false },
        )
    }
    if (showProofOptions) {
        val selected = proofProfile ?: settings.export.colorProfile
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProofOptions = false },
            title = { Text(stringResource(R.string.color_proof)) },
            text = {
                Column {
                    androidx.compose.material3.FilterChip(
                        selected = proofEnabled, onClick = { proofEnabled = !proofEnabled },
                        label = { Text(stringResource(R.string.color_proof_preview)) },
                    )
                    for ((profile, label) in listOf(
                        io.github.steve_linux.photograin.model.ColorProfile.SRGB to "sRGB",
                        io.github.steve_linux.photograin.model.ColorProfile.DISPLAY_P3 to "Display P3",
                        io.github.steve_linux.photograin.model.ColorProfile.ADOBE_RGB to "Adobe RGB",
                    )) {
                        androidx.compose.material3.FilterChip(
                            selected = selected == profile,
                            onClick = { proofProfile = profile; proofEnabled = true },
                            label = { Text(label) },
                        )
                    }
                    androidx.compose.material3.FilterChip(
                        selected = gamutWarning, enabled = proofEnabled,
                        onClick = { gamutWarning = !gamutWarning },
                        label = { Text(stringResource(R.string.color_proof_gamut)) },
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showProofOptions = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showPresetTransfer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPresetTransfer = false },
            title = { Text(stringResource(R.string.preset_transfer)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    androidx.compose.material3.TextButton(onClick = {
                        showPresetTransfer = false
                        presetImport.launch(arrayOf("*/*"))
                    }) { Text(stringResource(R.string.preset_import)) }
                    Text(
                        stringResource(R.string.preset_xmp_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    for (preset in state.userPresets) {
                        androidx.compose.material3.TextButton(onClick = {
                            presetToExport = preset
                            showPresetTransfer = false
                            presetExport.launch("application/json" to "photograin-preset.json")
                        }) { Text(stringResource(R.string.preset_export_named, preset.name)) }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPresetTransfer = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showSelectivePaste) {
        SelectivePasteDialog(
            onApply = viewModel::pasteSelectedAdjustments,
            onDismiss = { showSelectivePaste = false },
        )
    }
    if (showApplyPrevious && previousAdjustments != null) {
        SelectivePasteDialog(
            onApply = viewModel::applySelectedPreviousAdjustments,
            onDismiss = { showApplyPrevious = false },
            title = R.string.apply_previous,
            confirmLabel = R.string.apply_previous_selected,
            sourceName = previousAdjustments.displayName,
            onApplyAll = viewModel::applyPreviousAdjustments,
        )
    }
    if (showAdjustmentFinder) AdjustmentFinder(state.edit.adjustments, viewModel) { showAdjustmentFinder = false }
    if (showRawDevelopment) {
        var whiteBalance by remember(state.image?.uri, state.version) { mutableStateOf(state.edit.rawWhiteBalance) }
        val wbModes = io.github.steve_linux.photograin.model.RawWhiteBalanceMode.entries
        val wbLabels = listOf(R.string.raw_wb_camera, R.string.raw_wb_auto, R.string.raw_wb_manual)
        val modes = io.github.steve_linux.photograin.model.RawHighlight.entries
        val labels = listOf(R.string.raw_clip, R.string.raw_unclip, R.string.raw_blend, R.string.raw_reconstruct)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRawDevelopment = false },
            title = { Text(stringResource(R.string.raw_development)) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.wb_pick_label))
                    wbModes.forEachIndexed { index, mode ->
                        androidx.compose.material3.FilterChip(
                            selected = whiteBalance.mode == mode,
                            onClick = { whiteBalance = whiteBalance.copy(mode = mode) },
                            label = { Text(stringResource(wbLabels[index])) },
                        )
                    }
                    if (whiteBalance.mode == io.github.steve_linux.photograin.model.RawWhiteBalanceMode.MANUAL) {
                        for (red in listOf(true, false)) {
                            val value = if (red) whiteBalance.red else whiteBalance.blue
                            ParamSlider(
                                label = stringResource(if (red) R.string.raw_wb_red else R.string.raw_wb_blue),
                                value = value, range = 0.1f..8f, display = "%.2f".format(value),
                                onChange = { whiteBalance = if (red) whiteBalance.copy(red = it) else whiteBalance.copy(blue = it) },
                                onChangeFinished = {},
                                onReset = { whiteBalance = if (red) whiteBalance.copy(red = 1f) else whiteBalance.copy(blue = 1f) },
                            )
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.setRawWhiteBalance(whiteBalance)
                        showRawDevelopment = false
                    }) { Text(stringResource(R.string.raw_wb_apply)) }
                    androidx.compose.material3.FilterChip(
                        selected = whiteBalance.hotPixels,
                        onClick = { whiteBalance = whiteBalance.copy(hotPixels = !whiteBalance.hotPixels) },
                        label = { Text(stringResource(R.string.raw_hot_pixels)) },
                    )
                    Text(stringResource(R.string.raw_highlights))
                    modes.forEachIndexed { index, mode ->
                        androidx.compose.material3.FilterChip(
                            selected = state.edit.rawHighlight == mode,
                            onClick = { viewModel.setRawHighlight(mode); showRawDevelopment = false },
                            label = { Text(stringResource(labels[index])) },
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showRawDevelopment = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showGpx) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showGpx = false },
            title = { Text(stringResource(R.string.gpx_title)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = gpxOffset, onValueChange = { gpxOffset = it.take(6) },
                    label = { Text(stringResource(R.string.gpx_offset)) }, singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showGpx = false; gpxPicker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.gpx_import))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showGpx = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
    if (showSampledTools) SampledToolsDialog(state.edit.adjustments, viewModel) { showSampledTools = false }
    if (showGrading) ColorGradingDialog(state.edit.adjustments.grading, viewModel) { showGrading = false }
    if (showGradient) GradientMapDialog(state.edit.adjustments.gradientMap, viewModel::setGradientMap) { showGradient = false }
    if (showHistory) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(stringResource(R.string.edit_history)) },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    for (index in state.historySteps.indices.reversed()) {
                        androidx.compose.material3.TextButton(enabled = index != state.historyIndex, onClick = {
                            viewModel.goToHistory(index)
                            showHistory = false
                        }) { Text(stringResource(R.string.edit_history_step, index)) }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.newVersion(copy = true)
                    showHistory = false
                    showVersions = true
                }) { Text(stringResource(R.string.edit_history_checkpoint)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showHistory = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showInfo) {
        state.image?.let {
            PhotoInfoDialog(it, currentRating, ::toggleStars, ::setFlag, onDismiss = { showInfo = false })
        }
    }
    if (showVersions) {
        state.image?.let { image ->
            VersionsDialog(
                uri = image.uri,
                versions = state.versions,
                current = state.version,
                edit = state.edit,
                onOpen = { showVersions = false; viewModel.switchVersion(it) },
                onNew = { showVersions = false; viewModel.newVersion(it) },
                onRename = viewModel::renameVersion,
                onDelete = viewModel::deleteVersion,
                onDismiss = { showVersions = false },
            )
        }
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

@Composable
private fun EditorToolDock(
    selected: ToolGroup,
    expanded: Boolean,
    onSelect: (ToolGroup) -> Unit,
    onTogglePanel: () -> Unit,
    onGuide: (ToolGroup) -> Unit,
) {
    val tools = listOf(
        ToolGroup.LOOK, ToolGroup.LIGHT, ToolGroup.COLOR, ToolGroup.DETAIL,
        ToolGroup.EFFECTS, ToolGroup.LOCAL, ToolGroup.CROP,
    )
    val scroll = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = tools.indexOf(selected)
        if (index >= 0 && scroll.layoutInfo.visibleItemsInfo.none {
                it.index == index && it.offset >= scroll.layoutInfo.viewportStartOffset &&
                    it.offset + it.size <= scroll.layoutInfo.viewportEndOffset
            }
        ) scroll.animateScrollToItem(index)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    modifier = Modifier.weight(1f).selectableGroup(),
                    state = scroll,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tools, key = { it.name }) { tool ->
                        FilterChip(
                            selected = expanded && selected == tool,
                            onClick = { onSelect(tool) },
                            modifier = Modifier.onLongPress { onGuide(tool) },
                            leadingIcon = { Icon(toolIcon(tool), null, Modifier.size(18.dp)) },
                            label = { Text(stringResource(toolLabel(tool)), maxLines = 1) },
                        )
                    }
                }
                IconButton(onClick = onTogglePanel) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        stringResource(if (expanded) R.string.editor_hide_controls else R.string.editor_show_controls),
                    )
                }
            }
        }
    }
}

private enum class LightScope(val label: Int) {
    HISTOGRAM(R.string.histogram_label),
    WAVEFORM(R.string.waveform_label),
    PARADE(R.string.parade_label),
    VECTORSCOPE(R.string.vectorscope_label),
}

private fun toolIcon(tool: ToolGroup): ImageVector = when (tool) {
    ToolGroup.LOOK -> Icons.Outlined.Style
    ToolGroup.LIGHT -> Icons.Outlined.Tonality
    ToolGroup.COLOR -> Icons.Outlined.Palette
    ToolGroup.DETAIL -> Icons.Outlined.Texture
    ToolGroup.EFFECTS -> Icons.Outlined.Grain
    ToolGroup.LOCAL -> Icons.Outlined.Gradient
    ToolGroup.CROP -> Icons.Outlined.Crop
}

private fun messageText(m: Message): Int = when (m) {
    Message.EXPORTED -> R.string.msg_exported
    Message.EXPORTED_TO_PICTURES -> R.string.msg_exported_pictures
    Message.EXPORTED_TO_DOWNLOADS -> R.string.msg_exported_downloads
    Message.EXPORT_FAILED -> R.string.msg_export_failed
    Message.EXPORT_SIZE_LIMIT -> R.string.msg_export_size_limit
    Message.LOAD_FAILED -> R.string.msg_load_failed
    Message.ADJUSTMENTS_COPIED -> R.string.msg_copied
    Message.ADJUSTMENTS_PASTED -> R.string.msg_pasted
    Message.PREVIOUS_ADJUSTMENTS_APPLIED -> R.string.msg_previous_applied
    Message.OUT_OF_MEMORY -> R.string.msg_out_of_memory
    Message.LUT_IMPORTED -> R.string.msg_lut_imported
    Message.LUT_INVALID -> R.string.msg_lut_invalid
    Message.LUT_MISSING -> R.string.look_lut_missing
    Message.PRESET_SAVED -> R.string.msg_preset_saved
    Message.DEPTH_MAP_FAILED -> R.string.depth_map_failed
    Message.FILMSTRIP_LOAD_FAILED -> R.string.filmstrip_load_failed
    Message.HSL_COLOR_NOT_FOUND -> R.string.msg_hsl_color_not_found
    Message.WHITE_BALANCE_NOT_FOUND -> R.string.wb_auto_not_found
    Message.HORIZON_NOT_FOUND -> R.string.msg_horizon_not_found
    Message.SUBJECT_FAILED -> R.string.msg_subject_failed
    Message.SUBJECT_FALLBACK -> R.string.msg_subject_fallback
    Message.LENS_CATALOG_IMPORTED -> R.string.lens_catalog_imported
    Message.LENS_CATALOG_FAILED -> R.string.lens_catalog_failed
    Message.PRESET_TRANSFER_DONE -> R.string.preset_transfer_done
    Message.PRESET_TRANSFER_FAILED -> R.string.preset_transfer_failed
    Message.VERTICAL_GUIDES_NOT_FOUND -> R.string.vertical_auto_failed
    Message.GPX_MATCHED -> R.string.gpx_matched
    Message.GPX_FAILED -> R.string.gpx_failed
    Message.RAW_PREVIEW_FAILED -> R.string.raw_preview_failed
    Message.PROJECT_TRANSFER_DONE -> R.string.project_transfer_done
    Message.PROJECT_TRANSFER_FAILED -> R.string.project_transfer_failed
    Message.RECIPE_TRANSFER_DONE -> R.string.recipe_transfer_done
    Message.RECIPE_TRANSFER_FAILED -> R.string.recipe_transfer_failed
    Message.RECIPE_WRONG_ORIGINAL -> R.string.recipe_wrong_original
    Message.IMAGE_LAYER_FAILED -> R.string.image_layer_failed
    Message.REMOVE_FAILED -> R.string.msg_remove_failed
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
 * Calls [action] when the element is held down, without consuming the touch, so the element's own
 * tap handling still runs.
 */
@Composable
private fun Modifier.onLongPress(action: () -> Unit): Modifier {
    val current by rememberUpdatedState(action)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
                false
            } ?: true
            if (held) current()
        }
    }
}
