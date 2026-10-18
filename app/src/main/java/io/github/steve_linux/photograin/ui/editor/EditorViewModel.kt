// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.steve_linux.photograin.data.ExportDestination
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.ExportWriter
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.LoadedImage
import io.github.steve_linux.photograin.data.LookLibrary
import io.github.steve_linux.photograin.data.LutEntry
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.data.SaveLocation
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.History
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParamSpec
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.ParamSpec
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.ToolGroup
import io.github.steve_linux.photograin.model.flippedHorizontally
import io.github.steve_linux.photograin.model.flippedVertically
import io.github.steve_linux.photograin.model.rotatedClockwise
import io.github.steve_linux.photograin.render.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Message {
    EXPORTED, EXPORTED_TO_PICTURES, EXPORTED_TO_DOWNLOADS, EXPORT_FAILED, LOAD_FAILED, ADJUSTMENTS_COPIED, ADJUSTMENTS_PASTED, OUT_OF_MEMORY,
    LUT_IMPORTED, LUT_INVALID, LUT_MISSING, PRESET_SAVED,
}

data class EditorUiState(
    val image: LoadedImage? = null,
    val loading: Boolean = false,
    /** The live edit, including a slider that is still being dragged. */
    val edit: EditState = EditState(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Styles first: the quickest way to a good result, and where presets are saved. */
    val tool: ToolGroup = ToolGroup.LOOK,
    /** Press-and-hold on the photo shows the original. */
    val showOriginal: Boolean = false,
    /** Position of the before/after divider (0..1), or null when split view is off. */
    val split: Float? = null,
    val exportProgress: Float? = null,
    /** Set when the settings name no place to save: the screen asks the user for one. */
    val askDestination: SaveRequest? = null,
    val message: Message? = null,
    val hasCopiedAdjustments: Boolean = false,
    /** The parsed table for the edit's look, or null when there is none or it is missing. */
    val lut: CubeLut? = null,
    val luts: List<LutEntry> = emptyList(),
    val userPresets: List<Preset> = emptyList(),
    /** The local adjustment being edited, by index in [EditState.masks]. */
    val selectedMask: Int? = null,
    /** Tint the selected mask red, to see where it applies. */
    val showMask: Boolean = false,
    /** A mask handle is being dragged: the mask is shown while it moves. */
    val maskDragging: Boolean = false,
) {
    /** [selectedMask] if it still exists (undo can remove it). */
    val activeMask: Int? get() = selectedMask?.takeIf { it in edit.masks.indices }
}

/** An export waiting for the user to pick where it goes. */
data class SaveRequest(val options: ExportOptions, val fileName: String)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val recipes = RecipeStore(app)
    private val prefs = Preferences(app)
    private val library = LookLibrary(app)
    private val settings = SettingsStore.get(app)
    private var history = History(EditState())
    private var loadJob: Job? = null

    /** Recipe writes run one at a time, in order. */
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(EditorUiState(hasCopiedAdjustments = prefs.copiedAdjustments != null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val luts = withContext(Dispatchers.IO) { library.luts() }
            val presets = withContext(Dispatchers.IO) { library.userPresets() }
            _state.update { it.copy(luts = luts, userPresets = presets) }
        }
        // Load the LUT whenever the edit points at a different one (open, undo, paste, preset).
        viewModelScope.launch {
            _state.map { it.edit.adjustments.look.lutId }.distinctUntilChanged().collectLatest { id ->
                val lut = id?.let { withContext(Dispatchers.IO) { library.loadLut(it) } }
                _state.update { it.copy(lut = lut) }
                if (id != null && lut == null) _state.update { it.copy(message = Message.LUT_MISSING) }
            }
        }
    }

    fun open(uri: Uri) {
        if (_state.value.image?.uri == uri) return
        loadJob?.cancel()
        _state.update {
            EditorUiState(loading = true, hasCopiedAdjustments = it.hasCopiedAdjustments, luts = it.luts, userPresets = it.userPresets)
        }
        loadJob = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) { ImageLoader.load(getApplication(), uri) }
                val saved = withContext(Dispatchers.IO) { recipes.load(uri) } ?: EditState()
                prefs.addRecent(uri)
                history = History(saved)
                _state.update { it.copy(image = image, loading = false, edit = saved).withHistory() }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open $uri", e)
                prefs.removeRecent(uri)
                _state.update { it.copy(loading = false, message = Message.LOAD_FAILED) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(loading = false, message = Message.OUT_OF_MEMORY) }
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        _state.update {
            EditorUiState(hasCopiedAdjustments = it.hasCopiedAdjustments, luts = it.luts, userPresets = it.userPresets)
        }
    }

    fun selectTool(tool: ToolGroup) = _state.update { it.copy(tool = tool) }

    // --- Adjustments -------------------------------------------------------------------------

    /** Live update while a slider moves; [commit] records the step when the finger lifts. */
    fun setParam(spec: ParamSpec, value: Float) = updateLive { e ->
        e.copy(adjustments = spec.set(e.adjustments, value.coerceIn(spec.min, spec.max)))
    }

    /** Live update of the tone curves; [commit] when the drag ends. */
    fun setCurves(curves: Curves) = updateLive { it.copy(adjustments = it.adjustments.copy(curves = curves)) }

    /** Live update of the HSL bands; [commit] when the slider is released. */
    fun setHsl(hsl: Hsl) = updateLive { it.copy(adjustments = it.adjustments.copy(hsl = hsl)) }

    fun resetParam(spec: ParamSpec) {
        setParam(spec, spec.default)
        commit()
    }

    fun commit() {
        val edit = _state.value.edit
        history.commit(edit)
        _state.update { it.withHistory() }
        persist(edit)
    }

    fun undo() = applyHistory(history.undo())

    fun redo() = applyHistory(history.redo())

    fun resetAll() {
        updateLive { EditState() }
        commit()
    }

    fun copyAdjustments() {
        prefs.copiedAdjustments = _state.value.edit.adjustments
        _state.update { it.copy(hasCopiedAdjustments = true, message = Message.ADJUSTMENTS_COPIED) }
    }

    fun pasteAdjustments() {
        val copied: Adjustments = prefs.copiedAdjustments ?: return
        updateLive { it.copy(adjustments = copied) }
        commit()
        _state.update { it.copy(message = Message.ADJUSTMENTS_PASTED) }
    }

    // --- Looks: presets and LUTs ---------------------------------------------------------------

    fun applyPreset(preset: Preset) {
        updateLive { it.copy(adjustments = preset.applyTo(it.adjustments)) }
        commit()
    }

    /** Saves the current adjustments as a preset named [name]. */
    fun savePreset(name: String) {
        val adjustments = _state.value.edit.adjustments
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.savePreset(Preset(id = "", name = name.trim(), adjustments = adjustments)) }
            val presets = withContext(Dispatchers.IO) { library.userPresets() }
            _state.update { it.copy(userPresets = presets, message = Message.PRESET_SAVED) }
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            val presets = withContext(Dispatchers.IO) {
                library.deletePreset(preset.id)
                library.userPresets()
            }
            _state.update { it.copy(userPresets = presets) }
        }
    }

    /** Imports the .cube file at [uri] and applies it to the photo. */
    fun importLut(uri: Uri) {
        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { library.importLut(uri) }
                val luts = withContext(Dispatchers.IO) { library.luts() }
                _state.update { it.copy(luts = luts, message = Message.LUT_IMPORTED) }
                selectLut(entry)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot import LUT $uri", e)
                _state.update { it.copy(message = Message.LUT_INVALID) }
            }
        }
    }

    /** Applies [entry] at full strength, or removes the LUT when null. */
    fun selectLut(entry: LutEntry?) {
        updateLive { e ->
            val look = if (entry == null) Look() else Look(lutId = entry.id, name = entry.name, amount = 1f)
            e.copy(adjustments = e.adjustments.copy(look = look))
        }
        commit()
    }

    /** Live update of the LUT strength; [commit] when the slider is released. */
    fun setLookAmount(amount: Float) = updateLive { e ->
        e.copy(adjustments = e.adjustments.copy(look = e.adjustments.look.copy(amount = amount.coerceIn(0f, 1f))))
    }

    fun deleteLut(entry: LutEntry) {
        if (_state.value.edit.adjustments.look.lutId == entry.id) selectLut(null)
        viewModelScope.launch {
            val luts = withContext(Dispatchers.IO) {
                library.deleteLut(entry.id)
                library.luts()
            }
            _state.update { it.copy(luts = luts) }
        }
    }

    // --- Local adjustments -------------------------------------------------------------------

    /** Adds a mask of [type] over the visible part of the photo and selects it. */
    fun addMask(type: MaskType) {
        val image = _state.value.image ?: return
        val m = mapper() ?: return
        if (_state.value.edit.masks.size >= LocalAdjustment.MAX) return
        val local = LocalAdjustment.create(type, m.normalizedOutputToSource(), image.preview.width, image.preview.height)
        updateLive { it.copy(masks = it.masks + local) }
        _state.update { it.copy(selectedMask = it.edit.masks.lastIndex) }
        commit()
    }

    fun selectMask(index: Int?) = _state.update { it.copy(selectedMask = index) }

    fun deleteMask(index: Int) {
        if (index !in _state.value.edit.masks.indices) return
        updateLive { e -> e.copy(masks = e.masks.filterIndexed { i, _ -> i != index }) }
        _state.update { it.copy(selectedMask = null) }
        commit()
    }

    /** Live update of a local slider; [commit] when it is released. */
    fun setMaskParam(spec: LocalParamSpec, value: Float) = updateMask { spec.set(it, value.coerceIn(spec.min, spec.max)) }

    fun resetMaskParam(spec: LocalParamSpec) {
        setMaskParam(spec, spec.get(LocalAdjustment()))
        commit()
    }

    /** Live update of the selected mask's shape, from its handles or the feather slider. */
    fun setMaskShape(mask: Mask) = updateMask { it.copy(mask = mask) }

    fun setMaskInvert(invert: Boolean) {
        updateMask { it.copy(mask = it.mask.copy(invert = invert)) }
        commit()
    }

    fun setShowMask(show: Boolean) = _state.update { it.copy(showMask = show) }

    fun setMaskDragging(dragging: Boolean) {
        _state.update { it.copy(maskDragging = dragging) }
        if (!dragging) commit()
    }

    private fun updateMask(transform: (LocalAdjustment) -> LocalAdjustment) {
        val index = _state.value.activeMask ?: return
        updateLive { e -> e.copy(masks = e.masks.mapIndexed { i, l -> if (i == index) transform(l) else l }) }
    }

    // --- Before / after ----------------------------------------------------------------------

    fun setShowOriginal(show: Boolean) = _state.update { it.copy(showOriginal = show) }

    fun toggleSplit() = _state.update { it.copy(split = if (it.split == null) 0.5f else null) }

    fun setSplit(x: Float) = _state.update { it.copy(split = x.coerceIn(0f, 1f)) }

    // --- Geometry ----------------------------------------------------------------------------

    /** Mapper for the preview bitmap and the given geometry; null until an image is loaded. */
    fun mapper(geometry: Geometry = _state.value.edit.geometry): GeometryMapper? {
        val image = _state.value.image ?: return null
        return GeometryMapper(image.preview.width, image.preview.height, image.base, geometry)
    }

    /** Frame size (rotated and straightened, before crop) of the preview. */
    fun frameSize(): PxSize? = mapper()?.let { PxSize(it.frameWidth.toInt(), it.frameHeight.toInt()) }

    fun rotate() = commitGeometry { it.rotatedClockwise() }

    fun flipHorizontal() = commitGeometry { it.flippedHorizontally() }

    fun flipVertical() = commitGeometry { it.flippedVertically() }

    /** Live straighten; the crop shrinks as needed so no empty corners appear. */
    fun setStraighten(degrees: Float) = updateLive { e ->
        val g = e.geometry.copy(straighten = degrees.coerceIn(-45f, 45f))
        val m = mapper(g) ?: return@updateLive e
        e.copy(geometry = g.copy(crop = m.fitCrop(e.geometry.crop)))
    }

    /** Live crop change from the overlay; rejected if it would include empty corners. */
    fun setCrop(crop: CropRect) = updateLive { e ->
        val clamped = GeometryMapper.clampCrop(crop)
        val m = mapper() ?: return@updateLive e
        if (m.isCropValid(clamped)) e.copy(geometry = e.geometry.copy(crop = clamped)) else e
    }

    fun setAspect(aspect: AspectRatio) = commitGeometry { g ->
        val m = mapper(g) ?: return@commitGeometry g
        val ratio = aspectRatioValue(aspect, g)
        val crop = if (ratio == null) g.crop else m.centeredCrop(ratio)
        g.copy(aspect = aspect, crop = crop)
    }

    /** Width / height of an aspect preset in output pixels, or null for free-form. */
    fun aspectRatioValue(aspect: AspectRatio, geometry: Geometry = _state.value.edit.geometry): Float? {
        if (aspect == AspectRatio.FREE) return null
        if (aspect == AspectRatio.ORIGINAL) {
            val m = mapper(geometry.copy(straighten = 0f)) ?: return null
            return (m.frameWidth / m.frameHeight).toFloat()
        }
        val r = aspect.ratio ?: return null
        val m = mapper(geometry) ?: return r
        // Follow the frame's orientation: a portrait frame gets 3:4 instead of 4:3.
        return if (m.frameHeight > m.frameWidth) 1f / r else r
    }

    fun resetGeometry() = commitGeometry { Geometry() }

    private fun commitGeometry(transform: (Geometry) -> Geometry) {
        updateLive { it.copy(geometry = transform(it.geometry)) }
        commit()
    }

    // --- Export ------------------------------------------------------------------------------

    fun exportSize(options: ExportOptions): PxSize? {
        val image = _state.value.image ?: return null
        return Exporter.outputSize(
            PxSize(image.sourceWidth, image.sourceHeight), image.base, _state.value.edit, options.maxLongEdge, options.upscale,
        )
    }

    /** Saves where the settings say, or asks for a place through [EditorUiState.askDestination]. */
    fun export(options: ExportOptions) {
        val image = _state.value.image ?: return
        val current = settings.value
        val name = ExportDestination.fileName(image.displayName, current.naming, options.format)
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { ExportDestination.create(getApplication(), current, name, options.format) }
            if (uri == null) {
                _state.update { it.copy(askDestination = SaveRequest(options, name)) }
            } else {
                val done = when {
                    ExportDestination.isInDownloads(uri) -> Message.EXPORTED_TO_DOWNLOADS
                    current.saveLocation == SaveLocation.PICTURES -> Message.EXPORTED_TO_PICTURES
                    else -> Message.EXPORTED
                }
                export(uri, options, created = true, done = done)
            }
        }
    }

    fun destinationAsked() = _state.update { it.copy(askDestination = null) }

    /**
     * Renders and writes to [destination]. A file this app [created] is finished or removed
     * here; one the user picked is left to its provider.
     */
    fun export(destination: Uri, options: ExportOptions, created: Boolean = false, done: Message = Message.EXPORTED) {
        val image = _state.value.image ?: return
        val edit = _state.value.edit
        val lut = _state.value.lut
        _state.update { it.copy(exportProgress = 0f) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                val app = getApplication<Application>()
                try {
                    val (source, render) = Exporter.prepare(app, RegionSource.open(app, image.uri), image.base, edit, options)
                    val bitmap = source.use {
                        Exporter.render(it, image.base, edit, lut, render.maxLongEdge, render.colorProfile, render.upscale) { p ->
                            _state.update { s -> s.copy(exportProgress = p) }
                        }
                    }
                    ExportWriter.write(app, bitmap, image.uri, destination, options)
                    bitmap.recycle()
                    if (created) ExportDestination.finish(app, destination)
                    done
                } catch (e: OutOfMemoryError) {
                    if (created) ExportDestination.discard(app, destination)
                    Message.OUT_OF_MEMORY
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    if (created) ExportDestination.discard(app, destination)
                    Message.EXPORT_FAILED
                }
            }
            _state.update { it.copy(exportProgress = null, message = ok) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // --- Internals ---------------------------------------------------------------------------

    private inline fun updateLive(crossinline transform: (EditState) -> EditState) =
        _state.update { it.copy(edit = transform(it.edit)) }

    private fun applyHistory(edit: EditState) {
        _state.update { it.copy(edit = edit).withHistory() }
        persist(edit)
    }

    private fun EditorUiState.withHistory() = copy(canUndo = history.canUndo, canRedo = history.canRedo)

    private fun persist(edit: EditState) {
        val uri = _state.value.image?.uri ?: return
        viewModelScope.launch(saveDispatcher) {
            runCatching { recipes.save(uri, edit) }.onFailure { Log.w(TAG, "Cannot save recipe", it) }
        }
    }

    private companion object {
        const val TAG = "EditorViewModel"
    }
}
