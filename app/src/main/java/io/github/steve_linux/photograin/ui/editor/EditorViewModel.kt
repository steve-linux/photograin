// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
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
import io.github.steve_linux.photograin.data.Retoucher
import io.github.steve_linux.photograin.data.SaveLocation
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Frame
import io.github.steve_linux.photograin.model.FrameStyle
import io.github.steve_linux.photograin.model.TextItem
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.BrushStroke
import io.github.steve_linux.photograin.model.History
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParamSpec
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.WhiteBalance
import io.github.steve_linux.photograin.model.RangeType
import io.github.steve_linux.photograin.model.MaskRange
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.ParamSpec
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.Retouch
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.RetouchType
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
    val brush: BrushSettings = BrushSettings(),
    /** The preview with the healing and clone spots drawn in, or null when there are none. */
    val retouchedPreview: Bitmap? = null,
    /** In the Retouch tab: false for masks, true for healing and clone. */
    val retouchMode: Boolean = false,
    /** The spot being edited, by index in [EditState.retouch]. */
    val selectedSpot: Int? = null,
    /** Type and size (0..1, relative to the screen) of the next spot. */
    val spotType: RetouchType = RetouchType.HEAL,
    val spotSize: Float = 0.3f,
    /** In the Effects tab: 0 effects, 1 frame, 2 text. */
    val effectsMode: Int = 0,
    /** The text being edited, by index in [EditState.texts]. */
    val selectedText: Int? = null,
    /** A tap on the photo picks a color for this, instead of its usual meaning. */
    val picker: Picker = Picker.NONE,
) {
    /** [selectedText] if it still exists. */
    val activeText: Int? get() = selectedText?.takeIf { it in edit.texts.indices }

    /** [selectedSpot] if it still exists. */
    val activeSpot: Int? get() = selectedSpot?.takeIf { it in edit.retouch.indices }

    /** [selectedMask] if it still exists (undo can remove it). */
    val activeMask: Int? get() = selectedMask?.takeIf { it in edit.masks.indices }
}

/**
 * The brush used to paint masks. [size] (0..1) sets the radius relative to the screen, so
 * zooming in paints finer detail; [hardness] (0..1) is the part painted at full strength.
 */
data class BrushSettings(val size: Float = 0.35f, val hardness: Float = 0.4f, val erase: Boolean = false)

/** What a tap on the photo picks a color for. */
enum class Picker { NONE, MASK_COLOR, WHITE_BALANCE }

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
        watchRetouch()
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

    fun selectTool(tool: ToolGroup) = _state.update { it.copy(tool = tool, picker = Picker.NONE) }

    // --- Eyedropper ----------------------------------------------------------------------------

    fun setPicker(picker: Picker) = _state.update { it.copy(picker = picker) }

    /**
     * Uses the color at source-normalized ([x], [y]) for the current [Picker]: the hue of the
     * selected mask's color range, or the white balance that makes it gray.
     */
    fun pickAt(x: Float, y: Float) {
        val s = _state.value
        val bitmap = s.image?.preview ?: return
        val (r, g, b) = averageColor(bitmap, x, y)
        when (s.picker) {
            Picker.NONE -> return
            Picker.MASK_COLOR -> {
                val range = s.activeMask?.let { s.edit.masks[it].mask.range } ?: return
                updateMask { it.copy(mask = it.mask.copy(range = range.copy(type = RangeType.COLOR, hue = MaskRange.hueOf(r, g, b)))) }
            }
            Picker.WHITE_BALANCE -> {
                val (temperature, tint) = WhiteBalance.neutralize(r, g, b) ?: return
                updateLive { e -> e.copy(adjustments = e.adjustments.copy(temperature = temperature, tint = tint)) }
            }
        }
        _state.update { it.copy(picker = Picker.NONE) }
        commit()
    }

    /** The mean color (0..1) of a small square of [bitmap] around source-normalized ([x], [y]). */
    private fun averageColor(bitmap: Bitmap, x: Float, y: Float): Triple<Float, Float, Float> {
        val cx = (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val cy = (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val half = maxOf(1, maxOf(bitmap.width, bitmap.height) / 400)
        var r = 0f
        var g = 0f
        var b = 0f
        var n = 0
        for (py in (cy - half)..(cy + half)) for (px in (cx - half)..(cx + half)) {
            if (px !in 0 until bitmap.width || py !in 0 until bitmap.height) continue
            val c = bitmap.getPixel(px, py)
            r += (c shr 16 and 0xFF) / 255f
            g += (c shr 8 and 0xFF) / 255f
            b += (c and 0xFF) / 255f
            n++
        }
        return Triple(r / n, g / n, b / n)
    }

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

    fun selectMask(index: Int?) = _state.update { it.copy(selectedMask = index, picker = Picker.NONE) }

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

    fun setBrush(brush: BrushSettings) = _state.update { it.copy(brush = brush) }

    /** Starts a stroke on the selected brush mask at source-normalized ([x], [y]). */
    fun beginStroke(x: Float, y: Float, radius: Float) {
        val b = _state.value.brush
        val stroke = BrushStroke(listOf(x, y), radius, b.hardness, b.erase)
        _state.update { it.copy(maskDragging = true) }
        updateMask { it.copy(mask = it.mask.copy(strokes = it.mask.strokes + stroke)) }
    }

    /** Extends the stroke being painted, skipping points too close to the last one. */
    fun extendStroke(x: Float, y: Float) = updateMask { local ->
        val strokes = local.mask.strokes
        val last = strokes.lastOrNull() ?: return@updateMask local
        val n = last.points.size
        val dx = x - last.points[n - 2]
        val dy = y - last.points[n - 1]
        // A fifth of the radius between points; the radius is in long-edge units, close enough here.
        val step = last.radius * 0.2f
        if (dx * dx + dy * dy < step * step) return@updateMask local
        val grown = last.copy(points = last.points + listOf(x, y))
        local.copy(mask = local.mask.copy(strokes = strokes.dropLast(1) + grown))
    }

    fun endStroke() = setMaskDragging(false)

    fun setShowMask(show: Boolean) = _state.update { it.copy(showMask = show) }

    fun setMaskDragging(dragging: Boolean) {
        _state.update { it.copy(maskDragging = dragging) }
        if (!dragging) commit()
    }

    private fun updateMask(transform: (LocalAdjustment) -> LocalAdjustment) {
        val index = _state.value.activeMask ?: return
        updateLive { e -> e.copy(masks = e.masks.mapIndexed { i, l -> if (i == index) transform(l) else l }) }
    }

    // --- Healing and clone ---------------------------------------------------------------------

    fun setRetouchMode(on: Boolean) = _state.update { it.copy(retouchMode = on) }

    /**
     * Adds a spot at source-normalized ([x], [y]) of [radius] long-edge units, taking its
     * source from the best matching nearby area, and selects it.
     */
    fun addSpot(x: Float, y: Float, radius: Float) {
        val s = _state.value
        val image = s.image ?: return
        if (s.edit.retouch.size >= Retouch.MAX_SPOTS) return
        val type = s.spotType
        viewModelScope.launch {
            val bitmap = s.retouchedPreview ?: image.preview
            val (sx, sy) = withContext(Dispatchers.Default) {
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                val rPx = radius * maxOf(w, h).toDouble()
                Retouch.findSource(w, h, x * w.toDouble(), y * h.toDouble(), rPx, type == RetouchType.HEAL) { px, py ->
                    val c = pixels[py * w + px]
                    floatArrayOf(((c shr 16) and 0xFF).toFloat(), ((c shr 8) and 0xFF).toFloat(), (c and 0xFF).toFloat())
                }.let { (a, b) -> (a / w).toFloat() to (b / h).toFloat() }
            }
            val spot = RetouchSpot(type, x, y, sx, sy, radius)
            updateLive { it.copy(retouch = it.retouch + spot) }
            _state.update { it.copy(selectedSpot = it.edit.retouch.lastIndex) }
            commit()
        }
    }

    fun selectSpot(index: Int?) = _state.update { it.copy(selectedSpot = index) }

    /** Live change of the selected spot, from dragging it or the panel; [commit] when done. */
    fun setSpot(spot: RetouchSpot) {
        val index = _state.value.activeSpot ?: return
        updateLive { e -> e.copy(retouch = e.retouch.mapIndexed { i, old -> if (i == index) spot else old }) }
    }

    fun deleteSpot(index: Int) {
        if (index !in _state.value.edit.retouch.indices) return
        updateLive { e -> e.copy(retouch = e.retouch.filterIndexed { i, _ -> i != index }) }
        _state.update { it.copy(selectedSpot = null) }
        commit()
    }

    /** Type of the selected spot (if any) and of the next ones. */
    fun setSpotType(type: RetouchType) {
        _state.update { it.copy(spotType = type) }
        val index = _state.value.activeSpot ?: return
        setSpot(_state.value.edit.retouch[index].copy(type = type))
        commit()
    }

    fun setSpotSize(size: Float) = _state.update { it.copy(spotSize = size) }

    /** Keeps [EditorUiState.retouchedPreview] in step with the spots, reusing unchanged ones. */
    private fun watchRetouch() {
        val patches = java.util.concurrent.ConcurrentHashMap<RetouchSpot, Retoucher.Patch>()
        var patchesFor: Bitmap? = null
        viewModelScope.launch {
            _state.map { it.image?.preview to it.edit.retouch }.distinctUntilChanged().collectLatest { (preview, spots) ->
                if (preview == null || spots.isEmpty()) {
                    _state.update { it.copy(retouchedPreview = null) }
                    return@collectLatest
                }
                if (patchesFor !== preview) {
                    patches.clear()
                    patchesFor = preview
                }
                val result = withContext(Dispatchers.Default) {
                    val w = preview.width
                    val h = preview.height
                    val copy = preview.copy(Bitmap.Config.ARGB_8888, true)
                    Retoucher.apply(copy, Rect(0, 0, w, h), 1, w, h, spots, patches) { r, _ ->
                        val part = Bitmap.createBitmap(preview, r.left, r.top, r.width(), r.height())
                        if (part === preview) preview.copy(Bitmap.Config.ARGB_8888, false) else part
                    }
                    patches.keys.retainAll(spots.toSet())
                    copy
                }
                _state.update { if (it.image?.preview === preview) it.copy(retouchedPreview = result) else it }
            }
        }
    }

    // --- Frame and text ------------------------------------------------------------------------

    fun setEffectsMode(mode: Int) = _state.update { it.copy(effectsMode = mode) }

    /** Live frame change; [commit] when a slider is released. */
    fun setFrame(frame: Frame) = updateLive { it.copy(frame = frame) }

    /** Adds [text] and selects it: on a polaroid's bottom border if it has no caption yet, else in the middle. */
    fun addText(text: String) {
        val e = _state.value.edit
        if (text.isBlank() || e.texts.size >= TextItem.MAX_ITEMS) return
        val image = mapper()?.outputSize
        // Each new text a little lower than the last, so they do not land on top of each other.
        var item = TextItem(text.trim(), y = 0.5f + 0.08f * (e.texts.size % 4))
        if (image != null && e.frame.style == FrameStyle.POLAROID && e.texts.isEmpty()) {
            val layout = e.frame.layout(image)
            val bottomTop = layout.imageTop + image.height
            val y = (bottomTop + layout.size.height) / 2f / layout.size.height
            // A caption on the polaroid's bottom border: dark on a light border, white on a dark one.
            val c = e.frame.color
            val light = 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF) > 140
            item = item.copy(y = y, color = if (light) 0xFF333333.toInt() else Frame.WHITE, shadow = false)
        }
        updateLive { it.copy(texts = it.texts + item) }
        _state.update { it.copy(selectedText = it.edit.texts.lastIndex) }
        commit()
    }

    fun selectText(index: Int?) = _state.update { it.copy(selectedText = index) }

    /** Live change of the selected text; [commit] when done. */
    fun setText(item: TextItem) {
        val index = _state.value.activeText ?: return
        updateLive { e -> e.copy(texts = e.texts.mapIndexed { i, old -> if (i == index) item else old }) }
    }

    fun deleteText(index: Int) {
        if (index !in _state.value.edit.texts.indices) return
        updateLive { e -> e.copy(texts = e.texts.filterIndexed { i, _ -> i != index }) }
        _state.update { it.copy(selectedText = null) }
        commit()
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

    /** Live perspective correction; like straightening, the crop shrinks to keep out empty corners. */
    fun setPerspective(vertical: Float, horizontal: Float) = updateLive { e ->
        val g = e.geometry.copy(vertical = vertical.coerceIn(-1f, 1f), horizontal = horizontal.coerceIn(-1f, 1f))
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
